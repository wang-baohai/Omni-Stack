package com.omni.srm.client;

import com.omni.common.core.result.R;
import com.omni.srm.dto.WorkflowContracts;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Workflow 跨服务内部客户端。
 *
 * @author Omni-Stack Team
 */
@FeignClient(name = "omni-workflow", contextId = "srmWorkflowInternalClient",
        configuration = WorkflowInternalClient.FeignConfig.class)
public interface WorkflowInternalClient {

    /**
     * 查询当前租户可用于启动流程的已发布模型版本。
     *
     * @param tenantId       租户 ID
     * @param modelVersionId 模型版本 ID
     * @return 模型版本响应
     */
    @GetMapping("/api/internal/workflow/model-version/{modelVersionId}")
    R<WorkflowContracts.ModelVersionResponse> getModelVersion(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable("modelVersionId") Long modelVersionId);

    /**
     * 按业务分类查询当前已发布模型版本。
     *
     * @param tenantId 租户 ID
     * @param category 业务分类
     * @return 模型版本响应
     */
    @GetMapping("/api/internal/workflow/model-version/current")
    R<WorkflowContracts.ModelVersionResponse> getCurrentPublishedModelVersion(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam("category") String category);

    /**
     * 幂等启动供应商准入审批流程。
     *
     * @param tenantId 租户 ID
     * @param request  启动请求
     * @return 启动响应
     */
    @PostMapping("/api/internal/workflow/process-instance/start")
    R<WorkflowContracts.StartResponse> start(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestBody WorkflowContracts.StartRequest request);

    /**
     * 终止流程实例。
     *
     * @param tenantId          租户 ID
     * @param processInstanceId 流程实例 ID
     * @param reason            终止原因
     * @return 空响应
     */
    @PostMapping("/api/internal/workflow/process-instance/{processInstanceId}/terminate")
    R<Void> terminate(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable("processInstanceId") String processInstanceId,
            @RequestParam(value = "reason", required = false) String reason);

    /** Workflow Feign 内部认证配置。 */
    @Configuration
    class FeignConfig {

        @Value("${omni.internal.api.token:}")
        private String internalToken;

        /**
         * 注入服务间共享认证令牌。
         *
         * @return Feign 请求拦截器
         */
        @Bean
        public RequestInterceptor srmWorkflowInternalTokenInterceptor() {
            return template -> template.header("X-Internal-Token", internalToken);
        }
    }
}
