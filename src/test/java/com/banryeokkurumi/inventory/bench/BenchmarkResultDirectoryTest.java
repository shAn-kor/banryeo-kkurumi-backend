package com.banryeokkurumi.inventory.bench;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkResultDirectoryTest {
  @TempDir Path root;

  @Test
  void 없는결과디렉터리는_만들어서사용한다() {
    Path resultDir = root.resolve("new-run");

    assertThatCode(() -> StockReservationBenchmark.prepareResultDir(resultDir))
        .doesNotThrowAnyException();
  }

  @Test
  void 이전결과가남은디렉터리는_JSON과TSV가섞이지않게거절한다() throws Exception {
    Files.writeString(root.resolve("summary.tsv"), "strategy\n");

    assertThatThrownBy(() -> StockReservationBenchmark.prepareResultDir(root))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("empty");
  }
}
