package com.banryeokkurumi.payment;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class PaymentRepository {
    private final JdbcClient jdbc;
    PaymentRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    PaymentData create(UUID orderId,String member,long amount,String scenario,String key,Instant now) {
        jdbc.sql("""
                INSERT INTO payment_transaction(id,order_id,member_login_id,amount,status,scenario,idempotency_key,created_at,updated_at)
                VALUES (:id,:orderId,:member,:amount,'REQUESTED',:scenario,:key,:now,:now)
                ON DUPLICATE KEY UPDATE id=id
                """).param("id",UUID.randomUUID().toString()).param("orderId",orderId.toString()).param("member",member)
                .param("amount",amount).param("scenario",scenario).param("key",key).param("now",now).update();
        return find(orderId).orElseThrow();
    }
    Optional<PaymentData> find(UUID orderId) {
        return jdbc.sql("""
                SELECT order_id,member_login_id,amount,status,scenario,idempotency_key,provider_transaction_id,failure_reason,
                       reconciliation_deadline,next_reconciliation_at,updated_at FROM payment_transaction WHERE order_id=:orderId
                """).param("orderId",orderId.toString()).query((rs,row)->new PaymentData(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getLong(3),
                        rs.getString(4),DemoPaymentProvider.Scenario.valueOf(rs.getString(5)),rs.getString(6),rs.getString(7),rs.getString(8),
                        rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),rs.getTimestamp(10)==null?null:rs.getTimestamp(10).toInstant(),rs.getTimestamp(11).toInstant())).optional();
    }
    void success(UUID orderId,String transactionId,Instant now) { jdbc.sql("UPDATE payment_transaction SET status='SUCCEEDED',provider_transaction_id=:tx,failure_reason=NULL,updated_at=:now WHERE order_id=:id AND status IN ('REQUESTED','UNKNOWN')").param("tx",transactionId).param("now",now).param("id",orderId.toString()).update(); }
    void fail(UUID orderId,String reason,Instant now) { jdbc.sql("UPDATE payment_transaction SET status='FAILED',failure_reason=:reason,updated_at=:now WHERE order_id=:id AND status IN ('REQUESTED','UNKNOWN')").param("reason",reason).param("now",now).param("id",orderId.toString()).update(); }
    void unknown(UUID orderId,String reason,Instant next,Instant deadline,Instant now) { jdbc.sql("UPDATE payment_transaction SET status='UNKNOWN',failure_reason=:reason,next_reconciliation_at=:next,reconciliation_deadline=:deadline,updated_at=:now WHERE order_id=:id AND status='REQUESTED'").param("reason",reason).param("next",next).param("deadline",deadline).param("now",now).param("id",orderId.toString()).update(); }
    void reschedule(UUID orderId,Instant next,Instant now) { jdbc.sql("UPDATE payment_transaction SET next_reconciliation_at=:next,updated_at=:now WHERE order_id=:id AND status='UNKNOWN'").param("next",next).param("now",now).param("id",orderId.toString()).update(); }
    void cancel(UUID orderId,Instant now) { jdbc.sql("UPDATE payment_transaction SET status='CANCELLED',updated_at=:now WHERE order_id=:id AND status<>'CANCELLED'").param("now",now).param("id",orderId.toString()).update(); }
    List<PaymentData> due(Instant now) { return jdbc.sql("SELECT order_id FROM payment_transaction WHERE status='UNKNOWN' AND next_reconciliation_at<=:now").param("now",now).query(String.class).list().stream().map(UUID::fromString).map(this::find).flatMap(Optional::stream).toList(); }
    record PaymentData(UUID orderId,String memberLoginId,long amount,String status,DemoPaymentProvider.Scenario scenario,String idempotencyKey,
                       String providerTransactionId,String failureReason,Instant deadline,Instant nextAt,Instant updatedAt) {}
}
