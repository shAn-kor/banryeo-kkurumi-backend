package com.banryeokkurumi.ordering;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class OrderRepository {
    private final JdbcClient jdbc;
    OrderRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void create(OrderData order, List<CommerceEvents.OrderLine> lines) {
        jdbc.sql("""
                INSERT INTO ordering_order(id,member_login_id,status,total_amount,discount_amount,payable_amount,
                    issued_coupon_id,payment_scenario,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at)
                VALUES (:id,:member,:status,:total,0,:total,:coupon,:scenario,:name,:phone,:postal,:line1,:line2,:now,:now)
                """).param("id", order.id().toString()).param("member", order.memberLoginId()).param("status", order.status().name())
                .param("total", order.totalAmount()).param("coupon", order.issuedCouponId() == null ? null : order.issuedCouponId().toString())
                .param("scenario", order.paymentScenario()).param("name", order.address().recipientName()).param("phone", order.address().recipientPhone())
                .param("postal", order.address().postalCode()).param("line1", order.address().addressLine1()).param("line2", order.address().addressLine2())
                .param("now", order.updatedAt()).update();
        lines.forEach(line -> jdbc.sql("""
                INSERT INTO ordering_order_item(id,order_id,product_id,sku_id,product_name,option_name,unit_price,quantity,line_amount)
                VALUES (:id,:orderId,:productId,:skuId,:name,:option,:unitPrice,:quantity,:lineAmount)
                """).param("id", line.orderItemId().toString()).param("orderId", order.id().toString())
                .param("productId", line.productId().toString()).param("skuId", line.skuId().toString())
                .param("name", line.productName()).param("option", line.optionName()).param("unitPrice", line.unitPrice())
                .param("quantity", line.quantity()).param("lineAmount", Math.multiplyExact(line.unitPrice(), line.quantity())).update());
    }

    Optional<OrderData> find(UUID orderId) {
        return jdbc.sql("""
                SELECT id,member_login_id,status,total_amount,discount_amount,payable_amount,issued_coupon_id,payment_scenario,
                       recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at,updated_at
                  FROM ordering_order WHERE id=:id
                """).param("id", orderId.toString()).query((rs, row) -> new OrderData(UUID.fromString(rs.getString(1)), rs.getString(2),
                        OrderStatus.valueOf(rs.getString(3)), rs.getLong(4), rs.getLong(5), rs.getLong(6),
                        rs.getString(7) == null ? null : UUID.fromString(rs.getString(7)), rs.getString(8),
                        new CommerceEvents.EncryptedAddress(rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13)),
                        rs.getTimestamp(14).toInstant(), rs.getTimestamp(15).toInstant())).optional();
    }

    List<CommerceEvents.OrderLine> lines(UUID orderId) {
        return jdbc.sql("SELECT id,product_id,sku_id,product_name,option_name,unit_price,quantity FROM ordering_order_item WHERE order_id=:orderId ORDER BY id")
                .param("orderId", orderId.toString()).query((rs, row) -> new CommerceEvents.OrderLine(UUID.fromString(rs.getString(1)),
                        UUID.fromString(rs.getString(2)), UUID.fromString(rs.getString(3)), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getInt(7))).list();
    }

    void status(UUID orderId, OrderStatus expected, OrderStatus next, Instant now) {
        int updated = jdbc.sql("UPDATE ordering_order SET status=:next, updated_at=:now WHERE id=:id AND status=:expected")
                .param("next", next.name()).param("now", now).param("id", orderId.toString()).param("expected", expected.name()).update();
        if (updated == 0) {
            OrderData current = find(orderId).orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
            if (current.status() != next) throw new OrderStateException(expected + " 상태가 아닙니다.");
        }
    }

    void couponReserved(UUID orderId, long discount, Instant now) {
        int updated = jdbc.sql("""
                UPDATE ordering_order SET status='PAYMENT_PENDING', discount_amount=:discount,
                    payable_amount=GREATEST(total_amount-:discount,0), updated_at=:now
                 WHERE id=:id AND status='STOCK_RESERVED'
                """).param("discount", discount).param("now", now).param("id", orderId.toString()).update();
        if (updated == 0) throw new OrderStateException("쿠폰을 예약할 수 없는 주문 상태입니다.");
    }

    List<OrderData> findByMember(String memberLoginId) {
        return jdbc.sql("SELECT id FROM ordering_order WHERE member_login_id=:member ORDER BY created_at DESC")
                .param("member", memberLoginId).query(String.class).list().stream().map(UUID::fromString).map(this::find).flatMap(Optional::stream).toList();
    }

    record OrderData(UUID id, String memberLoginId, OrderStatus status, long totalAmount, long discountAmount,
                     long payableAmount, UUID issuedCouponId, String paymentScenario,
                     CommerceEvents.EncryptedAddress address, Instant createdAt, Instant updatedAt) {}
}
