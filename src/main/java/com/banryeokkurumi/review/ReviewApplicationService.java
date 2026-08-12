package com.banryeokkurumi.review;

import com.banryeokkurumi.contracts.CommerceEvents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewApplicationService {
    private final ReviewRepository repository;private final ApplicationEventPublisher events;private final Clock clock;
    public ReviewApplicationService(ReviewRepository repository,ApplicationEventPublisher events,Clock clock){this.repository=repository;this.events=events;this.clock=clock;}
    @Transactional public void grant(CommerceEvents.PurchaseConfirmed event){repository.grant(event);}
    @Transactional public ReviewView write(String member,UUID orderItemId,int rating,String content){validate(rating,content);UUID id=repository.write(member,orderItemId,rating,content.strip(),Instant.now(clock));ReviewView view=find(id,member);publishRating(view.skuId());return view;}
    @Transactional public ReviewView update(UUID id,String member,int rating,String content){validate(rating,content);repository.update(id,member,rating,content.strip(),Instant.now(clock));ReviewView view=find(id,member);publishRating(view.skuId());return view;}
    @Transactional public void delete(UUID id,String member){UUID sku=repository.delete(id,member);publishRating(sku);}
    @Transactional(readOnly=true) public ReviewView find(UUID id,String member){return view(repository.find(id,member).orElseThrow(()->new IllegalArgumentException("리뷰를 찾을 수 없습니다.")));}
    @Transactional(readOnly=true) public List<ReviewView> bySku(UUID skuId){return repository.bySku(skuId).stream().map(this::view).toList();}
    public void validate(int rating,String content){if(rating<1||rating>5)throw new IllegalArgumentException("평점은 1부터 5까지입니다.");if(content==null||content.isBlank()||content.length()>1000)throw new IllegalArgumentException("리뷰 내용은 1자 이상 1000자 이하여야 합니다.");}
    public void publishRating(UUID skuId){ReviewRepository.RatingSummary s=repository.summary(skuId);events.publishEvent(new CommerceEvents.RatingChanged(UUID.randomUUID(),Instant.now(clock),1,skuId,s.average(),s.count()));events.publishEvent(new CommerceEvents.InteractionObserved(UUID.randomUUID(),Instant.now(clock),1,skuId,"ALL","REVIEW",s.average()));}
    ReviewView view(ReviewRepository.ReviewData d){return new ReviewView(d.id(),d.orderItemId(),d.skuId(),d.rating(),d.content(),d.createdAt(),d.updatedAt());}
    public record ReviewView(UUID id,UUID orderItemId,UUID skuId,int rating,String content,Instant createdAt,Instant updatedAt){}
}
