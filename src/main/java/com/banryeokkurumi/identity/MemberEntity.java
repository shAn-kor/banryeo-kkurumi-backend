package com.banryeokkurumi.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_member")
class MemberEntity {
    @Id
    UUID id;
    @Column(nullable = false, unique = true, length = 50)
    String loginId;
    @Column(nullable = false, length = 100)
    String encodedPassword;
    @Column(nullable = false, length = 50)
    String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    MemberRole role;
    @Column(nullable = false)
    Instant createdAt;

    protected MemberEntity() {}

    MemberEntity(UUID id, String loginId, String encodedPassword, String name, MemberRole role, Instant createdAt) {
        this.id = id;
        this.loginId = loginId;
        this.encodedPassword = encodedPassword;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
    }
}
