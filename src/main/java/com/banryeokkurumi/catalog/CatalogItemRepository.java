package com.banryeokkurumi.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface CatalogItemRepository extends JpaRepository<CatalogItemEntity, UUID> {
    Optional<CatalogItemEntity> findBySkuId(UUID skuId);
}
