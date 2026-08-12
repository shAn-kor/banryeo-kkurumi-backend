package com.banryeokkurumi.recommendation;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationController {
    private final RecommendationApplicationService service;private final ApplicationEventPublisher events;private final Clock clock;
    RecommendationController(RecommendationApplicationService service,ApplicationEventPublisher events,Clock clock){this.service=service;this.events=events;this.clock=clock;}
    @GetMapping List<RecommendationApplicationService.RecommendationView> top(@RequestParam(defaultValue="ALL")String category){return service.top(category);}
    @PostMapping("/interactions") void observe(@RequestBody InteractionRequest request){events.publishEvent(new CommerceEvents.InteractionObserved(UUID.randomUUID(),Instant.now(clock),1,request.skuId(),request.category(),request.type(),1));}
    record InteractionRequest(UUID skuId,String category,String type){}
}
