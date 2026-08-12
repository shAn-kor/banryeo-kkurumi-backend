CREATE TABLE shipping_shipment (
    id CHAR(36) PRIMARY KEY, order_id CHAR(36) NOT NULL, member_login_id VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL, recipient_name VARCHAR(500) NOT NULL, recipient_phone VARCHAR(500) NOT NULL,
    postal_code VARCHAR(500) NOT NULL, address_line1 VARCHAR(1000) NOT NULL, address_line2 VARCHAR(1000) NOT NULL,
    shipped_at TIMESTAMP(6), delivered_at TIMESTAMP(6), confirmed_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_shipping_order UNIQUE (order_id), INDEX idx_shipping_auto_confirm (status, delivered_at)
);
CREATE TABLE review_review (
    id CHAR(36) PRIMARY KEY, member_login_id VARCHAR(50) NOT NULL, order_item_id CHAR(36) NOT NULL,
    sku_id CHAR(36) NOT NULL, rating INT NOT NULL, content VARCHAR(1000) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL, CONSTRAINT uk_review_order_item UNIQUE (member_login_id, order_item_id),
    INDEX idx_review_sku (sku_id), CHECK (rating BETWEEN 1 AND 5)
);
CREATE TABLE recommendation_interaction (
    id CHAR(36) PRIMARY KEY, sku_id CHAR(36) NOT NULL, category_name VARCHAR(100) NOT NULL,
    interaction_type VARCHAR(30) NOT NULL, multiplier DOUBLE NOT NULL, occurred_at TIMESTAMP(6) NOT NULL,
    INDEX idx_recommendation_interaction_time (occurred_at), INDEX idx_recommendation_interaction_sku (sku_id, occurred_at)
);
CREATE TABLE recommendation_ranking (
    category_name VARCHAR(100) NOT NULL, sku_id CHAR(36) NOT NULL, score DOUBLE NOT NULL,
    rank_number INT NOT NULL, calculated_at TIMESTAMP(6) NOT NULL, PRIMARY KEY (category_name, sku_id),
    INDEX idx_recommendation_top (category_name, rank_number)
);
