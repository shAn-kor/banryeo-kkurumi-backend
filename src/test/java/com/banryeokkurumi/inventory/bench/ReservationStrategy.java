package com.banryeokkurumi.inventory.bench;

import java.util.UUID;

interface ReservationStrategy {
  enum Outcome {
    RESERVED,
    INSUFFICIENT,
    RETRY_EXHAUSTED,
    ERROR
  }

  Outcome reserve(UUID orderId, String skuId, int quantity);

  StrategyCounters counters();
}
