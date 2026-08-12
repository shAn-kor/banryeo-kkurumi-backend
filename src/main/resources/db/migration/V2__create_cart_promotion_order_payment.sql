CREATE TABLE cart_item (
    id CHAR(36) PRIMARY KEY, member_login_id VARCHAR(50) NOT NULL, sku_id CHAR(36) NOT NULL,
    quantity INT NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_cart_member_sku UNIQUE (member_login_id, sku_id), CHECK (quantity > 0)
);
CREATE TABLE promotion_campaign (
    id CHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL, discount_type VARCHAR(20) NOT NULL,
    discount_value INT NOT NULL, maximum_discount BIGINT NOT NULL, minimum_order_amount BIGINT NOT NULL,
    scope_type VARCHAR(20) NOT NULL, scope_id CHAR(36), total_quantity INT NOT NULL, issued_quantity INT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL, ends_at TIMESTAMP(6) NOT NULL, CHECK (issued_quantity <= total_quantity)
);
CREATE TABLE promotion_issued_coupon (
    id CHAR(36) PRIMARY KEY, campaign_id CHAR(36) NOT NULL, member_login_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL, reserved_order_id CHAR(36), issued_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_promotion_campaign_member UNIQUE (campaign_id, member_login_id),
    INDEX idx_promotion_coupon_member (member_login_id, status)
);
CREATE TABLE ordering_order (
    id CHAR(36) PRIMARY KEY, member_login_id VARCHAR(50) NOT NULL, status VARCHAR(40) NOT NULL,
    total_amount BIGINT NOT NULL, discount_amount BIGINT NOT NULL, payable_amount BIGINT NOT NULL,
    issued_coupon_id CHAR(36), payment_scenario VARCHAR(30) NOT NULL, recipient_name VARCHAR(500) NOT NULL,
    recipient_phone VARCHAR(500) NOT NULL, postal_code VARCHAR(500) NOT NULL, address_line1 VARCHAR(1000) NOT NULL,
    address_line2 VARCHAR(1000) NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_ordering_member_created (member_login_id, created_at DESC)
);
CREATE TABLE ordering_order_item (
    id CHAR(36) PRIMARY KEY, order_id CHAR(36) NOT NULL, product_id CHAR(36) NOT NULL, sku_id CHAR(36) NOT NULL,
    product_name VARCHAR(200) NOT NULL, option_name VARCHAR(100) NOT NULL, unit_price BIGINT NOT NULL,
    quantity INT NOT NULL, line_amount BIGINT NOT NULL, INDEX idx_ordering_item_order (order_id)
);
CREATE TABLE payment_transaction (
    id CHAR(36) PRIMARY KEY, order_id CHAR(36) NOT NULL, member_login_id VARCHAR(50) NOT NULL, amount BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL, scenario VARCHAR(30) NOT NULL, idempotency_key VARCHAR(100) NOT NULL,
    provider_transaction_id VARCHAR(100), failure_reason VARCHAR(300), reconciliation_deadline TIMESTAMP(6),
    next_reconciliation_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_payment_order UNIQUE (order_id), CONSTRAINT uk_payment_idempotency UNIQUE (idempotency_key),
    INDEX idx_payment_reconciliation (status, next_reconciliation_at)
);
CREATE TABLE processed_event (
    listener_id VARCHAR(150) NOT NULL, event_id CHAR(36) NOT NULL, processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (listener_id, event_id)
);
