package com.banryeokkurumi.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SearchApplicationService {
    private static final int PAGE_SIZE = 20;
    private final JdbcClient jdbc;
    public SearchApplicationService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public SearchResult search(SearchQuery query) {
        Cursor cursor = Cursor.decode(query.cursor()).orElse(new Cursor(query.fingerprint(), null, ""));
        if (!cursor.fingerprint().equals(query.fingerprint())) throw new IllegalArgumentException("검색 조건과 cursor가 일치하지 않습니다.");
        String keyword = query.keyword() == null ? "" : query.keyword().strip();
        SortSql sort = SortSql.forSort(query.sort());
        String sql = """
                SELECT product_id, sku_id, product_name, brand_name, category_name, option_name,
                       price, available_quantity, average_rating, popularity_score, %s AS sort_value
                  FROM search_document
                 WHERE active = true
                   AND (:keyword = '' OR MATCH(search_text) AGAINST (:keyword IN BOOLEAN MODE))
                   AND (:category = '' OR category_name = :category)
                   AND (:brand = '' OR brand_name = :brand)
                   AND price BETWEEN :minimumPrice AND :maximumPrice
                   AND average_rating >= :minimumRating
                   AND (:inStock = false OR available_quantity > 0)
                   AND (:firstPage = true OR (%s))
                 ORDER BY %s %s, product_id ASC
                 LIMIT :limit
                """.formatted(sort.expression(), sort.cursorPredicate(), sort.expression(), sort.direction());
        List<SearchRow> rows = jdbc.sql(sql)
                .param("keyword", keyword)
                .param("category", query.categoryName() == null ? "" : query.categoryName())
                .param("brand", query.brandName() == null ? "" : query.brandName())
                .param("minimumPrice", query.minimumPrice())
                .param("maximumPrice", query.maximumPrice())
                .param("minimumRating", query.minimumRating())
                .param("inStock", query.inStockOnly())
                .param("firstPage", cursor.sortValue() == null)
                .param("afterValue", cursor.sortValue() == null ? BigDecimal.ZERO : cursor.sortValue())
                .param("afterId", cursor.lastProductId())
                .param("limit", PAGE_SIZE + 1)
                .query((rs, row) -> new SearchRow(new SearchItem(UUID.fromString(rs.getString("product_id")), UUID.fromString(rs.getString("sku_id")),
                                rs.getString("product_name"), rs.getString("brand_name"), rs.getString("category_name"), rs.getString("option_name"),
                                rs.getLong("price"), rs.getInt("available_quantity"), rs.getDouble("average_rating"), rs.getDouble("popularity_score")),
                        rs.getBigDecimal("sort_value")))
                .list();
        boolean hasNext = rows.size() > PAGE_SIZE;
        List<SearchRow> pageRows = hasNext ? rows.subList(0, PAGE_SIZE) : rows;
        List<SearchItem> page = pageRows.stream().map(SearchRow::item).toList();
        SearchRow last = pageRows.isEmpty() ? null : pageRows.getLast();
        String next = hasNext ? new Cursor(query.fingerprint(), last.sortValue(), last.item().productId().toString()).encode() : null;
        return new SearchResult(page, next);
    }

    public record SearchQuery(String keyword, String categoryName, String brandName, long minimumPrice, long maximumPrice,
                              double minimumRating, boolean inStockOnly, SearchSort sort, String cursor) {
        public SearchQuery {
            if (minimumPrice < 0 || maximumPrice < minimumPrice) throw new IllegalArgumentException("가격 범위가 올바르지 않습니다.");
            if (minimumRating < 0 || minimumRating > 5) throw new IllegalArgumentException("평점 범위가 올바르지 않습니다.");
            if (sort == null) sort = SearchSort.RELEVANCE;
        }
        String fingerprint() {
            String source = String.join("|", value(keyword), value(categoryName), value(brandName), Long.toString(minimumPrice),
                    Long.toString(maximumPrice), Double.toString(minimumRating), Boolean.toString(inStockOnly), sort.name());
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
            catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        private static String value(String value) { return value == null ? "" : value.strip(); }
    }
    public record SearchItem(UUID productId, UUID skuId, String name, String brandName, String categoryName,
                             String optionName, long price, int availableQuantity, double averageRating, double popularityScore) {}
    public record SearchResult(List<SearchItem> items, String nextCursor) { public SearchResult { items = List.copyOf(items); } }
    public enum SearchSort { RELEVANCE, LATEST, PRICE_ASC, PRICE_DESC, POPULAR }
    record SearchRow(SearchItem item, BigDecimal sortValue) {}
    record SortSql(String expression, String direction, String cursorPredicate) {
        static SortSql forSort(SearchSort sort) {
            return switch (sort) {
                case RELEVANCE -> descending("IF(:keyword = '', 0, MATCH(search_text) AGAINST (:keyword IN BOOLEAN MODE))");
                case LATEST -> descending("cataloged_epoch");
                case PRICE_ASC -> new SortSql("price", "ASC", "price > :afterValue OR (price = :afterValue AND product_id > :afterId)");
                case PRICE_DESC -> descending("price");
                case POPULAR -> descending("popularity_score");
            };
        }
        static SortSql descending(String expression) {
            return new SortSql(expression, "DESC", expression + " < :afterValue OR (" + expression + " = :afterValue AND product_id > :afterId)");
        }
    }
    record Cursor(String fingerprint, BigDecimal sortValue, String lastProductId) {
        String encode() { return Base64.getUrlEncoder().withoutPadding().encodeToString((fingerprint + ":" + sortValue.toPlainString() + ":" + lastProductId).getBytes(StandardCharsets.UTF_8)); }
        static Optional<Cursor> decode(String encoded) {
            if (encoded == null || encoded.isBlank()) return Optional.empty();
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split(":", 3);
                if (parts.length != 3 || parts[2].isBlank()) throw new IllegalArgumentException();
                return Optional.of(new Cursor(parts[0], new BigDecimal(parts[1]), parts[2]));
            } catch (RuntimeException exception) { throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다."); }
        }
    }
}
