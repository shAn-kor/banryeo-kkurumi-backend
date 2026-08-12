CREATE TABLE review_entitlement (
    order_item_id CHAR(36) PRIMARY KEY,
    order_id CHAR(36) NOT NULL,
    member_login_id VARCHAR(50) NOT NULL,
    sku_id CHAR(36) NOT NULL,
    confirmed_at TIMESTAMP(6) NOT NULL,
    INDEX idx_review_entitlement_member (member_login_id, confirmed_at DESC)
);
