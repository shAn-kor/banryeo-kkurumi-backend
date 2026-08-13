package com.banryeokkurumi.ordering;

import com.banryeokkurumi.TestcontainersConfiguration;
import com.banryeokkurumi.contracts.CommerceEvents;
import com.banryeokkurumi.inventory.InventoryApplicationService;
import com.banryeokkurumi.promotion.PromotionApplicationService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.payment.reconciliation-delay=1s",
        "app.payment.reconciliation-window=5s",
        "resilience4j.timelimiter.instances.paymentProvider.timeout-duration=100ms",
        "app.shipping.ship-delay=1h",
        "app.shipping.delivery-delay=2h"
})
@Testcontainers(disabledWithoutDocker = true)
class OrderConvergenceIntegrationTest {
    private static final String MEMBER = "convergence-member";

    private final OrderApplicationService orders;
    private final InventoryApplicationService inventory;
    private final PromotionApplicationService promotions;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final JdbcClient jdbc;

    @Autowired
    OrderConvergenceIntegrationTest(OrderApplicationService orders,
                                    InventoryApplicationService inventory,
                                    PromotionApplicationService promotions,
                                    ApplicationEventPublisher events,
                                    TransactionTemplate transactions,
                                    JdbcClient jdbc) {
        this.orders = orders;
        this.inventory = inventory;
        this.promotions = promotions;
        this.events = events;
        this.transactions = transactions;
        this.jdbc = jdbc;
    }

    @Test
    void 재고부족은_주문실패로종결되고_결제와배송을만들지않는다() {
        Fixture fixture = submit("SUCCESS", null, 0, 1);

        await(() -> {
            assertThat(orderStatus(fixture.orderId())).isEqualTo("FAILED");
            assertStock(fixture.skuId(), 0, 0, 0);
            assertThat(count("payment_transaction", fixture.orderId())).isZero();
            assertThat(count("shipping_shipment", fixture.orderId())).isZero();
        });
    }

    @Test
    void 존재하지않는쿠폰은_예약재고를전부해제하고_결제를시작하지않는다() {
        Fixture fixture = submit("SUCCESS", UUID.randomUUID(), 5, 2);

        await(() -> {
            assertThat(orderStatus(fixture.orderId())).isEqualTo("FAILED");
            assertStock(fixture.skuId(), 5, 0, 0);
            assertThat(count("payment_transaction", fixture.orderId())).isZero();
            assertThat(count("shipping_shipment", fixture.orderId())).isZero();
        });
    }

    @Test
    void 결제거절은_재고와쿠폰을원복하고_배송을만들지않는다() {
        UUID couponId = issueCoupon();
        Fixture fixture = submit("DECLINED", couponId, 5, 2);

        await(() -> {
            assertThat(orderStatus(fixture.orderId())).isEqualTo("FAILED");
            assertStock(fixture.skuId(), 5, 0, 0);
            assertThat(couponStatus(couponId)).isEqualTo("AVAILABLE");
            assertThat(paymentStatus(fixture.orderId())).isEqualTo("FAILED");
            assertThat(count("shipping_shipment", fixture.orderId())).isZero();
        });
    }

    @Test
    void 결제timeout은_상태조회로성공을확정한뒤_자원을확정한다() {
        UUID couponId = issueCoupon();
        Fixture fixture = submit("TIMEOUT", couponId, 5, 2);

        await(() -> assertSuccessfulFulfillment(fixture, couponId));
    }

    @Test
    void 결제연결실패는_상태조회로성공을확정한뒤_자원을확정한다() {
        UUID couponId = issueCoupon();
        Fixture fixture = submit("CONNECTION_FAILURE", couponId, 5, 2);

        await(() -> assertSuccessfulFulfillment(fixture, couponId));
    }

    @Test
    void 결제완료뒤_출고전취소는_판매재고와사용쿠폰과배송을모두취소로수렴한다() {
        UUID couponId = issueCoupon();
        Fixture fixture = submit("SUCCESS", couponId, 5, 2);
        await(() -> assertThat(orderStatus(fixture.orderId())).isEqualTo("FULFILLING"));

        orders.requestCancellation(fixture.orderId(), MEMBER);

        await(() -> {
            assertThat(orderStatus(fixture.orderId())).isEqualTo("CANCELLED");
            assertStock(fixture.skuId(), 5, 0, 0);
            assertThat(couponStatus(couponId)).isEqualTo("AVAILABLE");
            assertThat(paymentStatus(fixture.orderId())).isEqualTo("CANCELLED");
            assertThat(shippingStatus(fixture.orderId())).isEqualTo("CANCELLED");
        });
    }

