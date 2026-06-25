package com.omni.common.core.job;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户自定义任务消息 DTO。
 * <p>作为 XXL-JOB 执行器参数 JSON 结构，在任务创建时序列化存入调度条目的 {@code executorParam} 字段，
 * 触发执行时由 {@code userJobExecuteHandler} 反序列化后传递给具体 {@link UserJobHandler} 实现。</p>
 *
 * <p>传输链路：
 * {@code UserJobServiceImpl.createJob()} → JSON 序列化 → XXL-JOB Admin {@code executorParam} →
 * XXL-JOB 触发 → {@code userJobExecuteHandler} → JSON 反序列化 → {@link UserJobHandler#execute(UserJobMessage)}</p>
 *
 * @author Omni-Stack Team
 * @see UserJobHandler
 */
@Data
public class UserJobMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID，对应 {@code sys_user_job.id}，用于定位任务实例 */
    private Long jobId;

    /** 租户 ID，用于多租户隔离，确保任务在正确的租户上下文中执行 */
    private Long tenantId;

    /** 任务类型编码，对应 {@code sys_user_job_type.type_code}，用于路由到具体 Handler */
    private String jobType;

    /** 任务名称，用户在创建任务时填写的可读名称 */
    private String jobName;

    /** 任务参数 JSON，用户在创建任务时通过动态表单填写的自定义参数 */
    private String jobParams;
}
