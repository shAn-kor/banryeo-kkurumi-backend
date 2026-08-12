package com.banryeokkurumi.recommendation;

public enum InteractionType {
    VIEW(1), LIKE(3), CART(4), PURCHASE(10), REVIEW(2);

    private final int weight;

    InteractionType(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
