package com.banryeokkurumi.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class PopularityScoreTest {

  @Test
  void score_구매신호는조회신호보다크다() {
    Instant now = Instant.parse("2026-08-13T00:00:00Z");

    assertThat(PopularityScore.of(InteractionType.PURCHASE, now, now))
        .isGreaterThan(PopularityScore.of(InteractionType.VIEW, now, now));
  }

  @Test
  void score_칠일이지나면절반으로감쇠한다() {
    Instant occurredAt = Instant.parse("2026-08-01T00:00:00Z");
    double initial = PopularityScore.of(InteractionType.PURCHASE, occurredAt, occurredAt);
    double decayed =
        PopularityScore.of(
            InteractionType.PURCHASE, occurredAt, occurredAt.plus(7, ChronoUnit.DAYS));

    assertThat(decayed).isCloseTo(initial / 2, org.assertj.core.data.Offset.offset(0.0001));
  }
}
