package com.omni.base.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 操作日志查询参数。
 * <p>用于 {@link com.omni.base.controller.OperLogController} 接收前端传入的筛选条件，
 * 支持按模块、操作类型、操作人和时间范围进行过滤。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.OperLogService#listOperLogs(Long, OperLogQuery)
 */
public class OperLogQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模块名称（模糊匹配） */
    private String module;

    /** 操作类型 */
    private String operType;

    /** 操作人（模糊匹配） */
    private String operUsername;

    /** 开始时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 页码 */
    private int page = 1;

    /** 每页大小 */
    private int size = 10;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getOperType() {
        return operType;
    }

    public void setOperType(String operType) {
        this.operType = operType;
    }

    public String getOperUsername() {
        return operUsername;
    }

    public void setOperUsername(String operUsername) {
        this.operUsername = operUsername;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
