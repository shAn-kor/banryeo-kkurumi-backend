package com.banryeokkurumi.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banryeokkurumi.TestcontainersConfiguration;
import com.banryeokkurumi.contracts.CommerceEvents;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationInteractionIntegrationTest {
  private final RecommendationController controller;
  private final JdbcClient jdbc;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate transactions;

  @Autowired
  RecommendationInteractionIntegrationTest(
      RecommendationController controller,
      JdbcClient jdbc,
      ApplicationEventPublisher events,
      TransactionTemplate transactions) {
    this.controller = controller;
    this.jdbc = jdbc;
    this.events = events;
    this.transactions = transactions;
  }

  @Test
  void 행동API로받은조회는_추천행동으로저장된다() {
    UUID skuId = UUID.randomUUID();

    controller.observe(new RecommendationController.InteractionRequest(skuId, "FOOD", "VIEW"));

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(interactions(skuId)).isEqualTo(1));
  }

  @Test
  void 알수없는행동유형은_거절한다() {
    UUID skuId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                controller.observe(
                    new RecommendationController.InteractionRequest(skuId, "FOOD", "UNKNOWN")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(interactions(skuId)).isZero();
  }

  @Test
  void 클라이언트가보낸구매행동은_거절하고저장하지않는다() {
    UUID skuId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                controller.observe(
                    new RecommendationController.InteractionRequest(skuId, "FOOD", "PURCHASE")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(interactions(skuId)).isZero();
  }

  @Test
  void 서버구매확정이벤트는_구매행동으로집계된다() {
    UUID skuId = UUID.randomUUID();
    CommerceEvents.OrderLine line =
        new CommerceEvents.OrderLine(
            UUID.randomUUID(), UUID.randomUUID(), skuId, "사료", "1kg", 10_000, 2);

    transactions.executeWithoutResult(
        status ->
            events.publishEvent(
                new CommerceEvents.PurchaseConfirmed(
                    UUID.randomUUID(),
                    Instant.now(),
                    1,
                    UUID.randomUUID(),
                    "buyer",
                    List.of(line))));

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(purchases(skuId)).isEqualTo(1));
  }

  private long purchases(UUID skuId) {
    return jdbc.sql(
            "SELECT COUNT(*) FROM recommendation_interaction WHERE sku_id=:sku AND interaction_type='PURCHASE'")
        .param("sku", skuId.toString())
        .query(Long.class)
        .single();
  }

  private long interactions(UUID skuId) {
    return jdbc.sql("SELECT COUNT(*) FROM recommendation_interaction WHERE sku_id=:sku")
        .param("sku", skuId.toString())
        .query(Long.class)
        .single();
  }
}
