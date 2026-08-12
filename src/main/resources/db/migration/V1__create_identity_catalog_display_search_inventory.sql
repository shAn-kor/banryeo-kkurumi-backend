CREATE TABLE identity_member (
    id CHAR(36) PRIMARY KEY, login_id VARCHAR(50) NOT NULL, encoded_password VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL, role VARCHAR(20) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_identity_member_login_id UNIQUE (login_id)
);
CREATE TABLE catalog_item (
    product_id CHAR(36) PRIMARY KEY, sku_id CHAR(36) NOT NULL, name VARCHAR(200) NOT NULL,
    category_name VARCHAR(100) NOT NULL, brand_name VARCHAR(100) NOT NULL, option_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL, CONSTRAINT uk_catalog_item_sku UNIQUE (sku_id)
);
CREATE TABLE display_offer (
    sku_id CHAR(36) PRIMARY KEY, product_id CHAR(36) NOT NULL, price BIGINT NOT NULL, active BOOLEAN NOT NULL,
    display_order INT NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_display_offer_product (product_id), INDEX idx_display_offer_active_order (active, display_order)
);
CREATE TABLE search_document (
    product_id CHAR(36) PRIMARY KEY, sku_id CHAR(36) NOT NULL, product_name VARCHAR(200) NOT NULL DEFAULT '',
    brand_name VARCHAR(100) NOT NULL DEFAULT '', category_name VARCHAR(100) NOT NULL DEFAULT '',
    option_name VARCHAR(100) NOT NULL DEFAULT '', search_text TEXT NOT NULL, price BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT FALSE, display_order INT NOT NULL DEFAULT 0, available_quantity INT NOT NULL DEFAULT 0,
    average_rating DECIMAL(4,2) NOT NULL DEFAULT 0, review_count BIGINT NOT NULL DEFAULT 0,
    popularity_score DOUBLE NOT NULL DEFAULT 0, cataloged_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_search_document_sku UNIQUE (sku_id),
    FULLTEXT INDEX ft_search_document_text (search_text) WITH PARSER ngram,
    INDEX idx_search_document_filter (active, category_name, brand_name, price),
    INDEX idx_search_document_popular (active, popularity_score DESC, product_id)
);
CREATE TABLE inventory_stock (
    sku_id CHAR(36) PRIMARY KEY, available_quantity INT NOT NULL, reserved_quantity INT NOT NULL,
    sold_quantity INT NOT NULL, CHECK (available_quantity >= 0), CHECK (reserved_quantity >= 0), CHECK (sold_quantity >= 0)
);
CREATE TABLE inventory_reservation (
    order_id CHAR(36) NOT NULL, sku_id CHAR(36) NOT NULL, quantity INT NOT NULL, status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL, PRIMARY KEY (order_id, sku_id), INDEX idx_inventory_reservation_expiry (status, expires_at)
);
