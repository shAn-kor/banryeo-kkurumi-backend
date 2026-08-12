package com.banryeokkurumi.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
class ReviewController {
    private final ReviewApplicationService service;
    ReviewController(ReviewApplicationService service){this.service=service;}
    @GetMapping("/api/v1/products/{skuId}/reviews") List<ReviewApplicationService.ReviewView> list(@PathVariable UUID skuId){return service.bySku(skuId);}
    @PostMapping("/api/v1/reviews") @ResponseStatus(HttpStatus.CREATED) ReviewApplicationService.ReviewView write(Authentication auth,@Valid @RequestBody ReviewRequest r){return service.write(auth.getName(),r.orderItemId(),r.rating(),r.content());}
    @PutMapping("/api/v1/reviews/{id}") ReviewApplicationService.ReviewView update(Authentication auth,@PathVariable UUID id,@Valid @RequestBody UpdateRequest r){return service.update(id,auth.getName(),r.rating(),r.content());}
    @DeleteMapping("/api/v1/reviews/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(Authentication auth,@PathVariable UUID id){service.delete(id,auth.getName());}
    record ReviewRequest(@NotNull UUID orderItemId,@Min(1) @Max(5) int rating,@NotBlank String content){}
    record UpdateRequest(@Min(1) @Max(5) int rating,@NotBlank String content){}
}
