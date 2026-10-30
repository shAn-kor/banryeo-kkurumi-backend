package com.banryeokkurumi.ordering;

import com.banryeokkurumi.contracts.CommerceEvents;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OrderRepository {
  private final JdbcClient jdbc;

  OrderRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void create(OrderData order, List<CommerceEvents.OrderLine> lines) {
    jdbc.sql(
            """
                INSERT INTO ordering_order(id,member_login_id,status,total_amount,discount_amount,payable_amount,
                    issued_coupon_id,payment_scenario,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at)
                VALUES (:id,:member,:status,:total,0,:total,:coupon,:scenario,:name,:phone,:postal,:line1,:line2,:now,:now)
                """)
        .param("id", order.id().toString())
        .param("member", order.memberLoginId())
        .param("status", order.status().name())
        .param("total", order.totalAmount())
        .param("coupon", order.issuedCouponId() == null ? null : order.issuedCouponId().toString())
        .param("scenario", order.paymentScenario())
        .param("name", order.address().recipientName())
        .param("phone", order.address().recipientPhone())
        .param("postal", order.address().postalCode())
        .param("line1", order.address().addressLine1())
        .param("line2", order.address().addressLine2())
        .param("now", order.updatedAt())
        .update();
    lines.forEach(
        line ->
            jdbc.sql(
                    """
                INSERT INTO ordering_order_item(id,order_id,product_id,sku_id,product_name,option_name,unit_price,quantity,line_amount)
                VALUES (:id,:orderId,:productId,:skuId,:name,:option,:unitPrice,:quantity,:lineAmount)
                """)
                .param("id", line.orderItemId().toString())
                .param("orderId", order.id().toString())
                .param("productId", line.productId().toString())
                .param("skuId", line.skuId().toString())
                .param("name", line.productName())
                .param("option", line.optionName())
                .param("unitPrice", line.unitPrice())
                .param("quantity", line.quantity())
                .param("lineAmount", Math.multiplyExact(line.unitPrice(), line.quantity()))
                .update());
  }

  private static final String ORDER_COLUMNS =
      """
      SELECT id,member_login_id,status,total_amount,discount_amount,payable_amount,issued_coupon_id,payment_scenario,
             recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at
        FROM ordering_order
      """;

  Optional<OrderData> find(UUID orderId) {
    return jdbc.sql(ORDER_COLUMNS + " WHERE id=:id")
        .param("id", orderId.toString())
        .query(OrderRepository::order)
        .optional();
  }

  private static OrderData order(ResultSet rs, int row) throws SQLException {
    return new OrderData(
        UUID.fromString(rs.getString(1)),
        rs.getString(2),
        OrderStatus.valueOf(rs.getString(3)),
        rs.getLong(4),
        rs.getLong(5),
        rs.getLong(6),
        rs.getString(7) == null ? null : UUID.fromString(rs.getString(7)),
        rs.getString(8),
        new CommerceEvents.EncryptedAddress(
            rs.getString(9),
            rs.getString(10),
            rs.getString(11),
            rs.getString(12),
            rs.getString(13)),
        rs.getTimestamp(14).toInstant(),
        rs.getTimestamp(15).toInstant());
  }

  List<CommerceEvents.OrderLine> lines(UUID orderId) {
    return jdbc.sql(LINE_COLUMNS + " WHERE order_id=:orderId ORDER BY id")
        .param("orderId", orderId.toString())
        .query(OrderRepository::line)
        .list();
  }

  /** Loads the lines of several orders in one query, keyed by order id. */
  Map<UUID, List<CommerceEvents.OrderLine>> linesByOrder(List<UUID> orderIds) {
    Map<UUID, List<CommerceEvents.OrderLine>> result = new LinkedHashMap<>();
    if (orderIds.isEmpty()) return result;
    orderIds.forEach(id -> result.put(id, new ArrayList<>()));
    jdbc.sql(LINE_COLUMNS + " WHERE order_id IN (:orderIds) ORDER BY order_id, id")
        .param("orderIds", orderIds.stream().map(UUID::toString).toList())
        .query(
            (RowCallbackHandler)
                rs -> result.get(UUID.fromString(rs.getString(8))).add(line(rs, 0)));
    return result;
  }

  private static final String LINE_COLUMNS =
      "SELECT id,product_id,sku_id,product_name,option_name,unit_price,quantity,order_id FROM ordering_order_item";

  private static CommerceEvents.OrderLine line(ResultSet rs, int row) throws SQLException {
    return new CommerceEvents.OrderLine(
        UUID.fromString(rs.getString(1)),
        UUID.fromString(rs.getString(2)),
        UUID.fromString(rs.getString(3)),
        rs.getString(4),
        rs.getString(5),
        rs.getLong(6),
        rs.getInt(7));
  }

  void status(UUID orderId, OrderStatus expected, OrderStatus next, Instant now) {
    int updated =
        jdbc.sql(
                "UPDATE ordering_order SET status=:next, updated_at=:now WHERE id=:id AND status=:expected")
            .param("next", next.name())
            .param("now", now)
            .param("id", orderId.toString())
            .param("expected", expected.name())
            .update();
    if (updated == 0) {
      OrderData current =
          find(orderId).orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
      if (current.status() != next) throw new OrderStateException(expected + " 상태가 아닙니다.");
    }
  }

  void couponReserved(UUID orderId, long discount, Instant now) {
    int updated =
        jdbc.sql(
                """
                UPDATE ordering_order SET status='PAYMENT_PENDING', discount_amount=:discount,
                    payable_amount=GREATEST(total_amount-:discount,0), updated_at=:now
                 WHERE id=:id AND status='STOCK_RESERVED'
                """)
            .param("discount", discount)
            .param("now", now)
            .param("id", orderId.toString())
            .update();
    if (updated == 0) throw new OrderStateException("쿠폰을 예약할 수 없는 주문 상태입니다.");
  }

  List<OrderData> findByMember(String memberLoginId) {
    return jdbc.sql(ORDER_COLUMNS + " WHERE member_login_id=:member ORDER BY created_at DESC")
        .param("member", memberLoginId)
        .query(OrderRepository::order)
        .list();
  }

  record OrderData(
      UUID id,
      String memberLoginId,
      OrderStatus status,
      long totalAmount,
      long discountAmount,
      long payableAmount,
      UUID issuedCouponId,
      String paymentScenario,
      CommerceEvents.EncryptedAddress address,
      Instant createdAt,
      Instant updatedAt) {}
}
