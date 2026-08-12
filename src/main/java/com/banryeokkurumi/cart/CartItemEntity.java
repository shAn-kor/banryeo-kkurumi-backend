package com.banryeokkurumi.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_item")
class CartItemEntity {
    @Id UUID id;
    @Column(nullable = false, length = 50) String memberLoginId;
    @Column(nullable = false) UUID skuId;
    @Column(nullable = false) int quantity;
    @Column(nullable = false) Instant updatedAt;
    protected CartItemEntity() {}
    CartItemEntity(UUID id, String memberLoginId, UUID skuId, int quantity, Instant updatedAt) {
        this.id = id; this.memberLoginId = memberLoginId; this.skuId = skuId; this.quantity = quantity; this.updatedAt = updatedAt;
    }
    void changeQuantity(int value, Instant at) { quantity = value; updatedAt = at; }
}
