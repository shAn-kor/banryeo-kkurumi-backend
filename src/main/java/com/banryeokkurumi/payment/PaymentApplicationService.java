package com.banryeokkurumi.payment;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentApplicationService {
    private final PaymentRepository repository; private final Clock clock; private final ApplicationEventPublisher events; private final Duration reconciliationDelay; private final Duration reconciliationWindow;
    public PaymentApplicationService(PaymentRepository repository,Clock clock,ApplicationEventPublisher events,
                                     @Value("${app.payment.reconciliation-delay:10s}") Duration reconciliationDelay,
                                     @Value("${app.payment.reconciliation-window:10m}") Duration reconciliationWindow){this.repository=repository;this.clock=clock;this.events=events;this.reconciliationDelay=reconciliationDelay;this.reconciliationWindow=reconciliationWindow;}
    @Transactional public PaymentView create(UUID orderId,String member,long amount,String scenario,String key){return view(repository.create(orderId,member,amount,DemoPaymentProvider.Scenario.valueOf(scenario).name(),key,Instant.now(clock)));}
    @Transactional public PaymentView success(UUID orderId,String tx){repository.success(orderId,tx,Instant.now(clock));PaymentView payment=find(orderId);events.publishEvent(new CommerceEvents.PaymentSucceeded(UUID.randomUUID(),Instant.now(clock),1,orderId,tx));return payment;}
    @Transactional public PaymentView fail(UUID orderId,String reason){repository.fail(orderId,reason,Instant.now(clock));PaymentView payment=find(orderId);events.publishEvent(new CommerceEvents.PaymentFailed(UUID.randomUUID(),Instant.now(clock),1,orderId,reason));return payment;}
    @Transactional public PaymentView unknown(UUID orderId,String reason){Instant now=Instant.now(clock);repository.unknown(orderId,reason,now.plus(reconciliationDelay),now.plus(reconciliationWindow),now);return find(orderId);}
    @Transactional public PaymentView reschedule(UUID orderId){Instant now=Instant.now(clock);repository.reschedule(orderId,now.plus(reconciliationDelay),now);return find(orderId);}
    @Transactional public PaymentView cancel(UUID orderId){repository.cancel(orderId,Instant.now(clock));return find(orderId);}
    @Transactional(readOnly=true) public PaymentView find(UUID orderId){return view(repository.find(orderId).orElseThrow(()->new IllegalArgumentException("결제를 찾을 수 없습니다.")));}
    @Transactional(readOnly=true) public Optional<PaymentView> findOptional(UUID orderId){return repository.find(orderId).map(this::view);}
    @Transactional(readOnly=true) public List<PaymentView> due(){return repository.due(Instant.now(clock)).stream().map(this::view).toList();}
    PaymentView view(PaymentRepository.PaymentData d){return new PaymentView(d.orderId(),d.memberLoginId(),d.amount(),d.status(),d.scenario(),d.providerTransactionId(),d.failureReason(),d.deadline(),d.nextAt(),d.updatedAt());}
    public record PaymentView(UUID orderId,String memberLoginId,long amount,String status,DemoPaymentProvider.Scenario scenario,String providerTransactionId,
                              String failureReason,Instant reconciliationDeadline,Instant nextReconciliationAt,Instant updatedAt){}
}
