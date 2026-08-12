package com.banryeokkurumi.promotion;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record CouponCampaign(
        UUID campaignId,
        int totalQuantity,
        int issuedQuantity,
        Instant startsAt,
        Instant endsAt,
        Set<UUID> issuedMembers
) {
    public CouponCampaign {
        if (campaignId == null || totalQuantity <= 0 || issuedQuantity < 0 || issuedQuantity > totalQuantity) {
            throw new IllegalArgumentException("쿠폰 캠페인 값이 올바르지 않습니다.");
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("발급 기간이 올바르지 않습니다.");
        }
        issuedMembers = Set.copyOf(issuedMembers);
    }

    public static CouponCampaign open(UUID campaignId, int totalQuantity, Instant startsAt, Instant endsAt) {
        return new CouponCampaign(campaignId, totalQuantity, 0, startsAt, endsAt, Set.of());
    }

    public CouponCampaign issue(UUID memberId, Instant now) {
        if (now.isBefore(startsAt) || now.isAfter(endsAt)) {
            throw new IllegalStateException("쿠폰 발급 기간이 아닙니다.");
        }
        if (issuedMembers.contains(memberId)) {
            return this;
        }
        if (issuedQuantity >= totalQuantity) {
            throw new CouponSoldOutException();
        }
        Set<UUID> next = new HashSet<>(issuedMembers);
        next.add(memberId);
        return new CouponCampaign(campaignId, totalQuantity, issuedQuantity + 1, startsAt, endsAt, next);
    }
}
