package com.banryeokkurumi.inventory.bench;

import com.banryeokkurumi.inventory.bench.ReservationStrategy.Outcome;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Tag("benchmark")
class StockReservationBenchmark {
  private record Scenario(String name, int skuCount, int stockPerSku, int attempts) {}

  private static final Scenario HOT = new Scenario("hot", 1, 2_000, 6_000);
  private static final Scenario SPREAD = new Scenario("spread", 1_000, 50, 20_000);
  private static final Scenario WARM_UP = new Scenario("warmup", 1, 200, 500);

  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  private final String jdbcUrl =
      System.getProperty("bench.jdbcUrl", "jdbc:mysql://localhost:13306/banryeo_performance");
  private final String dbUser = System.getProperty("bench.user", "performance");
  private final String dbSecret = System.getProperty("bench.password", "performance");
  private final String redisUri = System.getProperty("bench.redisUri", "redis://localhost:16379");

  @Test
  void run() throws Exception {
    String resultDirProperty = System.getProperty("bench.resultDir");
    Assumptions.assumeTrue(
        resultDirProperty != null && !resultDirProperty.isBlank(), "bench.resultDir is required");
    Path resultDir = Path.of(resultDirProperty);
    Files.createDirectories(resultDir);

    List<String> strategies = list("bench.strategies", "conditional,pessimistic,optimistic,redis");
    List<String> scenarios = list("bench.scenarios", "hot,spread");
    List<Integer> workerCounts =
        list("bench.workers", "16,64").stream().map(Integer::parseInt).toList();
    int runs = Integer.parseInt(System.getProperty("bench.runs", "3"));

    Path summary = resultDir.resolve("summary.tsv");
    if (!Files.exists(summary)) {
      Files.writeString(summary, BenchResult.tsvHeader() + "\n", StandardOpenOption.CREATE);
    }

    // Strategies are interleaved inside each run so slow drift of the VM does not favour one.
    for (int workers : workerCounts) {
      for (String strategy : strategies) execute(strategy, WARM_UP, workers, 0);
      for (String scenarioName : scenarios) {
        Scenario scenario = scenarioName.equals("hot") ? HOT : SPREAD;
        for (int run = 1; run <= runs; run++) {
          for (String strategy : strategies) {
            BenchResult result = execute(strategy, scenario, workers, run);
            String base = "%s-%s-w%d-run%d".formatted(strategy, scenario.name(), workers, run);
            MAPPER.writeValue(resultDir.resolve(base + ".json").toFile(), result);
            Files.writeString(
                summary,
                result.tsvLine() + "\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
            System.out.println("[bench] " + result.tsvLine());
          }
        }
      }
    }
  }

  private BenchResult execute(String strategyName, Scenario scenario, int workers, int run)
      throws Exception {
    int[] skuIndexes = skuSequence(scenario, run);
    String[] skuIds = new String[scenario.skuCount()];
    for (int i = 0; i < skuIds.length; i++) {
      skuIds[i] =
          UUID.nameUUIDFromBytes(("bench-sku-" + i).getBytes(StandardCharsets.UTF_8)).toString();
    }

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(dbUser);
    config.setPassword(dbSecret);
    config.setMaximumPoolSize(workers);
    config.setMinimumIdle(workers);
    config.setConnectionTimeout(60_000);

    RedisClient redisClient = null;
    StatefulRedisConnection<String, String> redisConnection = null;
    try (HikariDataSource dataSource = new HikariDataSource(config)) {
      prepareTables(dataSource, skuIds, scenario.stockPerSku());
      fillPool(dataSource, workers);

      RedisCommands<String, String> redis = null;
      if (strategyName.equals("redis")) {
        redisClient = RedisClient.create(redisUri);
        redisConnection = redisClient.connect();
        redis = redisConnection.sync();
        loadRedis(redis, skuIds, scenario.stockPerSku());
      }
      ReservationStrategy strategy = strategy(strategyName, dataSource, redis);

      int attempts = scenario.attempts();
      long[] latencyNanos = new long[attempts];
      Outcome[] outcomes = new Outcome[attempts];
      AtomicInteger next = new AtomicInteger();
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(workers);
      List<Future<?>> futures = new ArrayList<>();
      for (int w = 0; w < workers; w++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  int i;
                  while ((i = next.getAndIncrement()) < attempts) {
                    long began = System.nanoTime();
                    Outcome outcome;
                    try {
                      outcome = strategy.reserve(UUID.randomUUID(), skuIds[skuIndexes[i]], 1);
                    } catch (RuntimeException e) {
                      outcome = Outcome.ERROR;
                    }
                    latencyNanos[i] = System.nanoTime() - began;
                    outcomes[i] = outcome;
                  }
                  return null;
                }));
      }
      Instant startedAt = Instant.now();
      long wallStart = System.nanoTime();
      start.countDown();
      for (Future<?> future : futures) future.get();
      long wallNanos = System.nanoTime() - wallStart;
      pool.shutdown();

