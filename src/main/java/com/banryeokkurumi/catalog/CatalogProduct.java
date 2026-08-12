package com.banryeokkurumi.catalog;

import java.util.UUID;

public record CatalogProduct(
        UUID productId,
        UUID skuId,
        String name,
        String categoryName,
        String brandName,
        String optionName
) {
    public CatalogProduct {
        if (productId == null || skuId == null) {
            throw new IllegalArgumentException("상품과 SKU 식별자는 필수입니다.");
        }
        name = requireText(name, "상품명");
        categoryName = requireText(categoryName, "카테고리명");
        brandName = requireText(brandName, "브랜드명");
        optionName = requireText(optionName, "옵션명");
    }

    public static CatalogProduct create(String name, String categoryName, String brandName, String optionName) {
        return new CatalogProduct(UUID.randomUUID(), UUID.randomUUID(), name, categoryName, brandName, optionName);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은(는) 필수입니다.");
        }
        return value.strip();
    }
}
