package com.banryeokkurumi.inventory.bench;

import java.util.Locale;

record BenchResult(
    String strategy,
    String scenario,
    int workers,
    int run,
    int attempts,
    int successes,
    int insufficient,
    int retryExhausted,
    int errors,
    long deadlocks,
    long lockWaitTimeouts,
    long retries,
    long compensations,
    double wallMs,
    double attemptsPerSec,
    double successesPerSec,
    Latency latency,
    Invariants invariants,
    String startedAt) {

  record Latency(double p50, double p95, double p99, double max) {}

  record Invariants(
      int oversell,
      int negativeStock,
      int sumMismatch,
      int rowCountMismatch,
      int conservationMismatch,
      int lost,
      int redisMismatch) {}

  static String tsvHeader() {
    return String.join(
        "\t",
        "strategy",
        "scenario",
        "workers",
        "run",
        "attempts",
        "successes",
        "insufficient",
        "retryExhausted",
        "errors",
        "deadlocks",
        "lockWaitTimeouts",
        "retries",
        "compensations",
        "wallMs",
        "attemptsPerSec",
        "successesPerSec",
        "p50",
        "p95",
        "p99",
        "max",
        "oversell",
        "negativeStock",
        "sumMismatch",
        "rowCountMismatch",
        "conservationMismatch",
        "lost",
        "redisMismatch",
        "startedAt");
  }

  String tsvLine() {
    return String.join(
        "\t",
        strategy,
        scenario,
        String.valueOf(workers),
        String.valueOf(run),
        String.valueOf(attempts),
        String.valueOf(successes),
        String.valueOf(insufficient),
        String.valueOf(retryExhausted),
        String.valueOf(errors),
        String.valueOf(deadlocks),
        String.valueOf(lockWaitTimeouts),
        String.valueOf(retries),
        String.valueOf(compensations),
        String.format(Locale.ROOT, "%.1f", wallMs),
        String.format(Locale.ROOT, "%.1f", attemptsPerSec),
        String.format(Locale.ROOT, "%.1f", successesPerSec),
        String.format(Locale.ROOT, "%.3f", latency.p50()),
        String.format(Locale.ROOT, "%.3f", latency.p95()),
        String.format(Locale.ROOT, "%.3f", latency.p99()),
        String.format(Locale.ROOT, "%.3f", latency.max()),
        String.valueOf(invariants.oversell()),
        String.valueOf(invariants.negativeStock()),
        String.valueOf(invariants.sumMismatch()),
        String.valueOf(invariants.rowCountMismatch()),
        String.valueOf(invariants.conservationMismatch()),
        String.valueOf(invariants.lost()),
        String.valueOf(invariants.redisMismatch()),
        startedAt);
  }
}
