package com.banryeokkurumi.payment;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
class PaymentEventProcessingRegistry {
    private final JdbcClient jdbc;
    PaymentEventProcessingRegistry(JdbcClient jdbc) { this.jdbc = jdbc; }
    boolean claim(UUID eventId) {
        return jdbc.sql("INSERT IGNORE INTO processed_event(listener_id,event_id,processed_at) VALUES ('payment',:eventId,:now)")
                .param("eventId", eventId.toString()).param("now", Instant.now()).update() == 1;
    }
}
