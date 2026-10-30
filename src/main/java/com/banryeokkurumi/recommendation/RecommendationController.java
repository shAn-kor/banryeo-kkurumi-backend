package com.banryeokkurumi.recommendation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationController {
  private final RecommendationApplicationService service;
  private final Clock clock;

  RecommendationController(RecommendationApplicationService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @GetMapping
  List<RecommendationApplicationService.RecommendationView> top(
      @RequestParam(defaultValue = "ALL") String category) {
    return service.top(category);
  }

  @PostMapping("/interactions")
  void observe(@RequestBody InteractionRequest request) {
    service.observe(request.skuId(), request.category(), request.type(), 1, Instant.now(clock));
  }

  record InteractionRequest(UUID skuId, String category, String type) {}
}
