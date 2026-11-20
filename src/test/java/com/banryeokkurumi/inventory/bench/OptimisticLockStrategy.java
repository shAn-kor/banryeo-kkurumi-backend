package com.banryeokkurumi.inventory.bench;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;

final class OptimisticLockStrategy extends JdbcReservationStrategy {
  private static final int MAX_ATTEMPTS = 20;

  OptimisticLockStrategy(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public Outcome reserve(UUID orderId, String skuId, int quantity) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      Connection c = null;
      try {
        c = begin();
        if (reservationExists(c, orderId, skuId)) {
          c.rollback();
          return Outcome.RESERVED;
        }
        long[] stock = readStock(c, skuId);
        if (stock[0] < quantity) {
          c.rollback();
          return Outcome.INSUFFICIENT;
        }
        if (updateWithVersion(c, skuId, quantity, stock[1]) == 1) {
          insertReservation(c, orderId, skuId, quantity);
          c.commit();
          return Outcome.RESERVED;
        }
        c.rollback();
      } catch (SQLException e) {
        return failed(c, e);
      } finally {
        ConditionalUpdateStrategy.close(c);
      }
      if (attempt < MAX_ATTEMPTS) {
        counters().retries.increment();
        sleepBackoff();
      }
    }
    return Outcome.RETRY_EXHAUSTED;
  }

  private static long[] readStock(Connection c, String skuId) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT available_quantity, version FROM bench_inventory_stock WHERE sku_id=?")) {
      ps.setString(1, skuId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next()
            ? new long[] {rs.getInt(1), rs.getLong(2)}
            : new long[] {Integer.MIN_VALUE, -1};
      }
    }
  }

  private static int updateWithVersion(Connection c, String skuId, int quantity, long version)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "UPDATE bench_inventory_stock SET available_quantity=available_quantity-?, reserved_quantity=reserved_quantity+?, version=version+1 WHERE sku_id=? AND version=?")) {
      ps.setInt(1, quantity);
      ps.setInt(2, quantity);
      ps.setString(3, skuId);
      ps.setLong(4, version);
      return ps.executeUpdate();
    }
  }

  private static void sleepBackoff() {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextLong(1, 11));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
