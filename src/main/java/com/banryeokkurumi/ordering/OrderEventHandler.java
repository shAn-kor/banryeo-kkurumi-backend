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
    private final OrderApplicationService service; private final ApplicationEventPublisher events; private final Clock clock;
    OrderEventHandler(OrderApplicationService service, ApplicationEventPublisher events, Clock clock) { this.service=service; this.events=events; this.clock=clock; }

    @ApplicationModuleListener
    void on(CommerceEvents.StockReserved event) {
        OrderApplicationService.OrderView order = service.stockReserved(event.orderId());
        if (order.issuedCouponId() == null) publishPayment(order);
        else events.publishEvent(new CommerceEvents.CouponReservationRequested(UUID.randomUUID(), now(), 1, order.orderId(),
                order.issuedCouponId(), order.memberLoginId(), order.totalAmount()));
    }
    @ApplicationModuleListener void on(CommerceEvents.StockRejected event) { service.fail(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.CouponReserved event) { publishPayment(service.couponReserved(event.orderId(), event.discountAmount())); }
    @ApplicationModuleListener void on(CommerceEvents.CouponRejected event) { service.fail(event.orderId()); events.publishEvent(new CommerceEvents.OrderCancelled(UUID.randomUUID(),now(),1,event.orderId())); }
    @ApplicationModuleListener void on(CommerceEvents.PaymentSucceeded event) {
        OrderApplicationService.OrderView order=service.paid(event.orderId());
        OrderRepository.OrderData data=service.load(event.orderId());
        events.publishEvent(new CommerceEvents.OrderPaid(UUID.randomUUID(),now(),1,order.orderId(),order.memberLoginId()));
        events.publishEvent(new CommerceEvents.ShipmentRequested(UUID.randomUUID(),now(),1,order.orderId(),order.memberLoginId(),data.address()));
    }
    @ApplicationModuleListener void on(CommerceEvents.PaymentFailed event) { service.fail(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.PaymentCancelled event) { service.cancelled(event.orderId()); events.publishEvent(new CommerceEvents.OrderCancelled(UUID.randomUUID(),now(),1,event.orderId())); }
    @ApplicationModuleListener void on(CommerceEvents.ShipmentCreated event) { service.fulfilling(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.ShipmentShipped event) { service.shipped(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.ShipmentDelivered event) { service.delivered(event.orderId()); }
    @ApplicationModuleListener void on(CommerceEvents.ShipmentConfirmed event) {
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
