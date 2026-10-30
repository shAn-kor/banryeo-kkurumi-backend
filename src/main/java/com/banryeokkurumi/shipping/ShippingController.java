package com.banryeokkurumi.shipping;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
class ShippingController {
  private final ShippingApplicationService service;

  ShippingController(ShippingApplicationService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/orders/{orderId}/shipping")
  ShippingApplicationService.ShipmentView get(Authentication auth, @PathVariable UUID orderId) {
    ShippingApplicationService.ShipmentView v = service.find(orderId);
    if (!v.memberLoginId().equals(auth.getName())) throw new SecurityException("다른 회원의 배송입니다.");
    return v;
  }

  @PostMapping("/api/v1/orders/{orderId}/confirmation")
  ShippingApplicationService.ShipmentView confirm(Authentication auth, @PathVariable UUID orderId) {
    return service.confirm(orderId, auth.getName());
  }

  @PostMapping("/api-admin/v1/shipments/{orderId}/ship")
  ShippingApplicationService.ShipmentView ship(@PathVariable UUID orderId) {
    return service.ship(orderId);
  }

  @PostMapping("/api-admin/v1/shipments/{orderId}/deliver")
  ShippingApplicationService.ShipmentView deliver(@PathVariable UUID orderId) {
    return service.deliver(orderId);
  }
}
