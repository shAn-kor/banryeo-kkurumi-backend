package com.banryeokkurumi.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banryeokkurumi.TestcontainersConfiguration;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationInteractionIntegrationTest {
  private final RecommendationController controller;
  private final JdbcClient jdbc;

  @Autowired
  RecommendationInteractionIntegrationTest(RecommendationController controller, JdbcClient jdbc) {
    this.controller = controller;
    this.jdbc = jdbc;
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

  private long interactions(UUID skuId) {
    return jdbc.sql("SELECT COUNT(*) FROM recommendation_interaction WHERE sku_id=:sku")
        .param("sku", skuId.toString())
        .query(Long.class)
        .single();
  }
}
