package com.banryeokkurumi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_item")
class CatalogItemEntity {
    @Id UUID productId;
    @Column(nullable = false, unique = true) UUID skuId;
    @Column(nullable = false, length = 200) String name;
    @Column(nullable = false, length = 100) String categoryName;
    @Column(nullable = false, length = 100) String brandName;
    @Column(nullable = false, length = 100) String optionName;
    @Column(nullable = false) Instant createdAt;

    protected CatalogItemEntity() {}

    CatalogItemEntity(CatalogProduct product, Instant createdAt) {
        this.productId = product.productId();
        this.skuId = product.skuId();
        this.name = product.name();
        this.categoryName = product.categoryName();
        this.brandName = product.brandName();
        this.optionName = product.optionName();
        this.createdAt = createdAt;
    }
}
