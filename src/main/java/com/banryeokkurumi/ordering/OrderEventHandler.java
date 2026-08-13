package com.banryeokkurumi.ordering;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
class OrderEventHandler {
    private final OrderApplicationService service; private final ApplicationEventPublisher events; private final Clock clock; private final OrderingEventProcessingRegistry processed;
    OrderEventHandler(OrderApplicationService service, ApplicationEventPublisher events, Clock clock, OrderingEventProcessingRegistry processed) { this.service=service; this.events=events; this.clock=clock; this.processed=processed; }

    @ApplicationModuleListener
    void on(CommerceEvents.StockReserved event) {
        if (!processed.claim(event.eventId())) return;
        OrderApplicationService.OrderView order = service.stockReserved(event.orderId());
        if (order.issuedCouponId() == null) publishPayment(order);
        else events.publishEvent(new CommerceEvents.CouponReservationRequested(UUID.randomUUID(), now(), 1, order.orderId(),
                order.issuedCouponId(), order.memberLoginId(), order.totalAmount()));
    }
    @ApplicationModuleListener void on(CommerceEvents.StockRejected event) { if (processed.claim(event.eventId())) service.fail(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.CouponReserved event) { if (processed.claim(event.eventId())) publishPayment(service.couponReserved(event.orderId(), event.discountAmount())); }
    @ApplicationModuleListener void on(CommerceEvents.CouponRejected event) { if (!processed.claim(event.eventId())) return; service.fail(event.orderId()); events.publishEvent(new CommerceEvents.OrderCancelled(UUID.randomUUID(),now(),1,event.orderId())); }
    @ApplicationModuleListener void on(CommerceEvents.PaymentSucceeded event) {
        if (!processed.claim(event.eventId())) return;
        OrderApplicationService.OrderView order=service.paid(event.orderId());
        OrderRepository.OrderData data=service.load(event.orderId());
        events.publishEvent(new CommerceEvents.OrderPaid(UUID.randomUUID(),now(),1,order.orderId(),order.memberLoginId()));
        events.publishEvent(new CommerceEvents.ShipmentRequested(UUID.randomUUID(),now(),1,order.orderId(),order.memberLoginId(),data.address()));
    }
    @ApplicationModuleListener void on(CommerceEvents.PaymentFailed event) { if (processed.claim(event.eventId())) service.fail(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.PaymentCancelled event) { if (!processed.claim(event.eventId())) return; service.cancelled(event.orderId()); events.publishEvent(new CommerceEvents.OrderCancelled(UUID.randomUUID(),now(),1,event.orderId())); }
    @ApplicationModuleListener void on(CommerceEvents.ShipmentCreated event) { if (processed.claim(event.eventId())) service.fulfilling(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.ShipmentShipped event) { if (processed.claim(event.eventId())) service.shipped(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.ShipmentDelivered event) { if (processed.claim(event.eventId())) service.delivered(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.ShipmentConfirmed event) {
        if (!processed.claim(event.eventId())) return;
        OrderApplicationService.OrderView order=service.confirmed(event.orderId());
        events.publishEvent(new CommerceEvents.PurchaseConfirmed(UUID.randomUUID(),now(),1,order.orderId(),order.memberLoginId(),order.items()));
    }

    void publishPayment(OrderApplicationService.OrderView order) {
        OrderRepository.OrderData data=service.load(order.orderId());
        events.publishEvent(new CommerceEvents.PaymentRequested(UUID.randomUUID(),now(),1,order.orderId(),order.memberLoginId(),
                order.payableAmount(),"order:"+order.orderId(),data.paymentScenario()));
    }
    Instant now() { return Instant.now(clock); }
}
