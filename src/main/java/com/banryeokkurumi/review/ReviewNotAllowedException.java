package com.banryeokkurumi.review;

public final class ReviewNotAllowedException extends RuntimeException {
    public ReviewNotAllowedException() {
        super("구매 확정된 주문 상품만 리뷰할 수 있습니다.");
    }
}
