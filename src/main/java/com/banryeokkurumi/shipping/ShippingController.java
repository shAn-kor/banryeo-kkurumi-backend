package com.banryeokkurumi.shipping;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RestController
class ShippingController {
    private final ShippingApplicationService service;private final ApplicationEventPublisher events;private final Clock clock;
    ShippingController(ShippingApplicationService service,ApplicationEventPublisher events,Clock clock){this.service=service;this.events=events;this.clock=clock;}
    @GetMapping("/api/v1/orders/{orderId}/shipping") ShippingApplicationService.ShipmentView get(Authentication auth,@PathVariable UUID orderId){ShippingApplicationService.ShipmentView v=service.find(orderId);if(!v.memberLoginId().equals(auth.getName()))throw new SecurityException("다른 회원의 배송입니다.");return v;}
    @PostMapping("/api/v1/orders/{orderId}/confirmation") ShippingApplicationService.ShipmentView confirm(Authentication auth,@PathVariable UUID orderId){ShippingApplicationService.ShipmentView v=service.confirm(orderId,auth.getName());events.publishEvent(new CommerceEvents.ShipmentConfirmed(UUID.randomUUID(),Instant.now(clock),1,orderId));return v;}
    @PostMapping("/api-admin/v1/shipments/{orderId}/ship") ShippingApplicationService.ShipmentView ship(@PathVariable UUID orderId){ShippingApplicationService.ShipmentView v=service.ship(orderId);events.publishEvent(new CommerceEvents.ShipmentShipped(UUID.randomUUID(),Instant.now(clock),1,orderId));return v;}
    @PostMapping("/api-admin/v1/shipments/{orderId}/deliver") ShippingApplicationService.ShipmentView deliver(@PathVariable UUID orderId){ShippingApplicationService.ShipmentView v=service.deliver(orderId);events.publishEvent(new CommerceEvents.ShipmentDelivered(UUID.randomUUID(),Instant.now(clock),1,orderId,Instant.now(clock)));return v;}
}
