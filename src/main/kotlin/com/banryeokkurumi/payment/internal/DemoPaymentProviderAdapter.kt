package com.banryeokkurumi.payment.internal

import com.banryeokkurumi.payment.DemoPaymentProvider
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.github.resilience4j.timelimiter.annotation.TimeLimiter
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Component
internal class DemoPaymentProviderAdapter : DemoPaymentProvider {
    @CircuitBreaker(name = "paymentProvider")
    @TimeLimiter(name = "paymentProvider")
    @Retry(name = "paymentProvider")
    override fun authorize(
        orderId: UUID,
        amount: Long,
        idempotencyKey: String,
        scenario: DemoPaymentProvider.Scenario,
    ): CompletableFuture<DemoPaymentProvider.Result> = when (scenario) {
        DemoPaymentProvider.Scenario.SUCCESS -> CompletableFuture.completedFuture(
            DemoPaymentProvider.Result(DemoPaymentProvider.Status.SUCCEEDED, "demo-$orderId", null),
        )
        DemoPaymentProvider.Scenario.DECLINED -> CompletableFuture.completedFuture(
            DemoPaymentProvider.Result(DemoPaymentProvider.Status.DECLINED, null, "DEMO_DECLINED"),
        )
        DemoPaymentProvider.Scenario.TIMEOUT -> CompletableFuture()
        DemoPaymentProvider.Scenario.CONNECTION_FAILURE -> CompletableFuture.failedFuture(PaymentProviderConnectionException())
    }

    @CircuitBreaker(name = "paymentProvider")
    @Retry(name = "paymentProvider")
    override fun query(orderId: UUID, scenario: DemoPaymentProvider.Scenario): DemoPaymentProvider.Result =
        if (scenario == DemoPaymentProvider.Scenario.DECLINED) {
            DemoPaymentProvider.Result(DemoPaymentProvider.Status.DECLINED, null, "DEMO_DECLINED")
        } else {
            DemoPaymentProvider.Result(DemoPaymentProvider.Status.SUCCEEDED, "demo-$orderId", null)
        }

    @CircuitBreaker(name = "paymentProvider")
    @Retry(name = "paymentProvider")
    override fun cancel(orderId: UUID, scenario: DemoPaymentProvider.Scenario) =
        DemoPaymentProvider.Result(DemoPaymentProvider.Status.CANCELLED, "demo-$orderId", null)
}

internal class PaymentProviderConnectionException : RuntimeException("데모 결제 provider 연결 실패")
