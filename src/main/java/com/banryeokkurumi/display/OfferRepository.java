package com.banryeokkurumi.display;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OfferRepository extends JpaRepository<OfferEntity, UUID> {}
