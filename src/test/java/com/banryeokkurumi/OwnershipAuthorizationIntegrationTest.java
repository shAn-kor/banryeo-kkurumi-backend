package com.banryeokkurumi;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banryeokkurumi.contracts.CommerceEvents;
import com.banryeokkurumi.ordering.OrderApplicationService;
import com.banryeokkurumi.payment.PaymentApplicationService;
import com.banryeokkurumi.shipping.ShippingApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OwnershipAuthorizationIntegrationTest {
  private static final String OWNER = "owner-member";
  private static final String OTHER = "other-member";
  private static final CommerceEvents.EncryptedAddress ADDRESS =
      new CommerceEvents.EncryptedAddress("n", "p", "z", "l1", "l2");

  private final MockMvc mvc;
  private final OrderApplicationService orders;
  private final ShippingApplicationService shipping;
  private final PaymentApplicationService payments;

  @Autowired
  OwnershipAuthorizationIntegrationTest(
      MockMvc mvc,
      OrderApplicationService orders,
      ShippingApplicationService shipping,
      PaymentApplicationService payments) {
    this.mvc = mvc;
    this.orders = orders;
    this.shipping = shipping;
    this.payments = payments;
  }

  @Test
  void 다른회원의_주문배송결제조회는_403이다() throws Exception {
    UUID orderId = order();
    shipping.create(orderId, OWNER, ADDRESS);
    payments.create(orderId, OWNER, 10_000, "SUCCESS", "ownership:" + orderId);

    for (String path : List.of("", "/shipping", "/payment")) {
      mvc.perform(get("/api/v1/orders/" + orderId + path).with(user(OTHER)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.title").value("접근 거부"));
      mvc.perform(get("/api/v1/orders/" + orderId + path).with(user(OWNER)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void 다른회원의_주문취소와구매확정은_403이다() throws Exception {
    UUID orderId = order();
    shipping.create(orderId, OWNER, ADDRESS);

    mvc.perform(post("/api/v1/orders/" + orderId + "/cancellation").with(user(OTHER)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/orders/" + orderId + "/confirmation").with(user(OTHER)).with(csrf()))
        .andExpect(status().isForbidden());
  }

  private UUID order() {
    return orders
        .create(
            new OrderApplicationService.CreateOrderCommand(
                OWNER,
                List.of(
                    new CommerceEvents.OrderLine(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "상품",
                        "기본",
                        10_000,
                        1)),
                null,
                "SUCCESS",
                ADDRESS))
        .orderId();
  }
}
