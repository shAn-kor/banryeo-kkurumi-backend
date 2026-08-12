package com.banryeokkurumi.promotion;

public final class CouponSoldOutException extends RuntimeException {
    public CouponSoldOutException() {
        super("쿠폰이 모두 발급되었습니다.");
    }
}
