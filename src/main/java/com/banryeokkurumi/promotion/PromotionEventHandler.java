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
    private final PromotionApplicationService service; private final ApplicationEventPublisher events; private final Clock clock;
    PromotionEventHandler(PromotionApplicationService service, ApplicationEventPublisher events, Clock clock) { this.service=service; this.events=events; this.clock=clock; }
    @ApplicationModuleListener void on(CommerceEvents.CouponReservationRequested event) {
        try {
            long discount=service.reserve(event.issuedCouponId(),event.memberLoginId(),event.orderId(),event.orderAmount());
            events.publishEvent(new CommerceEvents.CouponReserved(UUID.randomUUID(),Instant.now(clock),1,event.orderId(),discount));
        } catch (RuntimeException exception) {
            events.publishEvent(new CommerceEvents.CouponRejected(UUID.randomUUID(),Instant.now(clock),1,event.orderId(),exception.getMessage()));
        }
    }
    @ApplicationModuleListener void on(CommerceEvents.PaymentSucceeded event) { service.use(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.PaymentFailed event) { service.release(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.OrderCancelled event) { service.release(event.orderId()); }
}
