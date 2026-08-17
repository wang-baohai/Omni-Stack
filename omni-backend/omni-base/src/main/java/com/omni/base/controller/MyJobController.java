package com.omni.base.controller;

import com.omni.base.dto.CreateUserJobRequest;
import com.omni.base.dto.MyJobStats;
import com.omni.base.dto.UpdateUserJobRequest;
import com.omni.base.dto.UserJobLogQuery;
import com.omni.base.dto.UserJobQuery;
import com.omni.base.entity.SysUserJob;
import com.omni.base.entity.SysUserJobLog;
import com.omni.base.entity.SysUserJobType;
import com.omni.base.service.UserJobLogService;
import com.omni.base.service.UserJobService;
import com.omni.base.service.UserJobTypeService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户工作台控制器。
 * <p>
 * 面向所有已登录用户的自助任务管理接口，无需 RBAC 权限码。
 * 数据按 {@code create_by} 隔离，用户只能操作自己创建的任务。
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.UserJobService
 * @see com.omni.base.service.UserJobLogService
 */
@Slf4j
@RestController
@RequestMapping("/api/base/my-job")
@RequiredArgsConstructor
public class MyJobController {

    private final UserJobService userJobService;
    private final UserJobLogService userJobLogService;
    private final UserJobTypeService userJobTypeService;

    /**
     * 查询当前用户的任务列表（分页）。
     *
     * @param tenantId 租户 ID（从请求头获取）
     * @param jobName  任务名称（模糊匹配，可选）
     * @param jobType  任务类型（可选）
     * @param status   状态（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 分页任务列表
     */
    @GetMapping("/list")
    public R<PageResult<SysUserJob>> list(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String username = currentUsername();
        UserJobQuery query = new UserJobQuery();
        query.setJobName(jobName);
        query.setJobType(jobType);
        query.setStatus(status);
        query.setCreateBy(username);
        return R.ok(userJobService.listJobs(tenantId, query, page, size));
    }

    /**
     * 查询启用状态的任务类型列表（供创建任务下拉使用）。
     *
     * @return 启用的任务类型列表
     */
    @GetMapping("/types")
    public R<List<SysUserJobType>> types() {
        return R.ok(userJobTypeService.listEnabledTypes());
    }

    /**
     * 查询当前用户的任务统计数据。
     *
     * @param tenantId 租户 ID（从请求头获取）
     * @return 统计数据（任务总数、今日执行次数、今日失败次数）
     */
    @GetMapping("/stats")
    public R<MyJobStats> stats(
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        String username = currentUsername();
        return R.ok(userJobService.getStats(tenantId, username));
    }

    /**
     * 创建任务。
     *
     * @param tenantId 租户 ID（从请求头获取）
     * @param request  创建请求
     * @return 创建成功后的任务实体
     */
    @PostMapping
    public R<SysUserJob> create(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Valid @RequestBody CreateUserJobRequest request) {
        String username = currentUsername();
        return R.ok(userJobService.createJob(tenantId, request, username));
    }

    /**
     * 更新任务（校验归属）。
     *
     * @param tenantId 租户 ID（从请求头获取）
     * @param id      任务 ID
     * @param request 更新请求
     * @return 更新后的任务实体
     */
    @PutMapping("/{id}")
    public R<SysUserJob> update(@RequestHeader("X-Tenant-Id") Long tenantId,
                                 @PathVariable Long id,
                                 @Valid @RequestBody UpdateUserJobRequest request) {
        String username = currentUsername();
        verifyOwnership(id, tenantId, username);
        return R.ok(userJobService.updateJob(id, request, username));
    }

    /**
     * 删除任务（校验归属）。
     *
     * @param tenantId 租户 ID（从请求头获取）
     * @param id 任务 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@RequestHeader("X-Tenant-Id") Long tenantId,
                          @PathVariable Long id) {
        String username = currentUsername();
        verifyOwnership(id, tenantId, username);
        userJobService.deleteJob(id);
        return R.ok();
    }

    /**
     * 切换任务状态（校验归属）。
     *
     * @param tenantId 租户 ID（从请求头获取）
     * @param id     任务 ID
     * @param status 目标状态（1=启用，0=停止）
     * @return 空结果
     */
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@RequestHeader("X-Tenant-Id") Long tenantId,
                                @PathVariable Long id,
                                @RequestParam Integer status) {
        String username = currentUsername();
        verifyOwnership(id, tenantId, username);
        userJobService.toggleStatus(id, status);
        return R.ok();
    }

    /**
     * 立即触发任务执行（校验归属）。
     *
     * @param tenantId 租户 ID（从请求头获取）
     * @param id 任务 ID
     * @return 空结果
     */
    @PostMapping("/{id}/trigger")
    public R<Void> trigger(@RequestHeader("X-Tenant-Id") Long tenantId,
                           @PathVariable Long id) {
        String username = currentUsername();
        verifyOwnership(id, tenantId, username);
        userJobService.triggerNow(id);
        return R.ok();
    }

    /**
     * 查询指定任务的执行日志（校验归属）。
     *
     * @param id       任务 ID
     * @param tenantId 租户 ID（从请求头获取）
     * @param page     页码
     * @param size     每页大小
     * @return 分页日志结果
     */
    @GetMapping("/{id}/logs")
    public R<PageResult<SysUserJobLog>> logs(
            @PathVariable Long id,
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String username = currentUsername();
        verifyOwnership(id, tenantId, username);
        UserJobLogQuery query = new UserJobLogQuery();
        query.setJobId(id);
        return R.ok(userJobLogService.listLogs(tenantId, query, page, size));
    }

    /**
     * 获取当前登录用户名。
     */
    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * 校验任务归属：确保当前用户是任务的创建者。
     *
     * @param jobId    任务 ID
     * @param tenantId 当前租户 ID
     * @param username 当前用户名
     */
    private void verifyOwnership(Long jobId, Long tenantId, String username) {
        SysUserJob job = userJobService.getJobById(jobId);
        if (!tenantId.equals(job.getTenantId()) || !username.equals(job.getCreateBy())) {
            throw new BusinessException(403, "无权操作他人的任务");
        }
    }
}
