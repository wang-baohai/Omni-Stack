package com.omni.common.core.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页查询结果封装类。
 * <p>
 * 用于统一分页接口的返回格式，包含记录列表和分页元数据。
 * </p>
 *
 * @param <T> 记录列表中元素的泛型类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页记录列表 */
    private List<T> records;
    /** 总记录数 */
    private long total;
    /** 每页大小 */
    private long size;
    /** 当前页码（从 1 开始） */
    private long current;
    /** 总页数 */
    private long pages;

    /** 默认无参构造函数 */
    public PageResult() {
    }

    /**
     * 带参构造函数，自动计算总页数。
     *
     * @param records 当前页记录列表
     * @param total   总记录数
     * @param size    每页大小
     * @param current 当前页码
     */
    public PageResult(List<T> records, long total, long size, long current) {
        this.records = records;
        this.total = total;
        this.size = size;
        this.current = current;
        // 向上取整计算总页数
        this.pages = (total + size - 1) / size;
    }
}
