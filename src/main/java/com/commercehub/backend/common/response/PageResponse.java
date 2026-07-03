package com.commercehub.backend.common.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResponse<T> {

    int currentPage;
    int pageSize;
    int totalPages;
    long totalElements;
    List<T> data;

    public static <T> PageResponse<T> of(Page<T> pageData) {
        return PageResponse.<T>builder()
                .currentPage(pageData.getNumber())
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElements(pageData.getTotalElements())
                .data(pageData.getContent())
                .build();
    }
}