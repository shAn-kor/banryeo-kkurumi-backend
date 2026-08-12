package com.banryeokkurumi.review;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ReviewRepository {
    private final JdbcClient jdbc;
    ReviewRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    void grant(CommerceEvents.PurchaseConfirmed event){event.lines().forEach(line->jdbc.sql("""
            INSERT INTO review_entitlement(order_item_id,order_id,member_login_id,sku_id,confirmed_at)
            VALUES (:item,:orderId,:member,:sku,:at) ON DUPLICATE KEY UPDATE confirmed_at=confirmed_at
            """).param("item",line.orderItemId().toString()).param("orderId",event.orderId().toString()).param("member",event.memberLoginId())
            .param("sku",line.skuId().toString()).param("at",event.occurredAt()).update());}
    UUID write(String member,UUID orderItemId,int rating,String content,Instant now){
        UUID skuId=jdbc.sql("SELECT sku_id FROM review_entitlement WHERE order_item_id=:item AND member_login_id=:member")
                .param("item",orderItemId.toString()).param("member",member).query(String.class).optional().map(UUID::fromString).orElseThrow(ReviewNotAllowedException::new);
        UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO review_review(id,member_login_id,order_item_id,sku_id,rating,content,created_at,updated_at) VALUES (:id,:member,:item,:sku,:rating,:content,:now,:now)")
                .param("id",id.toString()).param("member",member).param("item",orderItemId.toString()).param("sku",skuId.toString()).param("rating",rating).param("content",content).param("now",now).update();return id;
    }
    void update(UUID id,String member,int rating,String content,Instant now){int changed=jdbc.sql("UPDATE review_review SET rating=:rating,content=:content,updated_at=:now WHERE id=:id AND member_login_id=:member")
            .param("rating",rating).param("content",content).param("now",now).param("id",id.toString()).param("member",member).update();if(changed==0)throw new IllegalArgumentException("리뷰를 찾을 수 없습니다.");}
    UUID delete(UUID id,String member){UUID sku=find(id,member).orElseThrow(()->new IllegalArgumentException("리뷰를 찾을 수 없습니다.")).skuId();jdbc.sql("DELETE FROM review_review WHERE id=:id AND member_login_id=:member").param("id",id.toString()).param("member",member).update();return sku;}
    Optional<ReviewData> find(UUID id,String member){return jdbc.sql("SELECT id,order_item_id,sku_id,rating,content,created_at,updated_at FROM review_review WHERE id=:id AND member_login_id=:member")
            .param("id",id.toString()).param("member",member).query((rs,row)->new ReviewData(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),UUID.fromString(rs.getString(3)),rs.getInt(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getTimestamp(7).toInstant())).optional();}
    List<ReviewData> bySku(UUID skuId){return jdbc.sql("SELECT id,order_item_id,sku_id,rating,content,created_at,updated_at FROM review_review WHERE sku_id=:sku ORDER BY created_at DESC")
            .param("sku",skuId.toString()).query((rs,row)->new ReviewData(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),UUID.fromString(rs.getString(3)),rs.getInt(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getTimestamp(7).toInstant())).list();}
    RatingSummary summary(UUID skuId){return jdbc.sql("SELECT COALESCE(AVG(rating),0),COUNT(*) FROM review_review WHERE sku_id=:sku").param("sku",skuId.toString()).query((rs,row)->new RatingSummary(rs.getDouble(1),rs.getLong(2))).single();}
    record ReviewData(UUID id,UUID orderItemId,UUID skuId,int rating,String content,Instant createdAt,Instant updatedAt){}
    record RatingSummary(double average,long count){}
}
