package com.banryeokkurumi.payment;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
class PaymentEventHandler {
    private final PaymentApplicationService service; private final DemoPaymentProvider provider; private final ApplicationEventPublisher events; private final Clock clock; private final PaymentEventProcessingRegistry processed;
    PaymentEventHandler(PaymentApplicationService service,DemoPaymentProvider provider,ApplicationEventPublisher events,Clock clock,PaymentEventProcessingRegistry processed){this.service=service;this.provider=provider;this.events=events;this.clock=clock;this.processed=processed;}
    @ApplicationModuleListener void on(CommerceEvents.PaymentRequested event){
        if(!processed.claim(event.eventId()))return;
        PaymentApplicationService.PaymentView payment=service.create(event.orderId(),event.memberLoginId(),event.amount(),event.scenario(),event.idempotencyKey());
        if("SUCCEEDED".equals(payment.status())){publishSuccess(payment);return;}
        try{resolve(event.orderId(),provider.authorize(event.orderId(),event.amount(),event.idempotencyKey(),payment.scenario()).join());}
        catch(RuntimeException exception){service.unknown(event.orderId(),rootMessage(exception));events.publishEvent(new CommerceEvents.PaymentUnknown(UUID.randomUUID(),now(),1,event.orderId(),rootMessage(exception)));}
    }
    @ApplicationModuleListener void on(CommerceEvents.OrderCancellationRequested event){
        if(!processed.claim(event.eventId()))return;
        PaymentApplicationService.PaymentView payment=service.findOptional(event.orderId()).orElse(null);
        if(payment!=null) provider.cancel(event.orderId(),payment.scenario());
        if(payment!=null) service.cancel(event.orderId());
        events.publishEvent(new CommerceEvents.PaymentCancelled(UUID.randomUUID(),now(),1,event.orderId()));
    }
    @Scheduled(fixedDelayString="${app.payment.reconciliation-delay:10s}") void reconcile(){
        service.due().forEach(payment->{
            if(payment.reconciliationDeadline()!=null && !now().isBefore(payment.reconciliationDeadline())){
                provider.cancel(payment.orderId(),payment.scenario());service.fail(payment.orderId(),"RECONCILIATION_TIMEOUT");
                return;
            }
            try{resolve(payment.orderId(),provider.query(payment.orderId(),payment.scenario()));}
            catch(RuntimeException exception){service.reschedule(payment.orderId());}
        });
    }
    void resolve(UUID orderId,DemoPaymentProvider.Result result){
        if(result.status()==DemoPaymentProvider.Status.SUCCEEDED){service.success(orderId,result.transactionId());}
        else if(result.status()==DemoPaymentProvider.Status.DECLINED){service.fail(orderId,result.reason());}
    }
    void publishSuccess(PaymentApplicationService.PaymentView p){events.publishEvent(new CommerceEvents.PaymentSucceeded(UUID.randomUUID(),now(),1,p.orderId(),p.providerTransactionId()));}
    String rootMessage(Throwable error){Throwable root=error;while(root.getCause()!=null)root=root.getCause();return root.getMessage()==null?root.getClass().getSimpleName():root.getMessage();}
    Instant now(){return Instant.now(clock);}
}
