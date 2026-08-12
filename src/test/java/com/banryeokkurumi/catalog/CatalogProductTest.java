package com.banryeokkurumi.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogProductTest {

    @Test
    void create_상품과SKU옵션_판매단위를분리한다() {
        CatalogProduct product = CatalogProduct.create("튼튼 하네스", "산책", "반려산책", "M/주황");

        assertThat(product.productId()).isNotNull();
        assertThat(product.skuId()).isNotNull();
        assertThat(product.optionName()).isEqualTo("M/주황");
    }

    @Test
    void create_빈상품명_거부한다() {
        assertThatThrownBy(() -> CatalogProduct.create(" ", "산책", "반려산책", "기본"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
