package com.banryeokkurumi.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MemberRepository extends JpaRepository<MemberEntity, UUID> {
  Optional<MemberEntity> findByLoginId(String loginId);
}
