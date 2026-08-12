package com.banryeokkurumi.review;

import java.util.UUID;

public record Review(UUID reviewId, UUID memberId, UUID orderItemId, UUID skuId, int rating, String content) {
    public Review {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("평점은 1부터 5까지입니다.");
        }
        if (content == null || content.isBlank() || content.length() > 1000) {
            throw new IllegalArgumentException("리뷰 내용은 1자 이상 1000자 이하여야 합니다.");
        }
    }

    public static Review write(UUID memberId, UUID orderItemId, UUID skuId, int rating, String content, boolean purchaseConfirmed) {
        if (!purchaseConfirmed) {
            throw new ReviewNotAllowedException();
        }
        return new Review(UUID.randomUUID(), memberId, orderItemId, skuId, rating, content.strip());
    }
}
