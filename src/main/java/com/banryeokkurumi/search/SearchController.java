package com.banryeokkurumi.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class SearchController {
    private final SearchApplicationService service;
    SearchController(SearchApplicationService service) { this.service = service; }

    @GetMapping({"/products", "/search"})
    SearchApplicationService.SearchResult search(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) String brand,
                                                  @RequestParam(defaultValue = "0") long minimumPrice,
                                                  @RequestParam(defaultValue = "9223372036854775807") long maximumPrice,
                                                  @RequestParam(defaultValue = "0") double minimumRating,
                                                  @RequestParam(defaultValue = "false") boolean inStock,
                                                  @RequestParam(defaultValue = "RELEVANCE") SearchApplicationService.SearchSort sort,
                                                  @RequestParam(required = false) String cursor) {
        return service.search(new SearchApplicationService.SearchQuery(keyword, category, brand, minimumPrice, maximumPrice,
                minimumRating, inStock, sort, cursor));
    }
}
