package com.banryeokkurumi.ordering;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {
    private final CheckoutFacade checkout; private final OrderApplicationService orders;
    OrderController(CheckoutFacade checkout, OrderApplicationService orders) { this.checkout=checkout; this.orders=orders; }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    OrderApplicationService.OrderView create(Authentication auth, @Valid @RequestBody CreateOrderRequest request) {
        return checkout.checkout(new CheckoutFacade.CheckoutCommand(auth.getName(), request.items().stream().map(i -> new CheckoutFacade.CheckoutItem(i.skuId(),i.quantity())).toList(),
                request.issuedCouponId(), request.paymentScenario(), new CheckoutFacade.ShippingAddress(request.address().recipientName(),request.address().recipientPhone(),request.address().postalCode(),request.address().addressLine1(),request.address().addressLine2())));
    }
    @GetMapping List<OrderApplicationService.OrderView> mine(Authentication auth) { return orders.findMine(auth.getName()); }
    @GetMapping("/{orderId}") OrderApplicationService.OrderView detail(Authentication auth,@PathVariable UUID orderId) {
        OrderApplicationService.OrderView view=orders.find(orderId); if(!view.memberLoginId().equals(auth.getName())) throw new SecurityException("다른 회원의 주문입니다."); return view;
    }
    @PostMapping("/{orderId}/cancellation") @ResponseStatus(HttpStatus.ACCEPTED)
    OrderApplicationService.OrderView cancel(Authentication auth,@PathVariable UUID orderId) { return orders.requestCancellation(orderId,auth.getName()); }
    record CreateOrderRequest(@NotEmpty List<@Valid ItemRequest> items, UUID issuedCouponId, @NotBlank String paymentScenario, @Valid @NotNull AddressRequest address) {}
    record ItemRequest(@NotNull UUID skuId,@Min(1) @Max(99) int quantity) {}
    record AddressRequest(@NotBlank String recipientName,@NotBlank String recipientPhone,@NotBlank String postalCode,@NotBlank String addressLine1,String addressLine2) { public AddressRequest { if(addressLine2==null) addressLine2=""; } }
}
