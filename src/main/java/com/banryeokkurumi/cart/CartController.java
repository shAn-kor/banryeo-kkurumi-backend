package com.banryeokkurumi.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
class CartController {
    private final CartApplicationService service;
    CartController(CartApplicationService service) { this.service = service; }
    @GetMapping CartApplicationService.CartView get(Authentication auth) { return service.get(auth.getName()); }
    @PutMapping CartApplicationService.CartView put(Authentication auth, @Valid @RequestBody CartRequest request) { return service.put(auth.getName(), request.skuId(), request.quantity()); }
    @DeleteMapping void clear(Authentication auth) { service.clear(auth.getName()); }
    record CartRequest(@NotNull UUID skuId, @Min(1) @Max(99) int quantity) {}
}
