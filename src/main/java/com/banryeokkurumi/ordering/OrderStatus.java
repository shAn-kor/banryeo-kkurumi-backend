package com.banryeokkurumi.ordering;

public enum OrderStatus {
    SUBMITTED,
    STOCK_RESERVED,
    COUPON_RESERVED,
    PAYMENT_PENDING,
    PAID,
    FULFILLING,
    SHIPPED,
    DELIVERED,
    CONFIRMED,
    CANCELLATION_REQUESTED,
    CANCELLED,
    FAILED
}
