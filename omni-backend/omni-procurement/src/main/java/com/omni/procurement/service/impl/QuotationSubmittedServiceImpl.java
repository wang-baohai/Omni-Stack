package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.RetryableQuotationEventException;
import com.omni.procurement.domain.RfqStateMachine;
import com.omni.procurement.dto.RfqContracts;
import com.omni.procurement.entity.ProcEventInbox;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqSupplier;
import com.omni.procurement.mapper.ProcEventInboxMapper;
import com.omni.procurement.mapper.ProcRfqMapper;
import com.omni.procurement.mapper.ProcRfqSupplierMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.procurement.service.QuotationSubmittedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRM 报价提交事件 Inbox 处理服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuotationSubmittedServiceImpl implements QuotationSubmittedService {

    private static final String EVENT_TYPE = "srm.quotation.submitted.v1";
    private static final String PRODUCER = "omni-srm";
    private static final String AGGREGATE_TYPE = "QUOTATION";
    private static final String QUOTATION_STATUS = "SUBMITTED";
    private static final String RECEIVED = "RECEIVED";
    private static final String PROCESSED = "PROCESSED";
    private static final String IGNORED = "IGNORED";

    private final ProcEventInboxMapper inboxMapper;
    private final ProcRfqMapper rfqMapper;
    private final ProcRfqSupplierMapper supplierMapper;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean handle(RfqContracts.QuotationSubmittedEvent event) {
        validateEnvelope(event);
        Long tenantId = ServiceIdentityContext.requireTenantId();
        if (!event.getTenantId().equals(tenantId)) {
            throw new BusinessException(403, "报价事件与当前租户上下文不一致");
        }
        ProcEventInbox inbox = registerAndLock(event);
        validateDuplicateIntent(inbox, event);
        if (PROCESSED.equals(inbox.getStatus()) || IGNORED.equals(inbox.getStatus())) {
            return false;
        }

        RfqContracts.QuotationSubmittedPayload payload = event.getPayload();
        ProcRfq rfq = rfqMapper.selectForUpdate(tenantId, payload.getRfqId());
        if (rfq == null) {
            throw new RetryableQuotationEventException("报价事件对应询价单尚未落库，请稍后重试");
        }
        ProcRfqSupplier invitation = supplierMapper.selectForUpdate(
                tenantId, payload.getRfqId(), payload.getSupplierId());
        if (invitation == null) {
            throw new RetryableQuotationEventException("报价事件对应供应商邀请尚未落库，请稍后重试");
        }
        if (!tenantId.equals(rfq.getTenantId())
                || !tenantId.equals(invitation.getTenantId())
                || !payload.getRfqId().equals(rfq.getId())
                || !payload.getRfqId().equals(invitation.getRfqId())
                || !payload.getSupplierId().equals(invitation.getSupplierId())
                || !payload.getRfqNo().equals(rfq.getRfqNo())
                || !payload.getCurrencyCode().equals(rfq.getCurrencyCode())
                || rfq.getQuotationDeadline() == null) {
            throw new BusinessException(409, "报价事件与询价业务快照不一致");
        }
        if (payload.getValidUntil().isBefore(rfq.getQuotationDeadline())) {
            throw new BusinessException(409, "报价有效期早于询价截止时间");
        }
        if (RfqStateMachine.DRAFT.equals(rfq.getStatus())) {
            throw new RetryableQuotationEventException("报价事件早于询价发送确认，请稍后重试");
        }
        if (!RfqStateMachine.SENT.equals(rfq.getStatus())
                || !RfqStateMachine.isActiveInvitation(invitation.getStatus())) {
            markInbox(inbox, IGNORED);
            return false;
        }

        Integer currentQuotationVersion = invitation.getQuotationVersion();
        if (currentQuotationVersion != null
                && payload.getQuotationVersion() <= currentQuotationVersion) {
            markInbox(inbox, IGNORED);
            return false;
        }
        if (invitation.getQuotationId() != null
                && !invitation.getQuotationId().equals(payload.getQuotationId())) {
            throw new BusinessException(409, "同一供应商邀请不能绑定不同报价聚合");
        }

        LambdaUpdateWrapper<ProcRfqSupplier> update = new LambdaUpdateWrapper<ProcRfqSupplier>()
                .eq(ProcRfqSupplier::getTenantId, tenantId)
                .eq(ProcRfqSupplier::getId, invitation.getId())
                .eq(ProcRfqSupplier::getRfqId, payload.getRfqId())
                .eq(ProcRfqSupplier::getSupplierId, payload.getSupplierId())
                .in(ProcRfqSupplier::getStatus,
                        RfqStateMachine.INVITED, RfqStateMachine.QUOTED)
                .eq(ProcRfqSupplier::getDeleted, 0)
                .set(ProcRfqSupplier::getStatus, RfqStateMachine.QUOTED)
                .set(ProcRfqSupplier::getQuotationId, payload.getQuotationId())
                .set(ProcRfqSupplier::getQuotationVersion, payload.getQuotationVersion())
                .set(ProcRfqSupplier::getQuotationRequestId, payload.getRequestId())
                .set(ProcRfqSupplier::getQuotationTime, event.getOccurredAt())
                .set(ProcRfqSupplier::getUpdateTime, LocalDateTime.now())
                .set(ProcRfqSupplier::getUpdateBy, "srm-quotation-event")
                .setSql("version = version + 1");
        if (currentQuotationVersion == null) {
            update.isNull(ProcRfqSupplier::getQuotationVersion);
        } else {
            update.eq(ProcRfqSupplier::getQuotationVersion, currentQuotationVersion);
        }
        if (supplierMapper.update(null, update) != 1) {
            throw new RetryableQuotationEventException("报价邀请状态并发变化，请稍后重试");
        }
        markInbox(inbox, PROCESSED);
        return true;
    }

    private ProcEventInbox registerAndLock(RfqContracts.QuotationSubmittedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        ProcEventInbox candidate = new ProcEventInbox();
        candidate.setTenantId(event.getTenantId());
        candidate.setEventId(event.getEventId());
        candidate.setEventType(event.getEventType());
        candidate.setSourceService(event.getProducer());
        candidate.setAggregateType(event.getAggregateType());
        candidate.setAggregateId(String.valueOf(event.getAggregateId()));
        candidate.setPayload(toJson(event));
        candidate.setStatus(RECEIVED);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        inboxMapper.insertIgnore(candidate);
        ProcEventInbox inbox = inboxMapper.selectForUpdate(event.getTenantId(), event.getEventId());
        if (inbox == null) {
            throw new RetryableQuotationEventException("无法锁定报价事件 Inbox");
        }
        return inbox;
    }

    private void validateDuplicateIntent(ProcEventInbox inbox,
                                         RfqContracts.QuotationSubmittedEvent event) {
        if (!event.getEventType().equals(inbox.getEventType())
                || !event.getProducer().equals(inbox.getSourceService())
                || !event.getAggregateType().equals(inbox.getAggregateType())
                || !String.valueOf(event.getAggregateId()).equals(inbox.getAggregateId())
                || !jsonEquals(toJson(event), inbox.getPayload())) {
            throw new BusinessException(409, "同一报价事件 ID 绑定了不同业务意图");
        }
    }

    /**
     * JSON 语义等价对比。MySQL JSON 列读回时会对键重排序并插入空白，
     * 直接字符串对比会把同一事件误判为不同意图，必须按 JsonNode 语义比较。
     *
     * @param left  当前事件序列化结果
     * @param right Inbox 读回的载荷
     * @return 语义等价返回 true
     */
    private boolean jsonEquals(String left, String right) {
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "报价事件载荷解析失败");
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
            throw new RetryableQuotationEventException("更新报价事件 Inbox 失败");
        }
    }

    private void validateEnvelope(RfqContracts.QuotationSubmittedEvent event) {
        if (event == null
                || invalidText(event.getEventId(), 64)
                || !EVENT_TYPE.equals(event.getEventType())
                || event.getOccurredAt() == null
                || event.getTenantId() == null || event.getTenantId() <= 0
                || !PRODUCER.equals(event.getProducer())
                || !AGGREGATE_TYPE.equals(event.getAggregateType())
                || event.getAggregateId() == null || event.getAggregateId() <= 0
                || event.getAggregateVersion() == null || event.getAggregateVersion() <= 0
                || event.getPayload() == null) {
            throw new IllegalArgumentException("SRM 报价提交事件缺少必需信封字段或契约不匹配");
        }
        RfqContracts.QuotationSubmittedPayload payload = event.getPayload();
        if (invalidText(payload.getRequestId(), 64)
                || payload.getQuotationId() == null || payload.getQuotationId() <= 0
                || payload.getQuotationVersion() == null || payload.getQuotationVersion() <= 0
                || payload.getRfqId() == null || payload.getRfqId() <= 0
                || invalidText(payload.getRfqNo(), 64)
                || payload.getSupplierId() == null || payload.getSupplierId() <= 0
                || !QUOTATION_STATUS.equals(payload.getStatus())
                || payload.getTotalAmount() == null
                || payload.getTotalAmount().compareTo(BigDecimal.ZERO) < 0
                || invalidText(payload.getCurrencyCode(), 3)
                || !payload.getCurrencyCode().matches("[A-Z]{3}")
                || payload.getValidUntil() == null
                || !payload.getQuotationId().equals(event.getAggregateId())
                || !payload.getQuotationVersion().equals(event.getAggregateVersion())
                || !payload.getRequestId().equals(event.getCorrelationId())) {
            throw new IllegalArgumentException("SRM 报价提交事件业务载荷无效");
        }
    }

    private boolean invalidText(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private String toJson(RfqContracts.QuotationSubmittedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("SRM 报价提交事件无法序列化", exception);
        }
    }
}
