package com.banryeokkurumi.review;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class ReviewEventHandler {
    private final ReviewApplicationService service;
    ReviewEventHandler(ReviewApplicationService service){this.service=service;}
    @ApplicationModuleListener void on(CommerceEvents.PurchaseConfirmed event){service.grant(event);}
}
