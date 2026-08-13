package com.banryeokkurumi.inventory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record Stock(
        UUID skuId,
        int availableQuantity,
        int reservedQuantity,
        int soldQuantity,
        Map<UUID, Reservation> reservations
) {
    public Stock {
        if (skuId == null || availableQuantity < 0 || reservedQuantity < 0 || soldQuantity < 0) {
            throw new IllegalArgumentException("재고 값이 올바르지 않습니다.");
        }
        reservations = Map.copyOf(reservations);
    }

    public static Stock open(UUID skuId, int quantity) {
        return new Stock(skuId, quantity, 0, 0, Map.of());
    }

    public Stock reserve(UUID orderId, int quantity, Instant reservedAt) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("예약 수량은 1 이상이어야 합니다.");
        }
        Reservation existing = reservations.get(orderId);
        if (existing != null) {
            return this;
        }
        if (availableQuantity < quantity) {
            throw new InsufficientStockException();
        }
        Map<UUID, Reservation> next = new HashMap<>(reservations);
        next.put(orderId, new Reservation(quantity, reservedAt));
        return new Stock(skuId, availableQuantity - quantity, reservedQuantity + quantity, soldQuantity, next);
    }

    public Stock commit(UUID orderId) {
        Reservation reservation = reservations.get(orderId);
        if (reservation == null) {
            return this;
        }
        Map<UUID, Reservation> next = new HashMap<>(reservations);
        next.remove(orderId);
        return new Stock(skuId, availableQuantity, reservedQuantity - reservation.quantity(),
                soldQuantity + reservation.quantity(), next);
    }

    public Stock release(UUID orderId) {
        Reservation reservation = reservations.get(orderId);
        if (reservation == null) {
            return this;
        }
        Map<UUID, Reservation> next = new HashMap<>(reservations);
        next.remove(orderId);
        return new Stock(skuId, availableQuantity + reservation.quantity(),
                reservedQuantity - reservation.quantity(), soldQuantity, next);
    }

    public Stock cancelCommitted(UUID orderId, int quantity) {
        if (quantity <= 0 || quantity > soldQuantity) {
            throw new IllegalArgumentException("판매 취소 수량이 올바르지 않습니다.");
        }
        return new Stock(skuId, availableQuantity + quantity, reservedQuantity,
                soldQuantity - quantity, reservations);
    }

    public record Reservation(int quantity, Instant reservedAt) {
        public Reservation {
            if (quantity <= 0 || reservedAt == null) {
                throw new IllegalArgumentException("예약 값이 올바르지 않습니다.");
            }
        }
    }
}
