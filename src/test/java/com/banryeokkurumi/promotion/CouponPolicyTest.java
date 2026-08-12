package com.banryeokkurumi.promotion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponPolicyTest {

    @Test
    void discount_정률과최대할인을함께적용한다() {
        CouponPolicy policy = CouponPolicy.percentage(20, 3_000, 10_000);

        assertThat(policy.discount(50_000)).isEqualTo(3_000);
    }

    @Test
    void issue_총발급수량을초과하면거부한다() {
        CouponCampaign campaign = CouponCampaign.open(UUID.randomUUID(), 1,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z"));
        CouponCampaign issued = campaign.issue(UUID.randomUUID(), Instant.parse("2026-08-13T00:00:00Z"));

        assertThatThrownBy(() -> issued.issue(UUID.randomUUID(), Instant.parse("2026-08-13T00:00:00Z")))
                .isInstanceOf(CouponSoldOutException.class);
    }
}
