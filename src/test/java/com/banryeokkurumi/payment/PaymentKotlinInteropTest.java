package com.banryeokkurumi.payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentKotlinInteropTest {
    @Test
    void kotlinRecords_JavaRecordAccessor를제공한다() {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentApplicationService.PaymentView view = new PaymentApplicationService.PaymentView(
                orderId, "member", 10_000, "SUCCEEDED", DemoPaymentProvider.Scenario.SUCCESS,
                "demo-transaction", null, null, null, now);
        DemoPaymentProvider.Result result = new DemoPaymentProvider.Result(
                DemoPaymentProvider.Status.SUCCEEDED, "demo-transaction", null);

        assertThat(view.orderId()).isEqualTo(orderId);
        assertThat(view.providerTransactionId()).isEqualTo("demo-transaction");
        assertThat(result.status()).isEqualTo(DemoPaymentProvider.Status.SUCCEEDED);
        assertThat(result.transactionId()).isEqualTo("demo-transaction");
    }
}