    @Test
    void 동일결제성공이벤트를_10회전달해도_재고쿠폰배송은한번만반영한다() {
        UUID couponId = issueCoupon();
        Fixture fixture = submit("TIMEOUT", couponId, 5, 2);
        await(() -> assertThat(paymentStatus(fixture.orderId())).isEqualTo("UNKNOWN"));
        jdbc.sql("UPDATE payment_transaction SET status='SUCCEEDED' WHERE order_id=:orderId")
                .param("orderId", fixture.orderId().toString()).update();
        UUID eventId = UUID.randomUUID();
        CommerceEvents.PaymentSucceeded duplicated = new CommerceEvents.PaymentSucceeded(
                eventId, Instant.now(), 1, fixture.orderId(), "duplicate-test-transaction");

        transactions.executeWithoutResult(status -> {
            for (int delivery = 0; delivery < 10; delivery++) events.publishEvent(duplicated);
        });

        await(() -> {
            assertThat(orderStatus(fixture.orderId())).isEqualTo("FULFILLING");
            assertStock(fixture.skuId(), 3, 0, 2);
            assertThat(couponStatus(couponId)).isEqualTo("USED");
            assertThat(count("shipping_shipment", fixture.orderId())).isEqualTo(1);
            assertThat(processedListenerCount(eventId)).isEqualTo(3);
        });
    }

    Fixture submit(String scenario, UUID couponId, int available, int quantity) {
        UUID productId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        inventory.setStock(skuId, available);
        CommerceEvents.OrderLine line = new CommerceEvents.OrderLine(
                UUID.randomUUID(), productId, skuId, "테스트 상품", "기본", 10_000, quantity);
        CommerceEvents.EncryptedAddress address = new CommerceEvents.EncryptedAddress("enc-name", "enc-phone", "enc-post", "enc-line1", "enc-line2");
        UUID orderId = orders.create(new OrderApplicationService.CreateOrderCommand(
                MEMBER, List.of(line), couponId, scenario, address)).orderId();
        return new Fixture(orderId, skuId, available, quantity);
    }

    UUID issueCoupon() {
        Instant now = Instant.now();
        UUID campaignId = promotions.createCampaign(new PromotionApplicationService.CreateCampaignCommand(
                "상태수렴 테스트", "FIXED", 1_000, 1_000, 0, "ALL", null, 100,
                now.minusSeconds(60), now.plusSeconds(600)));
        return promotions.issue(campaignId, MEMBER);
    }

    void assertSuccessfulFulfillment(Fixture fixture, UUID couponId) {
        assertThat(orderStatus(fixture.orderId())).isEqualTo("FULFILLING");
        assertStock(fixture.skuId(), fixture.available() - fixture.quantity(), 0, fixture.quantity());
        assertThat(couponStatus(couponId)).isEqualTo("USED");
        assertThat(paymentStatus(fixture.orderId())).isEqualTo("SUCCEEDED");
        assertThat(shippingStatus(fixture.orderId())).isEqualTo("PREPARING");
    }

    void assertStock(UUID skuId, int available, int reserved, int sold) {
        InventoryApplicationService.InventoryView stock = inventory.find(skuId);
        assertThat(stock.availableQuantity()).isEqualTo(available);
        assertThat(stock.reservedQuantity()).isEqualTo(reserved);
        assertThat(stock.soldQuantity()).isEqualTo(sold);
    }

    String orderStatus(UUID orderId) { return orders.find(orderId).status().name(); }
    String couponStatus(UUID couponId) { return jdbc.sql("SELECT status FROM promotion_issued_coupon WHERE id=:id").param("id", couponId.toString()).query(String.class).single(); }
    String paymentStatus(UUID orderId) { return jdbc.sql("SELECT status FROM payment_transaction WHERE order_id=:id").param("id", orderId.toString()).query(String.class).single(); }
    String shippingStatus(UUID orderId) { return jdbc.sql("SELECT status FROM shipping_shipment WHERE order_id=:id").param("id", orderId.toString()).query(String.class).single(); }
    int count(String table, UUID orderId) { return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE order_id=:id").param("id", orderId.toString()).query(Integer.class).single(); }
    int processedListenerCount(UUID eventId) { return jdbc.sql("SELECT COUNT(*) FROM processed_event WHERE event_id=:id").param("id", eventId.toString()).query(Integer.class).single(); }
    void await(org.awaitility.core.ThrowingRunnable assertion) { Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).untilAsserted(assertion); }

    record Fixture(UUID orderId, UUID skuId, int available, int quantity) {}
}
