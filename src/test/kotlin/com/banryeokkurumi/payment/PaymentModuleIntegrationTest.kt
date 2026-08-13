package com.banryeokkurumi.payment

import com.banryeokkurumi.TestcontainersConfiguration
import com.banryeokkurumi.contracts.CommerceEvents
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.annotation.DirtiesContext
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ApplicationModuleTest
@Import(TestcontainersConfiguration::class, PaymentModuleTestConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = [
    "resilience4j.circuitbreaker.instances.paymentProvider.sliding-window-size=2",
    "resilience4j.circuitbreaker.instances.paymentProvider.minimum-number-of-calls=2",
    "resilience4j.circuitbreaker.instances.paymentProvider.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.paymentProvider.permitted-number-of-calls-in-half-open-state=2",
    "resilience4j.timelimiter.instances.paymentProvider.timeout-duration=50ms",
    "resilience4j.retry.instances.paymentProvider.max-attempts=1",
])
internal class PaymentModuleIntegrationTest @Autowired constructor(
    private val service: PaymentApplicationService,
    private val provider: DemoPaymentProvider,
    private val eventPublisher: ApplicationEventPublisher,
    private val recordedEvents: PaymentEventCollector,
    private val transactions: TransactionTemplate,
    private val jdbc: JdbcClient,
    private val circuitBreakers: CircuitBreakerRegistry,
) {
    @BeforeEach
    fun resetState() {
        circuitBreakers.circuitBreaker("paymentProvider").reset()
        recordedEvents.clear()
        jdbc.sql("DELETE FROM payment_transaction").update()
        jdbc.sql("DELETE FROM processed_event WHERE listener_id='payment'").update()
    }

    @Test
    fun authorize_성공과거절조회취소_결정적결과를반환한다() {
        val orderId = UUID.randomUUID()

        val success = provider.authorize(orderId, 10_000, "authorize-$orderId", DemoPaymentProvider.Scenario.SUCCESS).join()
        val declined = provider.authorize(orderId, 10_000, "decline-$orderId", DemoPaymentProvider.Scenario.DECLINED).join()

        assertThat(success.status).isEqualTo(DemoPaymentProvider.Status.SUCCEEDED)
        assertThat(success.transactionId).isEqualTo("demo-$orderId")
        assertThat(declined.status).isEqualTo(DemoPaymentProvider.Status.DECLINED)
        assertThat(provider.query(orderId, DemoPaymentProvider.Scenario.SUCCESS).status)
            .isEqualTo(DemoPaymentProvider.Status.SUCCEEDED)
        assertThat(provider.cancel(orderId, DemoPaymentProvider.Scenario.SUCCESS).status)
            .isEqualTo(DemoPaymentProvider.Status.CANCELLED)
    }

    @Test
    fun authorize_정상거절_breaker실패로기록하지않는다() {
        val breaker = circuitBreakers.circuitBreaker("paymentProvider")

        provider.authorize(UUID.randomUUID(), 10_000, "declined", DemoPaymentProvider.Scenario.DECLINED).join()

        assertThat(breaker.metrics.numberOfFailedCalls).isZero()
    }

    @Test
    fun authorize_timeout_TimeLimiter가실패로종결한다() {
        val future = provider.authorize(UUID.randomUUID(), 10_000, "timeout", DemoPaymentProvider.Scenario.TIMEOUT)

        assertThatThrownBy { future.join() }
            .hasRootCauseInstanceOf(java.util.concurrent.TimeoutException::class.java)
    }

    @Test
    fun authorize_연결실패누적_breaker가열리고_halfOpen성공뒤닫힌다() {
        val breaker = circuitBreakers.circuitBreaker("paymentProvider")

        repeat(2) {
            assertThatThrownBy {
                provider.authorize(UUID.randomUUID(), 10_000, "failure-$it", DemoPaymentProvider.Scenario.CONNECTION_FAILURE).join()
            }.hasRootCauseMessage("데모 결제 provider 연결 실패")
        }
        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN)

        breaker.transitionToHalfOpenState()
        repeat(2) {
            provider.query(UUID.randomUUID(), DemoPaymentProvider.Scenario.SUCCESS)
        }
        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun create_같은주문반복호출_row한건과nullable필드를유지한다() {
        val orderId = UUID.randomUUID()

        val first = service.create(orderId, "member", 10_000, "SUCCESS", "payment-$orderId")
        val second = service.create(orderId, "member", 10_000, "SUCCESS", "payment-$orderId")

        assertThat(first).isEqualTo(second)
        assertThat(first.providerTransactionId).isNull()
        assertThat(first.failureReason).isNull()
        assertThat(first.reconciliationDeadline).isNull()
        assertThat(paymentCount(orderId)).isEqualTo(1)
    }

    @Test
    fun unknown_조회일정과deadline을기록하고_성공으로수렴한다() {
        val orderId = UUID.randomUUID()
        service.create(orderId, "member", 10_000, "TIMEOUT", "payment-$orderId")
        val before = Instant.now()

        val unknown = service.unknown(orderId, "TIMEOUT")
        val succeeded = service.success(orderId, "demo-$orderId")

        assertThat(unknown.status).isEqualTo("UNKNOWN")
        assertThat(unknown.nextReconciliationAt).isBetween(before.plusSeconds(9), Instant.now().plusSeconds(11))
        assertThat(unknown.reconciliationDeadline).isBetween(before.plusSeconds(599), Instant.now().plusSeconds(601))
        assertThat(succeeded.status).isEqualTo("SUCCEEDED")
    }

    @Test
    fun success_중복호출과terminal실패_추가event를발행하지않는다() {
        val orderId = UUID.randomUUID()
        service.create(orderId, "member", 10_000, "SUCCESS", "payment-$orderId")

        service.success(orderId, "demo-$orderId")
        service.success(orderId, "demo-$orderId")
        val terminal = service.fail(orderId, "LATE_FAILURE")

        assertThat(terminal.status).isEqualTo("SUCCEEDED")
        assertThat(recordedEvents.succeeded).hasSize(1)
        assertThat(recordedEvents.failed).isEmpty()
    }

    @Test
    fun PaymentRequested_동일event열번전달_결제와처리기록을한번만생성한다() {
        val orderId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val event = CommerceEvents.PaymentRequested(
            eventId,
            Instant.now(),
            1,
            orderId,
            "member",
            10_000,
            "payment-$orderId",
            "SUCCESS",
        )

        transactions.executeWithoutResult { eventPublisher.publishEvent(event) }
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(paymentCount(orderId)).isEqualTo(1)
            assertThat(processedCount(eventId)).isEqualTo(1)
            assertThat(service.find(orderId).status).isEqualTo("SUCCEEDED")
        }

        transactions.executeWithoutResult { repeat(9) { eventPublisher.publishEvent(event) } }

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            assertThat(paymentCount(orderId)).isEqualTo(1)
            assertThat(processedCount(eventId)).isEqualTo(1)
            assertThat(service.find(orderId).status).isEqualTo("SUCCEEDED")
        }
    }

    private fun paymentCount(orderId: UUID): Int = jdbc.sql(
        "SELECT COUNT(*) FROM payment_transaction WHERE order_id=:orderId",
    ).param("orderId", orderId.toString()).query(Int::class.java).single()

    private fun processedCount(eventId: UUID): Int = jdbc.sql(
        "SELECT COUNT(*) FROM processed_event WHERE listener_id='payment' AND event_id=:eventId",
    ).param("eventId", eventId.toString()).query(Int::class.java).single()
}

@TestConfiguration(proxyBeanMethods = false)
internal class PaymentModuleTestConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun paymentEventCollector() = PaymentEventCollector()
}

internal class PaymentEventCollector {
    val succeeded = mutableListOf<CommerceEvents.PaymentSucceeded>()
    val failed = mutableListOf<CommerceEvents.PaymentFailed>()

    @EventListener
    fun on(event: CommerceEvents.PaymentSucceeded) {
        succeeded += event
    }

    @EventListener
    fun on(event: CommerceEvents.PaymentFailed) {
        failed += event
    }

    fun clear() {
        succeeded.clear()
        failed.clear()
    }
}
