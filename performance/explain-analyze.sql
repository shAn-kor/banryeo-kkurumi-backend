EXPLAIN ANALYZE
SELECT product_id FROM search_document
WHERE active=TRUE AND MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE)
ORDER BY MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE) DESC, product_id ASC LIMIT 21;

EXPLAIN ANALYZE
SELECT product_id FROM search_document
WHERE active=TRUE AND category_name='카테고리3' AND brand_name='브랜드3'
  AND price BETWEEN 10000 AND 100000 AND available_quantity>0 AND average_rating>=3
ORDER BY product_id ASC LIMIT 21;

EXPLAIN ANALYZE
SELECT product_id FROM search_document WHERE active=TRUE
ORDER BY cataloged_epoch DESC, product_id ASC LIMIT 21;

EXPLAIN ANALYZE
SELECT product_id FROM search_document WHERE active=TRUE AND price>50000
ORDER BY price ASC, product_id ASC LIMIT 21;

EXPLAIN ANALYZE
SELECT product_id FROM search_document WHERE active=TRUE AND price<50000
ORDER BY price DESC, product_id ASC LIMIT 21;

EXPLAIN ANALYZE
SELECT product_id FROM search_document WHERE active=TRUE
ORDER BY popularity_score DESC, product_id ASC LIMIT 21;
