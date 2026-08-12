package com.banryeokkurumi.display;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api-admin/v1/display")
class DisplayAdminController {
    private final DisplayApplicationService service;
    DisplayAdminController(DisplayApplicationService service) { this.service = service; }
    @PutMapping("/offers")
    DisplayApplicationService.OfferView upsert(@Valid @RequestBody OfferRequest request) {
        return service.upsert(new DisplayApplicationService.UpsertOfferCommand(request.productId(), request.skuId(), request.price(), request.active(), request.displayOrder()));
    }
    record OfferRequest(@NotNull UUID productId, @NotNull UUID skuId, @Min(0) long price, boolean active, int displayOrder) {}
}
