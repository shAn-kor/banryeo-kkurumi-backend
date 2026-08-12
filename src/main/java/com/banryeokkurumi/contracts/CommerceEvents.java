package com.banryeokkurumi.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommerceEvents {
    private CommerceEvents() {
    }

    public interface Event {
        UUID eventId();
        Instant occurredAt();
        int schemaVersion();
    }

    public record ProductCataloged(UUID eventId, Instant occurredAt, int schemaVersion, UUID productId, UUID skuId,
                                   String name, String brandName, String categoryName, String optionName) implements Event {}
    public record OfferChanged(UUID eventId, Instant occurredAt, int schemaVersion, UUID productId, UUID skuId,
                               long price, boolean active, int displayOrder) implements Event {}
    public record StockChanged(UUID eventId, Instant occurredAt, int schemaVersion, UUID skuId,
                               int availableQuantity) implements Event {}
    public record RatingChanged(UUID eventId, Instant occurredAt, int schemaVersion, UUID skuId,
                                double averageRating, long reviewCount) implements Event {}
    public record RankingChanged(UUID eventId, Instant occurredAt, int schemaVersion, UUID skuId,
                                 double popularityScore) implements Event {}

    public record OrderSubmitted(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, String memberLoginId,
                                 List<OrderLine> lines, UUID issuedCouponId, long totalAmount) implements Event {
        public OrderSubmitted { lines = List.copyOf(lines); }
    }
    public record OrderLine(UUID orderItemId, UUID productId, UUID skuId, String productName, String optionName,
                            long unitPrice, int quantity) {}
    public record StockReserved(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId) implements Event {}
    public record StockRejected(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, String reason) implements Event {}
    public record CouponReservationRequested(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId,
                                             UUID issuedCouponId, String memberLoginId, long orderAmount) implements Event {}
    public record CouponReserved(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId,
                                 long discountAmount) implements Event {}
    public record CouponRejected(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, String reason) implements Event {}
    public record PaymentRequested(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, String memberLoginId,
                                   long amount, String idempotencyKey, String scenario) implements Event {}
    public record PaymentSucceeded(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId,
                                   String providerTransactionId) implements Event {}
    public record PaymentFailed(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, String reason) implements Event {}
    public record PaymentUnknown(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, String reason) implements Event {}
    public record PaymentCancelled(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId) implements Event {}
    public record OrderPaid(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, String memberLoginId) implements Event {}
    public record OrderCancellationRequested(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId) implements Event {}
    public record OrderCancelled(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId) implements Event {}
    public record ShipmentRequested(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, String memberLoginId,
                                    EncryptedAddress address) implements Event {}
    public record EncryptedAddress(String recipientName, String recipientPhone, String postalCode,
                                   String addressLine1, String addressLine2) {}
    public record ShipmentCreated(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId) implements Event {}
    public record ShipmentShipped(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId) implements Event {}
    public record ShipmentDelivered(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId, Instant deliveredAt) implements Event {}
    public record ShipmentConfirmed(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId) implements Event {}
    public record PurchaseConfirmed(UUID eventId, Instant occurredAt, int schemaVersion, UUID orderId,
                                    String memberLoginId, List<OrderLine> lines) implements Event {
        public PurchaseConfirmed { lines = List.copyOf(lines); }
    }
    public record InteractionObserved(UUID eventId, Instant occurredAt, int schemaVersion, UUID skuId,
                                      String categoryName, String interactionType, double multiplier) implements Event {}
}
