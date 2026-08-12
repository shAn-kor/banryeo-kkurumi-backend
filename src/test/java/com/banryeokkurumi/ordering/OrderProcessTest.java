package com.banryeokkurumi.ordering;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderProcessTest {

    @Test
    void paymentSucceeded_재고와쿠폰예약뒤_결제완료로전이한다() {
        OrderProcess process = OrderProcess.submitted(UUID.randomUUID(), true)
                .stockReserved()
                .couponReserved()
                .paymentSucceeded();

        assertThat(process.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void cancel_출고뒤에는취소할수없다() {
        OrderProcess process = OrderProcess.submitted(UUID.randomUUID(), false)
                .stockReserved()
                .paymentSucceeded()
                .shipmentCreated()
                .shipped();

        assertThatThrownBy(process::requestCancellation)
                .isInstanceOf(OrderStateException.class);
    }
}
