package com.banryeokkurumi.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {
    List<CartItemEntity> findAllByMemberLoginIdOrderByUpdatedAtDesc(String memberLoginId);
    Optional<CartItemEntity> findByMemberLoginIdAndSkuId(String memberLoginId, UUID skuId);
    void deleteAllByMemberLoginId(String memberLoginId);
}
