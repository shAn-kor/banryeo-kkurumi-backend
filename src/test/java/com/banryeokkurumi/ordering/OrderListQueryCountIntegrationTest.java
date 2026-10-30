package com.banryeokkurumi.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.banryeokkurumi.TestcontainersConfiguration;
import com.banryeokkurumi.contracts.CommerceEvents;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

@Import({
  TestcontainersConfiguration.class,
  OrderListQueryCountIntegrationTest.QueryCountConfiguration.class
})
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderListQueryCountIntegrationTest {
  private static final int ORDERS = 20;
  private static final int LINES_PER_ORDER = 2;

  private final OrderApplicationService orders;
  private final OrderRepository repository;
  private final QueryCounter counter;

  @Autowired
  OrderListQueryCountIntegrationTest(
      OrderApplicationService orders, OrderRepository repository, QueryCounter counter) {
    this.orders = orders;
    this.repository = repository;
    this.counter = counter;
  }

  @Test
  void 내주문목록은_주문수와관계없이_쿼리2회로조회한다() {
    String member = "list-member-" + UUID.randomUUID();
    Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
    List<UUID> created =
        IntStream.range(0, ORDERS).mapToObj(i -> insertOrder(member, base.plusSeconds(i))).toList();

    counter.start();
    List<OrderApplicationService.OrderView> mine = orders.findMine(member);
    int queries = counter.stop();

    assertThat(mine).hasSize(ORDERS);
    assertThat(mine)
        .extracting(OrderApplicationService.OrderView::orderId)
        .containsExactlyElementsOf(created.reversed());
    assertThat(mine).allSatisfy(order -> assertThat(order.items()).hasSize(LINES_PER_ORDER));
    assertThat(queries).isEqualTo(2);
  }

  @Test
  void 주문이없는회원은_빈목록을반환한다() {
    counter.start();
    List<OrderApplicationService.OrderView> mine = orders.findMine("no-order-" + UUID.randomUUID());
    int queries = counter.stop();

    assertThat(mine).isEmpty();
    assertThat(queries).isEqualTo(1);
  }

  private UUID insertOrder(String member, Instant createdAt) {
    UUID orderId = UUID.randomUUID();
    List<CommerceEvents.OrderLine> lines =
        IntStream.range(0, LINES_PER_ORDER)
            .mapToObj(
                i ->
                    new CommerceEvents.OrderLine(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "상품" + i,
                        "기본",
                        1_000,
                        1))
            .toList();
    repository.create(
        new OrderRepository.OrderData(
            orderId,
            member,
            OrderStatus.SUBMITTED,
            2_000,
            0,
            2_000,
            null,
            "SUCCESS",
            new CommerceEvents.EncryptedAddress("n", "p", "z", "l1", "l2"),
            createdAt,
            createdAt),
        lines);
    return orderId;
  }

  /** Counts statements executed on the thread that called {@link #start()}. */
  static class QueryCounter implements QueryExecutionListener {
    private final AtomicInteger count = new AtomicInteger();
    private volatile Thread target;

    void start() {
      count.set(0);
      target = Thread.currentThread();
    }

    int stop() {
      target = null;
      return count.get();
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {}

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
      if (Thread.currentThread() == target) count.addAndGet(queryInfoList.size());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class QueryCountConfiguration {
    @Bean
    static QueryCounter queryCounter() {
      return new QueryCounter();
    }

    @Bean
    static BeanPostProcessor countingDataSource(QueryCounter counter) {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (!(bean instanceof DataSource dataSource)) return bean;
          return ProxyDataSourceBuilder.create(dataSource).listener(counter).build();
        }
      };
    }
  }
}
