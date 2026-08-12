package com.banryeokkurumi.shipping;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShippingApplicationService {
    private final ShippingRepository repository;private final Clock clock;
    public ShippingApplicationService(ShippingRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    @Transactional public ShipmentView create(UUID orderId,String member,CommerceEvents.EncryptedAddress address){return view(repository.create(orderId,member,address,Instant.now(clock)));}
    @Transactional public ShipmentView ship(UUID orderId){repository.transition(orderId,"PREPARING","SHIPPED","shipped_at",Instant.now(clock));return find(orderId);}
    @Transactional public ShipmentView deliver(UUID orderId){repository.transition(orderId,"SHIPPED","DELIVERED","delivered_at",Instant.now(clock));return find(orderId);}
    @Transactional public ShipmentView confirm(UUID orderId,String member){ShipmentView shipment=find(orderId);if(!shipment.memberLoginId().equals(member))throw new SecurityException("다른 회원의 배송입니다.");repository.transition(orderId,"DELIVERED","CONFIRMED","confirmed_at",Instant.now(clock));return find(orderId);}
    @Transactional public ShipmentView autoConfirm(UUID orderId){repository.transition(orderId,"DELIVERED","CONFIRMED","confirmed_at",Instant.now(clock));return find(orderId);}
    @Transactional(readOnly=true) public ShipmentView find(UUID orderId){return view(repository.find(orderId).orElseThrow(()->new IllegalArgumentException("배송을 찾을 수 없습니다.")));}
    @Transactional(readOnly=true) public List<ShipmentView> dueToShip(Duration delay){return repository.due("PREPARING","created_at",Instant.now(clock).minus(delay)).stream().map(this::view).toList();}
    @Transactional(readOnly=true) public List<ShipmentView> dueToDeliver(Duration delay){return repository.due("SHIPPED","shipped_at",Instant.now(clock).minus(delay)).stream().map(this::view).toList();}
    @Transactional(readOnly=true) public List<ShipmentView> dueToConfirm(){return repository.due("DELIVERED","delivered_at",Instant.now(clock).minus(Duration.ofDays(7))).stream().map(this::view).toList();}
    ShipmentView view(ShippingRepository.ShipmentData d){return new ShipmentView(d.orderId(),d.memberLoginId(),d.status(),d.shippedAt(),d.deliveredAt(),d.confirmedAt());}
    public record ShipmentView(UUID orderId,String memberLoginId,String status,Instant shippedAt,Instant deliveredAt,Instant confirmedAt){}
}
