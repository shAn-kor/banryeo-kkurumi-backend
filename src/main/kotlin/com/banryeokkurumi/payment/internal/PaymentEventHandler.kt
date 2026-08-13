package com.banryeokkurumi.payment.internal

import com.banryeokkurumi.contracts.CommerceEvents
import com.banryeokkurumi.payment.DemoPaymentProvider
import com.banryeokkurumi.payment.PaymentApplicationService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
internal class PaymentEventHandler(
    private val service: PaymentApplicationService,
    private val provider: DemoPaymentProvider,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
    private val processed: PaymentEventProcessingRegistry,
) {
    @ApplicationModuleListener
    fun on(event: CommerceEvents.PaymentRequested) {
        if (!processed.claim(event.eventId())) return
        val payment = service.create(
            event.orderId(),
            event.memberLoginId(),
            event.amount(),
            event.scenario(),
            event.idempotencyKey(),
        )
        if (payment.status == "SUCCEEDED") {
            publishSuccess(payment)
            return
        }
        try {
            resolve(
                event.orderId(),
                provider.authorize(event.orderId(), event.amount(), event.idempotencyKey(), payment.scenario).join(),
            )
        } catch (exception: RuntimeException) {
            val reason = rootMessage(exception)
            service.unknown(event.orderId(), reason)
            events.publishEvent(CommerceEvents.PaymentUnknown(UUID.randomUUID(), now(), 1, event.orderId(), reason))
        }
    }

    @ApplicationModuleListener
    fun on(event: CommerceEvents.OrderCancellationRequested) {
        if (!processed.claim(event.eventId())) return
        val payment = service.findOptional(event.orderId()).orElse(null)
        if (payment != null) {
            provider.cancel(event.orderId(), payment.scenario)
            service.cancel(event.orderId())
        }
        events.publishEvent(CommerceEvents.PaymentCancelled(UUID.randomUUID(), now(), 1, event.orderId()))
    }

    @Scheduled(fixedDelayString = "\${app.payment.reconciliation-delay:10s}")
    fun reconcile() {
        service.due().forEach { payment ->
            if (payment.reconciliationDeadline != null && !now().isBefore(payment.reconciliationDeadline)) {
                provider.cancel(payment.orderId, payment.scenario)
                service.fail(payment.orderId, "RECONCILIATION_TIMEOUT")
            } else {
                try {
                    resolve(payment.orderId, provider.query(payment.orderId, payment.scenario))
                } catch (_: RuntimeException) {
                    service.reschedule(payment.orderId)
                }
            }
        }
    }

    internal fun resolve(orderId: UUID, result: DemoPaymentProvider.Result) {
        when (result.status) {
            DemoPaymentProvider.Status.SUCCEEDED -> service.success(orderId, requireNotNull(result.transactionId))
            DemoPaymentProvider.Status.DECLINED -> service.fail(orderId, requireNotNull(result.reason))
            DemoPaymentProvider.Status.CANCELLED -> Unit
        }
    }

    internal fun publishSuccess(payment: PaymentApplicationService.PaymentView) {
        events.publishEvent(
            CommerceEvents.PaymentSucceeded(
                UUID.randomUUID(),
                now(),
                1,
                payment.orderId,
                requireNotNull(payment.providerTransactionId),
            ),
        )
    }

    internal fun rootMessage(error: Throwable): String {
        var root = error
        while (root.cause != null) root = requireNotNull(root.cause)
        return root.message ?: root.javaClass.simpleName
    }

    internal fun now(): Instant = Instant.now(clock)
}
