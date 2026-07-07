package com.omni.workflow.controller;

import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.workflow.dto.DeployProcessRequest;
import com.omni.workflow.dto.ProcessDefinitionVO;
import com.omni.workflow.service.ProcessDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 流程定义管理控制器。
 * <p>提供流程定义的部署、查询、挂起、激活、删除等管理接口。</p>
 *
 * @author Omni-Stack Team
 */
@RestController
@RequestMapping("/api/workflow/process-definition")
@RequiredArgsConstructor
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;

    /**
     * 分页查询流程定义列表。
     *
     * @param name     流程名称（模糊查询，可选）
     * @param category 流程分类（可选）
     * @param page     页码
     * @param size     每页大小
     * @return 流程定义分页列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('workflow:definition:list')")
    public R<PageResult<ProcessDefinitionVO>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(processDefinitionService.list(name, category, page, size));
    }

    /**
     * 获取流程定义的 BPMN XML。
     *
     * @param processDefinitionId 流程定义 ID
     * @return BPMN XML 字符串
     */
    @GetMapping("/{processDefinitionId}/bpmn")
    @PreAuthorize("hasAuthority('workflow:definition:list')")
    public R<String> getBpmnXml(@PathVariable String processDefinitionId) {
        return R.ok(processDefinitionService.getBpmnXml(processDefinitionId));
    }

    /**
     * 部署流程定义。
     *
     * @param request 部署请求（名称 + 分类 + BPMN XML）
     * @return 部署 ID
     */
    @PostMapping("/deploy")
    @PreAuthorize("hasAuthority('workflow:definition:deploy')")
    public R<String> deploy(@Valid @RequestBody DeployProcessRequest request) {
        return R.ok(processDefinitionService.deploy(request));
    }

    /**
     * 挂起流程定义。
     *
     * @param processDefinitionId 流程定义 ID
     * @return 操作结果
     */
    @PutMapping("/{processDefinitionId}/suspend")
    @PreAuthorize("hasAuthority('workflow:definition:update')")
    public R<Void> suspend(@PathVariable String processDefinitionId) {
        processDefinitionService.suspend(processDefinitionId);
        return R.ok();
    }

    /**
     * 激活流程定义。
     *
     * @param processDefinitionId 流程定义 ID
     * @return 操作结果
     */
    @PutMapping("/{processDefinitionId}/activate")
    @PreAuthorize("hasAuthority('workflow:definition:update')")
    public R<Void> activate(@PathVariable String processDefinitionId) {
        processDefinitionService.activate(processDefinitionId);
        return R.ok();
    }

    /**
     * 删除部署（级联删除关联实例）。
     *
     * @param deploymentId 部署 ID
     * @return 操作结果
     */
    @DeleteMapping("/{deploymentId}")
    @PreAuthorize("hasAuthority('workflow:definition:delete')")
    public R<Void> delete(@PathVariable String deploymentId) {
        processDefinitionService.delete(deploymentId);
        return R.ok();
    }
}
