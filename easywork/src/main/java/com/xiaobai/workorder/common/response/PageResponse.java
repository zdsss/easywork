package com.xiaobai.workorder.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> records;
    private Long total;
    private Long current;
    private Long size;
    private Long pages;

    public static <T> PageResponse<T> of(List<T> records, Long total, Long current, Long size) {
        Long pages = (total + size - 1) / size;
        return new PageResponse<>(records, total, current, size, pages);
    }
}
