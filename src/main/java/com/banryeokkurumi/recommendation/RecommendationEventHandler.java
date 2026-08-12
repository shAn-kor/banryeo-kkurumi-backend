package com.banryeokkurumi.recommendation;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class RecommendationEventHandler {
    private final RecommendationApplicationService service;
    RecommendationEventHandler(RecommendationApplicationService service){this.service=service;}
    @ApplicationModuleListener void on(CommerceEvents.InteractionObserved event){service.observe(event.skuId(),event.categoryName(),event.interactionType(),event.multiplier(),event.occurredAt());}
    @ApplicationModuleListener void on(CommerceEvents.PurchaseConfirmed event){event.lines().forEach(line->service.observe(line.skuId(),"ALL","PURCHASE",line.quantity(),event.occurredAt()));}
    @Scheduled(cron="0 0 * * * *") void rebuild(){service.rebuild();}
}
