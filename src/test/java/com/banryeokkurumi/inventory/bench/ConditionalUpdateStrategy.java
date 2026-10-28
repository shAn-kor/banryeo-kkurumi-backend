package com.banryeokkurumi.inventory.bench;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

final class ConditionalUpdateStrategy extends JdbcReservationStrategy {
  ConditionalUpdateStrategy(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  public Outcome reserve(UUID orderId, String skuId, int quantity) {
    Connection c = null;
    try {
      c = begin();
      if (reservationExists(c, orderId, skuId)) {
        c.rollback();
        return Outcome.RESERVED;
      }
      if (reserveStock(c, skuId, quantity, " AND available_quantity>=?") == 0) {
        c.rollback();
        return Outcome.INSUFFICIENT;
      }
      insertReservation(c, orderId, skuId, quantity);
      c.commit();
      return Outcome.RESERVED;
    } catch (SQLException e) {
      return failed(c, e);
    } finally {
      close(c);
    }
  }

  static void close(Connection c) {
    if (c == null) return;
    try {
      c.close();
    } catch (SQLException ignored) {
      // Nothing to recover when returning a connection fails.
    }
  }
}
