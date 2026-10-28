EXPLAIN ANALYZE
SELECT product_id, sku_id, product_name, brand_name, category_name, option_name,
       price, available_quantity, average_rating, popularity_score,
       IF('반려 건강' = '', 0, MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE)) AS sort_value
  FROM search_document
 WHERE active = true
   AND ('반려 건강' = '' OR MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE))
   AND ('' = '' OR category_name = '')
   AND ('' = '' OR brand_name = '')
   AND price BETWEEN 0 AND 9223372036854775807
   AND average_rating >= 0.0
   AND (false = false OR available_quantity > 0)
   AND (true = true OR (IF('반려 건강' = '', 0, MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE)) < 0 OR (IF('반려 건강' = '', 0, MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE)) = 0 AND product_id > '')))
 ORDER BY IF('반려 건강' = '', 0, MATCH(search_text) AGAINST ('반려 건강' IN BOOLEAN MODE)) DESC, product_id ASC
 LIMIT 21;
