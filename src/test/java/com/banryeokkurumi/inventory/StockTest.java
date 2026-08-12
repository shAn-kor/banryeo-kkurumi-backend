package com.banryeokkurumi.inventory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Test
    void reserve_가용재고안에서예약한다() {
        Stock stock = Stock.open(UUID.randomUUID(), 10);

        Stock reserved = stock.reserve(UUID.randomUUID(), 4, Instant.parse("2026-08-13T00:00:00Z"));

        assertThat(reserved.availableQuantity()).isEqualTo(6);
        assertThat(reserved.reservedQuantity()).isEqualTo(4);
    }

    @Test
    void reserve_가용재고를초과하면거부한다() {
        Stock stock = Stock.open(UUID.randomUUID(), 3);

        assertThatThrownBy(() -> stock.reserve(UUID.randomUUID(), 4, Instant.now()))
                .isInstanceOf(InsufficientStockException.class);
    }
}
