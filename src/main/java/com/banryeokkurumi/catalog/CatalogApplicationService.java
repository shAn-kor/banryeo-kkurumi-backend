package com.banryeokkurumi.catalog;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CatalogApplicationService {
    private final CatalogItemRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CatalogApplicationService(CatalogItemRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CatalogView create(CreateCatalogCommand command) {
        CatalogProduct product = CatalogProduct.create(command.name(), command.categoryName(), command.brandName(), command.optionName());
        Instant now = Instant.now(clock);
        repository.save(new CatalogItemEntity(product, now));
        events.publishEvent(new CommerceEvents.ProductCataloged(UUID.randomUUID(), now, 1, product.productId(),
                product.skuId(), product.name(), product.brandName(), product.categoryName(), product.optionName()));
        return CatalogView.from(product, now);
    }

    @Transactional(readOnly = true)
    public CatalogView findBySkuId(UUID skuId) {
        CatalogItemEntity item = repository.findBySkuId(skuId).orElseThrow(() -> new CatalogNotFoundException());
        return CatalogView.from(item);
    }

    @Transactional(readOnly = true)
    public List<CatalogView> findAll() {
        return repository.findAll().stream().map(CatalogView::from).toList();
    }

    public record CreateCatalogCommand(String name, String categoryName, String brandName, String optionName) {}
    public record CatalogView(UUID productId, UUID skuId, String name, String categoryName, String brandName,
                              String optionName, Instant createdAt) {
        static CatalogView from(CatalogProduct p, Instant at) { return new CatalogView(p.productId(), p.skuId(), p.name(), p.categoryName(), p.brandName(), p.optionName(), at); }
        static CatalogView from(CatalogItemEntity e) { return new CatalogView(e.productId, e.skuId, e.name, e.categoryName, e.brandName, e.optionName, e.createdAt); }
    }
    public static final class CatalogNotFoundException extends RuntimeException { public CatalogNotFoundException() { super("상품을 찾을 수 없습니다."); } }
}
