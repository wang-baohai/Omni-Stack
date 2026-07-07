package com.omni.common.workflow.config;

import com.omni.common.workflow.approval.ApprovalService;
import com.omni.common.workflow.approval.ApprovalServiceImpl;
import com.omni.common.workflow.identity.UserGroupLookup;
import com.omni.common.workflow.notification.NoOpNotificationService;
import com.omni.common.workflow.notification.WorkflowNotificationService;
import com.omni.common.workflow.tenant.TenantInfoFilter;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Flowable 工作流引擎自动配置。
 * <p>
 * 当 classpath 中存在 Flowable 引擎类时自动激活，提供以下能力：</p>
 * <ul>
 *   <li>禁用 Flowable 内置身份系统（IDM Engine），桥接现有 RBAC</li>
 *   <li>注册 {@link TenantInfoFilter}（仅 Servlet Web 环境）</li>
 *   <li>注册 {@link ApprovalService}（审批服务，{@code @ConditionalOnMissingBean} 可覆盖）</li>
 *   <li>注册 {@link WorkflowNotificationService} 默认 NoOp 实现</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see ApprovalService
 * @see WorkflowNotificationService
 * @see TenantInfoFilter
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SpringProcessEngineConfiguration.class)
public class FlowableAutoConfiguration {

    /**
     * Flowable 引擎配置定制器。
     * <p>
     * 禁用内置 IDM 引擎（{@code idmEngineEnabled=false}），
     * 设置历史级别为 {@code audit}，启用异步执行器。</p>
     *
     * @return 引擎配置定制器
     */
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableEngineConfigurer(
            ObjectProvider<UserGroupLookup> userGroupLookupProvider) {
        return config -> {
            // Flowable 8.x 中 IDM 引擎通过 configurator 注入，置空即禁用
            config.setIdmEngineConfigurator(null);
            config.setAsyncExecutorActivate(true);
            UserGroupLookup userGroupLookup = userGroupLookupProvider.getIfAvailable();
            if (userGroupLookup != null) {
                config.setCandidateManager(userGroupLookup::getGroupsForUser);
                log.info("Flowable 候选组已接入 RBAC 用户组查询");
            }
            log.info("Flowable 引擎配置: IDM 已禁用, 异步执行器已启用");
        };
    }

    /**
     * 租户信息过滤器 Bean（仅 Servlet Web 环境注册）。
     * <p>
     * 从 {@code X-Tenant-Id} 请求头提取租户 ID 并设置到 {@link com.omni.common.workflow.tenant.TenantInfoHolder}。
     * </p>
     *
     * @return 租户信息过滤器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public TenantInfoFilter tenantInfoFilter() {
        return new TenantInfoFilter();
    }

    /**
     * 审批服务默认 Bean。
     * <p>
     * 封装 Flowable Multi-Instance 机制，提供通过/驳回/加签/减签/委托操作。
     * 业务服务可通过自定义同名 Bean 覆盖此默认实现。</p>
     *
     * @param taskService    Flowable 任务服务
     * @param runtimeService Flowable 运行时服务
     * @return 审批服务实现
     */
    @Bean
    @ConditionalOnMissingBean
    public ApprovalService approvalService(TaskService taskService, RuntimeService runtimeService) {
        return new ApprovalServiceImpl(taskService, runtimeService);
    }

    /**
     * 通知服务默认空实现 Bean。
     * <p>
     * 仅记录日志，不发送实际通知。业务服务实现自定义通知后此 Bean 自动被覆盖。</p>
     *
     * @return NoOp 通知服务实现
     */
    @Bean
    @ConditionalOnMissingBean
    public WorkflowNotificationService workflowNotificationService() {
        return new NoOpNotificationService();
    }
}
