package com.banryeokkurumi.ordering;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderApplicationService {
    private final OrderRepository repository; private final ApplicationEventPublisher events; private final Clock clock;
    public OrderApplicationService(OrderRepository repository, ApplicationEventPublisher events, Clock clock) { this.repository=repository; this.events=events; this.clock=clock; }

    @Transactional
    public OrderView create(CreateOrderCommand command) {
        if (command.lines().isEmpty()) throw new IllegalArgumentException("주문 상품은 1개 이상이어야 합니다.");
        long total = command.lines().stream().mapToLong(line -> Math.multiplyExact(line.unitPrice(), line.quantity())).sum();
        UUID orderId = UUID.randomUUID(); Instant now = Instant.now(clock);
        OrderRepository.OrderData order = new OrderRepository.OrderData(orderId, command.memberLoginId(), OrderStatus.SUBMITTED,
                total, 0, total, command.issuedCouponId(), command.paymentScenario(), command.address(), now, now);
        repository.create(order, command.lines());
        events.publishEvent(new CommerceEvents.OrderSubmitted(UUID.randomUUID(), now, 1, orderId, command.memberLoginId(), command.lines(), command.issuedCouponId(), total));
        return view(order);
    }

    @Transactional public OrderView stockReserved(UUID orderId) {
        OrderRepository.OrderData order = load(orderId);
        repository.status(orderId, OrderStatus.SUBMITTED, order.issuedCouponId() == null ? OrderStatus.PAYMENT_PENDING : OrderStatus.STOCK_RESERVED, Instant.now(clock));
        return find(orderId);
    }
    @Transactional public OrderView couponReserved(UUID orderId, long discount) { repository.couponReserved(orderId, discount, Instant.now(clock)); return find(orderId); }
    @Transactional public OrderView paid(UUID orderId) { repository.status(orderId, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, Instant.now(clock)); return find(orderId); }
    @Transactional public OrderView fail(UUID orderId) {
        OrderRepository.OrderData order=load(orderId);
        if (order.status()==OrderStatus.FAILED) return view(order);
        repository.status(orderId, order.status(), OrderStatus.FAILED, Instant.now(clock)); return find(orderId);
    }
    @Transactional public OrderView requestCancellation(UUID orderId, String memberLoginId) {
        OrderRepository.OrderData order=load(orderId);
        if (!order.memberLoginId().equals(memberLoginId)) throw new SecurityException("다른 회원의 주문입니다.");
        if (order.status()==OrderStatus.SHIPPED || order.status()==OrderStatus.DELIVERED || order.status()==OrderStatus.CONFIRMED) throw new OrderStateException("출고된 주문은 취소할 수 없습니다.");
        repository.status(orderId, order.status(), OrderStatus.CANCELLATION_REQUESTED, Instant.now(clock));
        events.publishEvent(new CommerceEvents.OrderCancellationRequested(UUID.randomUUID(), Instant.now(clock), 1, orderId));
        return find(orderId);
    }
    @Transactional public OrderView cancelled(UUID orderId) { repository.status(orderId, OrderStatus.CANCELLATION_REQUESTED, OrderStatus.CANCELLED, Instant.now(clock)); return find(orderId); }
    @Transactional public OrderView fulfilling(UUID orderId) { repository.status(orderId, OrderStatus.PAID, OrderStatus.FULFILLING, Instant.now(clock)); return find(orderId); }
    @Transactional public OrderView shipped(UUID orderId) { repository.status(orderId, OrderStatus.FULFILLING, OrderStatus.SHIPPED, Instant.now(clock)); return find(orderId); }
    @Transactional public OrderView delivered(UUID orderId) { repository.status(orderId, OrderStatus.SHIPPED, OrderStatus.DELIVERED, Instant.now(clock)); return find(orderId); }
    @Transactional public OrderView confirmed(UUID orderId) { repository.status(orderId, OrderStatus.DELIVERED, OrderStatus.CONFIRMED, Instant.now(clock)); return find(orderId); }
    @Transactional(readOnly=true) public OrderView find(UUID orderId) { return view(load(orderId)); }
    @Transactional(readOnly=true) public List<OrderView> findMine(String member) { return repository.findByMember(member).stream().map(this::view).toList(); }
    @Transactional(readOnly=true) public List<CommerceEvents.OrderLine> lines(UUID orderId) { return repository.lines(orderId); }
    @Transactional(readOnly=true) OrderRepository.OrderData load(UUID orderId) { return repository.find(orderId).orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다.")); }
    OrderView view(OrderRepository.OrderData d) { return new OrderView(d.id(),d.memberLoginId(),d.status(),d.totalAmount(),d.discountAmount(),d.payableAmount(),d.issuedCouponId(),d.createdAt(),repository.lines(d.id())); }

    public record CreateOrderCommand(String memberLoginId, List<CommerceEvents.OrderLine> lines, UUID issuedCouponId,
                                     String paymentScenario, CommerceEvents.EncryptedAddress address) { public CreateOrderCommand { lines=List.copyOf(lines); } }
    public record OrderView(UUID orderId, String memberLoginId, OrderStatus status, long totalAmount, long discountAmount,
                            long payableAmount, UUID issuedCouponId, Instant createdAt, List<CommerceEvents.OrderLine> items) { public OrderView { items=List.copyOf(items); } }
}
