package com.omni.procurement.consumer;

import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.security.ProcDataScopeContext;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.service.WorkflowCompletionService;
import com.omni.procurement.workflow.RequisitionWorkflowCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Workflow 流程完成事件消费者。
 *
 * @author Omni-Stack Team
 */
@Configuration
@RequiredArgsConstructor
public class WorkflowProcessCompletedConsumer {

    private static final String EVENT_TYPE = "workflow.process.completed.v1";

    private final WorkflowCompletionService workflowCompletionService;

    /**
     * 消费 Workflow 完成事件并在 finally 中清理消息线程上下文。
     *
     * @return 消息消费函数
     */
    @Bean(name = "workflowCompletionFunction")
    public Consumer<WorkflowContracts.ProcessCompletedEvent> workflowCompletionFunction() {
        return event -> {
            if (event == null || !EVENT_TYPE.equals(event.getEventType())
                    || !RequisitionWorkflowCoordinator.BUSINESS_TYPE.equals(event.getBusinessType())) {
                return;
            }
            if (event.getTenantId() == null || event.getTenantId() <= 0) {
                throw new IllegalArgumentException("Workflow 完成事件 tenantId 必须为正整数");
            }
            ProcTenantContext.set(new ProcTenantContext.RequestIdentity(
                    0L, event.getTenantId(), "workflow-event"));
            ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                    0L, event.getTenantId(), "workflow-event", null, "TENANT", Set.of()));
            try {
                workflowCompletionService.handle(event);
            } finally {
                ProcDataScopeContext.clear();
                ProcTenantContext.clear();
            }
        };
    }
}
