package com.banryeokkurumi.ordering;

import java.util.UUID;

public record OrderProcess(UUID orderId, OrderStatus status, boolean couponRequired) {
    public OrderProcess {
        if (orderId == null || status == null) {
            throw new IllegalArgumentException("주문 프로세스 값이 올바르지 않습니다.");
        }
    }

    public static OrderProcess submitted(UUID orderId, boolean couponRequired) {
        return new OrderProcess(orderId, OrderStatus.SUBMITTED, couponRequired);
    }

    public OrderProcess stockReserved() {
        require(OrderStatus.SUBMITTED);
        return new OrderProcess(orderId, couponRequired ? OrderStatus.STOCK_RESERVED : OrderStatus.PAYMENT_PENDING, couponRequired);
    }

    public OrderProcess couponReserved() {
        require(OrderStatus.STOCK_RESERVED);
        return new OrderProcess(orderId, OrderStatus.PAYMENT_PENDING, couponRequired);
    }

    public OrderProcess paymentSucceeded() {
        require(OrderStatus.PAYMENT_PENDING);
        return new OrderProcess(orderId, OrderStatus.PAID, couponRequired);
    }

    public OrderProcess shipmentCreated() {
        require(OrderStatus.PAID);
        return new OrderProcess(orderId, OrderStatus.FULFILLING, couponRequired);
    }

    public OrderProcess shipped() {
        require(OrderStatus.FULFILLING);
        return new OrderProcess(orderId, OrderStatus.SHIPPED, couponRequired);
    }

    public OrderProcess requestCancellation() {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED || status == OrderStatus.CONFIRMED) {
            throw new OrderStateException("출고된 주문은 취소할 수 없습니다.");
        }
        if (status == OrderStatus.CANCELLED) {
            return this;
        }
        return new OrderProcess(orderId, OrderStatus.CANCELLATION_REQUESTED, couponRequired);
    }

    private void require(OrderStatus expected) {
        if (status != expected) {
            throw new OrderStateException(expected + " 상태에서만 처리할 수 있습니다.");
        }
    }
}
