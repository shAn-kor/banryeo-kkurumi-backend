EXPLAIN ANALYZE
SELECT product_id FROM search_document
WHERE active=TRUE AND MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE)
ORDER BY MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE) DESC, product_id ASC LIMIT 21;
