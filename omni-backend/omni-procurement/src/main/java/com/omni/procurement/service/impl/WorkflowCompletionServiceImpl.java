package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.RequisitionStateMachine;
import com.omni.procurement.dto.RequisitionDomainEvent;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.entity.ProcEventInbox;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.mapper.ProcEventInboxMapper;
import com.omni.procurement.mapper.ProcRequisitionMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.procurement.service.WorkflowCompletionService;
import com.omni.procurement.workflow.RequisitionWorkflowCoordinator;
import com.omni.procurement.workflow.RetryableWorkflowEventException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow 完成事件 Inbox 处理服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class WorkflowCompletionServiceImpl implements WorkflowCompletionService {

    private static final String WORKFLOW_EVENT = "workflow.process.completed.v1";
    private static final String WORKFLOW_PRODUCER = "omni-workflow";
    private static final String DOMAIN_BINDING = "procurement-domain-out-0";
    private static final String RECEIVED = "RECEIVED";
    private static final String PROCESSED = "PROCESSED";
    private static final String IGNORED = "IGNORED";

    private final ProcEventInboxMapper inboxMapper;
    private final ProcRequisitionMapper requisitionMapper;
    private final ReliableMessageRelay reliableMessageRelay;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean handle(WorkflowContracts.ProcessCompletedEvent event) {
        validate(event);
        if (!event.getTenantId().equals(ServiceIdentityContext.requireTenantId())) {
            throw new BusinessException(403, "Workflow 完成事件与当前租户上下文不一致");
        }
        BusinessKey businessKey = parseBusinessKey(event.getBusinessKey());
        ProcEventInbox inbox = registerAndLock(event);
        validateDuplicateIntent(inbox, event);
        if (PROCESSED.equals(inbox.getStatus()) || IGNORED.equals(inbox.getStatus())) {
            return false;
        }

        ProcRequisition requisition = requisitionMapper.selectForUpdate(
                event.getTenantId(), businessKey.requisitionId());
        if (requisition == null) {
            markInbox(inbox, IGNORED);
            return false;
        }
        if (!event.getBusinessKey().equals(requisition.getWorkflowBusinessKey())) {
            markInbox(inbox, IGNORED);
            return false;
        }
        if (isTerminal(requisition.getStatus())) {
            markInbox(inbox, IGNORED);
            return false;
        }
        if (RequisitionStateMachine.SUBMITTED.equals(requisition.getStatus())
                && (RequisitionStateMachine.START_PENDING.equals(requisition.getWorkflowStartStatus())
                || RequisitionStateMachine.START_FAILED.equals(requisition.getWorkflowStartStatus())
                || requisition.getProcessInstanceId() == null)) {
            throw new RetryableWorkflowEventException("Workflow 完成事件早于本地启动确认，请稍后重试");
        }
        if (!RequisitionStateMachine.APPROVING.equals(requisition.getStatus())
                || !RequisitionStateMachine.START_STARTED.equals(requisition.getWorkflowStartStatus())
                || !event.getProcessInstanceId().equals(requisition.getProcessInstanceId())) {
            markInbox(inbox, IGNORED);
            return false;
        }

        String targetStatus = targetStatus(event.getResult());
        LambdaUpdateWrapper<ProcRequisition> update = new LambdaUpdateWrapper<ProcRequisition>()
                .eq(ProcRequisition::getTenantId, event.getTenantId())
                .eq(ProcRequisition::getId, requisition.getId())
                .eq(ProcRequisition::getStatus, RequisitionStateMachine.APPROVING)
                .eq(ProcRequisition::getWorkflowStartStatus, RequisitionStateMachine.START_STARTED)
                .eq(ProcRequisition::getWorkflowBusinessKey, event.getBusinessKey())
                .eq(ProcRequisition::getProcessInstanceId, event.getProcessInstanceId())
                .eq(ProcRequisition::getDeleted, 0)
                .set(ProcRequisition::getStatus, targetStatus)
                .set(ProcRequisition::getWorkflowCompletedTime, event.getCompletedTime())
                .set(ProcRequisition::getApprovedTime,
                        RequisitionStateMachine.APPROVED.equals(targetStatus)
                                ? event.getCompletedTime() : null)
                .set(ProcRequisition::getUpdateTime, LocalDateTime.now())
                .set(ProcRequisition::getUpdateBy, "workflow-event")
                .setSql("version = version + 1");
        if (requisitionMapper.update(null, update) != 1) {
            throw new RetryableWorkflowEventException("请购审批状态并发变化，请稍后重试完成事件");
        }
        publishResult(requisition, event, targetStatus);
        markInbox(inbox, PROCESSED);
        return true;
    }

    private ProcEventInbox registerAndLock(WorkflowContracts.ProcessCompletedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        ProcEventInbox candidate = new ProcEventInbox();
        candidate.setTenantId(event.getTenantId());
        candidate.setEventId(event.getEventId());
        candidate.setEventType(event.getEventType());
        candidate.setSourceService(event.getProducer());
        candidate.setAggregateType(event.getBusinessType());
        candidate.setAggregateId(event.getBusinessKey());
        candidate.setPayload(toJson(event));
        candidate.setStatus(RECEIVED);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        inboxMapper.insertIgnore(candidate);
        ProcEventInbox inbox = inboxMapper.selectForUpdate(event.getTenantId(), event.getEventId());
        if (inbox == null) {
            throw new RetryableWorkflowEventException("无法锁定 Workflow 完成事件 Inbox");
        }
        return inbox;
    }

    private void validateDuplicateIntent(ProcEventInbox inbox,
                                         WorkflowContracts.ProcessCompletedEvent event) {
        if (!event.getEventType().equals(inbox.getEventType())
                || !event.getProducer().equals(inbox.getSourceService())
                || !event.getBusinessType().equals(inbox.getAggregateType())
                || !event.getBusinessKey().equals(inbox.getAggregateId())
                || !jsonEquivalent(inbox.getPayload(), toJson(event))) {
            throw new BusinessException(409, "同一 Workflow 事件 ID 绑定了不同业务意图");
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

    private void markInbox(ProcEventInbox inbox, String status) {
        LocalDateTime now = LocalDateTime.now();
        int affected = inboxMapper.update(null, new LambdaUpdateWrapper<ProcEventInbox>()
                .eq(ProcEventInbox::getTenantId, inbox.getTenantId())
                .eq(ProcEventInbox::getId, inbox.getId())
                .eq(ProcEventInbox::getEventId, inbox.getEventId())
                .set(ProcEventInbox::getStatus, status)
                .set(ProcEventInbox::getProcessedTime, now)
                .set(ProcEventInbox::getErrorMessage, null)
                .set(ProcEventInbox::getUpdateTime, now));
        if (affected != 1) {
            throw new RetryableWorkflowEventException("更新 Workflow 完成事件 Inbox 失败");
        }
    }

    private void publishResult(ProcRequisition requisition,
                               WorkflowContracts.ProcessCompletedEvent source, String targetStatus) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requisitionId", requisition.getId());
        payload.put("requisitionNo", requisition.getRequisitionNo());
        payload.put("status", targetStatus);
        payload.put("approvalAttempt", requisition.getApprovalAttempt());
        payload.put("businessKey", source.getBusinessKey());
        payload.put("processInstanceId", source.getProcessInstanceId());
        payload.put("workflowEventId", source.getEventId());
        payload.put("completedTime", source.getCompletedTime());
        payload.put("totalAmount", requisition.getTotalAmount().setScale(4).toPlainString());
        payload.put("currencyCode", requisition.getCurrencyCode());
        RequisitionDomainEvent event = RequisitionDomainEvent.builder()
                .eventId(eventId)
                .eventType(resultEventType(targetStatus))
                .occurredAt(LocalDateTime.now())
                .tenantId(source.getTenantId())
                .payload(payload)
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, event, source.getTenantId(), eventId);
    }

    private String resultEventType(String status) {
        return switch (status) {
            case RequisitionStateMachine.APPROVED -> "procurement.requisition.approved.v1";
            case RequisitionStateMachine.REJECTED -> "procurement.requisition.rejected.v1";
            case RequisitionStateMachine.CANCELLED -> "procurement.requisition.cancelled.v1";
            default -> throw new IllegalArgumentException("不支持的请购完成状态");
        };
    }

    private String targetStatus(String result) {
        return switch (result) {
            case "APPROVED" -> RequisitionStateMachine.APPROVED;
            case "REJECTED" -> RequisitionStateMachine.REJECTED;
            case "CANCELLED" -> RequisitionStateMachine.CANCELLED;
            default -> throw new IllegalArgumentException("Workflow 完成结果不受支持");
        };
    }

    private BusinessKey parseBusinessKey(String value) {
        int separator = value == null ? -1 : value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Workflow 业务键格式无效");
        }
        try {
            Long requisitionId = Long.valueOf(value.substring(0, separator));
            Integer attempt = Integer.valueOf(value.substring(separator + 1));
            if (requisitionId <= 0 || attempt <= 0) {
                throw new NumberFormatException("业务键必须为正数");
            }
            return new BusinessKey(requisitionId, attempt);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Workflow 业务键格式无效", exception);
        }
    }

    private boolean isTerminal(String status) {
        return RequisitionStateMachine.APPROVED.equals(status)
                || RequisitionStateMachine.REJECTED.equals(status)
                || RequisitionStateMachine.CANCELLED.equals(status);
    }

    private void validate(WorkflowContracts.ProcessCompletedEvent event) {
        if (event == null
                || event.getEventId() == null || event.getEventId().isBlank()
                || !WORKFLOW_EVENT.equals(event.getEventType())
                || event.getTenantId() == null || event.getTenantId() <= 0
                || !WORKFLOW_PRODUCER.equals(event.getProducer())
                || !RequisitionWorkflowCoordinator.BUSINESS_TYPE.equals(event.getBusinessType())
                || event.getBusinessKey() == null || event.getBusinessKey().isBlank()
                || event.getProcessInstanceId() == null || event.getProcessInstanceId().isBlank()
                || event.getResult() == null
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

    private record BusinessKey(Long requisitionId, Integer attempt) {
    }
}
