package com.omni.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户自定义任务实体，对应 {@code sys_user_job} 表。
 * <p>记录用户创建的定时任务配置信息，包括任务名称、类型、Cron 表达式及参数等。
 * 任务通过 XXL-JOB 调度平台执行，{@code xxlJobId} 字段关联 XXL-JOB 中的任务 ID。
 * 不继承 {@link com.omni.common.core.model.BaseEntity}，字段独立维护。</p>
 *
 * @author Omni-Stack Team
 * @see SysUserJobLog
 * @see SysUserJobType
 */
@TableName("sys_user_job")
public class SysUserJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID，用于多租户数据隔离 */
    private Long tenantId;

    /** 任务名称，用户自定义的任务描述性名称（租户内建议唯一） */
    private String jobName;

    /** 任务类型编码，关联 {@link SysUserJobType#getTypeCode()}，如 "DATA_SYNC"、"REPORT_GEN" */
    private String jobType;

    /** Cron 表达式，定义任务执行周期，如 "0 0/5 * * * ?" 表示每 5 分钟执行 */
    private String cronExpression;

    /** 任务参数，JSON 格式，传递给任务处理器的自定义参数 */
    private String jobParams;

    /** 任务状态：1-启用（正常运行），0-禁用（暂停调度） */
    private Integer status;

    /** XXL-JOB 任务 ID，关联 XXL-JOB 调度平台中的任务记录 */
    private Long xxlJobId;

    /** 上次触发时间，任务最近一次实际执行的时间 */
    private LocalDateTime lastFireTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建人，记录任务创建者的用户名 */
    private String createBy;

    /** 更新人，记录最后修改者的用户名 */
    private String updateBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getJobParams() {
        return jobParams;
    }

    public void setJobParams(String jobParams) {
        this.jobParams = jobParams;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getXxlJobId() {
        return xxlJobId;
    }

    public void setXxlJobId(Long xxlJobId) {
        this.xxlJobId = xxlJobId;
    }

    public LocalDateTime getLastFireTime() {
        return lastFireTime;
    }

    public void setLastFireTime(LocalDateTime lastFireTime) {
        this.lastFireTime = lastFireTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }
}
