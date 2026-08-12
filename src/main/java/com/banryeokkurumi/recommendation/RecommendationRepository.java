package com.banryeokkurumi.recommendation;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class RecommendationRepository {
    private final JdbcClient jdbc;
    RecommendationRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    void interaction(UUID sku,String category,String type,double multiplier,Instant occurredAt){jdbc.sql("INSERT INTO recommendation_interaction(id,sku_id,category_name,interaction_type,multiplier,occurred_at) VALUES (:id,:sku,:category,:type,:multiplier,:at)")
            .param("id",UUID.randomUUID().toString()).param("sku",sku.toString()).param("category",category).param("type",type).param("multiplier",multiplier).param("at",occurredAt).update();}
    List<Score> calculate(Instant now){return jdbc.sql("""
            SELECT sku_id,category_name,SUM((CASE interaction_type WHEN 'VIEW' THEN 1 WHEN 'LIKE' THEN 3 WHEN 'CART' THEN 4 WHEN 'PURCHASE' THEN 10 WHEN 'REVIEW' THEN 2 ELSE 0 END)*multiplier*POW(0.5,TIMESTAMPDIFF(SECOND,occurred_at,:now)/604800.0)) score
              FROM recommendation_interaction WHERE occurred_at>=:cutoff GROUP BY sku_id,category_name
            """).param("now",now).param("cutoff",now.minusSeconds(60L*60*24*90)).query((rs,row)->new Score(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getDouble(3))).list();}
    void replace(List<Rank> ranks,Instant now){jdbc.sql("DELETE FROM recommendation_ranking").update();ranks.forEach(r->jdbc.sql("INSERT INTO recommendation_ranking(category_name,sku_id,score,rank_number,calculated_at) VALUES (:category,:sku,:score,:rank,:now)")
            .param("category",r.category()).param("sku",r.skuId().toString()).param("score",r.score()).param("rank",r.rank()).param("now",now).update());}
    List<Rank> top(String category){return jdbc.sql("SELECT category_name,sku_id,score,rank_number FROM recommendation_ranking WHERE category_name=:category ORDER BY rank_number LIMIT 100")
            .param("category",category).query((rs,row)->new Rank(rs.getString(1),UUID.fromString(rs.getString(2)),rs.getDouble(3),rs.getInt(4))).list();}
    record Score(UUID skuId,String category,double score){}
    record Rank(String category,UUID skuId,double score,int rank){}
}
