package com.banryeokkurumi.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface MemberRepository extends JpaRepository<MemberEntity, UUID> {
    Optional<MemberEntity> findByLoginId(String loginId);
}
