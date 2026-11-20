package com.banryeokkurumi.inventory.bench;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

final class PessimisticLockStrategy extends JdbcReservationStrategy {
  PessimisticLockStrategy(DataSource dataSource) {
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
      if (lockedAvailable(c, skuId) < quantity) {
        c.rollback();
        return Outcome.INSUFFICIENT;
      }
      reserveStock(c, skuId, quantity, "");
      insertReservation(c, orderId, skuId, quantity);
      c.commit();
      return Outcome.RESERVED;
    } catch (SQLException e) {
      return failed(c, e);
    } finally {
      ConditionalUpdateStrategy.close(c);
    }
  }

  private static int lockedAvailable(Connection c, String skuId) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT available_quantity FROM bench_inventory_stock WHERE sku_id=? FOR UPDATE")) {
      ps.setString(1, skuId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : Integer.MIN_VALUE;
      }
    }
  }
}
