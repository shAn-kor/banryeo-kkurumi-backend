package com.banryeokkurumi.payment;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DemoPaymentProvider {
    CompletableFuture<Result> authorize(UUID orderId, long amount, String idempotencyKey, Scenario scenario);
    Result query(UUID orderId, Scenario scenario);
    Result cancel(UUID orderId, Scenario scenario);

    enum Scenario { SUCCESS, DECLINED, TIMEOUT, CONNECTION_FAILURE }
    enum Status { SUCCEEDED, DECLINED, CANCELLED }
    record Result(Status status, String transactionId, String reason) {}
}
