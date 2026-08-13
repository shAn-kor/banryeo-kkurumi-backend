package com.banryeokkurumi.promotion;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PromotionApplicationService {
    private final PromotionRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public PromotionApplicationService(PromotionRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository; this.events = events; this.clock = clock;
    }
    @Transactional public UUID createCampaign(CreateCampaignCommand command) {
        return repository.createCampaign(new PromotionRepository.CreateCampaign(command.name(), command.type(), command.value(),
                command.maximumDiscount(), command.minimumOrderAmount(), command.scopeType(), command.scopeId(), command.totalQuantity(), command.startsAt(), command.endsAt()));
    }
    @Transactional public UUID issue(UUID campaignId, String memberLoginId) { return repository.issue(campaignId, memberLoginId, Instant.now(clock)); }
    @Transactional(readOnly = true) public List<CouponView> findMine(String memberLoginId) { return repository.memberCoupons(memberLoginId).stream().map(c -> new CouponView(c.id(), c.campaignId(), c.status(), c.issuedAt())).toList(); }
    @Transactional public long reserve(UUID couponId, String memberLoginId, UUID orderId, long amount) { return couponId == null ? 0 : repository.reserve(couponId, memberLoginId, orderId, amount); }
    @Transactional public CouponReservationResult tryReserve(UUID couponId, String memberLoginId, UUID orderId, long amount) {
        try { return new CouponReservationResult(true, couponId == null ? 0 : repository.reserve(couponId, memberLoginId, orderId, amount), null); }
        catch (RuntimeException exception) { return new CouponReservationResult(false, 0, exception.getMessage()); }
    }
    @Transactional public void use(UUID orderId) { repository.use(orderId); }
    @Transactional public void release(UUID orderId) { repository.release(orderId); }

    public record CreateCampaignCommand(String name, String type, int value, long maximumDiscount, long minimumOrderAmount,
                                        String scopeType, UUID scopeId, int totalQuantity, Instant startsAt, Instant endsAt) {}
    public record CouponView(UUID id, UUID campaignId, String status, Instant issuedAt) {}
    public record CouponReservationResult(boolean accepted, long discountAmount, String reason) {}
}
