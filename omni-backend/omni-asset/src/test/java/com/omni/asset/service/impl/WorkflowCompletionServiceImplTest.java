package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.asset.domain.AssetOperationStateMachine;
import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.WorkflowContracts;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.entity.AstDisposal;
import com.omni.asset.entity.AstInboxEvent;
import com.omni.asset.entity.AstTransfer;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.mapper.AstDisposalMapper;
import com.omni.asset.mapper.AstInboxEventMapper;
import com.omni.asset.mapper.AstTransferMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.asset.workflow.AssetWorkflowCoordinator;
import com.omni.asset.workflow.RetryableWorkflowEventException;
import com.omni.common.core.result.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Workflow 完成事件幂等、乱序和边界校验测试。 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class WorkflowCompletionServiceImplTest {

    @Mock private AstInboxEventMapper inboxMapper;
    @Mock private AstTransferMapper transferMapper;
    @Mock private AstDisposalMapper disposalMapper;
    @Mock private AstAssetMapper assetMapper;
    @Mock private AstAssetHistoryMapper historyMapper;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(AstAsset.class, "AstAssetMapper");
        initialize(AstAssetHistory.class, "AstAssetHistoryMapper");
        initialize(AstTransfer.class, "AstTransferMapper");
        initialize(AstDisposal.class, "AstDisposalMapper");
        initialize(AstInboxEvent.class, "AstInboxEventMapper");
    }

    /** 清理事件线程身份。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** 审批通过必须只推进申请，保留资产活动占位等待业务完成。 */
    @Test
    void shouldApproveTransferAndKeepAssetOccupied() throws Exception {
        WorkflowContracts.ProcessCompletedEvent event = transferEvent("event-1", "p-1", "APPROVED");
        AstInboxEvent inbox = inbox(event, "RECEIVED");
        AstTransfer transfer = transfer("p-1", AssetOperationStateMachine.START_STARTED);
        stubInbox(inbox);
        when(inboxMapper.markProcessed(inbox)).thenReturn(1);
        when(transferMapper.selectForUpdate(1L, 10L)).thenReturn(transfer);
        when(assetMapper.selectForUpdate(1L, 50L)).thenReturn(occupiedTransferAsset());
        when(transferMapper.update(ArgumentMatchers.isNull(), any(Wrapper.class))).thenReturn(1);
        ServiceIdentityContext.set(new ServiceRequestIdentity(0L, 1L, "workflow-event"));

        boolean handled = service().handle(event);

        assertThat(handled).isTrue();
        verify(assetMapper).selectForUpdate(1L, 50L);
        verify(assetMapper, never()).update(ArgumentMatchers.isNull(), any(Wrapper.class));
        verify(inboxMapper).markProcessed(inbox);
    }

    /** 早到本地启动确认之前的完成事件必须回滚并等待重试。 */
    @Test
    void shouldRetryWhenCompletionArrivesBeforeMarkStarted() throws Exception {
        WorkflowContracts.ProcessCompletedEvent event = transferEvent("event-2", "p-1", "APPROVED");
        AstInboxEvent inbox = inbox(event, "RECEIVED");
        AstTransfer transfer = transfer(null, AssetOperationStateMachine.START_PENDING);
        stubInbox(inbox);
        when(transferMapper.selectForUpdate(1L, 10L)).thenReturn(transfer);
        ServiceIdentityContext.set(new ServiceRequestIdentity(0L, 1L, "workflow-event"));

        assertThatThrownBy(() -> service().handle(event))
                .isInstanceOf(RetryableWorkflowEventException.class);
        verify(inboxMapper, never()).markProcessed(any());
        verify(transferMapper, never()).update(ArgumentMatchers.isNull(), any(Wrapper.class));
    }

    /** 错误流程实例和乱序事件必须终结为忽略且不改变业务。 */
    @Test
    void shouldIgnoreMismatchedProcessInstance() throws Exception {
        WorkflowContracts.ProcessCompletedEvent event = transferEvent("event-3", "wrong", "APPROVED");
        AstInboxEvent inbox = inbox(event, "RECEIVED");
        AstTransfer transfer = transfer("p-1", AssetOperationStateMachine.START_STARTED);
        stubInbox(inbox);
        when(inboxMapper.markProcessed(inbox)).thenReturn(1);
        when(transferMapper.selectForUpdate(1L, 10L)).thenReturn(transfer);
        ServiceIdentityContext.set(new ServiceRequestIdentity(0L, 1L, "workflow-event"));

        assertThat(service().handle(event)).isFalse();
        assertThat(inbox.getStatus()).isEqualTo("IGNORED");
        verify(transferMapper, never()).update(ArgumentMatchers.isNull(), any(Wrapper.class));
    }

    /** 拒绝必须恢复 previous status 并清除活动占位。 */
    @Test
    void shouldRestorePreviousAssetStatusWhenTransferRejected() throws Exception {
        WorkflowContracts.ProcessCompletedEvent event = transferEvent("event-4", "p-1", "REJECTED");
        AstInboxEvent inbox = inbox(event, "RECEIVED");
        AstTransfer transfer = transfer("p-1", AssetOperationStateMachine.START_STARTED);
        AstAsset asset = new AstAsset();
        asset.setId(50L);
        asset.setTenantId(1L);
        asset.setStatus(AssetStateMachine.TRANSFER);
        asset.setActiveOperationType(AssetOperationWorkflowStateServiceImpl.TRANSFER);
        asset.setActiveOperationId(10L);
        asset.setVersion(3);
        asset.setDeleted(0);
        stubInbox(inbox);
        when(inboxMapper.markProcessed(inbox)).thenReturn(1);
        when(transferMapper.selectForUpdate(1L, 10L)).thenReturn(transfer);
        when(transferMapper.update(ArgumentMatchers.isNull(), any(Wrapper.class))).thenReturn(1);
        when(assetMapper.selectForUpdate(1L, 50L)).thenReturn(asset);
        when(assetMapper.update(ArgumentMatchers.isNull(), any(Wrapper.class))).thenReturn(1);
        when(historyMapper.insert(any(AstAssetHistory.class))).thenReturn(1);
        ServiceIdentityContext.set(new ServiceRequestIdentity(0L, 1L, "workflow-event"));

        assertThat(service().handle(event)).isTrue();
        verify(assetMapper).update(ArgumentMatchers.isNull(), any(Wrapper.class));
        verify(historyMapper).insert(any(AstAssetHistory.class));
    }

    /** 重复已处理事件必须直接返回且不二次更新。 */
    @Test
    void shouldIgnoreDuplicateProcessedEvent() throws Exception {
        WorkflowContracts.ProcessCompletedEvent event = transferEvent("event-5", "p-1", "APPROVED");
        AstInboxEvent inbox = inbox(event, "PROCESSED");
        stubInbox(inbox);
        ServiceIdentityContext.set(new ServiceRequestIdentity(0L, 1L, "workflow-event"));

        assertThat(service().handle(event)).isFalse();
        verify(transferMapper, never()).selectForUpdate(any(), any());
    }

    /** 事件租户与线程上下文不一致必须失败关闭。 */
    @Test
    void shouldFailClosedForWrongTenantContext() {
        WorkflowContracts.ProcessCompletedEvent event = transferEvent("event-6", "p-1", "APPROVED");
        ServiceIdentityContext.set(new ServiceRequestIdentity(0L, 2L, "workflow-event"));

        assertThatThrownBy(() -> service().handle(event))
                .isInstanceOf(BusinessException.class);
        verify(inboxMapper, never()).insertIgnore(any());
    }

    /** 全局事件 ID 被其他租户占用时必须按意图冲突返回，而不是进入无限重试。 */
    @Test
    void shouldRejectCrossTenantEventIdCollision() throws Exception {
        WorkflowContracts.ProcessCompletedEvent event = transferEvent("event-7", "p-1", "APPROVED");
        AstInboxEvent inbox = inbox(event, "RECEIVED");
        inbox.setTenantId(2L);
        stubInbox(inbox);
        ServiceIdentityContext.set(new ServiceRequestIdentity(0L, 1L, "workflow-event"));

        assertThatThrownBy(() -> service().handle(event))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("事件 ID");
        verify(transferMapper, never()).selectForUpdate(any(), any());
    }

    private WorkflowCompletionServiceImpl service() {
        return new WorkflowCompletionServiceImpl(inboxMapper, transferMapper, disposalMapper,
                assetMapper, historyMapper, objectMapper());
    }

    private void stubInbox(AstInboxEvent inbox) {
        when(inboxMapper.insertIgnore(any(AstInboxEvent.class))).thenReturn(1);
        when(inboxMapper.selectForUpdate("asset-workflow-completion-v1", inbox.getEventId()))
                .thenReturn(inbox);
    }

    private AstTransfer transfer(String processInstanceId, String startStatus) {
        AstTransfer transfer = new AstTransfer();
        transfer.setId(10L);
        transfer.setTenantId(1L);
        transfer.setAssetId(50L);
        transfer.setStatus(AssetOperationStateMachine.PENDING_APPROVAL);
        transfer.setPreviousAssetStatus(AssetStateMachine.IN_USE);
        transfer.setActiveFlag(1);
        transfer.setWorkflowBusinessKey("10");
        transfer.setWorkflowStartStatus(startStatus);
        transfer.setProcessInstanceId(processInstanceId);
        transfer.setVersion(1);
        transfer.setDeleted(0);
        return transfer;
    }

    private AstAsset occupiedTransferAsset() {
        AstAsset asset = new AstAsset();
        asset.setId(50L);
        asset.setTenantId(1L);
        asset.setStatus(AssetStateMachine.TRANSFER);
        asset.setActiveOperationType(AssetOperationWorkflowStateServiceImpl.TRANSFER);
        asset.setActiveOperationId(10L);
        asset.setVersion(3);
        asset.setDeleted(0);
        return asset;
    }

    private AstInboxEvent inbox(WorkflowContracts.ProcessCompletedEvent event,
                                String status) throws Exception {
        AstInboxEvent inbox = new AstInboxEvent();
        inbox.setId(100L);
        inbox.setTenantId(event.getTenantId());
        inbox.setConsumerName("asset-workflow-completion-v1");
        inbox.setEventId(event.getEventId());
        inbox.setEventType(event.getEventType());
        inbox.setSourceService(event.getProducer());
        inbox.setAggregateType(event.getBusinessType());
        inbox.setAggregateId(event.getBusinessKey());
        inbox.setPayload(objectMapper().writeValueAsString(event));
        inbox.setStatus(status);
        return inbox;
    }

    private WorkflowContracts.ProcessCompletedEvent transferEvent(
            String eventId, String processInstanceId, String result) {
        WorkflowContracts.ProcessCompletedEvent event = new WorkflowContracts.ProcessCompletedEvent();
        event.setEventId(eventId);
        event.setEventType("workflow.process.completed.v1");
        event.setOccurredAt(LocalDateTime.of(2026, 7, 27, 9, 0));
        event.setTenantId(1L);
        event.setProducer("omni-workflow");
        event.setBusinessType(AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE);
        event.setBusinessKey("10");
        event.setProcessInstanceId(processInstanceId);
        event.setResult(result);
        event.setCompletedTime(LocalDateTime.of(2026, 7, 27, 9, 1));
        return event;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private static void initialize(Class<?> entityClass, String resource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, resource), entityClass);
    }
}
