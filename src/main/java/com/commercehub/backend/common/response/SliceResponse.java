package com.commercehub.backend.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Slice;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SliceResponse<T> {
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
    private List<Integer> pageNumbers;
    private List<T> data;

    public static <T> SliceResponse<T> of(Slice<T> slice) {
        return SliceResponse.<T>builder()
                .currentPage(slice.getNumber())
                .pageSize(slice.getSize())
                .hasNext(slice.hasNext())
                .hasPrevious(slice.hasPrevious())
                .pageNumbers(List.of(slice.getNumber() + 1))
                .data(slice.getContent())
                .build();
    }
}
