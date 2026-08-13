package com.banryeokkurumi.payment

import java.util.UUID
import java.util.concurrent.CompletableFuture

interface DemoPaymentProvider {
    fun authorize(orderId: UUID, amount: Long, idempotencyKey: String, scenario: Scenario): CompletableFuture<Result>

    fun query(orderId: UUID, scenario: Scenario): Result

    fun cancel(orderId: UUID, scenario: Scenario): Result

    enum class Scenario { SUCCESS, DECLINED, TIMEOUT, CONNECTION_FAILURE }

    enum class Status { SUCCEEDED, DECLINED, CANCELLED }

    @JvmRecord
    data class Result(
        val status: Status,
        val transactionId: String?,
        val reason: String?,
    )
}
