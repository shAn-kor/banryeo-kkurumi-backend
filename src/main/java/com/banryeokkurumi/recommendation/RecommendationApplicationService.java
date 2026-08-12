package com.banryeokkurumi.recommendation;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class RecommendationApplicationService {
    private final RecommendationRepository repository;private final StringRedisTemplate redis;private final ApplicationEventPublisher events;private final Clock clock;
    public RecommendationApplicationService(RecommendationRepository repository,StringRedisTemplate redis,ApplicationEventPublisher events,Clock clock){this.repository=repository;this.redis=redis;this.events=events;this.clock=clock;}
    @Transactional public void observe(UUID sku,String category,String type,double multiplier,Instant at){InteractionType.valueOf(type);repository.interaction(sku,category,type,multiplier,at);}
    @Transactional public List<RecommendationView> rebuild(){Instant now=Instant.now(clock);List<RecommendationRepository.Score> scores=repository.calculate(now);Map<String,List<RecommendationRepository.Score>> grouped=new HashMap<>();scores.forEach(s->{grouped.computeIfAbsent(s.category(),key->new ArrayList<>()).add(s);grouped.computeIfAbsent("ALL",key->new ArrayList<>()).add(s);});List<RecommendationRepository.Rank> ranks=new ArrayList<>();grouped.forEach((category,values)->{List<RecommendationRepository.Score> sorted=values.stream().sorted(Comparator.comparingDouble(RecommendationRepository.Score::score).reversed()).limit(100).toList();for(int i=0;i<sorted.size();i++)ranks.add(new RecommendationRepository.Rank(category,sorted.get(i).skuId(),sorted.get(i).score(),i+1));});repository.replace(ranks,now);ranks.forEach(r->events.publishEvent(new CommerceEvents.RankingChanged(UUID.randomUUID(),now,1,r.skuId(),r.score())));return top("ALL");}
    @Transactional(readOnly=true) public List<RecommendationView> top(String category){List<RecommendationView> result=repository.top(category).stream().map(r->new RecommendationView(r.skuId(),r.score(),r.rank())).toList();cache(category,result);return result;}
    public void cache(String category,List<RecommendationView> values){try{redis.opsForValue().set("recommendation:"+category,Integer.toString(values.size()),Duration.ofHours(2));}catch(RuntimeException ignored){/* MySQL fallback remains available. */}}
    public record RecommendationView(UUID skuId,double score,int rank){}
}
