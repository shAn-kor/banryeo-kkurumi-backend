package com.banryeokkurumi.inventory;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class InventoryRepository {
    private final JdbcClient jdbc;
    InventoryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void upsert(UUID skuId, int quantity) {
        jdbc.sql("""
                INSERT INTO inventory_stock(sku_id, available_quantity, reserved_quantity, sold_quantity)
                VALUES (:skuId, :quantity, 0, 0)
                ON DUPLICATE KEY UPDATE available_quantity=:quantity
                """).param("skuId", skuId.toString()).param("quantity", quantity).update();
    }

    boolean reserve(UUID orderId, UUID skuId, int quantity, Instant expiresAt) {
        if (existsReservation(orderId, skuId)) return true;
        int updated = jdbc.sql("""
                UPDATE inventory_stock SET available_quantity=available_quantity-:quantity,
                    reserved_quantity=reserved_quantity+:quantity
                 WHERE sku_id=:skuId AND available_quantity>=:quantity
                """).param("quantity", quantity).param("skuId", skuId.toString()).update();
        if (updated == 0) return false;
        try {
            jdbc.sql("INSERT INTO inventory_reservation(order_id, sku_id, quantity, status, expires_at) VALUES (:orderId,:skuId,:quantity,'RESERVED',:expiresAt)")
                    .param("orderId", orderId.toString()).param("skuId", skuId.toString()).param("quantity", quantity).param("expiresAt", expiresAt).update();
            return true;
        } catch (DuplicateKeyException duplicate) {
            jdbc.sql("UPDATE inventory_stock SET available_quantity=available_quantity+:quantity, reserved_quantity=reserved_quantity-:quantity WHERE sku_id=:skuId")
                    .param("quantity", quantity).param("skuId", skuId.toString()).update();
            return true;
        }
    }

    void commit(UUID orderId) {
        reservations(orderId, "RESERVED").forEach(r -> {
            int changed = jdbc.sql("UPDATE inventory_reservation SET status='COMMITTED' WHERE order_id=:orderId AND sku_id=:skuId AND status='RESERVED'")
                    .param("orderId", orderId.toString()).param("skuId", r.skuId().toString()).update();
            if (changed == 1) jdbc.sql("UPDATE inventory_stock SET reserved_quantity=reserved_quantity-:quantity, sold_quantity=sold_quantity+:quantity WHERE sku_id=:skuId")
                    .param("quantity", r.quantity()).param("skuId", r.skuId().toString()).update();
        });
    }

    void release(UUID orderId) {
        reservations(orderId, "RESERVED").forEach(r -> {
            int changed = jdbc.sql("UPDATE inventory_reservation SET status='RELEASED' WHERE order_id=:orderId AND sku_id=:skuId AND status='RESERVED'")
                    .param("orderId", orderId.toString()).param("skuId", r.skuId().toString()).update();
            if (changed == 1) jdbc.sql("UPDATE inventory_stock SET available_quantity=available_quantity+:quantity, reserved_quantity=reserved_quantity-:quantity WHERE sku_id=:skuId")
                    .param("quantity", r.quantity()).param("skuId", r.skuId().toString()).update();
        });
    }

    void cancel(UUID orderId) {
        release(orderId);
        reservations(orderId, "COMMITTED").forEach(reservation -> {
            int changed = jdbc.sql("UPDATE inventory_reservation SET status='CANCELLED' WHERE order_id=:orderId AND sku_id=:skuId AND status='COMMITTED'")
                    .param("orderId", orderId.toString()).param("skuId", reservation.skuId().toString()).update();
            if (changed == 1) {
                jdbc.sql("UPDATE inventory_stock SET available_quantity=available_quantity+:quantity, sold_quantity=sold_quantity-:quantity WHERE sku_id=:skuId AND sold_quantity>=:quantity")
                        .param("quantity", reservation.quantity()).param("skuId", reservation.skuId().toString()).update();
            }
        });
    }

    Optional<StockView> find(UUID skuId) {
        return jdbc.sql("SELECT sku_id, available_quantity, reserved_quantity, sold_quantity FROM inventory_stock WHERE sku_id=:skuId")
                .param("skuId", skuId.toString()).query((rs, row) -> new StockView(UUID.fromString(rs.getString(1)), rs.getInt(2), rs.getInt(3), rs.getInt(4))).optional();
    }

    List<ReservationView> reservations(UUID orderId, String status) {
        return jdbc.sql("SELECT sku_id, quantity FROM inventory_reservation WHERE order_id=:orderId AND status=:status")
                .param("orderId", orderId.toString()).param("status", status)
                .query((rs, row) -> new ReservationView(UUID.fromString(rs.getString(1)), rs.getInt(2))).list();
    }

    private boolean existsReservation(UUID orderId, UUID skuId) {
        return jdbc.sql("SELECT COUNT(*) FROM inventory_reservation WHERE order_id=:orderId AND sku_id=:skuId")
                .param("orderId", orderId.toString()).param("skuId", skuId.toString()).query(Integer.class).single() > 0;
    }

    record StockView(UUID skuId, int available, int reserved, int sold) {}
    record ReservationView(UUID skuId, int quantity) {}
}
