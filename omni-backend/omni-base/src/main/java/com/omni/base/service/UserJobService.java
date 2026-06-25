package com.omni.base.service;

import com.omni.base.dto.CreateUserJobRequest;
import com.omni.base.dto.MyJobStats;
import com.omni.base.dto.UpdateUserJobRequest;
import com.omni.base.dto.UserJobQuery;
import com.omni.base.entity.SysUserJob;
import com.omni.common.core.result.PageResult;

/**
 * 用户任务服务接口。
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.impl.UserJobServiceImpl
 */
public interface UserJobService {

    /**
     * 分页查询用户任务列表。
     *
     * @param tenantId 租户 ID
     * @param query    查询条件（创建人、任务名称、类型、状态）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<SysUserJob> listJobs(Long tenantId, UserJobQuery query, int page, int size);

    /**
     * 按 ID 查询用户任务。
     *
     * @param id 任务 ID
     * @return 用户任务实体
     * @throws com.omni.common.core.result.BusinessException 任务不存在时抛出 404
     */
    SysUserJob getJobById(Long id);

    /**
     * 创建用户任务（自动注册到 XXL-JOB 调度中心）。
     *
     * @param tenantId 租户 ID
     * @param request  创建请求
     * @param operator 操作人用户名
     * @return 创建成功后的任务实体（含 xxlJobId）
     * @throws com.omni.common.core.result.BusinessException 任务类型不存在或注册失败时抛出
     */
    SysUserJob createJob(Long tenantId, CreateUserJobRequest request, String operator);

    /**
     * 更新用户任务（cron/参数变更时同步更新 XXL-JOB）。
     *
     * @param id       任务 ID
     * @param request  更新请求
     * @param operator 操作人用户名
     * @return 更新后的任务实体
     */
    SysUserJob updateJob(Long id, UpdateUserJobRequest request, String operator);

    /**
     * 删除用户任务（同时从 XXL-JOB 注销）。
     *
     * @param id 任务 ID
     */
    void deleteJob(Long id);

    /**
     * 切换任务状态（启用/停止对应 XXL-JOB 调度条目）。
     *
     * @param id     任务 ID
     * @param status 目标状态（1=启用，0=停止）
     */
    void toggleStatus(Long id, Integer status);

    /**
     * 立即触发任务（通过 XXL-JOB triggerJob API）。
     *
     * @param id 任务 ID
     * @throws com.omni.common.core.result.BusinessException 任务未注册到调度中心时抛出 400
     */
    void triggerNow(Long id);

    /**
     * 查询用户工作台统计数据。
     *
     * @param tenantId 租户 ID
     * @param createBy 创建人
     * @return 统计结果
     */
    MyJobStats getStats(Long tenantId, String createBy);
}
