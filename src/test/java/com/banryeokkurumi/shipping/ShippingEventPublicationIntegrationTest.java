package com.banryeokkurumi.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import com.banryeokkurumi.TestcontainersConfiguration;
import com.banryeokkurumi.contracts.CommerceEvents;
import java.time.Duration;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;

@Import({
  TestcontainersConfiguration.class,
  ShippingEventPublicationIntegrationTest.CaptureConfiguration.class
})
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShippingEventPublicationIntegrationTest {
  private static final String MEMBER = "shipping-event-member";
  private static final CommerceEvents.EncryptedAddress ADDRESS =
      new CommerceEvents.EncryptedAddress("name", "phone", "postal", "line1", "line2");

  private final ShippingApplicationService service;
  private final ShippingEventHandler handler;
  private final ShippingController controller;
  private final Capture capture;

  @Autowired
  ShippingEventPublicationIntegrationTest(
      ShippingApplicationService service,
      ShippingEventHandler handler,
      ShippingController controller,
      Capture capture) {
    this.service = service;
    this.handler = handler;
    this.controller = controller;
    this.capture = capture;
  }

  @Test
  void 스케줄러의_출고배송확정이벤트가_모듈리스너에한번씩전달된다() {
    UUID orderId = prepared();

    handler.ship(orderId);
    handler.ship(orderId);
    handler.deliver(orderId);
    handler.confirm(orderId);

    await(
        () -> {
          assertThat(capture.shipped()).contains(orderId);
          assertThat(capture.delivered()).contains(orderId);
          assertThat(capture.confirmed()).contains(orderId);
        });
    assertThat(capture.shipped()).filteredOn(orderId::equals).hasSize(1);
  }

  @Test
  void 회원과관리자API의_배송이벤트가_모듈리스너에전달된다() {
    UUID orderId = prepared();

    controller.ship(orderId);
    controller.deliver(orderId);
    controller.confirm(new TestingAuthenticationToken(MEMBER, null), orderId);

    await(
        () -> {
          assertThat(capture.shipped()).contains(orderId);
          assertThat(capture.delivered()).contains(orderId);
          assertThat(capture.confirmed()).contains(orderId);
        });
  }

  private UUID prepared() {
    UUID orderId = UUID.randomUUID();
    service.create(orderId, MEMBER, ADDRESS);
    return orderId;
  }

  private static void await(Runnable assertion) {
    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(assertion::run);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class CaptureConfiguration {
    @Bean
    Capture shippingEventCapture() {
      return new Capture();
    }
  }

  static class Capture {
    private final Queue<UUID> shipped = new ConcurrentLinkedQueue<>();
    private final Queue<UUID> delivered = new ConcurrentLinkedQueue<>();
    private final Queue<UUID> confirmed = new ConcurrentLinkedQueue<>();

    // The listener annotations proxy this bean, so tests read through methods, not fields.
    Queue<UUID> shipped() {
      return shipped;
    }

    Queue<UUID> delivered() {
      return delivered;
    }

    Queue<UUID> confirmed() {
      return confirmed;
    }

    @ApplicationModuleListener
    void on(CommerceEvents.ShipmentShipped event) {
      shipped.add(event.orderId());
    }

    @ApplicationModuleListener
    void on(CommerceEvents.ShipmentDelivered event) {
      delivered.add(event.orderId());
    }

    @ApplicationModuleListener
    void on(CommerceEvents.ShipmentConfirmed event) {
      confirmed.add(event.orderId());
    }
  }
}
