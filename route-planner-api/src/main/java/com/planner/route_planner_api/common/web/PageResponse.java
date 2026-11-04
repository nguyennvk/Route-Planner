package com.planner.route_planner_api.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

// The JSON shape every paged list returns: { content, page, size, totalElements, totalPages }.
// Spring's Page object has many more fields; this keeps the API response small and stable.
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
