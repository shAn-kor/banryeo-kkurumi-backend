package com.banryeokkurumi.promotion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
class PromotionController {
    private final PromotionApplicationService service;
    PromotionController(PromotionApplicationService service) { this.service=service; }
    @PostMapping("/api/v1/coupons/campaigns/{campaignId}/issues") @ResponseStatus(HttpStatus.CREATED)
    UUID issue(@PathVariable UUID campaignId, Authentication auth) { return service.issue(campaignId, auth.getName()); }
    @GetMapping("/api/v1/coupons") List<PromotionApplicationService.CouponView> mine(Authentication auth) { return service.findMine(auth.getName()); }
    @PostMapping("/api-admin/v1/coupons/campaigns") @ResponseStatus(HttpStatus.CREATED)
    UUID create(@Valid @RequestBody CampaignRequest r) { return service.createCampaign(new PromotionApplicationService.CreateCampaignCommand(r.name(),r.type(),r.value(),r.maximumDiscount(),r.minimumOrderAmount(),r.scopeType(),r.scopeId(),r.totalQuantity(),r.startsAt(),r.endsAt())); }
    record CampaignRequest(@NotBlank String name, @NotBlank String type, @Min(1) int value, @Min(0) long maximumDiscount,
                           @Min(0) long minimumOrderAmount, @NotBlank String scopeType, UUID scopeId,
                           @Min(1) int totalQuantity, Instant startsAt, Instant endsAt) {}
}
