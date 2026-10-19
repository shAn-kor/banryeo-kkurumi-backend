package com.banryeokkurumi.cart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {
  List<CartItemEntity> findAllByMemberLoginIdOrderByUpdatedAtDesc(String memberLoginId);

  Optional<CartItemEntity> findByMemberLoginIdAndSkuId(String memberLoginId, UUID skuId);

  void deleteAllByMemberLoginId(String memberLoginId);
}
