package com.banryeokkurumi.search;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class SearchProjectionHandler {
    private final JdbcClient jdbc;
    SearchProjectionHandler(JdbcClient jdbc) { this.jdbc = jdbc; }

    @ApplicationModuleListener
    void on(CommerceEvents.ProductCataloged event) {
        jdbc.sql("""
                INSERT INTO search_document(product_id, sku_id, product_name, brand_name, category_name, option_name, search_text, cataloged_at, cataloged_epoch)
                VALUES (:productId, :skuId, :name, :brand, :category, :option, :searchText, :catalogedAt, :catalogedEpoch)
                ON DUPLICATE KEY UPDATE product_name=:name, brand_name=:brand, category_name=:category,
                    option_name=:option, search_text=:searchText
                """).param("productId", event.productId().toString()).param("skuId", event.skuId().toString())
                .param("name", event.name()).param("brand", event.brandName()).param("category", event.categoryName())
                .param("option", event.optionName()).param("searchText", String.join(" ", event.name(), event.brandName(), event.categoryName()))
                .param("catalogedAt", event.occurredAt())
                .param("catalogedEpoch", event.occurredAt().getEpochSecond())
                .update();
    }

    @ApplicationModuleListener
    void on(CommerceEvents.OfferChanged event) {
        jdbc.sql("UPDATE search_document SET price=:price, active=:active, display_order=:displayOrder WHERE sku_id=:skuId")
                .param("price", event.price()).param("active", event.active()).param("displayOrder", event.displayOrder())
                .param("skuId", event.skuId().toString()).update();
    }

    @ApplicationModuleListener
    void on(CommerceEvents.StockChanged event) {
        jdbc.sql("UPDATE search_document SET available_quantity=:quantity WHERE sku_id=:skuId")
                .param("quantity", event.availableQuantity()).param("skuId", event.skuId().toString()).update();
    }

    @ApplicationModuleListener
    void on(CommerceEvents.RatingChanged event) {
        jdbc.sql("UPDATE search_document SET average_rating=:rating, review_count=:count WHERE sku_id=:skuId")
                .param("rating", event.averageRating()).param("count", event.reviewCount()).param("skuId", event.skuId().toString()).update();
    }

    @ApplicationModuleListener
    void on(CommerceEvents.RankingChanged event) {
        jdbc.sql("UPDATE search_document SET popularity_score=:score WHERE sku_id=:skuId")
                .param("score", event.popularityScore()).param("skuId", event.skuId().toString()).update();
    }
}
