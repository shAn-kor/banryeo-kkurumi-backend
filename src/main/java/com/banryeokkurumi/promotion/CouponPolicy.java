package com.banryeokkurumi.promotion;

public record CouponPolicy(Type type, int value, long maximumDiscount, long minimumOrderAmount) {
    public CouponPolicy {
        if (value <= 0 || maximumDiscount < 0 || minimumOrderAmount < 0) {
            throw new IllegalArgumentException("쿠폰 정책 값이 올바르지 않습니다.");
        }
        if (type == Type.PERCENTAGE && value > 100) {
            throw new IllegalArgumentException("할인율은 100 이하여야 합니다.");
        }
    }

    public static CouponPolicy percentage(int percent, long maximumDiscount, long minimumOrderAmount) {
        return new CouponPolicy(Type.PERCENTAGE, percent, maximumDiscount, minimumOrderAmount);
    }

    public static CouponPolicy fixed(long amount, long minimumOrderAmount) {
        if (amount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("정액 할인 금액이 너무 큽니다.");
        }
        return new CouponPolicy(Type.FIXED, (int) amount, amount, minimumOrderAmount);
    }

    public long discount(long orderAmount) {
        if (orderAmount < minimumOrderAmount) {
            return 0;
        }
        long calculated = type == Type.FIXED ? value : orderAmount * value / 100;
        return Math.min(orderAmount, Math.min(calculated, maximumDiscount));
    }

    public enum Type { FIXED, PERCENTAGE }
}
