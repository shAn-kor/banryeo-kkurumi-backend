package com.banryeokkurumi.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/catalog")
class CatalogAdminController {
    private final CatalogApplicationService service;
    CatalogAdminController(CatalogApplicationService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CatalogApplicationService.CatalogView create(@Valid @RequestBody CreateRequest request) {
        return service.create(new CatalogApplicationService.CreateCatalogCommand(request.name(), request.categoryName(), request.brandName(), request.optionName()));
    }

    record CreateRequest(@NotBlank String name, @NotBlank String categoryName, @NotBlank String brandName,
                         @NotBlank String optionName) {}
}
