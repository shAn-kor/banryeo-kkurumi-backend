package com.banryeokkurumi.inventory;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryApplicationService {
    private final InventoryRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public InventoryApplicationService(InventoryRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository; this.events = events; this.clock = clock;
    }

    @Transactional
    public InventoryView setStock(UUID skuId, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        repository.upsert(skuId, quantity);
        events.publishEvent(new CommerceEvents.StockChanged(UUID.randomUUID(), Instant.now(clock), 1, skuId, quantity));
        return find(skuId);
    }

    @Transactional
    public void reserve(UUID orderId, List<CommerceEvents.OrderLine> lines) {
        Instant expiresAt = Instant.now(clock).plus(Duration.ofMinutes(15));
        for (CommerceEvents.OrderLine line : lines) {
            if (!repository.reserve(orderId, line.skuId(), line.quantity(), expiresAt)) {
                repository.release(orderId);
                throw new InsufficientStockException();
            }
        }
    }

    @Transactional
    public void commit(UUID orderId) { repository.commit(orderId); }

    @Transactional
    public void release(UUID orderId) { repository.release(orderId); }

    @Transactional
    public void cancel(UUID orderId) { repository.cancel(orderId); }

    @Transactional(readOnly = true)
    public InventoryView find(UUID skuId) {
        InventoryRepository.StockView stock = repository.find(skuId).orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다."));
        return new InventoryView(stock.skuId(), stock.available(), stock.reserved(), stock.sold());
    }

    public record InventoryView(UUID skuId, int availableQuantity, int reservedQuantity, int soldQuantity) {}
}
