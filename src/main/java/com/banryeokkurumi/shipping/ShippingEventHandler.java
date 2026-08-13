package com.banryeokkurumi.shipping;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
class ShippingEventHandler {
    private final ShippingApplicationService service;private final ApplicationEventPublisher events;private final Clock clock;private final ShippingEventProcessingRegistry processed;
    private final Duration shipDelay;private final Duration deliveryDelay;
    ShippingEventHandler(ShippingApplicationService service,ApplicationEventPublisher events,Clock clock,ShippingEventProcessingRegistry processed,
                         @Value("${app.shipping.ship-delay:1m}")Duration shipDelay,@Value("${app.shipping.delivery-delay:2m}")Duration deliveryDelay){
        this.service=service;this.events=events;this.clock=clock;this.processed=processed;this.shipDelay=shipDelay;this.deliveryDelay=deliveryDelay;}
    @ApplicationModuleListener void on(CommerceEvents.ShipmentRequested event){if(!processed.claim(event.eventId()))return;service.create(event.orderId(),event.memberLoginId(),event.address());events.publishEvent(new CommerceEvents.ShipmentCreated(UUID.randomUUID(),now(),1,event.orderId()));}
    @ApplicationModuleListener void on(CommerceEvents.OrderCancelled event){if(processed.claim(event.eventId()))service.cancel(event.orderId());}
    @Scheduled(fixedDelay=30000) void advance(){service.dueToShip(shipDelay).forEach(s->ship(s.orderId()));service.dueToDeliver(deliveryDelay).forEach(s->deliver(s.orderId()));service.dueToConfirm().forEach(s->confirm(s.orderId()));}
    void ship(UUID orderId){service.ship(orderId);events.publishEvent(new CommerceEvents.ShipmentShipped(UUID.randomUUID(),now(),1,orderId));}
    void deliver(UUID orderId){service.deliver(orderId);events.publishEvent(new CommerceEvents.ShipmentDelivered(UUID.randomUUID(),now(),1,orderId,now()));}
    void confirm(UUID orderId){service.autoConfirm(orderId);events.publishEvent(new CommerceEvents.ShipmentConfirmed(UUID.randomUUID(),now(),1,orderId));}
    Instant now(){return Instant.now(clock);}
}
