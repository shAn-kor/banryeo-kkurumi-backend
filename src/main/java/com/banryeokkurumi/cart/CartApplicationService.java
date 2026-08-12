package com.banryeokkurumi.cart;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CartApplicationService {
    private final CartItemRepository repository;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public CartApplicationService(CartItemRepository repository, StringRedisTemplate redis, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository; this.redis = redis; this.events = events; this.clock = clock;
    }

    @Transactional
    public CartView put(String memberLoginId, UUID skuId, int quantity) {
        if (quantity <= 0 || quantity > 99) throw new IllegalArgumentException("장바구니 수량은 1부터 99까지입니다.");
        Instant now = Instant.now(clock);
        CartItemEntity item = repository.findByMemberLoginIdAndSkuId(memberLoginId, skuId)
                .map(existing -> { existing.changeQuantity(quantity, now); return existing; })
                .orElseGet(() -> new CartItemEntity(UUID.randomUUID(), memberLoginId, skuId, quantity, now));
        repository.save(item);
        evictCache(memberLoginId);
        events.publishEvent(new CommerceEvents.InteractionObserved(UUID.randomUUID(), now, 1, skuId, "ALL", "CART", 1));
        return get(memberLoginId);
    }

    @Transactional(readOnly = true)
    public CartView get(String memberLoginId) {
        List<CartLine> lines = repository.findAllByMemberLoginIdOrderByUpdatedAtDesc(memberLoginId).stream()
                .map(item -> new CartLine(item.skuId, item.quantity, item.updatedAt)).toList();
        cache(memberLoginId, lines);
        return new CartView(lines);
    }

    @Transactional
    public void clear(String memberLoginId) { repository.deleteAllByMemberLoginId(memberLoginId); evictCache(memberLoginId); }

    public void evictCache(String memberLoginId) {
        try { redis.delete("cart:" + memberLoginId); } catch (RuntimeException ignored) { /* MySQL remains authoritative. */ }
    }

    public void cache(String memberLoginId, List<CartLine> lines) {
        try { redis.opsForValue().set("cart:" + memberLoginId, Integer.toString(lines.size()), Duration.ofHours(24)); }
        catch (RuntimeException ignored) { /* Cache failure must not fail the cart read. */ }
    }

    public record CartLine(UUID skuId, int quantity, Instant updatedAt) {}
    public record CartView(List<CartLine> items) { public CartView { items = List.copyOf(items); } }
}
