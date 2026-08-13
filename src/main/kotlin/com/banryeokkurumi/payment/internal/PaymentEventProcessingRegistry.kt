package com.banryeokkurumi.payment.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Repository
internal class PaymentEventProcessingRegistry(
    private val jdbc: JdbcClient,
    private val clock: Clock,
) {
    fun claim(eventId: UUID): Boolean = jdbc.sql(
        "INSERT IGNORE INTO processed_event(listener_id,event_id,processed_at) VALUES ('payment',:eventId,:now)",
    ).param("eventId", eventId.toString()).param("now", Instant.now(clock)).update() == 1
}
