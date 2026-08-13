package com.banryeokkurumi.payment.internal

import com.banryeokkurumi.payment.DemoPaymentProvider
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
internal class PaymentRepository(private val jdbc: JdbcClient) {
    fun create(
        orderId: UUID,
        member: String,
        amount: Long,
        scenario: DemoPaymentProvider.Scenario,
        key: String,
        now: Instant,
    ): PaymentData {
        jdbc.sql(
            """
            INSERT INTO payment_transaction(id,order_id,member_login_id,amount,status,scenario,idempotency_key,created_at,updated_at)
            VALUES (:id,:orderId,:member,:amount,'REQUESTED',:scenario,:key,:now,:now)
            ON DUPLICATE KEY UPDATE id=id
            """.trimIndent(),
        ).param("id", UUID.randomUUID().toString())
            .param("orderId", orderId.toString())
            .param("member", member)
            .param("amount", amount)
            .param("scenario", scenario.name)
            .param("key", key)
            .param("now", now)
            .update()
        return require(orderId)
    }

    fun find(orderId: UUID): PaymentData? = jdbc.sql(
        """
        SELECT order_id,member_login_id,amount,status,scenario,idempotency_key,provider_transaction_id,failure_reason,
               reconciliation_deadline,next_reconciliation_at,updated_at FROM payment_transaction WHERE order_id=:orderId
        """.trimIndent(),
    ).param("orderId", orderId.toString()).query { resultSet, _ ->
        PaymentData(
            orderId = UUID.fromString(resultSet.getString(1)),
            memberLoginId = resultSet.getString(2),
            amount = resultSet.getLong(3),
            status = resultSet.getString(4),
            scenario = DemoPaymentProvider.Scenario.valueOf(resultSet.getString(5)),
            idempotencyKey = resultSet.getString(6),
            providerTransactionId = resultSet.getString(7),
            failureReason = resultSet.getString(8),
            deadline = resultSet.getTimestamp(9)?.toInstant(),
            nextAt = resultSet.getTimestamp(10)?.toInstant(),
            updatedAt = resultSet.getTimestamp(11).toInstant(),
        )
    }.optional().orElse(null)

    fun require(orderId: UUID): PaymentData = find(orderId)
        ?: throw IllegalArgumentException("결제를 찾을 수 없습니다.")

    fun success(orderId: UUID, transactionId: String, now: Instant): Transition = transition(
        orderId,
        jdbc.sql(
            "UPDATE payment_transaction SET status='SUCCEEDED',provider_transaction_id=:tx,failure_reason=NULL,updated_at=:now " +
                "WHERE order_id=:id AND status IN ('REQUESTED','UNKNOWN')",
        ).param("tx", transactionId).param("now", now).param("id", orderId.toString()).update(),
    )

    fun fail(orderId: UUID, reason: String, now: Instant): Transition = transition(
        orderId,
        jdbc.sql(
            "UPDATE payment_transaction SET status='FAILED',failure_reason=:reason,updated_at=:now " +
                "WHERE order_id=:id AND status IN ('REQUESTED','UNKNOWN')",
        ).param("reason", reason).param("now", now).param("id", orderId.toString()).update(),
    )

    fun unknown(orderId: UUID, reason: String, next: Instant, deadline: Instant, now: Instant): PaymentData {
        jdbc.sql(
            "UPDATE payment_transaction SET status='UNKNOWN',failure_reason=:reason,next_reconciliation_at=:next," +
                "reconciliation_deadline=:deadline,updated_at=:now WHERE order_id=:id AND status='REQUESTED'",
        ).param("reason", reason).param("next", next).param("deadline", deadline).param("now", now)
            .param("id", orderId.toString()).update()
        return require(orderId)
    }

    fun reschedule(orderId: UUID, next: Instant, now: Instant): PaymentData {
        jdbc.sql(
            "UPDATE payment_transaction SET next_reconciliation_at=:next,updated_at=:now " +
                "WHERE order_id=:id AND status='UNKNOWN'",
        ).param("next", next).param("now", now).param("id", orderId.toString()).update()
        return require(orderId)
    }

    fun cancel(orderId: UUID, now: Instant): PaymentData {
        jdbc.sql("UPDATE payment_transaction SET status='CANCELLED',updated_at=:now WHERE order_id=:id AND status<>'CANCELLED'")
            .param("now", now).param("id", orderId.toString()).update()
        return require(orderId)
    }

    fun due(now: Instant): List<PaymentData> = jdbc.sql(
        "SELECT order_id FROM payment_transaction WHERE status='UNKNOWN' AND next_reconciliation_at<=:now",
    ).param("now", now).query(String::class.java).list().map(UUID::fromString).map(::require)

    private fun transition(orderId: UUID, updated: Int) = Transition(require(orderId), updated == 1)

    data class Transition(val payment: PaymentData, val changed: Boolean)

    data class PaymentData(
        val orderId: UUID,
        val memberLoginId: String,
        val amount: Long,
        val status: String,
        val scenario: DemoPaymentProvider.Scenario,
        val idempotencyKey: String,
        val providerTransactionId: String?,
        val failureReason: String?,
        val deadline: Instant?,
        val nextAt: Instant?,
        val updatedAt: Instant,
    )
}
