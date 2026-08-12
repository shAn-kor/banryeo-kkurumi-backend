package com.banryeokkurumi.inventory;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
class InventoryEventHandler {
    private final InventoryApplicationService service;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    InventoryEventHandler(InventoryApplicationService service, ApplicationEventPublisher events, Clock clock) {
        this.service = service; this.events = events; this.clock = clock;
    }

    @ApplicationModuleListener
    void on(CommerceEvents.OrderSubmitted event) {
        try {
            service.reserve(event.orderId(), event.lines());
            events.publishEvent(new CommerceEvents.StockReserved(UUID.randomUUID(), Instant.now(clock), 1, event.orderId()));
        } catch (InsufficientStockException exception) {
            events.publishEvent(new CommerceEvents.StockRejected(UUID.randomUUID(), Instant.now(clock), 1, event.orderId(), exception.getMessage()));
        }
    }

    @ApplicationModuleListener
    void on(CommerceEvents.PaymentSucceeded event) { service.commit(event.orderId()); }

    @ApplicationModuleListener
    void on(CommerceEvents.PaymentFailed event) { service.release(event.orderId()); }

    @ApplicationModuleListener
    void on(CommerceEvents.OrderCancelled event) { service.release(event.orderId()); }
}
