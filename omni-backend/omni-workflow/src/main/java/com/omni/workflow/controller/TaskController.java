package com.omni.workflow.controller;

import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.workflow.entity.WfTodoTask;
import com.omni.workflow.service.WorkflowTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 任务控制器。
 * <p>提供待办任务查询、待办数量、任务表单数据等接口。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/workflow/task")
@RequiredArgsConstructor
public class TaskController {

    private final WorkflowTaskService workflowTaskService;

    /**
     * 查询"待我审批"的待办任务列表。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param title    流程标题（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 待办任务分页列表
     */
    @GetMapping("/todo")
    public R<PageResult<WfTodoTask>> todoList(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(workflowTaskService.todoList(userId, tenantId, title, page, size));
    }

    /**
     * 查询当前用户的待办数量。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 待办数量
     */
    @GetMapping("/count")
    public R<Long> todoCount(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestHeader("X-User-Id") Long userId) {
        return R.ok(workflowTaskService.todoCount(userId, tenantId));
    }

    /**
     * 获取任务的表单数据。
     *
     * @param taskId 任务 ID
     * @return 表单数据
     */
    @GetMapping("/{taskId}/form")
    public R<Map<String, Object>> getTaskFormData(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "1") Long tenantId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String taskId) {
        return R.ok(workflowTaskService.getTaskFormData(taskId, userId, tenantId));
    }
}
