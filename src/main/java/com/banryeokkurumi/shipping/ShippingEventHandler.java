package com.banryeokkurumi.shipping;

import com.banryeokkurumi.contracts.CommerceEvents;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ShippingEventHandler {
  private final ShippingApplicationService service;
  private final ShippingEventProcessingRegistry processed;
  private final Duration shipDelay;
  private final Duration deliveryDelay;

  ShippingEventHandler(
      ShippingApplicationService service,
      ShippingEventProcessingRegistry processed,
      @Value("${app.shipping.ship-delay:1m}") Duration shipDelay,
      @Value("${app.shipping.delivery-delay:2m}") Duration deliveryDelay) {
    this.service = service;
    this.processed = processed;
    this.shipDelay = shipDelay;
    this.deliveryDelay = deliveryDelay;
  }

  @ApplicationModuleListener
  void on(CommerceEvents.ShipmentRequested event) {
    if (!processed.claim(event.eventId())) return;
    service.create(event.orderId(), event.memberLoginId(), event.address());
  }

  @ApplicationModuleListener
  void on(CommerceEvents.OrderCancelled event) {
    if (processed.claim(event.eventId())) service.cancel(event.orderId());
  }

  @Scheduled(fixedDelay = 30000)
  void advance() {
    service.dueToShip(shipDelay).forEach(s -> ship(s.orderId()));
    service.dueToDeliver(deliveryDelay).forEach(s -> deliver(s.orderId()));
    service.dueToConfirm().forEach(s -> confirm(s.orderId()));
  }

  void ship(UUID orderId) {
    service.ship(orderId);
  }

  void deliver(UUID orderId) {
    service.deliver(orderId);
  }

  void confirm(UUID orderId) {
    service.autoConfirm(orderId);
  }
}
