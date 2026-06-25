package com.omni.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户任务执行日志实体，对应 {@code sys_user_job_log} 表。
 * <p>每次定时任务触发执行时生成一条记录，用于跟踪任务执行结果、耗时及异常信息。
 * 与 {@link SysUserJob} 通过 {@code jobId} 字段关联，支持任务执行历史的查询与审计。
 * 不继承 {@link com.omni.common.core.model.BaseEntity}，字段独立维护。</p>
 *
 * @author Omni-Stack Team
 * @see SysUserJob
 * @see SysUserJobType
 */
@TableName("sys_user_job_log")
public class SysUserJobLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务 ID，对应 {@link SysUserJob#getId()} */
    private Long jobId;

    /** 租户 ID，用于多租户数据隔离 */
    private Long tenantId;

    /** 任务名称，执行时快照的任务名称，避免任务改名后历史日志不可读 */
    private String jobName;

    /** 任务类型编码，执行时快照的类型编码，如 "DATA_SYNC"、"REPORT_GEN" */
    private String jobType;

    /** 触发时间，任务被调度触发的精确时间 */
    private LocalDateTime fireTime;

    /** 执行耗时（毫秒），任务从开始到结束的耗时 */
    private Long executeTimeMs;

    /** 执行状态：1-成功，0-失败，2-部分成功 */
    private Integer status;

    /** 错误信息，任务执行失败时的异常堆栈或错误描述 */
    private String errorMessage;

    /** 结果信息，任务执行成功后返回的结果摘要或处理统计 */
    private String resultMessage;

    /** 创建时间，日志入库时间 */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public LocalDateTime getFireTime() {
        return fireTime;
    }

    public void setFireTime(LocalDateTime fireTime) {
        this.fireTime = fireTime;
    }

    public Long getExecuteTimeMs() {
        return executeTimeMs;
    }

    public void setExecuteTimeMs(Long executeTimeMs) {
        this.executeTimeMs = executeTimeMs;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