      BenchResult.Invariants invariants =
          verify(dataSource, redis, skuIds, skuIndexes, outcomes, scenario.stockPerSku());
      return summarize(
          strategyName,
          scenario,
          workers,
          run,
          strategy,
          outcomes,
          latencyNanos,
          wallNanos,
          invariants,
          startedAt);
    } finally {
      if (redisConnection != null) redisConnection.close();
      if (redisClient != null) redisClient.shutdown();
    }
  }

  private static ReservationStrategy strategy(
      String name, HikariDataSource dataSource, RedisCommands<String, String> redis) {
    return switch (name) {
      case "conditional" -> new ConditionalUpdateStrategy(dataSource);
      case "pessimistic" -> new PessimisticLockStrategy(dataSource);
      case "optimistic" -> new OptimisticLockStrategy(dataSource);
      case "redis" -> new RedisLuaStrategy(dataSource, redis);
      default -> throw new IllegalArgumentException("Unknown strategy: " + name);
    };
  }

  private static int[] skuSequence(Scenario scenario, int run) {
    int[] sequence = new int[scenario.attempts()];
    if (scenario.skuCount() == 1) return sequence;
    Random random = new Random(run);
    for (int i = 0; i < sequence.length; i++) sequence[i] = random.nextInt(scenario.skuCount());
    return sequence;
  }

  private static void prepareTables(HikariDataSource dataSource, String[] skuIds, int stock)
      throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute("DROP TABLE IF EXISTS bench_inventory_reservation");
      s.execute("DROP TABLE IF EXISTS bench_inventory_stock");
      s.execute(
          """
          CREATE TABLE bench_inventory_stock (
              sku_id CHAR(36) PRIMARY KEY, available_quantity INT NOT NULL, reserved_quantity INT NOT NULL,
              sold_quantity INT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
              CHECK (available_quantity >= 0), CHECK (reserved_quantity >= 0), CHECK (sold_quantity >= 0)
          )
          """);
      s.execute(
          """
          CREATE TABLE bench_inventory_reservation (
              order_id CHAR(36) NOT NULL, sku_id CHAR(36) NOT NULL, quantity INT NOT NULL, status VARCHAR(20) NOT NULL,
              expires_at TIMESTAMP(6) NOT NULL, PRIMARY KEY (order_id, sku_id),
              INDEX idx_bench_inventory_reservation_expiry (status, expires_at)
          )
          """);
      c.setAutoCommit(false);
      try (PreparedStatement ps =
          c.prepareStatement(
              "INSERT INTO bench_inventory_stock(sku_id, available_quantity, reserved_quantity, sold_quantity, version) VALUES (?,?,0,0,0)")) {
        for (String skuId : skuIds) {
          ps.setString(1, skuId);
          ps.setInt(2, stock);
          ps.addBatch();
        }
        ps.executeBatch();
      }
      c.commit();
    }
  }

  private static void fillPool(HikariDataSource dataSource, int size) throws SQLException {
    List<Connection> held = new ArrayList<>();
    try {
      for (int i = 0; i < size; i++) held.add(dataSource.getConnection());
    } finally {
      for (Connection connection : held) connection.close();
    }
  }

  private static void loadRedis(RedisCommands<String, String> redis, String[] skuIds, int stock) {
    List<String> old = redis.keys(RedisLuaStrategy.KEY_PREFIX + "*");
    if (!old.isEmpty()) redis.del(old.toArray(new String[0]));
    for (String skuId : skuIds) {
      redis.set(RedisLuaStrategy.KEY_PREFIX + skuId, Integer.toString(stock));
    }
  }

  private static BenchResult.Invariants verify(
      HikariDataSource dataSource,
      RedisCommands<String, String> redis,
      String[] skuIds,
      int[] skuIndexes,
      Outcome[] outcomes,
      int initialStock)
      throws SQLException {
    int[] successes = new int[skuIds.length];
    int[] rejected = new int[skuIds.length];
    int totalSuccesses = 0;
    for (int i = 0; i < outcomes.length; i++) {
      if (outcomes[i] == Outcome.RESERVED) {
        successes[skuIndexes[i]]++;
        totalSuccesses++;
      } else {
        rejected[skuIndexes[i]]++;
      }
    }

    int negative = 0;
    int sumMismatch = 0;
    int conservation = 0;
    int lost = 0;
    int redisMismatch = 0;
    int oversell = Math.max(0, totalSuccesses - skuIds.length * initialStock);
    long reservationRows;
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      for (int i = 0; i < skuIds.length; i++) {
        try (PreparedStatement ps =
            c.prepareStatement(
                "SELECT available_quantity, reserved_quantity FROM bench_inventory_stock WHERE sku_id=?")) {
          ps.setString(1, skuIds[i]);
          try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            int available = rs.getInt(1);
            int reserved = rs.getInt(2);
            if (available < 0) negative++;
            if (reserved != successes[i]) sumMismatch++;
            if (available + reserved != initialStock) conservation++;
            if (available > 0) lost += rejected[i];
            if (successes[i] > initialStock) oversell = Math.max(oversell, 1);
            if (redis != null) {
              String remaining = redis.get(RedisLuaStrategy.KEY_PREFIX + skuIds[i]);
              if (remaining == null || Integer.parseInt(remaining) != available) redisMismatch++;
            }
          }
        }
      }
      try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM bench_inventory_reservation")) {
        rs.next();
        reservationRows = rs.getLong(1);
      }
    }
    int rowCountMismatch = (int) Math.abs(reservationRows - totalSuccesses);
    return new BenchResult.Invariants(
        oversell, negative, sumMismatch, rowCountMismatch, conservation, lost, redisMismatch);
  }

  private static BenchResult summarize(
      String strategyName,
      Scenario scenario,
      int workers,
      int run,
      ReservationStrategy strategy,
      Outcome[] outcomes,
      long[] latencyNanos,
      long wallNanos,
      BenchResult.Invariants invariants,
      Instant startedAt) {
    int successes = 0;
    int insufficient = 0;
    int exhausted = 0;
    int errors = 0;
    for (Outcome outcome : outcomes) {
      switch (outcome) {
        case RESERVED -> successes++;
        case INSUFFICIENT -> insufficient++;
        case RETRY_EXHAUSTED -> exhausted++;
        case ERROR -> errors++;
      }
    }
    long[] sorted = latencyNanos.clone();
    Arrays.sort(sorted);
    double wallMs = wallNanos / 1_000_000.0;
    double wallSec = wallNanos / 1_000_000_000.0;
    StrategyCounters counters = strategy.counters();
    return new BenchResult(
        strategyName,
        scenario.name(),
        workers,
        run,
        outcomes.length,
        successes,
        insufficient,
        exhausted,
        errors,
        counters.deadlocks.sum(),
        counters.lockWaitTimeouts.sum(),
        counters.retries.sum(),
        counters.compensations.sum(),
        wallMs,
        outcomes.length / wallSec,
        successes / wallSec,
        new BenchResult.Latency(
            percentile(sorted, 0.50),
            percentile(sorted, 0.95),
            percentile(sorted, 0.99),
            sorted[sorted.length - 1] / 1_000_000.0),
        invariants,
        startedAt.toString());
  }

  private static double percentile(long[] sorted, double p) {
    int index = (int) Math.ceil(p * sorted.length) - 1;
    return sorted[Math.max(0, index)] / 1_000_000.0;
  }

  private static List<String> list(String property, String defaultValue) {
    return Arrays.stream(System.getProperty(property, defaultValue).split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .toList();
  }
}
