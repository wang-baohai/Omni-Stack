package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.RfqStateMachine;
import com.omni.procurement.dto.RfqViews;
import com.omni.procurement.entity.ProcRfq;
import com.omni.procurement.entity.ProcRfqLine;
import com.omni.procurement.entity.ProcRfqSupplier;
import com.omni.procurement.mapper.ProcRfqLineMapper;
import com.omni.procurement.mapper.ProcRfqMapper;
import com.omni.procurement.mapper.ProcRfqSupplierMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.InternalRfqInvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * SRM 询价邀请内部只读服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class InternalRfqInvitationServiceImpl implements InternalRfqInvitationService {

    private final ProcRfqMapper rfqMapper;
    private final ProcRfqLineMapper lineMapper;
    private final ProcRfqSupplierMapper supplierMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<RfqViews.InternalInvitationSummary> list(Long tenantId, Long supplierId) {
        requirePositive(tenantId, "租户 ID");
        requirePositive(supplierId, "供应商 ID");
        return runAsInternalTenant(tenantId, () -> doList(tenantId, supplierId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public RfqViews.InternalInvitationDetail get(Long tenantId, Long rfqId, Long supplierId) {
        requirePositive(tenantId, "租户 ID");
        requirePositive(rfqId, "询价单 ID");
        requirePositive(supplierId, "供应商 ID");
        return runAsInternalTenant(tenantId, () -> doGet(tenantId, rfqId, supplierId));
    }

    private List<RfqViews.InternalInvitationSummary> doList(Long tenantId, Long supplierId) {
        List<ProcRfqSupplier> invitations = supplierMapper.selectList(
                new LambdaQueryWrapper<ProcRfqSupplier>()
                        .eq(ProcRfqSupplier::getTenantId, tenantId)
                        .eq(ProcRfqSupplier::getSupplierId, supplierId)
                        .in(ProcRfqSupplier::getStatus,
                                RfqStateMachine.INVITED,
                                RfqStateMachine.QUOTED,
                                RfqStateMachine.AWARDED,
                                RfqStateMachine.REJECTED,
                                RfqStateMachine.EXPIRED)
                        .isNotNull(ProcRfqSupplier::getInvitedTime));
        if (invitations.isEmpty()) {
            return List.of();
        }
        Set<Long> rfqIds = invitations.stream().map(ProcRfqSupplier::getRfqId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, ProcRfq> rfqs = new LinkedHashMap<>();
        for (ProcRfq rfq : rfqMapper.selectList(new LambdaQueryWrapper<ProcRfq>()
                .eq(ProcRfq::getTenantId, tenantId)
                .in(ProcRfq::getId, rfqIds)
                .in(ProcRfq::getStatus,
                        RfqStateMachine.SENT,
                        RfqStateMachine.CLOSED,
                        RfqStateMachine.AWARDED,
                        RfqStateMachine.CANCELLED)
                .isNotNull(ProcRfq::getSentTime))) {
            rfqs.put(rfq.getId(), rfq);
        }
        List<RfqViews.InternalInvitationSummary> result = new ArrayList<>();
        for (ProcRfqSupplier invitation : invitations) {
            ProcRfq rfq = rfqs.get(invitation.getRfqId());
            if (rfq != null && isPortalVisible(
                    tenantId, invitation.getRfqId(), supplierId, rfq, invitation)) {
                result.add(toSummary(rfq, invitation));
            }
        }
        result.sort(Comparator.comparing(RfqViews.InternalInvitationSummary::getInvitedTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(result);
    }

    private RfqViews.InternalInvitationDetail doGet(Long tenantId, Long rfqId, Long supplierId) {
        ProcRfq rfq = rfqMapper.selectOne(new LambdaQueryWrapper<ProcRfq>()
                .eq(ProcRfq::getTenantId, tenantId)
                .eq(ProcRfq::getId, rfqId)
                .in(ProcRfq::getStatus,
                        RfqStateMachine.SENT,
                        RfqStateMachine.CLOSED,
                        RfqStateMachine.AWARDED,
                        RfqStateMachine.CANCELLED)
                .isNotNull(ProcRfq::getSentTime));
        ProcRfqSupplier invitation = supplierMapper.selectOne(
                new LambdaQueryWrapper<ProcRfqSupplier>()
                        .eq(ProcRfqSupplier::getTenantId, tenantId)
                        .eq(ProcRfqSupplier::getRfqId, rfqId)
                        .eq(ProcRfqSupplier::getSupplierId, supplierId)
                        .in(ProcRfqSupplier::getStatus,
                                RfqStateMachine.INVITED,
                                RfqStateMachine.QUOTED,
                                RfqStateMachine.AWARDED,
                                RfqStateMachine.REJECTED,
                                RfqStateMachine.EXPIRED)
                        .isNotNull(ProcRfqSupplier::getInvitedTime));
        if (rfq == null || invitation == null
                || !isPortalVisible(tenantId, rfqId, supplierId, rfq, invitation)) {
            throw new BusinessException(404, "询价邀请不存在");
        }
        List<ProcRfqLine> lines = lineMapper.selectList(new LambdaQueryWrapper<ProcRfqLine>()
                .eq(ProcRfqLine::getTenantId, tenantId)
                .eq(ProcRfqLine::getRfqId, rfqId)
                .orderByAsc(ProcRfqLine::getLineNo));
        if (lines.isEmpty()) {
            throw new BusinessException(404, "询价邀请不存在");
        }
        RfqViews.InternalInvitationDetail detail = new RfqViews.InternalInvitationDetail();
        copySummary(rfq, invitation, detail);
        detail.setLines(lines.stream().map(this::toLine).toList());
        return detail;
    }

    private boolean isPortalVisible(Long tenantId, Long rfqId, Long supplierId,
                                    ProcRfq rfq, ProcRfqSupplier invitation) {
        return Objects.equals(tenantId, rfq.getTenantId())
                && Objects.equals(tenantId, invitation.getTenantId())
                && Objects.equals(rfqId, rfq.getId())
                && Objects.equals(rfqId, invitation.getRfqId())
                && Objects.equals(supplierId, invitation.getSupplierId())
                && Objects.equals(rfq.getTenantId(), invitation.getTenantId())
                && Objects.equals(rfq.getId(), invitation.getRfqId())
                && RfqStateMachine.isPortalVisibleRfq(rfq.getStatus())
                && RfqStateMachine.isPortalVisibleInvitation(invitation.getStatus())
                && rfq.getSentTime() != null
                && invitation.getInvitedTime() != null
                && rfq.getQuotationDeadline() != null;
    }

    private RfqViews.InternalInvitationSummary toSummary(
            ProcRfq rfq, ProcRfqSupplier invitation) {
        RfqViews.InternalInvitationSummary result = new RfqViews.InternalInvitationSummary();
        copySummary(rfq, invitation, result);
        return result;
    }

    private void copySummary(ProcRfq rfq, ProcRfqSupplier invitation,
                             RfqViews.InternalInvitationSummary target) {
        target.setTenantId(rfq.getTenantId());
        target.setRfqId(rfq.getId());
        target.setRfqNo(rfq.getRfqNo());
        target.setTitle(rfq.getTitle());
        target.setStatus(rfq.getStatus());
        target.setInvitationStatus(invitation.getStatus());
        target.setSupplierId(invitation.getSupplierId());
        target.setQuotationDeadline(rfq.getQuotationDeadline());
        target.setCurrencyCode(rfq.getCurrencyCode());
        target.setInvitedTime(invitation.getInvitedTime());
    }

    private RfqViews.InternalInvitationLine toLine(ProcRfqLine source) {
        RfqViews.InternalInvitationLine target = new RfqViews.InternalInvitationLine();
        target.setRfqLineId(source.getId());
        target.setMaterialCode(source.getMaterialCode());
        target.setMaterialName(source.getMaterialName());
        target.setUnit(source.getUnit());
        target.setQuantity(source.getQuantity());
        target.setRemark(source.getRemark());
        return target;
    }

    private <T> T runAsInternalTenant(Long tenantId, Supplier<T> action) {
        try {
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    0L, tenantId, "internal-srm"));
            ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                    0L, tenantId, "INTERNAL", null, "TENANT", Collections.emptySet(), null));
            return action.get();
        } finally {
            ServiceDataScopeContext.clear();
            ServiceIdentityContext.clear();
        }
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new BusinessException(400, field + "必须为正整数");
        }
    }
}
