ALTER TABLE search_document
    ADD COLUMN cataloged_epoch BIGINT NOT NULL DEFAULT 0;

UPDATE search_document SET cataloged_epoch = UNIX_TIMESTAMP(cataloged_at);

ALTER TABLE search_document
    ADD INDEX idx_search_document_latest (active, cataloged_epoch DESC, product_id),
    ADD INDEX idx_search_document_price (active, price, product_id);
