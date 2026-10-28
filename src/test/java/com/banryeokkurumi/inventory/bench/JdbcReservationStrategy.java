package com.banryeokkurumi.inventory.bench;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;

abstract class JdbcReservationStrategy implements ReservationStrategy {
  private static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

  private final DataSource dataSource;
  private final StrategyCounters counters = new StrategyCounters();

  JdbcReservationStrategy(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public StrategyCounters counters() {
    return counters;
  }

  final Connection begin() throws SQLException {
    Connection connection = dataSource.getConnection();
    connection.setAutoCommit(false);
    return connection;
  }

  final Outcome failed(Connection connection, SQLException e) {
    counters.classify(e);
    rollbackQuietly(connection);
    return Outcome.ERROR;
  }

  static void rollbackQuietly(Connection connection) {
    if (connection == null) return;
    try {
      connection.rollback();
    } catch (SQLException ignored) {
      // The connection is discarded by the pool if it is broken.
    }
  }

  static boolean reservationExists(Connection c, UUID orderId, String skuId) throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT COUNT(*) FROM bench_inventory_reservation WHERE order_id=? AND sku_id=?")) {
      ps.setString(1, orderId.toString());
      ps.setString(2, skuId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1) > 0;
      }
    }
  }

  static void insertReservation(Connection c, UUID orderId, String skuId, int quantity)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO bench_inventory_reservation(order_id, sku_id, quantity, status, expires_at) VALUES (?,?,?,'RESERVED',?)")) {
      ps.setString(1, orderId.toString());
      ps.setString(2, skuId);
      ps.setInt(3, quantity);
      ps.setTimestamp(4, Timestamp.from(Instant.now().plus(RESERVATION_TTL)));
      ps.executeUpdate();
    }
  }

  static int reserveStock(Connection c, String skuId, int quantity, String condition)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "UPDATE bench_inventory_stock SET available_quantity=available_quantity-?, reserved_quantity=reserved_quantity+? WHERE sku_id=?"
                + condition)) {
      ps.setInt(1, quantity);
      ps.setInt(2, quantity);
      ps.setString(3, skuId);
      if (!condition.isEmpty()) ps.setInt(4, quantity);
      return ps.executeUpdate();
    }
  }
}
