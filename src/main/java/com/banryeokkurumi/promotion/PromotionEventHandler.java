package com.banryeokkurumi.promotion;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
class PromotionEventHandler {
    private final PromotionApplicationService service; private final ApplicationEventPublisher events; private final Clock clock; private final PromotionEventProcessingRegistry processed;
    PromotionEventHandler(PromotionApplicationService service, ApplicationEventPublisher events, Clock clock, PromotionEventProcessingRegistry processed) { this.service=service; this.events=events; this.clock=clock; this.processed=processed; }
    @ApplicationModuleListener void on(CommerceEvents.CouponReservationRequested event) {
        if (!processed.claim(event.eventId())) return;
        try {
            long discount=service.reserve(event.issuedCouponId(),event.memberLoginId(),event.orderId(),event.orderAmount());
            events.publishEvent(new CommerceEvents.CouponReserved(UUID.randomUUID(),Instant.now(clock),1,event.orderId(),discount));
        } catch (RuntimeException exception) {
            events.publishEvent(new CommerceEvents.CouponRejected(UUID.randomUUID(),Instant.now(clock),1,event.orderId(),exception.getMessage()));
        }
    }
    @ApplicationModuleListener void on(CommerceEvents.PaymentSucceeded event) { if (processed.claim(event.eventId())) service.use(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.PaymentFailed event) { if (processed.claim(event.eventId())) service.release(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.OrderCancelled event) { if (processed.claim(event.eventId())) service.release(event.orderId()); }
}
