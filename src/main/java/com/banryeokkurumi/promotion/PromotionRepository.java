package com.banryeokkurumi.promotion;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class PromotionRepository {
    private final JdbcClient jdbc;
    PromotionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    UUID createCampaign(CreateCampaign value) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO promotion_campaign(id,name,discount_type,discount_value,maximum_discount,minimum_order_amount,
                    scope_type,scope_id,total_quantity,issued_quantity,starts_at,ends_at)
                VALUES (:id,:name,:type,:value,:maxDiscount,:minimumAmount,:scopeType,:scopeId,:total,0,:startsAt,:endsAt)
                """).param("id", id.toString()).param("name", value.name()).param("type", value.type())
                .param("value", value.value()).param("maxDiscount", value.maximumDiscount()).param("minimumAmount", value.minimumOrderAmount())
                .param("scopeType", value.scopeType()).param("scopeId", value.scopeId() == null ? null : value.scopeId().toString())
                .param("total", value.totalQuantity()).param("startsAt", value.startsAt()).param("endsAt", value.endsAt()).update();
        return id;
    }

    UUID issue(UUID campaignId, String memberLoginId, Instant now) {
        int updated = jdbc.sql("""
                UPDATE promotion_campaign SET issued_quantity=issued_quantity+1
                 WHERE id=:id AND issued_quantity<total_quantity AND starts_at<=:now AND ends_at>=:now
                """).param("id", campaignId.toString()).param("now", now).update();
        if (updated == 0) throw new CouponSoldOutException();
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO promotion_issued_coupon(id,campaign_id,member_login_id,status,issued_at) VALUES (:id,:campaignId,:member,'AVAILABLE',:now)")
                .param("id", id.toString()).param("campaignId", campaignId.toString()).param("member", memberLoginId).param("now", now).update();
        return id;
    }

    long reserve(UUID issuedCouponId, String memberLoginId, UUID orderId, long orderAmount) {
        CouponData coupon = find(issuedCouponId, memberLoginId).orElseThrow(() -> new IllegalArgumentException("사용 가능한 쿠폰이 없습니다."));
        if (!"AVAILABLE".equals(coupon.status())) {
            if (orderId.equals(coupon.reservedOrderId())) return coupon.discount(orderAmount);
            throw new IllegalStateException("이미 사용 중인 쿠폰입니다.");
        }
        int updated = jdbc.sql("UPDATE promotion_issued_coupon SET status='RESERVED', reserved_order_id=:orderId WHERE id=:id AND status='AVAILABLE'")
                .param("orderId", orderId.toString()).param("id", issuedCouponId.toString()).update();
        if (updated == 0) throw new IllegalStateException("쿠폰 예약에 실패했습니다.");
        return coupon.discount(orderAmount);
    }

    void use(UUID orderId) { jdbc.sql("UPDATE promotion_issued_coupon SET status='USED' WHERE reserved_order_id=:orderId AND status='RESERVED'").param("orderId", orderId.toString()).update(); }
    void release(UUID orderId) { jdbc.sql("UPDATE promotion_issued_coupon SET status='AVAILABLE', reserved_order_id=NULL WHERE reserved_order_id=:orderId AND status='RESERVED'").param("orderId", orderId.toString()).update(); }

    List<CouponView> memberCoupons(String memberLoginId) {
        return jdbc.sql("SELECT id,campaign_id,status,issued_at FROM promotion_issued_coupon WHERE member_login_id=:member ORDER BY issued_at DESC")
                .param("member", memberLoginId).query((rs, row) -> new CouponView(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3), rs.getTimestamp(4).toInstant())).list();
    }

    Optional<CouponData> find(UUID issuedCouponId, String memberLoginId) {
        return jdbc.sql("""
                SELECT c.discount_type,c.discount_value,c.maximum_discount,c.minimum_order_amount,i.status,i.reserved_order_id
                  FROM promotion_issued_coupon i JOIN promotion_campaign c ON c.id=i.campaign_id
                 WHERE i.id=:id AND i.member_login_id=:member
                """).param("id", issuedCouponId.toString()).param("member", memberLoginId)
                .query((rs, row) -> new CouponData(rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4), rs.getString(5),
                        rs.getString(6) == null ? null : UUID.fromString(rs.getString(6)))).optional();
    }

    record CreateCampaign(String name, String type, int value, long maximumDiscount, long minimumOrderAmount,
                          String scopeType, UUID scopeId, int totalQuantity, Instant startsAt, Instant endsAt) {}
    record CouponData(String type, int value, long maximumDiscount, long minimumOrderAmount, String status, UUID reservedOrderId) {
        long discount(long amount) { return "FIXED".equals(type) ? Math.min(amount, value) : Math.min(maximumDiscount, amount * value / 100); }
    }
    record CouponView(UUID id, UUID campaignId, String status, Instant issuedAt) {}
}
