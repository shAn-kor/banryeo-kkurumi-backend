SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';

DROP PROCEDURE IF EXISTS load_search_dataset;
DELIMITER //
CREATE PROCEDURE load_search_dataset()
BEGIN
    DECLARE database_name VARCHAR(64);
    DECLARE batch_start INT DEFAULT 1;
    SELECT DATABASE() INTO database_name;
    IF database_name <> 'banryeo_performance' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'performance loader requires banryeo_performance database';
    END IF;

    TRUNCATE TABLE search_document;
    WHILE batch_start <= 300000 DO
        INSERT INTO search_document(
            product_id,sku_id,product_name,brand_name,category_name,option_name,search_text,price,active,
            display_order,available_quantity,average_rating,review_count,popularity_score,cataloged_at,cataloged_epoch
        )
        WITH RECURSIVE sequence(n) AS (
            SELECT batch_start
            UNION ALL SELECT n + 1 FROM sequence WHERE n < batch_start + 999
        )
        SELECT
            INSERT(INSERT(INSERT(INSERT(MD5(CONCAT('product-', n)),9,0,'-'),14,0,'-'),19,0,'-'),24,0,'-'),
            INSERT(INSERT(INSERT(INSERT(MD5(CONCAT('sku-', n)),9,0,'-'),14,0,'-'),19,0,'-'),24,0,'-'),
            CONCAT('반려 건강 상품 ', n), CONCAT('브랜드', MOD(n, 50)), CONCAT('카테고리', MOD(n, 20)),
            CONCAT('옵션', MOD(n, 5)),
            CASE MOD(n, 10)
                WHEN 0 THEN CONCAT('반려 건강 사료 상품 ', n)
                WHEN 1 THEN CONCAT('반려 간식 상품 ', n)
                WHEN 2 THEN CONCAT('건강 영양제 상품 ', n)
                ELSE CONCAT('생활 산책 목욕 장난감 용품 ', n)
            END,
            1000 + MOD(n * 7919, 199000), TRUE, MOD(n, 1000), IF(MOD(n, 7)=0,0,MOD(n,500)+1),
            MOD(n, 501) / 100.0, MOD(n * 13, 10000), MOD(n * 37, 100000) / 10.0,
            TIMESTAMP('2025-01-01 00:00:00') + INTERVAL MOD(n, 31536000) SECOND,
            UNIX_TIMESTAMP(TIMESTAMP('2025-01-01 00:00:00') + INTERVAL MOD(n, 31536000) SECOND)
        FROM sequence;
        SET batch_start = batch_start + 1000;
    END WHILE;
END//
DELIMITER ;

CALL load_search_dataset();
DROP PROCEDURE load_search_dataset;
ANALYZE TABLE search_document;

SELECT COUNT(*) AS document_count FROM search_document;
