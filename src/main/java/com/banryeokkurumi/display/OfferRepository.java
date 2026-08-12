package com.banryeokkurumi.display;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface OfferRepository extends JpaRepository<OfferEntity, UUID> {}
