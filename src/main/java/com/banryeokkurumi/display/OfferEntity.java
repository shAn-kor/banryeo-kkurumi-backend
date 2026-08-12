package com.banryeokkurumi.display;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "display_offer")
class OfferEntity {
    @Id UUID skuId;
    @Column(nullable = false) UUID productId;
    @Column(nullable = false) long price;
    @Column(nullable = false) boolean active;
    @Column(nullable = false) int displayOrder;
    @Column(nullable = false) Instant updatedAt;
    protected OfferEntity() {}
    OfferEntity(UUID productId, UUID skuId, long price, boolean active, int displayOrder, Instant updatedAt) {
        this.productId = productId; this.skuId = skuId; this.price = price; this.active = active;
        this.displayOrder = displayOrder; this.updatedAt = updatedAt;
    }
}
