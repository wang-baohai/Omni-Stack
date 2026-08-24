package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.procurement.client.WorkflowInternalClient;
import com.omni.procurement.domain.MaterialDomainPolicy;
import com.omni.procurement.domain.RequisitionStateMachine;
import com.omni.procurement.dto.ProcViewAssembler;
import com.omni.procurement.dto.RequisitionRequests;
import com.omni.procurement.dto.RequisitionViews;
import com.omni.procurement.dto.WorkflowContracts;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.entity.ProcRequisitionLine;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcMaterialMapper;
import com.omni.procurement.mapper.ProcRequisitionLineMapper;
import com.omni.procurement.mapper.ProcRequisitionMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.ProcTenantInitializer;
import com.omni.procurement.service.RequisitionService;
import com.omni.procurement.service.RequisitionWorkflowStateService;
import com.omni.procurement.service.support.ProcAuditSupport;
import com.omni.procurement.service.support.ProcRecordAccessGuard;
import com.omni.procurement.workflow.RequisitionWorkflowCommand;
import com.omni.procurement.workflow.RequisitionWorkflowCoordinator;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 请购申请服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class RequisitionServiceImpl implements RequisitionService {

    private static final String BUSINESS_TYPE = "PROCUREMENT_REQUISITION";

    private final ProcTenantInitializer tenantInitializer;
    private final ProcRequisitionMapper requisitionMapper;
    private final ProcRequisitionLineMapper lineMapper;
    private final ProcMaterialMapper materialMapper;
    private final ProcMaterialCategoryMapper categoryMapper;
    private final ProcRecordAccessGuard accessGuard;
    private final RequisitionWorkflowStateService workflowStateService;
    private final RequisitionWorkflowCoordinator workflowCoordinator;
    private final WorkflowInternalClient workflowInternalClient;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<RequisitionViews.Summary> page(RequisitionRequests.Query query) {
        tenantInitializer.ensureInitialized();
        Long tenantId = ServiceIdentityContext.requireTenantId();
        LambdaQueryWrapper<ProcRequisition> wrapper = new LambdaQueryWrapper<ProcRequisition>()
                .eq(ProcRequisition::getTenantId, tenantId);
        String keyword = MaterialDomainPolicy.trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested.like(ProcRequisition::getRequisitionNo, keyword)
                    .or().like(ProcRequisition::getTitle, keyword));
        }
        if (MaterialDomainPolicy.trimToNull(query.getStatus()) != null) {
            wrapper.eq(ProcRequisition::getStatus, query.getStatus().trim().toUpperCase());
        }
        if (MaterialDomainPolicy.trimToNull(query.getCategoryCode()) != null) {
            wrapper.eq(ProcRequisition::getPrimaryCategoryCode,
                    MaterialDomainPolicy.normalizeCode(query.getCategoryCode(), "品类编码"));
        }
        wrapper.orderByDesc(ProcRequisition::getCreateTime).orderByDesc(ProcRequisition::getId);
        Page<ProcRequisition> page = requisitionMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        List<RequisitionViews.Summary> records = page.getRecords().stream()
                .map(ProcViewAssembler::requisitionSummary)
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public RequisitionViews.Detail get(Long id) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        return loadVisibleDetail(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    public RequisitionViews.ApprovalView approvalView(Long id, String taskId) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        Long userId = ServiceIdentityContext.require().userId();
        ProcRequisition identity = requisitionMapper.selectWorkflowIdentity(tenantId, id);
        if (identity == null) {
            throw new BusinessException(404, "请购申请不存在");
        }
        if (!RequisitionStateMachine.APPROVING.equals(identity.getStatus())
                || !RequisitionStateMachine.START_STARTED.equals(identity.getWorkflowStartStatus())
                || MaterialDomainPolicy.trimToNull(identity.getWorkflowBusinessKey()) == null
                || MaterialDomainPolicy.trimToNull(identity.getProcessInstanceId()) == null) {
            throw new BusinessException(409, "请购申请当前不处于有效审批中状态");
        }

        WorkflowContracts.AssignmentRequest request = new WorkflowContracts.AssignmentRequest();
        request.setTenantId(tenantId);
        request.setTaskId(taskId);
        request.setUserId(userId);
        request.setBusinessType(BUSINESS_TYPE);
        request.setBusinessKey(identity.getWorkflowBusinessKey());
        WorkflowContracts.AssignmentResponse assignment = validateAssignment(tenantId, request);
        if (!assignment.isValid()
                || !identity.getProcessInstanceId().equals(assignment.getProcessInstanceId())) {
            throw new BusinessException(403, "当前任务未分配给当前用户或不属于该请购");
        }

        ServiceDataScopeContext.ScopeInfo previous = ServiceDataScopeContext.get();
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                userId, tenantId, "procurement:requisition:approve", null, "TENANT", Set.of(), null));
        try {
            RequisitionViews.ApprovalView result = new RequisitionViews.ApprovalView();
            result.setTaskId(taskId);
            result.setRequisition(loadVisibleDetail(tenantId, id));
            return result;
        } finally {
            if (previous == null) {
                ServiceDataScopeContext.clear();
            } else {
                ServiceDataScopeContext.set(previous);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RequisitionViews.Detail create(RequisitionRequests.CreateRequest request) {
        tenantInitializer.ensureInitialized();
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ServiceDataScopeContext.ScopeInfo scope = ServiceDataScopeContext.require();
        if (scope.primaryUnitId() == null || scope.primaryUnitId() <= 0) {
            throw new BusinessException(403, "当前用户缺少有效的主组织");
        }
        PreparedLines prepared = prepareLines(tenantId, request.getLines());
        ProcRequisition requisition = new ProcRequisition();
        requisition.setTenantId(tenantId);
        requisition.setRequisitionNo("TMP-" + UUID.randomUUID());
        requisition.setTitle(requiredText(request.getTitle(), "请购标题"));
        requisition.setRequesterUserId(scope.userId());
        requisition.setRequesterUnitId(scope.primaryUnitId());
        requisition.setReason(MaterialDomainPolicy.trimToNull(request.getReason()));
        requisition.setPrimaryCategoryCode(prepared.categoryCode());
        requisition.setTotalAmount(prepared.totalAmount());
        requisition.setCurrencyCode(tenantInitializer.currencyCode());
        requisition.setStatus(RequisitionStateMachine.DRAFT);
        requisition.setApprovalAttempt(0);
        requisition.setWorkflowStartStatus(RequisitionStateMachine.START_NOT_STARTED);
        requisition.setOwnerUserId(scope.userId());
        requisition.setOwnerUnitId(scope.primaryUnitId());
        requisition.setVersion(0);
        requisition.setDeleted(0);
        ProcAuditSupport.created(requisition);
        requisitionMapper.insert(requisition);

        String requisitionNo = "PR-" + tenantId + "-" + requisition.getId();
        requisitionMapper.update(null, new LambdaUpdateWrapper<ProcRequisition>()
                .eq(ProcRequisition::getTenantId, tenantId)
                .eq(ProcRequisition::getId, requisition.getId())
                .eq(ProcRequisition::getDeleted, 0)
                .set(ProcRequisition::getRequisitionNo, requisitionNo));
        requisition.setRequisitionNo(requisitionNo);
        List<ProcRequisitionLine> lines = insertLines(tenantId, requisition.getId(), prepared.lines());
        return ProcViewAssembler.requisitionDetail(requisition, lines);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RequisitionViews.Detail update(Long id, RequisitionRequests.UpdateRequest request) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcRequisition current = requireLocked(tenantId, id);
        RequisitionStateMachine.requireEditable(current.getStatus());
        requireVersion(current, request.getVersion());
        PreparedLines prepared = prepareLines(tenantId, request.getLines());
        boolean rejected = RequisitionStateMachine.REJECTED.equals(current.getStatus());
        LambdaUpdateWrapper<ProcRequisition> update = versioned(current, request.getVersion())
                .set(ProcRequisition::getTitle, requiredText(request.getTitle(), "请购标题"))
                .set(ProcRequisition::getReason, MaterialDomainPolicy.trimToNull(request.getReason()))
                .set(ProcRequisition::getPrimaryCategoryCode, prepared.categoryCode())
                .set(ProcRequisition::getTotalAmount, prepared.totalAmount());
        if (rejected) {
            update.set(ProcRequisition::getStatus, RequisitionStateMachine.DRAFT)
                    .set(ProcRequisition::getWorkflowStartStatus, RequisitionStateMachine.START_NOT_STARTED)
                    .set(ProcRequisition::getWorkflowRequestId, null)
                    .set(ProcRequisition::getWorkflowBusinessKey, null)
                    .set(ProcRequisition::getWorkflowModelVersionId, null)
                    .set(ProcRequisition::getProcessInstanceId, null)
                    .set(ProcRequisition::getApprovedTime, null)
                    .set(ProcRequisition::getWorkflowCompletedTime, null);
        }
        audit(update);
        accessGuard.requireAffected(requisitionMapper.update(null, update), "请购申请已被其他请求修改");
        softDeleteLines(tenantId, id);
        insertLines(tenantId, id, prepared.lines());
        return loadVisibleDetail(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcRequisition current = requireLocked(tenantId, id);
        RequisitionStateMachine.requireDeletable(current.getStatus());
        requireVersion(current, version);
        softDeleteLines(tenantId, id);
        LambdaUpdateWrapper<ProcRequisition> update = versioned(current, version)
                .set(ProcRequisition::getDeleted, 1);
        audit(update);
        accessGuard.requireAffected(requisitionMapper.update(null, update), "请购申请已被其他请求修改");
    }

    /** {@inheritDoc} */
    @Override
    public RequisitionViews.Detail submit(Long id, Integer version) {
        RequisitionWorkflowCommand command = workflowStateService.prepareSubmit(id, version);
        workflowCoordinator.start(command);
        return loadVisibleDetail(command.tenantId(), id);
    }

    /** {@inheritDoc} */
    @Override
    public RequisitionViews.Detail retryStart(Long id, Integer version) {
        RequisitionWorkflowCommand command = workflowStateService.prepareRetry(id, version);
        workflowCoordinator.start(command);
        return loadVisibleDetail(command.tenantId(), id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RequisitionViews.Detail cancel(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        ProcRequisition current = requireLocked(tenantId, id);
        RequisitionStateMachine.requireCancellable(current.getStatus(), current.getWorkflowStartStatus());
        requireVersion(current, version);
        LambdaUpdateWrapper<ProcRequisition> update = versioned(current, version)
                .set(ProcRequisition::getStatus, RequisitionStateMachine.CANCELLED);
        audit(update);
        accessGuard.requireAffected(requisitionMapper.update(null, update), "请购申请已被其他请求修改");
        return loadVisibleDetail(tenantId, id);
    }

    private WorkflowContracts.AssignmentResponse validateAssignment(
            Long tenantId, WorkflowContracts.AssignmentRequest request) {
        try {
            R<WorkflowContracts.AssignmentResponse> response =
                    workflowInternalClient.validateAssignment(tenantId, request);
            if (response == null || response.getCode() != 200 || response.getData() == null) {
                throw new BusinessException(503, "Workflow 任务资格校验服务暂时不可用");
            }
            return response.getData();
        } catch (FeignException.Forbidden exception) {
            throw new BusinessException(403, "Workflow 拒绝任务资格校验");
        } catch (FeignException exception) {
            throw new BusinessException(503, "Workflow 任务资格校验服务暂时不可用");
        }
    }

    private RequisitionViews.Detail loadVisibleDetail(Long tenantId, Long id) {
        ProcRequisition requisition = accessGuard.requireVisible(
                requisitionMapper.selectOne(new LambdaQueryWrapper<ProcRequisition>()
                        .eq(ProcRequisition::getTenantId, tenantId)
                        .eq(ProcRequisition::getId, id)), "请购申请不存在");
        List<ProcRequisitionLine> lines = lineMapper.selectList(
                new LambdaQueryWrapper<ProcRequisitionLine>()
                        .eq(ProcRequisitionLine::getTenantId, tenantId)
                        .eq(ProcRequisitionLine::getRequisitionId, id)
                        .orderByAsc(ProcRequisitionLine::getLineNo));
        return ProcViewAssembler.requisitionDetail(requisition, lines);
    }

    private ProcRequisition requireLocked(Long tenantId, Long id) {
        return accessGuard.requireVisible(requisitionMapper.selectForUpdate(tenantId, id), "请购申请不存在");
    }

    private PreparedLines prepareLines(Long tenantId, List<RequisitionRequests.LineInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException(400, "请购明细不能为空");
        }
        LinkedHashSet<Long> materialIds = new LinkedHashSet<>();
        for (RequisitionRequests.LineInput input : inputs) {
            if (input == null || input.getMaterialId() == null || input.getMaterialId() <= 0) {
                throw new BusinessException(400, "请购物料 ID 必须为正整数");
            }
            if (!materialIds.add(input.getMaterialId())) {
                throw new BusinessException(400, "同一物料不能在请购明细中重复");
            }
        }
        Map<Long, ProcMaterial> materials = materialMapper.selectList(
                        new LambdaQueryWrapper<ProcMaterial>()
                                .eq(ProcMaterial::getTenantId, tenantId)
                                .in(ProcMaterial::getId, materialIds))
                .stream().collect(Collectors.toMap(ProcMaterial::getId, Function.identity()));
        if (materials.size() != materialIds.size()) {
            throw new BusinessException(404, "请购明细包含不存在的物料");
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
        Map<Integer, LineSnapshot> snapshots = new LinkedHashMap<>();
        int lineNo = 1;
        for (RequisitionRequests.LineInput input : inputs) {
            ProcMaterial material = materials.get(input.getMaterialId());
            MaterialDomainPolicy.requireActiveMaterial(material);
            ProcMaterialCategory category = categories.get(material.getCategoryId());
            MaterialDomainPolicy.requireActiveCategory(category);
            if (categoryCode == null) {
                categoryCode = category.getCategoryCode();
            } else if (!categoryCode.equals(category.getCategoryCode())) {
                throw new BusinessException(409, "单张请购的所有明细必须属于同一物料品类");
            }
            BigDecimal quantity = normalizeDecimal(input.getQuantity(), 6, false, "请购数量");
            BigDecimal unitPrice = normalizeDecimal(input.getEstimatedUnitPrice(), 6, true, "预估单价");
            BigDecimal lineTotal = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
            requireAmountShape(lineTotal, "预估行金额");
            total = total.add(lineTotal);
            requireAmountShape(total, "请购总金额");
            snapshots.put(lineNo, new LineSnapshot(lineNo, material, category.getCategoryCode(), quantity,
                    unitPrice, lineTotal, MaterialDomainPolicy.trimToNull(input.getRemark())));
            lineNo++;
        }
        return new PreparedLines(categoryCode, total.setScale(4), List.copyOf(snapshots.values()));
    }

    private List<ProcRequisitionLine> insertLines(Long tenantId, Long requisitionId,
                                                   Collection<LineSnapshot> snapshots) {
        return snapshots.stream().map(snapshot -> {
            ProcRequisitionLine line = new ProcRequisitionLine();
            line.setTenantId(tenantId);
            line.setRequisitionId(requisitionId);
            line.setLineNo(snapshot.lineNo());
            line.setMaterialId(snapshot.material().getId());
            line.setMaterialCode(snapshot.material().getMaterialCode());
            line.setMaterialName(snapshot.material().getMaterialName());
            line.setCategoryCode(snapshot.categoryCode());
            line.setUnit(snapshot.material().getUnit());
            line.setQuantity(snapshot.quantity());
            line.setEstimatedUnitPrice(snapshot.unitPrice());
            line.setEstimatedTotalPrice(snapshot.totalPrice());
            line.setRemark(snapshot.remark());
            line.setVersion(0);
            line.setDeleted(0);
            ProcAuditSupport.created(line);
            lineMapper.insert(line);
            return line;
        }).toList();
    }

    private void softDeleteLines(Long tenantId, Long requisitionId) {
        LambdaUpdateWrapper<ProcRequisitionLine> update = new LambdaUpdateWrapper<ProcRequisitionLine>()
                .eq(ProcRequisitionLine::getTenantId, tenantId)
                .eq(ProcRequisitionLine::getRequisitionId, requisitionId)
                .eq(ProcRequisitionLine::getDeleted, 0)
                .set(ProcRequisitionLine::getDeleted, 1)
                .setSql("version = version + 1")
                .set(ProcRequisitionLine::getUpdateTime, LocalDateTime.now())
                .set(ProcRequisitionLine::getUpdateBy, operator());
        lineMapper.update(null, update);
    }

    private LambdaUpdateWrapper<ProcRequisition> versioned(ProcRequisition current, Integer version) {
        return new LambdaUpdateWrapper<ProcRequisition>()
                .eq(ProcRequisition::getTenantId, current.getTenantId())
                .eq(ProcRequisition::getId, current.getId())
                .eq(ProcRequisition::getVersion, version)
                .eq(ProcRequisition::getStatus, current.getStatus())
                .eq(ProcRequisition::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private void audit(LambdaUpdateWrapper<ProcRequisition> update) {
        update.set(ProcRequisition::getUpdateTime, LocalDateTime.now())
                .set(ProcRequisition::getUpdateBy, operator());
    }

    private String operator() {
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        return identity.username() == null || identity.username().isBlank()
                ? String.valueOf(identity.userId()) : identity.username();
    }

    private void requireVersion(ProcRequisition current, Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "乐观锁版本不能为空且不能小于 0");
        }
        if (!version.equals(current.getVersion())) {
            throw new BusinessException(409, "请购申请已被其他请求修改");
        }
    }

    private BigDecimal normalizeDecimal(BigDecimal value, int scale, boolean zeroAllowed, String field) {
        if (value == null || (zeroAllowed ? value.signum() < 0 : value.signum() <= 0)) {
            throw new BusinessException(400, field + (zeroAllowed ? "不能小于 0" : "必须大于 0"));
        }
        int integerDigits = Math.max(value.precision() - value.scale(), 0);
        if (value.scale() > scale || integerDigits > 13) {
            throw new BusinessException(400, field + "必须符合 DECIMAL(19,6)");
        }
        return value.setScale(scale, RoundingMode.UNNECESSARY);
    }

    private void requireAmountShape(BigDecimal value, String field) {
        int integerDigits = Math.max(value.precision() - value.scale(), 0);
        if (value.scale() > 4 || integerDigits > 15) {
            throw new BusinessException(400, field + "必须符合 DECIMAL(19,4)");
        }
    }

    private String requiredText(String value, String field) {
        String normalized = MaterialDomainPolicy.trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, field + "不能为空");
        }
        return normalized;
    }

    private record PreparedLines(String categoryCode, BigDecimal totalAmount, List<LineSnapshot> lines) {
    }

    private record LineSnapshot(Integer lineNo, ProcMaterial material, String categoryCode,
                                BigDecimal quantity, BigDecimal unitPrice, BigDecimal totalPrice,
                                String remark) {
    }
}
