package com.banryeokkurumi.review;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    @Test
    void write_구매확정주문상품에리뷰를작성한다() {
        Review review = Review.write(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5, "튼튼하고 좋아요", true);

        assertThat(review.rating()).isEqualTo(5);
    }

    @Test
    void write_구매확정전이면거부한다() {
        assertThatThrownBy(() -> Review.write(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5, "좋아요", false))
                .isInstanceOf(ReviewNotAllowedException.class);
    }
}
