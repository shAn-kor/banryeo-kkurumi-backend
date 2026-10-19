package com.banryeokkurumi.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CatalogItemRepository extends JpaRepository<CatalogItemEntity, UUID> {
  Optional<CatalogItemEntity> findBySkuId(UUID skuId);
}
