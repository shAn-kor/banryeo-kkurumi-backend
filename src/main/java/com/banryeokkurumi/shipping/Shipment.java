package com.banryeokkurumi.shipping;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record Shipment(UUID orderId, ShipmentStatus status, Instant deliveredAt, Instant confirmedAt) {
    public static Shipment prepare(UUID orderId) {
        return new Shipment(orderId, ShipmentStatus.PREPARING, null, null);
    }

    public Shipment ship() {
        if (status != ShipmentStatus.PREPARING) {
            throw new IllegalStateException("배송 준비 상태가 아닙니다.");
        }
        return new Shipment(orderId, ShipmentStatus.SHIPPED, null, null);
    }

    public Shipment deliver(Instant at) {
        if (status != ShipmentStatus.SHIPPED) {
            throw new IllegalStateException("출고 상태가 아닙니다.");
        }
        return new Shipment(orderId, ShipmentStatus.DELIVERED, at, null);
    }

    public Shipment confirm(Instant at) {
        if (status != ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("배송 완료 상태가 아닙니다.");
        }
        return new Shipment(orderId, ShipmentStatus.CONFIRMED, deliveredAt, at);
    }

    public Shipment autoConfirm(Instant now) {
        if (status == ShipmentStatus.DELIVERED && !now.isBefore(deliveredAt.plus(7, ChronoUnit.DAYS))) {
            return confirm(now);
        }
        return this;
    }

    public Shipment cancel() {
        if (status == ShipmentStatus.CANCELLED) {
            return this;
        }
        if (status != ShipmentStatus.PREPARING) {
            throw new IllegalStateException("출고된 배송은 취소할 수 없습니다.");
        }
        return new Shipment(orderId, ShipmentStatus.CANCELLED, null, null);
    }
}
