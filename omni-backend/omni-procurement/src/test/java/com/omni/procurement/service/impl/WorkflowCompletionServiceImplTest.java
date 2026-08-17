package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.RequisitionStateMachine;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.entity.ProcEventInbox;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.mapper.ProcEventInboxMapper;
import com.omni.procurement.mapper.ProcRequisitionMapper;
import com.omni.procurement.security.ProcDataScopeContext;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.workflow.RetryableWorkflowEventException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Workflow 完成事件 Inbox 和乱序保护测试。 */
@ExtendWith(MockitoExtension.class)
class WorkflowCompletionServiceImplTest {

    @Mock private ProcEventInboxMapper inboxMapper;
    @Mock private ProcRequisitionMapper requisitionMapper;
    @Mock private ReliableMessageRelay reliableMessageRelay;

    private ObjectMapper objectMapper;
    private WorkflowCompletionServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcEventInbox.class, "ProcEventInboxMapper");
        initialize(ProcRequisition.class, "ProcRequisitionMapper");
    }

    /** 初始化服务与租户上下文。 */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new WorkflowCompletionServiceImpl(
                inboxMapper, requisitionMapper, reliableMessageRelay, objectMapper);
        ProcTenantContext.set(new ProcTenantContext.RequestIdentity(0L, 41L, "workflow-event"));
        ProcDataScopeContext.set(new ProcDataScopeContext.ScopeInfo(
                0L, 41L, "workflow-event", null, "TENANT", Set.of()));
    }

    /** 清理消息线程上下文。 */
    @AfterEach
    void clearContext() {
        ProcDataScopeContext.clear();
        ProcTenantContext.clear();
    }

    /** 完成事件早于 markStarted 时必须抛可重试异常且不标记 Inbox。 */
    @Test
    void shouldRetryWhenCompletionArrivesBeforeMarkStarted() {
        WorkflowContracts.ProcessCompletedEvent event = event("evt-1", "100:2", "pi-9", "APPROVED");
        mockInbox(event, "RECEIVED");
        ProcRequisition requisition = requisition("100:2", null,
                RequisitionStateMachine.SUBMITTED, RequisitionStateMachine.START_PENDING);
        when(requisitionMapper.selectForUpdate(41L, 100L)).thenReturn(requisition);

        assertThatThrownBy(() -> service.handle(event))
                .isInstanceOf(RetryableWorkflowEventException.class);
        verify(requisitionMapper, never()).update(any(), any());
        verify(inboxMapper, never()).update(any(), any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
    }

    /** 完全匹配的完成事件必须推进状态、写领域 Outbox 并完成 Inbox。 */
    @Test
    void shouldAdvanceMatchingCompletionAndPublishOutbox() {
        WorkflowContracts.ProcessCompletedEvent event = event("evt-2", "100:2", "pi-9", "APPROVED");
        mockInbox(event, "RECEIVED");
        when(requisitionMapper.selectForUpdate(41L, 100L)).thenReturn(requisition(
                "100:2", "pi-9", RequisitionStateMachine.APPROVING,
                RequisitionStateMachine.START_STARTED));
        when(requisitionMapper.update(any(), any())).thenReturn(1);
        when(inboxMapper.update(any(), any())).thenReturn(1);

        assertThat(service.handle(event)).isTrue();

        verify(requisitionMapper).update(any(), any());
        verify(reliableMessageRelay).send(
                org.mockito.ArgumentMatchers.eq("procurement-domain-out-0"),
                any(), org.mockito.ArgumentMatchers.eq(41L), any());
        verify(inboxMapper).update(any(), any());
    }

    /** 旧审批轮次事件必须幂等忽略且不能发布领域事件。 */
    @Test
    void shouldIgnoreOldApprovalAttempt() {
        WorkflowContracts.ProcessCompletedEvent event = event("evt-old", "100:1", "pi-old", "REJECTED");
        mockInbox(event, "RECEIVED");
        when(requisitionMapper.selectForUpdate(41L, 100L)).thenReturn(requisition(
                "100:2", "pi-new", RequisitionStateMachine.APPROVING,
                RequisitionStateMachine.START_STARTED));
        when(inboxMapper.update(any(), any())).thenReturn(1);

        assertThat(service.handle(event)).isFalse();

        verify(requisitionMapper, never()).update(any(), any());
        verify(reliableMessageRelay, never()).send(any(), any(), any(), any());
        verify(inboxMapper).update(any(), any());
    }

    /** 已处理 eventId 若携带不同流程实例或结果必须返回 409。 */
    @Test
    void shouldRejectTamperedReplayOfProcessedEvent() throws Exception {
        WorkflowContracts.ProcessCompletedEvent original = event(
                "evt-fixed", "100:2", "pi-original", "APPROVED");
        ProcEventInbox inbox = inbox(original, "PROCESSED");
        when(inboxMapper.selectForUpdate(41L, "evt-fixed")).thenReturn(inbox);
        WorkflowContracts.ProcessCompletedEvent tampered = event(
                "evt-fixed", "100:2", "pi-tampered", "REJECTED");
        tampered.setOccurredAt(original.getOccurredAt());
        tampered.setCompletedTime(original.getCompletedTime());

        assertThatThrownBy(() -> service.handle(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        verify(requisitionMapper, never()).selectForUpdate(any(), any());
    }

    private void mockInbox(WorkflowContracts.ProcessCompletedEvent event, String status) {
        when(inboxMapper.selectForUpdate(41L, event.getEventId())).thenReturn(inbox(event, status));
    }

    private ProcEventInbox inbox(WorkflowContracts.ProcessCompletedEvent event, String status) {
        ProcEventInbox inbox = new ProcEventInbox();
        inbox.setId(1L);
        inbox.setTenantId(event.getTenantId());
        inbox.setEventId(event.getEventId());
        inbox.setEventType(event.getEventType());
        inbox.setSourceService(event.getProducer());
        inbox.setAggregateType(event.getBusinessType());
        inbox.setAggregateId(event.getBusinessKey());
        try {
            inbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        inbox.setStatus(status);
        return inbox;
    }

    private ProcRequisition requisition(String businessKey, String processInstanceId,
                                         String status, String startStatus) {
        ProcRequisition requisition = new ProcRequisition();
        requisition.setId(100L);
        requisition.setTenantId(41L);
        requisition.setRequisitionNo("PR-41-100");
        requisition.setWorkflowBusinessKey(businessKey);
        requisition.setProcessInstanceId(processInstanceId);
        requisition.setStatus(status);
        requisition.setWorkflowStartStatus(startStatus);
        requisition.setApprovalAttempt(2);
        requisition.setTotalAmount(new BigDecimal("100.0000"));
        requisition.setCurrencyCode("CNY");
        requisition.setDeleted(0);
        return requisition;
    }

    private WorkflowContracts.ProcessCompletedEvent event(String eventId, String businessKey,
                                                            String processInstanceId, String result) {
        WorkflowContracts.ProcessCompletedEvent event = new WorkflowContracts.ProcessCompletedEvent();
        event.setEventId(eventId);
        event.setEventType("workflow.process.completed.v1");
        event.setOccurredAt(LocalDateTime.of(2026, 7, 21, 10, 0));
        event.setTenantId(41L);
        event.setProducer("omni-workflow");
        event.setBusinessType("PROCUREMENT_REQUISITION");
        event.setBusinessKey(businessKey);
        event.setProcessInstanceId(processInstanceId);
        event.setResult(result);
        event.setCompletedTime(LocalDateTime.of(2026, 7, 21, 10, 1));
        return event;
    }

    private static void initialize(Class<?> entityType, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace("com.omni.procurement.test." + resource);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
