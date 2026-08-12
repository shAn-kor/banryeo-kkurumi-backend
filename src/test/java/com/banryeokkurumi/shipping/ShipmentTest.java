package com.banryeokkurumi.shipping;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentTest {

    @Test
    void confirm_배송완료뒤직접확정한다() {
        Shipment shipment = Shipment.prepare(UUID.randomUUID()).ship().deliver(Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(shipment.confirm(Instant.parse("2026-08-02T00:00:00Z")).status())
                .isEqualTo(ShipmentStatus.CONFIRMED);
    }

    @Test
    void autoConfirm_배송완료칠일뒤자동확정한다() {
        Instant deliveredAt = Instant.parse("2026-08-01T00:00:00Z");
        Shipment shipment = Shipment.prepare(UUID.randomUUID()).ship().deliver(deliveredAt);

        assertThat(shipment.autoConfirm(deliveredAt.plus(7, ChronoUnit.DAYS)).status())
                .isEqualTo(ShipmentStatus.CONFIRMED);
    }
}
