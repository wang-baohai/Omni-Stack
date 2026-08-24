package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.asset.domain.AssetOperationStateMachine;
import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.AssetDomainEvent;
import com.omni.asset.dto.AssetOperationRequests;
import com.omni.asset.dto.AssetOperationViewAssembler;
import com.omni.asset.dto.AssetOperationViews;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.entity.AstDisposal;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.mapper.AstDisposalMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.asset.service.AssetDisposalService;
import com.omni.asset.service.AssetOperationWorkflowStateService;
import com.omni.asset.service.support.AssetAuditSupport;
import com.omni.asset.service.support.AssetRecordAccessGuard;
import com.omni.asset.workflow.AssetWorkflowApprovalGuard;
import com.omni.asset.workflow.AssetWorkflowCommand;
import com.omni.asset.workflow.AssetWorkflowCoordinator;
import com.omni.asset.workflow.AssetWorkflowModelGuard;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资产处置服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class AssetDisposalServiceImpl implements AssetDisposalService {

    private static final String DOMAIN_BINDING = "asset-domain-out-0";
    private static final String PRODUCER = "omni-asset";

    private final AstDisposalMapper disposalMapper;
    private final AstAssetMapper assetMapper;
    private final AstAssetHistoryMapper historyMapper;
    private final AssetOperationWorkflowStateService workflowStateService;
    private final AssetWorkflowCoordinator workflowCoordinator;
    private final AssetWorkflowModelGuard workflowModelGuard;
    private final AssetWorkflowApprovalGuard workflowApprovalGuard;
    private final AssetRecordAccessGuard accessGuard;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<AssetOperationViews.DisposalVO> page(
            AssetOperationRequests.DisposalQuery query) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        LambdaQueryWrapper<AstDisposal> wrapper = new LambdaQueryWrapper<AstDisposal>()
                .eq(AstDisposal::getTenantId, tenantId);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(item -> item.like(AstDisposal::getDisposalNo, keyword)
                    .or().like(AstDisposal::getReason, keyword)
                    .or().apply("""
                            EXISTS (SELECT 1 FROM ast_asset asset_keyword
                                    WHERE asset_keyword.tenant_id = ast_disposal.tenant_id
                                      AND asset_keyword.id = ast_disposal.asset_id
                                      AND asset_keyword.deleted = 0
                                      AND (asset_keyword.asset_no LIKE CONCAT('%', {0}, '%')
                                           OR asset_keyword.name LIKE CONCAT('%', {0}, '%')))
                            """, keyword));
        }
        if (trimToNull(query.getDisposalType()) != null) {
            wrapper.eq(AstDisposal::getDisposalType, query.getDisposalType());
        }
        if (trimToNull(query.getStatus()) != null) {
            wrapper.eq(AstDisposal::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(AstDisposal::getCreateTime).orderByDesc(AstDisposal::getId);
        Page<AstDisposal> page = disposalMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        Map<Long, AstAsset> assets = assetsById(tenantId, page.getRecords().stream()
                .map(AstDisposal::getAssetId).collect(Collectors.toSet()));
        List<AssetOperationViews.DisposalVO> records = page.getRecords().stream()
                .map(item -> AssetOperationViewAssembler.disposal(item, assets.get(item.getAssetId())))
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AssetOperationViews.DisposalVO get(Long id) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        AstDisposal disposal = accessGuard.requireVisible(disposalMapper.selectOne(
                new LambdaQueryWrapper<AstDisposal>()
                        .eq(AstDisposal::getTenantId, tenantId)
                        .eq(AstDisposal::getId, id)), "处置申请不存在");
        AstAsset asset = accessGuard.requireVisible(assetMapper.selectOne(
                new LambdaQueryWrapper<AstAsset>()
                        .eq(AstAsset::getTenantId, tenantId)
                        .eq(AstAsset::getId, disposal.getAssetId())), "关联资产不存在");
        return AssetOperationViewAssembler.disposal(disposal, asset);
    }

    /** {@inheritDoc} */
    @Override
    public AssetOperationViews.DisposalVO approvalView(Long id, String taskId) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        Long userId = ServiceIdentityContext.require().userId();
        AstDisposal identity = disposalMapper.selectWorkflowIdentity(tenantId, id);
        requireApprovalIdentity(identity);
        workflowApprovalGuard.requireAssigned(new AssetWorkflowApprovalGuard.AssignmentIntent(
                tenantId, userId, taskId, AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE,
                identity.getWorkflowBusinessKey(), identity.getProcessInstanceId()));
        return withTenantScope(userId, tenantId, () -> get(id));
    }

    /** {@inheritDoc} */
    @Override
    public AssetOperationViews.DisposalVO create(
            AssetOperationRequests.CreateDisposalRequest request) {
        request.setModelVersionId(workflowModelGuard.resolveStartable(
                AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE));
        AssetWorkflowCommand command = workflowStateService.prepareDisposal(request);
        workflowCoordinator.start(command);
        return get(command.operationId());
    }

    /** {@inheritDoc} */
    @Override
    public AssetOperationViews.DisposalVO retryStart(Long id, Integer version) {
        AssetWorkflowCommand command =
                workflowStateService.prepareRetry(AssetOperationWorkflowStateServiceImpl.DISPOSAL,
                        id, version);
        workflowCoordinator.start(command);
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetOperationViews.DisposalVO cancel(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        AstDisposal disposal = requireLocked(tenantId, id);
        requireVersion(disposal.getVersion(), version);
        AssetOperationStateMachine.requireLocallyCancellable(
                disposal.getStatus(), disposal.getWorkflowStartStatus());
        AstAsset asset = requireOccupiedAsset(tenantId, disposal.getAssetId(), id);
        updateDisposalTerminal(disposal, version, AssetOperationStateMachine.CANCELLED, null);
        restoreAsset(asset, id, disposal.getPreviousAssetStatus(),
                "取消处置 " + disposal.getDisposalNo());
        disposal.setStatus(AssetOperationStateMachine.CANCELLED);
        disposal.setActiveFlag(0);
        disposal.setVersion(version + 1);
        return AssetOperationViewAssembler.disposal(disposal, asset);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetOperationViews.DisposalVO complete(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        AstDisposal disposal = requireLocked(tenantId, id);
        requireVersion(disposal.getVersion(), version);
        AssetOperationStateMachine.requireCompletable(disposal.getStatus());
        AstAsset asset = requireOccupiedAsset(tenantId, disposal.getAssetId(), id);
        String targetStatus = "SCRAP".equals(disposal.getDisposalType())
                ? AssetStateMachine.SCRAPPED : AssetStateMachine.DISPOSED;
        LocalDateTime now = LocalDateTime.now();
        int assetAffected = assetMapper.update(null, new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, tenantId)
                .eq(AstAsset::getId, asset.getId())
                .eq(AstAsset::getVersion, asset.getVersion())
                .eq(AstAsset::getStatus, AssetStateMachine.DISPOSAL_PENDING)
                .eq(AstAsset::getActiveOperationType,
                        AssetOperationWorkflowStateServiceImpl.DISPOSAL)
                .eq(AstAsset::getActiveOperationId, id)
                .eq(AstAsset::getDeleted, 0)
                .set(AstAsset::getStatus, targetStatus)
                .set(AstAsset::getActiveOperationType, null)
                .set(AstAsset::getActiveOperationId, null)
                .set(AstAsset::getUpdateTime, now)
                .set(AstAsset::getUpdateBy, AssetAuditSupport.operator())
                .setSql("version = version + 1"));
        accessGuard.requireAffected(assetAffected, "资产处置占位已发生变化");
        updateDisposalTerminal(disposal, version, AssetOperationStateMachine.COMPLETED, now);
        appendHistory(asset, AssetStateMachine.DISPOSAL_PENDING, targetStatus,
                "完成处置 " + disposal.getDisposalNo());
        asset.setStatus(targetStatus);
        asset.setActiveOperationType(null);
        asset.setActiveOperationId(null);
        asset.setVersion(asset.getVersion() + 1);
        disposal.setStatus(AssetOperationStateMachine.COMPLETED);
        disposal.setActiveFlag(0);
        disposal.setCompletedTime(now);
        disposal.setVersion(version + 1);
        publishCompleted(disposal, targetStatus, now);
        return AssetOperationViewAssembler.disposal(disposal, asset);
    }

    private void updateDisposalTerminal(AstDisposal disposal, Integer version,
                                        String targetStatus, LocalDateTime completedTime) {
        LambdaUpdateWrapper<AstDisposal> update = new LambdaUpdateWrapper<AstDisposal>()
                .eq(AstDisposal::getTenantId, disposal.getTenantId())
                .eq(AstDisposal::getId, disposal.getId())
                .eq(AstDisposal::getVersion, version)
                .eq(AstDisposal::getStatus, disposal.getStatus())
                .eq(AstDisposal::getActiveFlag, 1)
                .eq(AstDisposal::getDeleted, 0)
                .set(AstDisposal::getStatus, targetStatus)
                .set(AstDisposal::getActiveFlag, 0)
                .set(AstDisposal::getCompletedTime, completedTime)
                .set(AstDisposal::getUpdateTime, LocalDateTime.now())
                .set(AstDisposal::getUpdateBy, AssetAuditSupport.operator())
                .setSql("version = version + 1");
        accessGuard.requireAffected(disposalMapper.update(null, update), "处置申请已被其他请求修改");
    }

    private void restoreAsset(AstAsset asset, Long operationId, String previousStatus, String remark) {
        AssetStateMachine.requireTransition(AssetStateMachine.DISPOSAL_PENDING, previousStatus);
        int affected = assetMapper.update(null, new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, asset.getTenantId())
                .eq(AstAsset::getId, asset.getId())
                .eq(AstAsset::getVersion, asset.getVersion())
                .eq(AstAsset::getStatus, AssetStateMachine.DISPOSAL_PENDING)
                .eq(AstAsset::getActiveOperationType,
                        AssetOperationWorkflowStateServiceImpl.DISPOSAL)
                .eq(AstAsset::getActiveOperationId, operationId)
                .eq(AstAsset::getDeleted, 0)
                .set(AstAsset::getStatus, previousStatus)
                .set(AstAsset::getActiveOperationType, null)
                .set(AstAsset::getActiveOperationId, null)
                .set(AstAsset::getUpdateTime, LocalDateTime.now())
                .set(AstAsset::getUpdateBy, AssetAuditSupport.operator())
                .setSql("version = version + 1"));
        accessGuard.requireAffected(affected, "资产处置占位已发生变化");
        appendHistory(asset, AssetStateMachine.DISPOSAL_PENDING, previousStatus, remark);
        asset.setStatus(previousStatus);
        asset.setActiveOperationType(null);
        asset.setActiveOperationId(null);
        asset.setVersion(asset.getVersion() + 1);
    }

    private AstDisposal requireLocked(Long tenantId, Long id) {
        return accessGuard.requireVisible(disposalMapper.selectForUpdate(tenantId, id), "处置申请不存在");
    }

    private AstAsset requireOccupiedAsset(Long tenantId, Long assetId, Long operationId) {
        AstAsset asset = accessGuard.requireVisible(
                assetMapper.selectForUpdate(tenantId, assetId), "关联资产不存在");
        if (!AssetOperationWorkflowStateServiceImpl.DISPOSAL.equals(asset.getActiveOperationType())
                || !operationId.equals(asset.getActiveOperationId())
                || !AssetStateMachine.DISPOSAL_PENDING.equals(asset.getStatus())) {
            throw new BusinessException(409, "资产处置占位与申请不一致");
        }
        return asset;
    }

    private void requireApprovalIdentity(AstDisposal identity) {
        if (identity == null) {
            throw new BusinessException(404, "处置申请不存在");
        }
        if (!AssetOperationStateMachine.PENDING_APPROVAL.equals(identity.getStatus())
                || !AssetOperationStateMachine.START_STARTED.equals(identity.getWorkflowStartStatus())
                || trimToNull(identity.getWorkflowBusinessKey()) == null
                || trimToNull(identity.getProcessInstanceId()) == null) {
            throw new BusinessException(409, "处置申请当前不处于有效审批中状态");
        }
    }

    private <T> T withTenantScope(Long userId, Long tenantId,
                                  java.util.function.Supplier<T> action) {
        ServiceDataScopeContext.ScopeInfo previous = ServiceDataScopeContext.get();
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                userId, tenantId, "asset:disposal:approve", null, "TENANT", Set.of(), null));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                ServiceDataScopeContext.clear();
            } else {
                ServiceDataScopeContext.set(previous);
            }
        }
    }

    private Map<Long, AstAsset> assetsById(Long tenantId, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return assetMapper.selectList(new LambdaQueryWrapper<AstAsset>()
                        .eq(AstAsset::getTenantId, tenantId)
                        .in(AstAsset::getId, ids))
                .stream().collect(Collectors.toMap(AstAsset::getId, Function.identity()));
    }

    private void appendHistory(AstAsset asset, String fromStatus, String toStatus, String remark) {
        AstAssetHistory history = new AstAssetHistory();
        history.setTenantId(asset.getTenantId());
        history.setAssetId(asset.getId());
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedByUserId(ServiceIdentityContext.require().userId());
        history.setChangedTime(LocalDateTime.now());
        history.setRemark(remark);
        AssetAuditSupport.created(history);
        accessGuard.requireAffected(historyMapper.insert(history), "记录资产处置历史失败");
    }

    private void publishCompleted(AstDisposal disposal, String targetAssetStatus,
                                  LocalDateTime occurredAt) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("disposalId", disposal.getId());
        payload.put("disposalNo", disposal.getDisposalNo());
        payload.put("assetId", disposal.getAssetId());
        payload.put("disposalType", disposal.getDisposalType());
        payload.put("assetStatus", targetAssetStatus);
        payload.put("residualValue", disposal.getResidualValue() == null
                ? null : disposal.getResidualValue().toPlainString());
        payload.put("processInstanceId", disposal.getProcessInstanceId());
        String eventType = AssetStateMachine.SCRAPPED.equals(targetAssetStatus)
                ? "asset.scrapped.v1" : "asset.disposed.v1";
        AssetDomainEvent event = AssetDomainEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .occurredAt(occurredAt)
                .tenantId(disposal.getTenantId())
                .producer(PRODUCER)
                .payload(payload)
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, event, disposal.getTenantId(), eventId);
    }

    private void requireVersion(Integer actual, Integer expected) {
        if (expected == null || expected < 0 || !Objects.equals(actual, expected)) {
            throw new BusinessException(409, "处置申请已被其他请求修改，请刷新后重试");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
