package com.banryeokkurumi.payment

import com.banryeokkurumi.contracts.CommerceEvents
import com.banryeokkurumi.payment.internal.PaymentRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Service
class PaymentApplicationService internal constructor(
    private val repository: PaymentRepository,
    private val clock: Clock,
    private val events: ApplicationEventPublisher,
    @Value("\${app.payment.reconciliation-delay:10s}") private val reconciliationDelay: Duration,
    @Value("\${app.payment.reconciliation-window:10m}") private val reconciliationWindow: Duration,
) {
    @Transactional
    fun create(orderId: UUID, member: String, amount: Long, scenario: String, key: String): PaymentView =
        repository.create(orderId, member, amount, DemoPaymentProvider.Scenario.valueOf(scenario), key, Instant.now(clock)).toView()

    @Transactional
    fun success(orderId: UUID, transactionId: String): PaymentView {
        val transition = repository.success(orderId, transactionId, Instant.now(clock))
        if (transition.changed) {
            events.publishEvent(CommerceEvents.PaymentSucceeded(UUID.randomUUID(), Instant.now(clock), 1, orderId, transactionId))
        }
        return transition.payment.toView()
    }

    @Transactional
    fun fail(orderId: UUID, reason: String): PaymentView {
        val transition = repository.fail(orderId, reason, Instant.now(clock))
        if (transition.changed) {
            events.publishEvent(CommerceEvents.PaymentFailed(UUID.randomUUID(), Instant.now(clock), 1, orderId, reason))
        }
        return transition.payment.toView()
    }

    @Transactional
    fun unknown(orderId: UUID, reason: String): PaymentView {
        val current = Instant.now(clock)
        return repository.unknown(
            orderId,
            reason,
            current.plus(reconciliationDelay),
            current.plus(reconciliationWindow),
            current,
        ).toView()
    }

    @Transactional
    fun reschedule(orderId: UUID): PaymentView {
        val current = Instant.now(clock)
        return repository.reschedule(orderId, current.plus(reconciliationDelay), current).toView()
    }

    @Transactional
    fun cancel(orderId: UUID): PaymentView = repository.cancel(orderId, Instant.now(clock)).toView()

    @Transactional(readOnly = true)
    fun find(orderId: UUID): PaymentView = repository.require(orderId).toView()

    @Transactional(readOnly = true)
    fun findOptional(orderId: UUID): Optional<PaymentView> = Optional.ofNullable(repository.find(orderId)?.toView())

    @Transactional(readOnly = true)
    fun due(): List<PaymentView> = repository.due(Instant.now(clock)).map(PaymentRepository.PaymentData::toView)

    @JvmRecord
    data class PaymentView(
        val orderId: UUID,
        val memberLoginId: String,
        val amount: Long,
        val status: String,
        val scenario: DemoPaymentProvider.Scenario,
        val providerTransactionId: String?,
        val failureReason: String?,
        val reconciliationDeadline: Instant?,
        val nextReconciliationAt: Instant?,
        val updatedAt: Instant,
    )
}

private fun PaymentRepository.PaymentData.toView() = PaymentApplicationService.PaymentView(
    orderId = orderId,
    memberLoginId = memberLoginId,
    amount = amount,
    status = status,
    scenario = scenario,
    providerTransactionId = providerTransactionId,
    failureReason = failureReason,
    reconciliationDeadline = deadline,
    nextReconciliationAt = nextAt,
    updatedAt = updatedAt,
)
