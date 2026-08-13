package com.banryeokkurumi.inventory;

import com.banryeokkurumi.TestcontainersConfiguration;
import com.banryeokkurumi.contracts.CommerceEvents;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=32")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryConcurrencyIntegrationTest {
    private static final int AVAILABLE_STOCK = 50;
    private static final int CONCURRENT_CALLERS = 100;

    private final InventoryApplicationService inventory;
    private final JdbcClient jdbc;

    @Autowired
    InventoryConcurrencyIntegrationTest(InventoryApplicationService inventory, JdbcClient jdbc) {
        this.inventory = inventory;
        this.jdbc = jdbc;
    }

    @RepeatedTest(10)
    void reserve_재고50개에100개동시요청_정확히50개만예약한다() throws Exception {
        UUID skuId = UUID.randomUUID();
        inventory.setStock(skuId, AVAILABLE_STOCK);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_CALLERS);
        CountDownLatch start = new CountDownLatch(1);
        List<ReservationAttempt> attempts = new ArrayList<>(CONCURRENT_CALLERS);

        try (var executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS)) {
            for (int caller = 0; caller < CONCURRENT_CALLERS; caller++) {
                UUID orderId = UUID.randomUUID();
                Future<Boolean> result = executor.submit(() -> reserve(orderId, skuId, ready, start));
                attempts.add(new ReservationAttempt(orderId, result));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<UUID> succeeded = new ArrayList<>();
            for (ReservationAttempt attempt : attempts) {
                if (attempt.result().get()) succeeded.add(attempt.orderId());
            }

            InventoryApplicationService.InventoryView reserved = inventory.find(skuId);
            assertThat(succeeded).hasSize(AVAILABLE_STOCK);
            assertThat(reserved.availableQuantity()).isZero();
            assertThat(reserved.reservedQuantity()).isEqualTo(AVAILABLE_STOCK);
            assertThat(reserved.soldQuantity()).isZero();
            assertThat(reservationCount(skuId, "RESERVED")).isEqualTo(AVAILABLE_STOCK);
            assertThat(negativeStockCount(skuId)).isZero();

            succeeded.forEach(inventory::release);

            InventoryApplicationService.InventoryView released = inventory.find(skuId);
            assertThat(released.availableQuantity()).isEqualTo(AVAILABLE_STOCK);
            assertThat(released.reservedQuantity()).isZero();
            assertThat(released.soldQuantity()).isZero();
            assertThat(negativeStockCount(skuId)).isZero();
        }
    }

    private boolean reserve(UUID orderId, UUID skuId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            CommerceEvents.OrderLine line = new CommerceEvents.OrderLine(
                    UUID.randomUUID(), UUID.randomUUID(), skuId, "동시성 테스트 상품", "기본", 10_000, 1);
            inventory.reserve(orderId, List.of(line));
            return true;
        } catch (InsufficientStockException expected) {
            return false;
        }
    }

    private int reservationCount(UUID skuId, String status) {
        return jdbc.sql("SELECT COUNT(*) FROM inventory_reservation WHERE sku_id=:skuId AND status=:status")
                .param("skuId", skuId.toString()).param("status", status).query(Integer.class).single();
    }

    private int negativeStockCount(UUID skuId) {
        return jdbc.sql("SELECT COUNT(*) FROM inventory_stock WHERE sku_id=:skuId " +
                        "AND (available_quantity<0 OR reserved_quantity<0 OR sold_quantity<0)")
                .param("skuId", skuId.toString()).query(Integer.class).single();
    }

    private record ReservationAttempt(UUID orderId, Future<Boolean> result) {}
}
