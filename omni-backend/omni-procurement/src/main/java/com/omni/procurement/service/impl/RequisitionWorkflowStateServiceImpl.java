package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.ApprovalRouteResolver;
import com.omni.procurement.domain.MaterialDomainPolicy;
import com.omni.procurement.domain.RequisitionStateMachine;
import com.omni.procurement.dto.RequisitionDomainEvent;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.entity.ProcRequisitionLine;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcMaterialMapper;
import com.omni.procurement.mapper.ProcRequisitionLineMapper;
import com.omni.procurement.mapper.ProcRequisitionMapper;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.service.RequisitionWorkflowStateService;
import com.omni.procurement.service.support.ProcRecordAccessGuard;
import com.omni.procurement.workflow.RequisitionWorkflowCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 请购 Workflow 启动本地事务状态服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequisitionWorkflowStateServiceImpl implements RequisitionWorkflowStateService {

    private static final String SUBMITTED_EVENT = "procurement.requisition.submitted.v1";
    private static final String DOMAIN_BINDING = "procurement-domain-out-0";

    private final ProcRequisitionMapper requisitionMapper;
    private final ProcRequisitionLineMapper lineMapper;
    private final ProcMaterialMapper materialMapper;
    private final ProcMaterialCategoryMapper categoryMapper;
    private final ApprovalRouteResolver routeResolver;
    private final ProcRecordAccessGuard accessGuard;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RequisitionWorkflowCommand prepareSubmit(Long requisitionId, Integer version) {
        Long tenantId = ProcTenantContext.requireTenantId();
        ProcRequisition requisition = requireLocked(tenantId, requisitionId);
        RequisitionStateMachine.requireSubmittable(requisition.getStatus());
        requireVersion(requisition, version);
        SubmissionFacts facts = recomputeSubmissionFacts(tenantId, requisitionId);
        ProcApprovalRoute route = routeResolver.resolve(facts.categoryCode(), facts.totalAmount());
        int attempt = Math.addExact(requisition.getApprovalAttempt() == null
                ? 0 : requisition.getApprovalAttempt(), 1);
        String requestId = UUID.randomUUID().toString();
        String businessKey = requisitionId + ":" + attempt;
        LambdaUpdateWrapper<ProcRequisition> update = currentSnapshot(requisition, version)
                .set(ProcRequisition::getPrimaryCategoryCode, facts.categoryCode())
                .set(ProcRequisition::getTotalAmount, facts.totalAmount())
                .set(ProcRequisition::getStatus, RequisitionStateMachine.SUBMITTED)
                .set(ProcRequisition::getApprovalAttempt, attempt)
                .set(ProcRequisition::getWorkflowRequestId, requestId)
                .set(ProcRequisition::getWorkflowBusinessKey, businessKey)
                .set(ProcRequisition::getWorkflowModelVersionId, route.getModelVersionId())
                .set(ProcRequisition::getProcessInstanceId, null)
                .set(ProcRequisition::getWorkflowStartStatus, RequisitionStateMachine.START_PENDING)
                .set(ProcRequisition::getApprovedTime, null)
                .set(ProcRequisition::getWorkflowCompletedTime, null);
        audit(update);
        accessGuard.requireAffected(requisitionMapper.update(null, update), "请购申请已被其他请求修改");

        RequisitionWorkflowCommand command = new RequisitionWorkflowCommand(
                requisitionId, tenantId, requisition.getRequisitionNo(), requisition.getTitle(),
                requisition.getRequesterUserId(), requisition.getRequesterUnitId(), facts.categoryCode(),
                facts.totalAmount(), requisition.getCurrencyCode(), attempt, requestId, businessKey,
                route.getModelVersionId());
        publishSubmitted(command);
        return command;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RequisitionWorkflowCommand prepareRetry(Long requisitionId, Integer version) {
        Long tenantId = ProcTenantContext.requireTenantId();
        ProcRequisition requisition = requireLocked(tenantId, requisitionId);
        RequisitionStateMachine.requireStartRetryable(
                requisition.getStatus(), requisition.getWorkflowStartStatus());
        requireVersion(requisition, version);
        requireWorkflowSnapshot(requisition);
        LambdaUpdateWrapper<ProcRequisition> update = currentSnapshot(requisition, version)
                .eq(ProcRequisition::getWorkflowStartStatus, RequisitionStateMachine.START_FAILED)
                .set(ProcRequisition::getWorkflowStartStatus, RequisitionStateMachine.START_PENDING);
        audit(update);
        accessGuard.requireAffected(requisitionMapper.update(null, update), "请购申请已被其他请求修改");
        return commandOf(requisition);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStarted(RequisitionWorkflowCommand command, String processInstanceId) {
        if (MaterialDomainPolicy.trimToNull(processInstanceId) == null || processInstanceId.length() > 64) {
            throw new BusinessException(503, "Workflow 返回了无效的流程实例 ID");
        }
        LambdaUpdateWrapper<ProcRequisition> update = matchingPending(command)
                .set(ProcRequisition::getProcessInstanceId, processInstanceId)
                .set(ProcRequisition::getWorkflowStartStatus, RequisitionStateMachine.START_STARTED)
                .set(ProcRequisition::getStatus, RequisitionStateMachine.APPROVING)
                .setSql("version = version + 1");
        audit(update);
        accessGuard.requireAffected(requisitionMapper.update(null, update),
                "请购审批启动状态已变化，请刷新后重试");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(RequisitionWorkflowCommand command) {
        LambdaUpdateWrapper<ProcRequisition> update = matchingPending(command)
                .set(ProcRequisition::getWorkflowStartStatus, RequisitionStateMachine.START_FAILED)
                .setSql("version = version + 1");
        audit(update);
        int affected = requisitionMapper.update(null, update);
        if (affected == 0) {
            log.warn("请购 Workflow 启动失败状态未命中当前快照: tenantId={}, requisitionId={}, businessKey={}",
                    command.tenantId(), command.requisitionId(), command.businessKey());
        }
    }

    private SubmissionFacts recomputeSubmissionFacts(Long tenantId, Long requisitionId) {
        List<ProcRequisitionLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<ProcRequisitionLine>()
                        .eq(ProcRequisitionLine::getTenantId, tenantId)
                        .eq(ProcRequisitionLine::getRequisitionId, requisitionId)
                        .orderByAsc(ProcRequisitionLine::getLineNo));
        if (lines.isEmpty()) {
            throw new BusinessException(409, "请购明细不能为空");
        }
        Set<Long> materialIds = new LinkedHashSet<>();
        for (ProcRequisitionLine line : lines) {
            if (line.getMaterialId() == null || !materialIds.add(line.getMaterialId())) {
                throw new BusinessException(409, "请购明细包含无效或重复物料");
            }
        }
        Map<Long, ProcMaterial> materials = materialMapper.selectList(
                        new LambdaQueryWrapper<ProcMaterial>()
                                .eq(ProcMaterial::getTenantId, tenantId)
                                .in(ProcMaterial::getId, materialIds))
                .stream().collect(Collectors.toMap(ProcMaterial::getId, Function.identity()));
        if (materials.size() != materialIds.size()) {
            throw new BusinessException(404, "请购明细包含已不存在的物料");
        }
        Set<Long> categoryIds = materials.values().stream()
                .map(ProcMaterial::getCategoryId).collect(Collectors.toSet());
        Map<Long, ProcMaterialCategory> categories = categoryMapper.selectList(
                        new LambdaQueryWrapper<ProcMaterialCategory>()
                                .eq(ProcMaterialCategory::getTenantId, tenantId)
                                .in(ProcMaterialCategory::getId, categoryIds))
                .stream().collect(Collectors.toMap(ProcMaterialCategory::getId, Function.identity()));
        String categoryCode = null;
        BigDecimal total = new BigDecimal("0.0000");
        for (ProcRequisitionLine line : lines) {
            ProcMaterial material = materials.get(line.getMaterialId());
            MaterialDomainPolicy.requireActiveMaterial(material);
            ProcMaterialCategory category = categories.get(material.getCategoryId());
            MaterialDomainPolicy.requireActiveCategory(category);
            String lineCategory = category.getCategoryCode();
            if (categoryCode == null) {
                categoryCode = lineCategory;
            } else if (!categoryCode.equals(lineCategory)) {
                throw new BusinessException(409, "单张请购的所有明细必须属于同一物料品类");
            }
            if (line.getQuantity() == null || line.getQuantity().signum() <= 0
                    || line.getEstimatedUnitPrice() == null || line.getEstimatedUnitPrice().signum() < 0) {
                throw new BusinessException(409, "请购明细数量或预估单价无效");
            }
            BigDecimal lineTotal = line.getQuantity().multiply(line.getEstimatedUnitPrice())
                    .setScale(4, RoundingMode.HALF_UP);
            requireAmountShape(lineTotal, "预估行金额");
            total = total.add(lineTotal);
            requireAmountShape(total, "请购总金额");
            refreshLineSnapshot(
                    new RequisitionLineTarget(tenantId, requisitionId, line),
                    material, lineCategory, lineTotal);
        }
        return new SubmissionFacts(categoryCode, total.setScale(4));
    }

    private void refreshLineSnapshot(
            RequisitionLineTarget target,
            ProcMaterial material,
            String categoryCode,
            BigDecimal lineTotal) {
        ProcRequisitionLine line = target.line();
        LambdaUpdateWrapper<ProcRequisitionLine> update = new LambdaUpdateWrapper<ProcRequisitionLine>()
                .eq(ProcRequisitionLine::getTenantId, target.tenantId())
                .eq(ProcRequisitionLine::getId, line.getId())
                .eq(ProcRequisitionLine::getRequisitionId, target.requisitionId())
                .eq(ProcRequisitionLine::getDeleted, 0)
                .set(ProcRequisitionLine::getMaterialCode, material.getMaterialCode())
                .set(ProcRequisitionLine::getMaterialName, material.getMaterialName())
                .set(ProcRequisitionLine::getCategoryCode, categoryCode)
                .set(ProcRequisitionLine::getUnit, material.getUnit())
                .set(ProcRequisitionLine::getEstimatedTotalPrice, lineTotal)
                .set(ProcRequisitionLine::getUpdateTime, LocalDateTime.now())
                .set(ProcRequisitionLine::getUpdateBy, operator())
                .setSql("version = version + 1");
        if (lineMapper.update(null, update) != 1) {
            throw new BusinessException(409, "请购明细已被其他请求修改");
        }
    }

    private void publishSubmitted(RequisitionWorkflowCommand command) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requisitionId", command.requisitionId());
        payload.put("requisitionNo", command.requisitionNo());
        payload.put("status", RequisitionStateMachine.SUBMITTED);
        payload.put("approvalAttempt", command.approvalAttempt());
        payload.put("businessKey", command.businessKey());
        payload.put("materialCategory", command.categoryCode());
        payload.put("totalAmount", command.totalAmount().setScale(4).toPlainString());
        payload.put("currencyCode", command.currencyCode());
        RequisitionDomainEvent event = RequisitionDomainEvent.builder()
                .eventId(eventId)
                .eventType(SUBMITTED_EVENT)
                .occurredAt(LocalDateTime.now())
                .tenantId(command.tenantId())
                .payload(payload)
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, event, command.tenantId(), eventId);
    }

    private ProcRequisition requireLocked(Long tenantId, Long requisitionId) {
        return accessGuard.requireVisible(
                requisitionMapper.selectForUpdate(tenantId, requisitionId), "请购申请不存在");
    }

    private RequisitionWorkflowCommand commandOf(ProcRequisition requisition) {
        return new RequisitionWorkflowCommand(requisition.getId(), requisition.getTenantId(),
                requisition.getRequisitionNo(), requisition.getTitle(), requisition.getRequesterUserId(),
                requisition.getRequesterUnitId(), requisition.getPrimaryCategoryCode(),
                requisition.getTotalAmount(), requisition.getCurrencyCode(), requisition.getApprovalAttempt(),
                requisition.getWorkflowRequestId(), requisition.getWorkflowBusinessKey(),
                requisition.getWorkflowModelVersionId());
    }

    private void requireWorkflowSnapshot(ProcRequisition requisition) {
        if (requisition.getApprovalAttempt() == null || requisition.getApprovalAttempt() <= 0
                || MaterialDomainPolicy.trimToNull(requisition.getWorkflowRequestId()) == null
                || MaterialDomainPolicy.trimToNull(requisition.getWorkflowBusinessKey()) == null
                || requisition.getWorkflowModelVersionId() == null
                || requisition.getWorkflowModelVersionId() <= 0) {
            throw new BusinessException(409, "请购申请缺少可重试的 Workflow 幂等快照");
        }
    }

    private LambdaUpdateWrapper<ProcRequisition> currentSnapshot(ProcRequisition requisition,
                                                                  Integer version) {
        return new LambdaUpdateWrapper<ProcRequisition>()
                .eq(ProcRequisition::getTenantId, requisition.getTenantId())
                .eq(ProcRequisition::getId, requisition.getId())
                .eq(ProcRequisition::getVersion, version)
                .eq(ProcRequisition::getStatus, requisition.getStatus())
                .eq(ProcRequisition::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private LambdaUpdateWrapper<ProcRequisition> matchingPending(RequisitionWorkflowCommand command) {
        return new LambdaUpdateWrapper<ProcRequisition>()
                .eq(ProcRequisition::getTenantId, command.tenantId())
                .eq(ProcRequisition::getId, command.requisitionId())
                .eq(ProcRequisition::getStatus, RequisitionStateMachine.SUBMITTED)
                .eq(ProcRequisition::getWorkflowStartStatus, RequisitionStateMachine.START_PENDING)
                .eq(ProcRequisition::getApprovalAttempt, command.approvalAttempt())
                .eq(ProcRequisition::getWorkflowRequestId, command.requestId())
                .eq(ProcRequisition::getWorkflowBusinessKey, command.businessKey())
                .eq(ProcRequisition::getWorkflowModelVersionId, command.modelVersionId())
                .eq(ProcRequisition::getDeleted, 0);
    }

    private void audit(LambdaUpdateWrapper<ProcRequisition> update) {
        update.set(ProcRequisition::getUpdateTime, LocalDateTime.now())
                .set(ProcRequisition::getUpdateBy, operator());
    }

    private String operator() {
        ProcTenantContext.RequestIdentity identity = ProcTenantContext.require();
        return identity.username() == null || identity.username().isBlank()
                ? String.valueOf(identity.userId()) : identity.username();
    }

    private void requireVersion(ProcRequisition requisition, Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "乐观锁版本不能为空且不能小于 0");
        }
        if (!version.equals(requisition.getVersion())) {
            throw new BusinessException(409, "请购申请已被其他请求修改");
        }
    }

    private void requireAmountShape(BigDecimal value, String field) {
        int integerDigits = Math.max(value.precision() - value.scale(), 0);
        if (value.scale() > 4 || integerDigits > 15) {
            throw new BusinessException(409, field + "超过 DECIMAL(19,4) 范围");
        }
    }

    private record SubmissionFacts(String categoryCode, BigDecimal totalAmount) {
    }

    /**
     * 请购明细刷新目标。
     */
    private record RequisitionLineTarget(
            Long tenantId,
            Long requisitionId,
            ProcRequisitionLine line) {
    }
}
