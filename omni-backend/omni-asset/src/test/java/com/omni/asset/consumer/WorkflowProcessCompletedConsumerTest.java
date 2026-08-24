package com.omni.asset.consumer;

import com.omni.asset.dto.WorkflowContracts;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.asset.service.WorkflowCompletionService;
import com.omni.asset.workflow.AssetWorkflowCoordinator;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Workflow 完成事件路由与失败关闭测试。 */
@ExtendWith(MockitoExtension.class)
class WorkflowProcessCompletedConsumerTest {

    @Mock private WorkflowCompletionService workflowCompletionService;

    /** 清理消息线程上下文。 */
    @AfterEach
    void clearContext() {
        ServiceDataScopeContext.clear();
        ServiceIdentityContext.clear();
    }

    /** Asset v1 事件必须建立租户上下文并在处理后清理。 */
    @Test
    void shouldHandleSupportedAssetEventAndClearContext() {
        Consumer<WorkflowContracts.ProcessCompletedEvent> consumer = consumer();
        WorkflowContracts.ProcessCompletedEvent event =
                event("workflow.process.completed.v1",
                        AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE);

        consumer.accept(event);

        verify(workflowCompletionService).handle(event);
        assertThatThrownBy(ServiceIdentityContext::requireTenantId)
                .isInstanceOf(BusinessException.class);
    }

    /** 属于 Asset 的未知事件版本必须失败关闭，避免静默 ACK 丢失状态。 */
    @Test
    void shouldRejectUnsupportedAssetWorkflowEventVersion() {
        Consumer<WorkflowContracts.ProcessCompletedEvent> consumer = consumer();
        WorkflowContracts.ProcessCompletedEvent event =
                event("workflow.process.completed.v2",
                        AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE);

        assertThatThrownBy(() -> consumer.accept(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");
        verify(workflowCompletionService, never()).handle(event);
    }

    /** 同 Topic 的非 Asset 完成事件应交给其他消费者处理。 */
    @Test
    void shouldIgnoreUnrelatedWorkflowBusinessType() {
        Consumer<WorkflowContracts.ProcessCompletedEvent> consumer = consumer();
        WorkflowContracts.ProcessCompletedEvent event =
                event("workflow.process.completed.v2", "PROCUREMENT_REQUISITION");

        consumer.accept(event);

        verify(workflowCompletionService, never()).handle(event);
    }

    private Consumer<WorkflowContracts.ProcessCompletedEvent> consumer() {
        return new WorkflowProcessCompletedConsumer(workflowCompletionService)
                .workflowCompletionFunction();
    }

    private WorkflowContracts.ProcessCompletedEvent event(
            String eventType, String businessType) {
        WorkflowContracts.ProcessCompletedEvent event =
                new WorkflowContracts.ProcessCompletedEvent();
        event.setEventId("workflow-event-1");
        event.setEventType(eventType);
        event.setTenantId(1L);
        event.setBusinessType(businessType);
        return event;
    }
}
