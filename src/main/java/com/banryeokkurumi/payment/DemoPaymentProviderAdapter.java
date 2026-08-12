package com.banryeokkurumi.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
class DemoPaymentProviderAdapter implements DemoPaymentProvider {
    @Override
    @CircuitBreaker(name = "paymentProvider")
    @TimeLimiter(name = "paymentProvider")
    @Retry(name = "paymentProvider")
    public CompletableFuture<Result> authorize(UUID orderId, long amount, String idempotencyKey, Scenario scenario) {
        return switch (scenario) {
            case SUCCESS -> CompletableFuture.completedFuture(new Result(Status.SUCCEEDED, "demo-" + orderId, null));
            case DECLINED -> CompletableFuture.completedFuture(new Result(Status.DECLINED, null, "DEMO_DECLINED"));
            case TIMEOUT -> new CompletableFuture<>();
            case CONNECTION_FAILURE -> CompletableFuture.failedFuture(new PaymentProviderConnectionException());
        };
    }

    @Override
    @CircuitBreaker(name = "paymentProvider")
    @Retry(name = "paymentProvider")
    public Result query(UUID orderId, Scenario scenario) {
        return scenario == Scenario.DECLINED
                ? new Result(Status.DECLINED, null, "DEMO_DECLINED")
                : new Result(Status.SUCCEEDED, "demo-" + orderId, null);
    }

    @Override
    @CircuitBreaker(name = "paymentProvider")
    @Retry(name = "paymentProvider")
    public Result cancel(UUID orderId, Scenario scenario) {
        return new Result(Status.CANCELLED, "demo-" + orderId, null);
    }

    static final class PaymentProviderConnectionException extends RuntimeException {
        PaymentProviderConnectionException() { super("데모 결제 provider 연결 실패"); }
    }
}
