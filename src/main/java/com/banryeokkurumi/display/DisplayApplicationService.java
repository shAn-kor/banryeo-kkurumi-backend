package com.banryeokkurumi.display;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class DisplayApplicationService {
    private final OfferRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public DisplayApplicationService(OfferRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository; this.events = events; this.clock = clock;
    }

    @Transactional
    public OfferView upsert(UpsertOfferCommand command) {
        if (command.price() < 0) throw new IllegalArgumentException("판매가는 0 이상이어야 합니다.");
        Instant now = Instant.now(clock);
        OfferEntity offer = repository.save(new OfferEntity(command.productId(), command.skuId(), command.price(), command.active(), command.displayOrder(), now));
        events.publishEvent(new CommerceEvents.OfferChanged(UUID.randomUUID(), now, 1, offer.productId, offer.skuId,
                offer.price, offer.active, offer.displayOrder));
        return OfferView.from(offer);
    }

    @Transactional(readOnly = true)
    public OfferView quote(UUID skuId) {
        OfferEntity offer = repository.findById(skuId).filter(candidate -> candidate.active)
                .orElseThrow(() -> new OfferUnavailableException());
        return OfferView.from(offer);
    }

    public record UpsertOfferCommand(UUID productId, UUID skuId, long price, boolean active, int displayOrder) {}
    public record OfferView(UUID productId, UUID skuId, long price, boolean active, int displayOrder) {
        static OfferView from(OfferEntity e) { return new OfferView(e.productId, e.skuId, e.price, e.active, e.displayOrder); }
    }
    public static final class OfferUnavailableException extends RuntimeException { public OfferUnavailableException() { super("판매 가능한 상품이 아닙니다."); } }
}
