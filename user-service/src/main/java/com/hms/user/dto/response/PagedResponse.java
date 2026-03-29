package com.hms.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Returned by all GET-list endpoints to support client-side pagination.
 */
@Getter
@Builder
public class PagedResponse<T> {

    private final List<T> content;
    private final int     pageNumber;
    private final int     pageSize;
    private final long    totalElements;
    private final int     totalPages;
    private final boolean first;
    private final boolean last;

    public static <T> PagedResponse<T> from(Page<T> page) {
        return PagedResponse.<T>builder()
            .content(page.getContent())
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .first(page.isFirst())
            .last(page.isLast())
            .build();
    }
}
