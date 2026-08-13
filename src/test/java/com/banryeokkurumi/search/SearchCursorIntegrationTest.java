package com.banryeokkurumi.search;

import com.banryeokkurumi.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchCursorIntegrationTest {
    private static final int DOCUMENT_COUNT = 53;
    private static final String KEYWORD = "반려";

    private final SearchApplicationService search;
    private final JdbcClient jdbc;

    @Autowired
    SearchCursorIntegrationTest(SearchApplicationService search, JdbcClient jdbc) {
        this.search = search;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void setUpDocuments() {
        jdbc.sql("DELETE FROM search_document").update();
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < DOCUMENT_COUNT; index++) {
            UUID productId = new UUID(0, index + 1L);
            UUID skuId = new UUID(1, index + 1L);
            jdbc.sql("""
                    INSERT INTO search_document(product_id,sku_id,product_name,brand_name,category_name,option_name,
                        search_text,price,active,display_order,available_quantity,average_rating,review_count,
                        popularity_score,cataloged_at,cataloged_epoch)
                    VALUES (:productId,:skuId,:name,:brand,:category,'기본',:searchText,:price,true,:displayOrder,
                        :available,:rating,:reviewCount,:popularity,:catalogedAt,:catalogedEpoch)
                    """).param("productId", productId.toString()).param("skuId", skuId.toString())
                    .param("name", "반려 상품 " + index).param("brand", "브랜드" + index % 3)
                    .param("category", "카테고리" + index % 4).param("searchText", "반려 사료 건강 상품 " + index)
                    .param("price", 1_000L + (index % 7) * 500L).param("displayOrder", index)
                    .param("available", index % 5 == 0 ? 0 : index + 1).param("rating", (index % 6) * 0.8)
                    .param("reviewCount", index * 3L).param("popularity", (index % 9) * 10.0)
                    .param("catalogedAt", base.plusSeconds(index)).param("catalogedEpoch", base.plusSeconds(index).getEpochSecond()).update();
        }
    }

    @Test
    void search_다섯정렬전체cursor순회_중복누락정렬역전이없다() {
        for (SearchApplicationService.SearchSort sort : SearchApplicationService.SearchSort.values()) {
            List<UUID> traversed = traverse(sort);

            assertThat(traversed).hasSize(DOCUMENT_COUNT);
            assertThat(new HashSet<>(traversed)).hasSize(DOCUMENT_COUNT);
            assertThat(traversed).containsExactlyElementsOf(expectedOrder(sort));
        }
    }

    @Test
    void search_다른검색조건의cursor_거부한다() {
        SearchApplicationService.SearchResult first = search.search(query(SearchApplicationService.SearchSort.PRICE_ASC, null, null));

        assertThatThrownBy(() -> search.search(query(
                SearchApplicationService.SearchSort.PRICE_ASC, first.nextCursor(), "다른브랜드")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("검색 조건과 cursor가 일치하지 않습니다.");
    }

    private List<UUID> traverse(SearchApplicationService.SearchSort sort) {
        List<UUID> productIds = new ArrayList<>();
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        do {
            SearchApplicationService.SearchResult page = search.search(query(sort, cursor, null));
            productIds.addAll(page.items().stream().map(SearchApplicationService.SearchItem::productId).toList());
            cursor = page.nextCursor();
            if (cursor != null) assertThat(cursors.add(cursor)).isTrue();
        } while (cursor != null);
        return productIds;
    }

    private SearchApplicationService.SearchQuery query(SearchApplicationService.SearchSort sort, String cursor, String brand) {
        return new SearchApplicationService.SearchQuery(KEYWORD, null, brand, 0, Long.MAX_VALUE, 0, false, sort, cursor);
    }

    private List<UUID> expectedOrder(SearchApplicationService.SearchSort sort) {
        String orderBy = switch (sort) {
            case RELEVANCE -> "MATCH(search_text) AGAINST ('반려' IN BOOLEAN MODE) DESC, product_id ASC";
            case LATEST -> "cataloged_at DESC, product_id ASC";
            case PRICE_ASC -> "price ASC, product_id ASC";
            case PRICE_DESC -> "price DESC, product_id ASC";
            case POPULAR -> "popularity_score DESC, product_id ASC";
        };
        return jdbc.sql("SELECT product_id FROM search_document WHERE active=true " +
                        "AND MATCH(search_text) AGAINST ('반려' IN BOOLEAN MODE) ORDER BY " + orderBy)
                .query(String.class).list().stream().map(UUID::fromString).toList();
    }
}
