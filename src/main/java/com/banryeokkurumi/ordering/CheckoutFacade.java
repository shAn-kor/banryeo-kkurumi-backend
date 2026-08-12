package com.banryeokkurumi.ordering;

import com.banryeokkurumi.catalog.CatalogApplicationService;
import com.banryeokkurumi.contracts.CommerceEvents;
import com.banryeokkurumi.display.DisplayApplicationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CheckoutFacade {
    private final CatalogApplicationService catalog;
    private final DisplayApplicationService display;
    private final OrderApplicationService orders;
    private final PiiCipher piiCipher;
    public CheckoutFacade(CatalogApplicationService catalog, DisplayApplicationService display,
                          OrderApplicationService orders, PiiCipher piiCipher) {
        this.catalog=catalog; this.display=display; this.orders=orders; this.piiCipher=piiCipher;
    }

    public OrderApplicationService.OrderView checkout(CheckoutCommand command) {
        List<CommerceEvents.OrderLine> lines = command.items().stream().map(item -> {
            CatalogApplicationService.CatalogView product = catalog.findBySkuId(item.skuId());
            DisplayApplicationService.OfferView offer = display.quote(item.skuId());
            return new CommerceEvents.OrderLine(UUID.randomUUID(), product.productId(), product.skuId(), product.name(),
                    product.optionName(), offer.price(), item.quantity());
        }).toList();
        CommerceEvents.EncryptedAddress address = new CommerceEvents.EncryptedAddress(
                piiCipher.encrypt(command.address().recipientName()), piiCipher.encrypt(command.address().recipientPhone()),
                piiCipher.encrypt(command.address().postalCode()), piiCipher.encrypt(command.address().addressLine1()),
                piiCipher.encrypt(command.address().addressLine2()));
        return orders.create(new OrderApplicationService.CreateOrderCommand(command.memberLoginId(), lines,
                command.issuedCouponId(), command.paymentScenario(), address));
    }

    public record CheckoutCommand(String memberLoginId, List<CheckoutItem> items, UUID issuedCouponId,
                                  String paymentScenario, ShippingAddress address) { public CheckoutCommand { items=List.copyOf(items); } }
    public record CheckoutItem(UUID skuId, int quantity) { public CheckoutItem { if (quantity<=0 || quantity>99) throw new IllegalArgumentException("주문 수량은 1부터 99까지입니다."); } }
    public record ShippingAddress(String recipientName, String recipientPhone, String postalCode, String addressLine1,
                                  String addressLine2) {}
}
