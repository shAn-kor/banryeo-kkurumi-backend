package com.banryeokkurumi.inventory.bench;

import java.sql.SQLException;
import java.util.concurrent.atomic.LongAdder;

final class StrategyCounters {
  private static final int MYSQL_DEADLOCK = 1213;
  private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;

  final LongAdder deadlocks = new LongAdder();
  final LongAdder lockWaitTimeouts = new LongAdder();
  final LongAdder retries = new LongAdder();
  final LongAdder compensations = new LongAdder();

  void classify(SQLException e) {
    for (SQLException cause = e; cause != null; cause = cause.getNextException()) {
      if (cause.getErrorCode() == MYSQL_DEADLOCK) {
        deadlocks.increment();
        return;
      }
      if (cause.getErrorCode() == MYSQL_LOCK_WAIT_TIMEOUT) {
        lockWaitTimeouts.increment();
        return;
      }
    }
  }
}
