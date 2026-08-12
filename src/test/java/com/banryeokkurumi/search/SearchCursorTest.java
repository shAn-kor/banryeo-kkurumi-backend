package com.banryeokkurumi.search;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorTest {

    @Test
    void cursor는_검색_fingerprint와_정렬값을_왕복한다() {
        var cursor = new SearchApplicationService.Cursor("fingerprint", new BigDecimal("12000.50"), "product-id");

        assertThat(SearchApplicationService.Cursor.decode(cursor.encode())).contains(cursor);
    }

    @Test
    void cursor가_깨지면_요청을_거부한다() {
        assertThatThrownBy(() -> SearchApplicationService.Cursor.decode("not-a-cursor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor");
    }

    @Test
    void 정렬조건은_fingerprint에_포함된다() {
        var popular = new SearchApplicationService.SearchQuery("사료", null, null, 0, 10_000,
                0, true, SearchApplicationService.SearchSort.POPULAR, null);
        var latest = new SearchApplicationService.SearchQuery("사료", null, null, 0, 10_000,
                0, true, SearchApplicationService.SearchSort.LATEST, null);

        assertThat(popular.fingerprint()).isNotEqualTo(latest.fingerprint());
    }
}
