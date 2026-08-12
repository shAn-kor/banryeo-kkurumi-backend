package com.banryeokkurumi.inventory;

public final class InsufficientStockException extends RuntimeException {
    public InsufficientStockException() {
        super("가용 재고가 부족합니다.");
    }
}
