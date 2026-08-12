package com.banryeokkurumi.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api-admin/v1/inventory")
class InventoryAdminController {
    private final InventoryApplicationService service;
    InventoryAdminController(InventoryApplicationService service) { this.service = service; }
    @PutMapping
    InventoryApplicationService.InventoryView set(@Valid @RequestBody StockRequest request) { return service.setStock(request.skuId(), request.quantity()); }
    record StockRequest(UUID skuId, @Min(0) int quantity) {}
}
