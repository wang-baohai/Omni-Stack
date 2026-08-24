package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.domain.SrmStateMachine;
import com.omni.srm.domain.SrmStateMachine.SupplierStatus;
import com.omni.srm.dto.DomainEventEnvelope;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.WorkflowContracts;
import com.omni.srm.entity.SrmEventInbox;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.mapper.SrmEventInboxMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.srm.service.RiskService;
import com.omni.srm.service.SupplierWorkflowCompletionService;
import com.omni.srm.workflow.RetryableWorkflowEventException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 供应商准入 Workflow 完成事件 Inbox 处理服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierWorkflowCompletionServiceImpl implements SupplierWorkflowCompletionService {

    private static final String WORKFLOW_EVENT = "workflow.process.completed.v1";
    private static final String WORKFLOW_PRODUCER = "omni-workflow";
    private static final String DOMAIN_BINDING = "srm-domain-out-0";
    private static final String RECEIVED = "RECEIVED";
    private static final String PROCESSED = "PROCESSED";
    private static final String IGNORED = "IGNORED";

    private final SrmEventInboxMapper inboxMapper;
    private final SrmSupplierMapper supplierMapper;
    private final RiskService riskService;
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
        SrmEventInbox inbox = registerAndLock(event);
        validateDuplicateIntent(inbox, event);
        if (PROCESSED.equals(inbox.getStatus()) || IGNORED.equals(inbox.getStatus())) {
            return false;
        }

        SrmSupplier supplier = supplierMapper.selectForUpdate(
                event.getTenantId(), businessKey.supplierId());
        if (supplier == null) {
            markInbox(inbox, IGNORED);
            return false;
        }
        if (!event.getBusinessKey().equals(supplier.getWorkflowBusinessKey())) {
            markInbox(inbox, IGNORED);
            return false;
        }
        if (isTerminal(supplier.getStatus())) {
            markInbox(inbox, IGNORED);
            return false;
        }
        // 完成事件早于本地启动确认 → 重试
        if (SupplierStatus.PENDING_REVIEW.name().equals(supplier.getStatus())
                && (SrmStateMachine.START_PENDING.equals(supplier.getWorkflowStartStatus())
                || SrmStateMachine.START_FAILED.equals(supplier.getWorkflowStartStatus())
                || supplier.getProcessInstanceId() == null)) {
            throw new RetryableWorkflowEventException("Workflow 完成事件早于本地启动确认，请稍后重试");
        }
        if (!SupplierStatus.APPROVING.name().equals(supplier.getStatus())
                || !SrmStateMachine.START_STARTED.equals(supplier.getWorkflowStartStatus())
                || !event.getProcessInstanceId().equals(supplier.getProcessInstanceId())) {
            markInbox(inbox, IGNORED);
            return false;
        }

        String targetStatus = targetStatus(event.getResult());
        LambdaUpdateWrapper<SrmSupplier> update = new LambdaUpdateWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, event.getTenantId())
                .eq(SrmSupplier::getId, supplier.getId())
                .eq(SrmSupplier::getStatus, SupplierStatus.APPROVING.name())
                .eq(SrmSupplier::getWorkflowStartStatus, SrmStateMachine.START_STARTED)
                .eq(SrmSupplier::getWorkflowBusinessKey, event.getBusinessKey())
                .eq(SrmSupplier::getProcessInstanceId, event.getProcessInstanceId())
                .eq(SrmSupplier::getDeleted, 0)
                .set(SrmSupplier::getStatus, targetStatus)
                .set(SrmSupplier::getWorkflowCompletedTime, event.getCompletedTime())
                .set(SrmSupplier::getApprovedTime,
                        SupplierStatus.APPROVED.name().equals(targetStatus)
                                ? event.getCompletedTime() : null)
                .set(SrmSupplier::getUpdateTime, LocalDateTime.now())
                .set(SrmSupplier::getUpdateBy, "workflow-event")
                .setSql("version = version + 1");
        if (supplierMapper.update(null, update) != 1) {
            throw new RetryableWorkflowEventException("供应商审批状态并发变化，请稍后重试完成事件");
        }

        // APPROVED 时初始化风险评估 + 生成正式编号
        if (SupplierStatus.APPROVED.name().equals(targetStatus)) {
            onApproved(supplier, event);
        }

        publishResult(supplier, event, targetStatus);
        markInbox(inbox, PROCESSED);
        return true;
    }

    private void onApproved(SrmSupplier supplier, WorkflowContracts.ProcessCompletedEvent event) {
        // 替换 TMP- 前缀为正式供应商编号
        if (supplier.getSupplierNo() != null && supplier.getSupplierNo().startsWith("TMP-")) {
            String prefix = supplier.getSupplierNo().startsWith("SP") ? "SP" : "S";
            String formalNo = prefix + supplier.getTenantId() + "-" + supplier.getId();
            supplierMapper.update(null, new LambdaUpdateWrapper<SrmSupplier>()
                    .eq(SrmSupplier::getId, supplier.getId())
                    .eq(SrmSupplier::getDeleted, 0)
                    .set(SrmSupplier::getSupplierNo, formalNo));
        }
        // 初始化风险评估
        try {
            SrmRequests.CreateRiskAssessmentRequest riskRequest = new SrmRequests.CreateRiskAssessmentRequest();
            riskRequest.setRemark("供应商准入审批通过后初始化综合风险评估");
            riskService.createAssessment(supplier.getId(), riskRequest);
        } catch (RuntimeException riskException) {
            log.error("供应商准入通过后初始化风险评估失败: tenantId={}, supplierId={}",
                    event.getTenantId(), supplier.getId(), riskException);
        }
    }

    private SrmEventInbox registerAndLock(WorkflowContracts.ProcessCompletedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        SrmEventInbox candidate = new SrmEventInbox();
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
        SrmEventInbox inbox = inboxMapper.selectForUpdate(event.getTenantId(), event.getEventId());
        if (inbox == null) {
            throw new RetryableWorkflowEventException("无法锁定 Workflow 完成事件 Inbox");
        }
        return inbox;
    }

    private void validateDuplicateIntent(SrmEventInbox inbox, WorkflowContracts.ProcessCompletedEvent event) {
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

    private void markInbox(SrmEventInbox inbox, String status) {
        LocalDateTime now = LocalDateTime.now();
        int affected = inboxMapper.update(null, new LambdaUpdateWrapper<SrmEventInbox>()
                .eq(SrmEventInbox::getTenantId, inbox.getTenantId())
                .eq(SrmEventInbox::getId, inbox.getId())
                .eq(SrmEventInbox::getEventId, inbox.getEventId())
                .set(SrmEventInbox::getStatus, status)
                .set(SrmEventInbox::getProcessedTime, now)
                .set(SrmEventInbox::getErrorMessage, null)
                .set(SrmEventInbox::getUpdateTime, now));
        if (affected != 1) {
            throw new RetryableWorkflowEventException("更新 Workflow 完成事件 Inbox 失败");
        }
    }

    private void publishResult(SrmSupplier supplier,
                               WorkflowContracts.ProcessCompletedEvent source, String targetStatus) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supplierId", supplier.getId());
        payload.put("supplierNo", supplier.getSupplierNo());
        payload.put("status", targetStatus);
        payload.put("approvalAttempt", supplier.getApprovalAttempt());
        payload.put("businessKey", source.getBusinessKey());
        payload.put("processInstanceId", source.getProcessInstanceId());
        payload.put("workflowEventId", source.getEventId());
        payload.put("completedTime", source.getCompletedTime());
        DomainEventEnvelope envelope = DomainEventEnvelope.builder()
                .eventId(eventId)
                .eventType(resultEventType(targetStatus))
                .occurredAt(LocalDateTime.now())
                .tenantId(source.getTenantId())
                .producer("omni-srm")
                .aggregateType("SUPPLIER")
                .aggregateId(supplier.getId())
                .aggregateVersion(supplier.getVersion())
                .actorUserId(0L)
                .payload(Map.copyOf(payload))
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, envelope, source.getTenantId(), eventId);
    }

    private String resultEventType(String status) {
        return switch (status) {
            case "APPROVED" -> "srm.supplier.approved.v1";
            case "REJECTED" -> "srm.supplier.rejected.v1";
            default -> throw new IllegalArgumentException("不支持的供应商完成状态");
        };
    }

    private String targetStatus(String result) {
        return switch (result) {
            case "APPROVED" -> SupplierStatus.APPROVED.name();
            case "REJECTED" -> SupplierStatus.REJECTED.name();
            default -> throw new IllegalArgumentException("Workflow 完成结果不受支持");
        };
    }

    private BusinessKey parseBusinessKey(String value) {
        int separator = value == null ? -1 : value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Workflow 业务键格式无效");
        }
        try {
            Long supplierId = Long.valueOf(value.substring(0, separator));
            Integer attempt = Integer.valueOf(value.substring(separator + 1));
            if (supplierId <= 0 || attempt <= 0) {
                throw new NumberFormatException("业务键必须为正数");
            }
            return new BusinessKey(supplierId, attempt);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Workflow 业务键格式无效", exception);
        }
    }

    private boolean isTerminal(String status) {
        return SupplierStatus.APPROVED.name().equals(status)
                || SupplierStatus.REJECTED.name().equals(status);
    }

    private void validate(WorkflowContracts.ProcessCompletedEvent event) {
        if (event == null
                || event.getEventId() == null || event.getEventId().isBlank()
                || !WORKFLOW_EVENT.equals(event.getEventType())
                || event.getTenantId() == null || event.getTenantId() <= 0
                || !WORKFLOW_PRODUCER.equals(event.getProducer())
                || !WorkflowContracts.BUSINESS_TYPE.equals(event.getBusinessType())
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

    private record BusinessKey(Long supplierId, Integer attempt) {
    }
}
