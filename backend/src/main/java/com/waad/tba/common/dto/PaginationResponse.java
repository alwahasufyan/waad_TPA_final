package com.waad.tba.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginationResponse<T> {
    private List<T> items;
    private long total;
    private int page;
    private int size;
    
    /**
     * Create PaginationResponse from Spring Page object
     */
    public static <T> PaginationResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return PaginationResponse.<T>builder()
                .items(page.getContent())
                .total(page.getTotalElements())
                .page(page.getNumber())
                .size(page.getSize())
                .build();
    }
}

