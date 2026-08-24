package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.omni.asset.service.WorkflowCompletionService;
import com.omni.asset.service.support.AssetAuditSupport;
import com.omni.asset.workflow.AssetWorkflowCoordinator;
import com.omni.asset.workflow.RetryableWorkflowEventException;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Workflow 完成事件 Inbox 处理服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCompletionServiceImpl implements WorkflowCompletionService {

    private static final String EVENT_TYPE = "workflow.process.completed.v1";
    private static final String PRODUCER = "omni-workflow";
    private static final String CONSUMER_NAME = "asset-workflow-completion-v1";
    private static final String RECEIVED = "RECEIVED";
    private static final String PROCESSED = "PROCESSED";
    private static final String IGNORED = "IGNORED";
    private static final Set<String> RESULTS = Set.of("APPROVED", "REJECTED", "CANCELLED");

    private final AstInboxEventMapper inboxMapper;
    private final AstTransferMapper transferMapper;
    private final AstDisposalMapper disposalMapper;
    private final AstAssetMapper assetMapper;
    private final AstAssetHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean handle(WorkflowContracts.ProcessCompletedEvent event) {
        validate(event);
        if (!event.getTenantId().equals(ServiceIdentityContext.requireTenantId())) {
            throw new BusinessException(403, "Workflow 完成事件与当前租户上下文不一致");
        }
        Long operationId = parseBusinessKey(event.getBusinessKey());
        AstInboxEvent inbox = registerAndLock(event);
        validateDuplicateIntent(inbox, event);
        if (PROCESSED.equals(inbox.getStatus()) || IGNORED.equals(inbox.getStatus())) {
            return false;
        }
        return switch (event.getBusinessType()) {
            case AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE ->
                    handleTransfer(event, operationId, inbox);
            case AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE ->
                    handleDisposal(event, operationId, inbox);
            default -> throw new IllegalArgumentException("Workflow 业务类型不受支持");
        };
    }

    private boolean handleTransfer(WorkflowContracts.ProcessCompletedEvent event,
                                   Long operationId, AstInboxEvent inbox) {
        AstTransfer transfer = transferMapper.selectForUpdate(event.getTenantId(), operationId);
        if (transfer == null || !event.getBusinessKey().equals(transfer.getWorkflowBusinessKey())) {
            markInbox(inbox, IGNORED);
            return false;
        }
        if (isEarly(transfer.getStatus(), transfer.getWorkflowStartStatus(),
                transfer.getProcessInstanceId())) {
            throw new RetryableWorkflowEventException("调拨完成事件早于本地启动确认");
        }
        if (!matchesCurrentApproval(transfer.getStatus(), transfer.getWorkflowStartStatus(),
                transfer.getProcessInstanceId(), event.getProcessInstanceId())) {
            markInbox(inbox, IGNORED);
            return false;
        }
        String targetStatus = targetStatus(event.getResult());
        AstAsset asset = requireOccupiedAsset(event.getTenantId(), transfer.getAssetId(),
                AssetOperationWorkflowStateServiceImpl.TRANSFER, operationId,
                AssetStateMachine.TRANSFER);
        int affected = transferMapper.update(null, new LambdaUpdateWrapper<AstTransfer>()
                .eq(AstTransfer::getTenantId, event.getTenantId())
                .eq(AstTransfer::getId, operationId)
                .eq(AstTransfer::getStatus, AssetOperationStateMachine.PENDING_APPROVAL)
                .eq(AstTransfer::getWorkflowStartStatus, AssetOperationStateMachine.START_STARTED)
                .eq(AstTransfer::getWorkflowBusinessKey, event.getBusinessKey())
                .eq(AstTransfer::getProcessInstanceId, event.getProcessInstanceId())
                .eq(AstTransfer::getActiveFlag, 1)
                .eq(AstTransfer::getDeleted, 0)
                .set(AstTransfer::getStatus, targetStatus)
                .set(AstTransfer::getApprovedTime,
                        AssetOperationStateMachine.APPROVED.equals(targetStatus)
                                ? event.getCompletedTime() : null)
                .set(AstTransfer::getActiveFlag,
                        AssetOperationStateMachine.APPROVED.equals(targetStatus) ? 1 : 0)
                .set(AstTransfer::getUpdateTime, LocalDateTime.now())
                .set(AstTransfer::getUpdateBy, "workflow-event")
                .setSql("version = version + 1"));
        if (affected != 1) {
            throw new RetryableWorkflowEventException("调拨审批状态并发变化");
        }
        if (!AssetOperationStateMachine.APPROVED.equals(targetStatus)) {
            restoreAsset(asset, new RestoreIntent(operationId,
                    AssetOperationWorkflowStateServiceImpl.TRANSFER,
                    AssetStateMachine.TRANSFER, transfer.getPreviousAssetStatus(),
                    "Workflow " + event.getResult() + "，恢复调拨前状态"));
        }
        markInbox(inbox, PROCESSED);
        return true;
    }

    private boolean handleDisposal(WorkflowContracts.ProcessCompletedEvent event,
                                   Long operationId, AstInboxEvent inbox) {
        AstDisposal disposal = disposalMapper.selectForUpdate(event.getTenantId(), operationId);
        if (disposal == null || !event.getBusinessKey().equals(disposal.getWorkflowBusinessKey())) {
            markInbox(inbox, IGNORED);
            return false;
        }
        if (isEarly(disposal.getStatus(), disposal.getWorkflowStartStatus(),
                disposal.getProcessInstanceId())) {
            throw new RetryableWorkflowEventException("处置完成事件早于本地启动确认");
        }
        if (!matchesCurrentApproval(disposal.getStatus(), disposal.getWorkflowStartStatus(),
                disposal.getProcessInstanceId(), event.getProcessInstanceId())) {
            markInbox(inbox, IGNORED);
            return false;
        }
        String targetStatus = targetStatus(event.getResult());
        AstAsset asset = requireOccupiedAsset(event.getTenantId(), disposal.getAssetId(),
                AssetOperationWorkflowStateServiceImpl.DISPOSAL, operationId,
                AssetStateMachine.DISPOSAL_PENDING);
        int affected = disposalMapper.update(null, new LambdaUpdateWrapper<AstDisposal>()
                .eq(AstDisposal::getTenantId, event.getTenantId())
                .eq(AstDisposal::getId, operationId)
                .eq(AstDisposal::getStatus, AssetOperationStateMachine.PENDING_APPROVAL)
                .eq(AstDisposal::getWorkflowStartStatus, AssetOperationStateMachine.START_STARTED)
                .eq(AstDisposal::getWorkflowBusinessKey, event.getBusinessKey())
                .eq(AstDisposal::getProcessInstanceId, event.getProcessInstanceId())
                .eq(AstDisposal::getActiveFlag, 1)
                .eq(AstDisposal::getDeleted, 0)
                .set(AstDisposal::getStatus, targetStatus)
                .set(AstDisposal::getApprovedTime,
                        AssetOperationStateMachine.APPROVED.equals(targetStatus)
                                ? event.getCompletedTime() : null)
                .set(AstDisposal::getActiveFlag,
                        AssetOperationStateMachine.APPROVED.equals(targetStatus) ? 1 : 0)
                .set(AstDisposal::getUpdateTime, LocalDateTime.now())
                .set(AstDisposal::getUpdateBy, "workflow-event")
                .setSql("version = version + 1"));
        if (affected != 1) {
            throw new RetryableWorkflowEventException("处置审批状态并发变化");
        }
        if (!AssetOperationStateMachine.APPROVED.equals(targetStatus)) {
            restoreAsset(asset, new RestoreIntent(operationId,
                    AssetOperationWorkflowStateServiceImpl.DISPOSAL,
                    AssetStateMachine.DISPOSAL_PENDING, disposal.getPreviousAssetStatus(),
                    "Workflow " + event.getResult() + "，恢复处置前状态"));
        }
        markInbox(inbox, PROCESSED);
        return true;
    }

    private AstInboxEvent registerAndLock(WorkflowContracts.ProcessCompletedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        AstInboxEvent candidate = new AstInboxEvent();
        candidate.setTenantId(event.getTenantId());
        candidate.setConsumerName(CONSUMER_NAME);
        candidate.setEventId(event.getEventId());
        candidate.setEventType(event.getEventType());
        candidate.setSourceService(event.getProducer());
        candidate.setAggregateType(event.getBusinessType());
        candidate.setAggregateId(event.getBusinessKey());
        candidate.setPayload(toJson(event));
        candidate.setStatus(RECEIVED);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        int inserted = inboxMapper.insertIgnore(candidate);
        if (inserted != 0 && inserted != 1) {
            throw new RetryableWorkflowEventException("登记 Workflow 完成事件 Inbox 失败");
        }
        AstInboxEvent inbox = inboxMapper.selectForUpdate(CONSUMER_NAME, event.getEventId());
        if (inbox == null) {
            throw new RetryableWorkflowEventException("无法锁定 Workflow 完成事件 Inbox");
        }
        return inbox;
    }

    private void validateDuplicateIntent(AstInboxEvent inbox,
                                         WorkflowContracts.ProcessCompletedEvent event) {
        if (!event.getTenantId().equals(inbox.getTenantId())
                || !event.getEventType().equals(inbox.getEventType())
                || !event.getProducer().equals(inbox.getSourceService())
                || !event.getBusinessType().equals(inbox.getAggregateType())
                || !event.getBusinessKey().equals(inbox.getAggregateId())
                || !jsonEquivalent(inbox.getPayload(), toJson(event))) {
            throw new BusinessException(409, "同一 Workflow 事件 ID 绑定了不同业务意图");
        }
    }

    private void markInbox(AstInboxEvent inbox, String status) {
        inbox.setStatus(status);
        inbox.setProcessedTime(LocalDateTime.now());
        inbox.setUpdateTime(inbox.getProcessedTime());
        if (inboxMapper.markProcessed(inbox) != 1) {
            throw new RetryableWorkflowEventException("更新 Workflow 完成事件 Inbox 失败");
        }
    }

    private AstAsset requireOccupiedAsset(Long tenantId, Long assetId, String operationType,
                                          Long operationId, String expectedStatus) {
        AstAsset asset = assetMapper.selectForUpdate(tenantId, assetId);
        if (asset == null
                || !operationType.equals(asset.getActiveOperationType())
                || !operationId.equals(asset.getActiveOperationId())
                || !expectedStatus.equals(asset.getStatus())) {
            throw new RetryableWorkflowEventException("资产活动操作占位与 Workflow 申请不一致");
        }
        return asset;
    }

    private void restoreAsset(AstAsset asset, RestoreIntent intent) {
        AssetStateMachine.requireTransition(intent.currentStatus(), intent.previousStatus());
        int affected = assetMapper.update(null, new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, asset.getTenantId())
                .eq(AstAsset::getId, asset.getId())
                .eq(AstAsset::getVersion, asset.getVersion())
                .eq(AstAsset::getStatus, intent.currentStatus())
                .eq(AstAsset::getActiveOperationType, intent.operationType())
                .eq(AstAsset::getActiveOperationId, intent.operationId())
                .eq(AstAsset::getDeleted, 0)
                .set(AstAsset::getStatus, intent.previousStatus())
                .set(AstAsset::getActiveOperationType, null)
                .set(AstAsset::getActiveOperationId, null)
                .set(AstAsset::getUpdateTime, LocalDateTime.now())
                .set(AstAsset::getUpdateBy, "workflow-event")
                .setSql("version = version + 1"));
        if (affected != 1) {
            throw new RetryableWorkflowEventException("恢复资产 Workflow 操作占位失败");
        }
        AstAssetHistory history = new AstAssetHistory();
        history.setTenantId(asset.getTenantId());
        history.setAssetId(asset.getId());
        history.setFromStatus(intent.currentStatus());
        history.setToStatus(intent.previousStatus());
        history.setChangedByUserId(ServiceIdentityContext.require().userId());
        history.setChangedTime(LocalDateTime.now());
        history.setRemark(intent.remark());
        AssetAuditSupport.created(history);
        if (historyMapper.insert(history) != 1) {
            throw new RetryableWorkflowEventException("记录 Workflow 资产恢复历史失败");
        }
    }

    private boolean isEarly(String status, String startStatus, String processInstanceId) {
        return (AssetOperationStateMachine.PENDING_APPROVAL.equals(status)
                || AssetOperationStateMachine.START_FAILED.equals(status))
                && (!AssetOperationStateMachine.START_STARTED.equals(startStatus)
                || processInstanceId == null || processInstanceId.isBlank());
    }

    private boolean matchesCurrentApproval(String status, String startStatus,
                                           String localProcessInstanceId,
                                           String eventProcessInstanceId) {
        return AssetOperationStateMachine.PENDING_APPROVAL.equals(status)
                && AssetOperationStateMachine.START_STARTED.equals(startStatus)
                && eventProcessInstanceId.equals(localProcessInstanceId);
    }

    private String targetStatus(String result) {
        return switch (result) {
            case "APPROVED" -> AssetOperationStateMachine.APPROVED;
            case "REJECTED" -> AssetOperationStateMachine.REJECTED;
            case "CANCELLED" -> AssetOperationStateMachine.CANCELLED;
            default -> throw new IllegalArgumentException("Workflow 完成结果不受支持");
        };
    }

    private Long parseBusinessKey(String value) {
        try {
            Long id = Long.valueOf(value);
            if (id <= 0) {
                throw new NumberFormatException("业务键必须为正数");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Workflow 业务键格式无效", exception);
        }
    }

    private void validate(WorkflowContracts.ProcessCompletedEvent event) {
        if (event == null
                || event.getEventId() == null || event.getEventId().isBlank()
                || !EVENT_TYPE.equals(event.getEventType())
                || event.getOccurredAt() == null
                || event.getTenantId() == null || event.getTenantId() <= 0
                || !PRODUCER.equals(event.getProducer())
                || (!AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE.equals(event.getBusinessType())
                && !AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE.equals(event.getBusinessType()))
                || event.getBusinessKey() == null || event.getBusinessKey().isBlank()
                || event.getProcessInstanceId() == null || event.getProcessInstanceId().isBlank()
                || !RESULTS.contains(event.getResult())
                || event.getCompletedTime() == null) {
            throw new IllegalArgumentException("Workflow 完成事件缺少必需字段或契约不匹配");
        }
    }

    private String toJson(WorkflowContracts.ProcessCompletedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workflow 完成事件无法序列化", exception);
        }
    }

    private boolean jsonEquivalent(String left, String right) {
        try {
            JsonNode leftNode = objectMapper.readTree(left);
            JsonNode rightNode = objectMapper.readTree(right);
            return leftNode != null && leftNode.equals(rightNode);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(409, "Workflow Inbox 已存事件载荷无效");
        }
    }

    /**
     * 资产占位恢复意图。
     *
     * @param operationId 申请 ID
     * @param operationType 活动类型
     * @param currentStatus 当前占位状态
     * @param previousStatus 发起前状态
     * @param remark 历史说明
     */
    private record RestoreIntent(Long operationId, String operationType,
                                 String currentStatus, String previousStatus,
                                 String remark) {
    }
}
