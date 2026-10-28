package com.banryeokkurumi.inventory.bench;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

final class RedisLuaStrategy extends JdbcReservationStrategy {
  static final String KEY_PREFIX = "bench:stock:";
  private static final String SCRIPT =
      """
      local stock = tonumber(redis.call('GET', KEYS[1]))
      if stock ~= nil and stock >= tonumber(ARGV[1]) then
        redis.call('DECRBY', KEYS[1], ARGV[1])
        return 1
      end
      return 0
      """;

  private final RedisCommands<String, String> redis;
  private final String sha;

  RedisLuaStrategy(DataSource dataSource, RedisCommands<String, String> redis) {
    super(dataSource);
    this.redis = redis;
    this.sha = redis.scriptLoad(SCRIPT);
  }

  @Override
  public Outcome reserve(UUID orderId, String skuId, int quantity) {
    String key = KEY_PREFIX + skuId;
    try {
      Long taken =
          redis.evalsha(
              sha, ScriptOutputType.INTEGER, new String[] {key}, Integer.toString(quantity));
      if (taken == null || taken == 0L) return Outcome.INSUFFICIENT;
    } catch (RuntimeException e) {
      return Outcome.ERROR;
    }
    Connection c = null;
    try {
      c = begin();
      if (reservationExists(c, orderId, skuId)) {
        c.rollback();
        compensate(key, quantity);
        return Outcome.RESERVED;
      }
      reserveStock(c, skuId, quantity, "");
      insertReservation(c, orderId, skuId, quantity);
      c.commit();
      return Outcome.RESERVED;
    } catch (SQLException e) {
      Outcome outcome = failed(c, e);
      compensate(key, quantity);
      return outcome;
    } finally {
      ConditionalUpdateStrategy.close(c);
    }
  }

  private void compensate(String key, int quantity) {
    counters().compensations.increment();
    redis.incrby(key, quantity);
  }
}
