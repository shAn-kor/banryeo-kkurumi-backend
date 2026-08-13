package com.banryeokkurumi.shipping;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ShippingRepository {
    private final JdbcClient jdbc;
    ShippingRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    ShipmentData create(UUID orderId,String member,CommerceEvents.EncryptedAddress a,Instant now){
        jdbc.sql("""
                INSERT INTO shipping_shipment(id,order_id,member_login_id,status,recipient_name,recipient_phone,postal_code,address_line1,address_line2,created_at)
                VALUES (:id,:orderId,:member,'PREPARING',:name,:phone,:postal,:line1,:line2,:now)
                ON DUPLICATE KEY UPDATE order_id=order_id
                """).param("id",UUID.randomUUID().toString()).param("orderId",orderId.toString()).param("member",member)
                .param("name",a.recipientName()).param("phone",a.recipientPhone()).param("postal",a.postalCode())
                .param("line1",a.addressLine1()).param("line2",a.addressLine2()).param("now",now).update();return find(orderId).orElseThrow();
    }
    Optional<ShipmentData> find(UUID orderId){return jdbc.sql("SELECT order_id,member_login_id,status,shipped_at,delivered_at,confirmed_at,created_at FROM shipping_shipment WHERE order_id=:id")
            .param("id",orderId.toString()).query((rs,row)->new ShipmentData(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),
                    instant(rs.getTimestamp(4)),instant(rs.getTimestamp(5)),instant(rs.getTimestamp(6)),rs.getTimestamp(7).toInstant())).optional();}
    void transition(UUID orderId,String expected,String next,String column,Instant now){
        int updated=jdbc.sql("UPDATE shipping_shipment SET status=:next,"+column+"=:now WHERE order_id=:id AND status=:expected")
                .param("next",next).param("now",now).param("id",orderId.toString()).param("expected",expected).update();
        if(updated==0){ShipmentData current=find(orderId).orElseThrow(()->new IllegalArgumentException("배송을 찾을 수 없습니다."));if(!current.status().equals(next))throw new IllegalStateException("배송 상태를 변경할 수 없습니다.");}
    }
    void cancel(UUID orderId){
        int updated=jdbc.sql("UPDATE shipping_shipment SET status='CANCELLED' WHERE order_id=:id AND status='PREPARING'")
                .param("id",orderId.toString()).update();
        if(updated==0){
            ShipmentData current=find(orderId).orElse(null);
            if(current!=null && !current.status().equals("CANCELLED"))throw new IllegalStateException("출고된 배송은 취소할 수 없습니다.");
        }
    }
    List<ShipmentData> due(String status,String timeColumn,Instant cutoff){return jdbc.sql("SELECT order_id FROM shipping_shipment WHERE status=:status AND "+timeColumn+"<=:cutoff")
            .param("status",status).param("cutoff",cutoff).query(String.class).list().stream().map(UUID::fromString).map(this::find).flatMap(Optional::stream).toList();}
    Instant instant(java.sql.Timestamp value){return value==null?null:value.toInstant();}
    record ShipmentData(UUID orderId,String memberLoginId,String status,Instant shippedAt,Instant deliveredAt,Instant confirmedAt,Instant createdAt){}
}
