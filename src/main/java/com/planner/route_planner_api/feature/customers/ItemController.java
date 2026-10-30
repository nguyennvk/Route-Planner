package com.planner.route_planner_api.feature.customers;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.customers.dto.ItemRequest;
import com.planner.route_planner_api.feature.customers.dto.ItemResponse;
import com.planner.route_planner_api.feature.customers.dto.UpdateItemRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Mapped at /api/v1 because the routes mix /companies/{id}/items (create, list) and /items/{id} (update)
@RestController
@RequestMapping("/api/v1")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService){
        this.itemService = itemService;
    }

    @PostMapping("/companies/{companyId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse createItem(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @Valid @RequestBody ItemRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return itemService.createItem(userId, companyId, request);
    }

    @GetMapping("/companies/{companyId}/items")
    @ResponseStatus(HttpStatus.OK)
    public PageResponse<ItemResponse> listItems(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return itemService.listItems(userId, companyId, page, size);
    }

    @PatchMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.OK)
    public ItemResponse updateItem(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long itemId,
        @Valid @RequestBody UpdateItemRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return itemService.updateItem(userId, itemId, request);
    }
}
