package com.omni.common.core.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Paginated response wrapper.
 *
 * @param <T> the type of elements in the records list
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long size, long current) {
        this.records = records;
        this.total = total;
        this.size = size;
        this.current = current;
        this.pages = (total + size - 1) / size;
    }
}
