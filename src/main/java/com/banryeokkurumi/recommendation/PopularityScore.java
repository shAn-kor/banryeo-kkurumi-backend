package com.banryeokkurumi.recommendation;

import java.time.Duration;
import java.time.Instant;

public final class PopularityScore {
    private static final double HALF_LIFE_DAYS = 7.0;

    private PopularityScore() {
    }

    public static double of(InteractionType type, Instant occurredAt, Instant calculatedAt) {
        if (calculatedAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("계산 시각은 발생 시각보다 빠를 수 없습니다.");
        }
        double ageDays = Duration.between(occurredAt, calculatedAt).toSeconds() / 86_400.0;
        return type.weight() * Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
    }
}
