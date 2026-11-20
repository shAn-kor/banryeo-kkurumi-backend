-- V0 current shape (product_id only)
EXPLAIN ANALYZE SELECT product_id FROM search_document WHERE active=TRUE AND MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE) ORDER BY MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE) DESC, product_id ASC LIMIT 21;
-- V1 no active filter, no tie-break (FTS-only ranking)
EXPLAIN ANALYZE SELECT product_id FROM search_document WHERE MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE) ORDER BY MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE) DESC LIMIT 21;
-- V2 no active filter, with tie-break
EXPLAIN ANALYZE SELECT product_id FROM search_document WHERE MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE) ORDER BY MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE) DESC, product_id ASC LIMIT 21;
-- V3 AND semantics
EXPLAIN ANALYZE SELECT product_id FROM search_document WHERE active=TRUE AND MATCH(search_text) AGAINST ('+반려 +건강' IN BOOLEAN MODE) ORDER BY MATCH(search_text) AGAINST ('+반려 +건강' IN BOOLEAN MODE) DESC, product_id ASC LIMIT 21;
