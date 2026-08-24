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
import com.omni.asset.entity.AstTransfer;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.mapper.AstTransferMapper;
import com.omni.common.service.datascope.ServiceDataScopeContext;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.asset.service.AssetOperationWorkflowStateService;
import com.omni.asset.service.AssetTransferService;
import com.omni.asset.service.support.AssetAuditSupport;
import com.omni.asset.service.support.AssetIdentityGuard;
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
 * 资产调拨服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class AssetTransferServiceImpl implements AssetTransferService {

    private static final String DOMAIN_BINDING = "asset-domain-out-0";
    private static final String PRODUCER = "omni-asset";

    private final AstTransferMapper transferMapper;
    private final AstAssetMapper assetMapper;
    private final AstAssetHistoryMapper historyMapper;
    private final AssetOperationWorkflowStateService workflowStateService;
    private final AssetWorkflowCoordinator workflowCoordinator;
    private final AssetWorkflowModelGuard workflowModelGuard;
    private final AssetWorkflowApprovalGuard workflowApprovalGuard;
    private final AssetIdentityGuard identityGuard;
    private final AssetRecordAccessGuard accessGuard;
    private final ReliableMessageRelay reliableMessageRelay;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<AssetOperationViews.TransferVO> page(
            AssetOperationRequests.TransferQuery query) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        LambdaQueryWrapper<AstTransfer> wrapper = new LambdaQueryWrapper<AstTransfer>()
                .eq(AstTransfer::getTenantId, tenantId);
        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(item -> item.like(AstTransfer::getTransferNo, keyword)
                    .or().like(AstTransfer::getReason, keyword)
                    .or().apply("""
                            EXISTS (SELECT 1 FROM ast_asset asset_keyword
                                    WHERE asset_keyword.tenant_id = ast_transfer.tenant_id
                                      AND asset_keyword.id = ast_transfer.asset_id
                                      AND asset_keyword.deleted = 0
                                      AND (asset_keyword.asset_no LIKE CONCAT('%', {0}, '%')
                                           OR asset_keyword.name LIKE CONCAT('%', {0}, '%')))
                            """, keyword));
        }
        if (trimToNull(query.getStatus()) != null) {
            wrapper.eq(AstTransfer::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(AstTransfer::getCreateTime).orderByDesc(AstTransfer::getId);
        Page<AstTransfer> page = transferMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);
        Map<Long, AstAsset> assets = assetsById(tenantId, page.getRecords().stream()
                .map(AstTransfer::getAssetId).collect(Collectors.toSet()));
        List<AssetOperationViews.TransferVO> records = page.getRecords().stream()
                .map(item -> AssetOperationViewAssembler.transfer(item, assets.get(item.getAssetId())))
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AssetOperationViews.TransferVO get(Long id) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        AstTransfer transfer = accessGuard.requireVisible(transferMapper.selectOne(
                new LambdaQueryWrapper<AstTransfer>()
                        .eq(AstTransfer::getTenantId, tenantId)
                        .eq(AstTransfer::getId, id)), "调拨申请不存在");
        AstAsset asset = accessGuard.requireVisible(assetMapper.selectOne(
                new LambdaQueryWrapper<AstAsset>()
                        .eq(AstAsset::getTenantId, tenantId)
                        .eq(AstAsset::getId, transfer.getAssetId())), "关联资产不存在");
        return AssetOperationViewAssembler.transfer(transfer, asset);
    }

    /** {@inheritDoc} */
    @Override
    public AssetOperationViews.TransferVO approvalView(Long id, String taskId) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        Long userId = ServiceIdentityContext.require().userId();
        AstTransfer identity = transferMapper.selectWorkflowIdentity(tenantId, id);
        requireApprovalIdentity(identity);
        workflowApprovalGuard.requireAssigned(new AssetWorkflowApprovalGuard.AssignmentIntent(
                tenantId, userId, taskId, AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE,
                identity.getWorkflowBusinessKey(), identity.getProcessInstanceId()));
        return withTenantScope(userId, tenantId, () -> get(id));
    }

    /** {@inheritDoc} */
    @Override
    public AssetOperationViews.TransferVO create(
            AssetOperationRequests.CreateTransferRequest request) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        accessGuard.requireUnitWritable(request.getToUnitId());
        identityGuard.requireActiveUserInUnit(tenantId, request.getToUserId(), request.getToUnitId());
        request.setModelVersionId(workflowModelGuard.resolveStartable(
                AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE));
        AssetWorkflowCommand command = workflowStateService.prepareTransfer(request);
        workflowCoordinator.start(command);
        return get(command.operationId());
    }

    /** {@inheritDoc} */
    @Override
    public AssetOperationViews.TransferVO retryStart(Long id, Integer version) {
        AssetWorkflowCommand command =
                workflowStateService.prepareRetry(AssetOperationWorkflowStateServiceImpl.TRANSFER,
                        id, version);
        workflowCoordinator.start(command);
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetOperationViews.TransferVO cancel(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        AstTransfer transfer = requireLocked(tenantId, id);
        requireVersion(transfer.getVersion(), version);
        AssetOperationStateMachine.requireLocallyCancellable(
                transfer.getStatus(), transfer.getWorkflowStartStatus());
        AstAsset asset = requireOccupiedAsset(tenantId, transfer.getAssetId(), id);
        updateTransferTerminal(transfer, version, AssetOperationStateMachine.CANCELLED, null);
        restoreAsset(asset, id, transfer.getPreviousAssetStatus(), "取消调拨 " + transfer.getTransferNo());
        transfer.setStatus(AssetOperationStateMachine.CANCELLED);
        transfer.setActiveFlag(0);
        transfer.setVersion(version + 1);
        return AssetOperationViewAssembler.transfer(transfer, asset);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetOperationViews.TransferVO complete(Long id, Integer version) {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        AstTransfer transfer = requireLocked(tenantId, id);
        requireVersion(transfer.getVersion(), version);
        AssetOperationStateMachine.requireCompletable(transfer.getStatus());
        accessGuard.requireUnitWritable(transfer.getToUnitId());
        identityGuard.requireActiveUserInUnit(
                tenantId, transfer.getToUserId(), transfer.getToUnitId());
        AstAsset asset = requireOccupiedAsset(tenantId, transfer.getAssetId(), id);
        LocalDateTime now = LocalDateTime.now();
        int assetAffected = assetMapper.update(null, new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, tenantId)
                .eq(AstAsset::getId, asset.getId())
                .eq(AstAsset::getVersion, asset.getVersion())
                .eq(AstAsset::getStatus, AssetStateMachine.TRANSFER)
                .eq(AstAsset::getActiveOperationType,
                        AssetOperationWorkflowStateServiceImpl.TRANSFER)
                .eq(AstAsset::getActiveOperationId, id)
                .eq(AstAsset::getDeleted, 0)
                .set(AstAsset::getStatus, AssetStateMachine.IN_USE)
                .set(AstAsset::getCurrentUserId, transfer.getToUserId())
                .set(AstAsset::getCurrentUnitId, transfer.getToUnitId())
                .set(AstAsset::getAllocatedTime, now)
                .set(AstAsset::getActiveOperationType, null)
                .set(AstAsset::getActiveOperationId, null)
                .set(AstAsset::getLocationCode,
                        transfer.getToLocation() == null ? asset.getLocationCode() : transfer.getToLocation())
                .set(AstAsset::getUpdateTime, now)
                .set(AstAsset::getUpdateBy, AssetAuditSupport.operator())
                .setSql("version = version + 1"));
        accessGuard.requireAffected(assetAffected, "资产调拨占位已发生变化");
        updateTransferTerminal(transfer, version, AssetOperationStateMachine.COMPLETED, now);
        appendHistory(asset, AssetStateMachine.TRANSFER, AssetStateMachine.IN_USE,
                "完成调拨 " + transfer.getTransferNo());
        asset.setStatus(AssetStateMachine.IN_USE);
        asset.setCurrentUserId(transfer.getToUserId());
        asset.setCurrentUnitId(transfer.getToUnitId());
        asset.setLocationCode(transfer.getToLocation() == null
                ? asset.getLocationCode() : transfer.getToLocation());
        asset.setActiveOperationType(null);
        asset.setActiveOperationId(null);
        asset.setVersion(asset.getVersion() + 1);
        transfer.setStatus(AssetOperationStateMachine.COMPLETED);
        transfer.setActiveFlag(0);
        transfer.setCompletedTime(now);
        transfer.setVersion(version + 1);
        publishCompleted(transfer, now);
        return AssetOperationViewAssembler.transfer(transfer, asset);
    }

    private void updateTransferTerminal(AstTransfer transfer, Integer version,
                                        String targetStatus, LocalDateTime completedTime) {
        LambdaUpdateWrapper<AstTransfer> update = new LambdaUpdateWrapper<AstTransfer>()
                .eq(AstTransfer::getTenantId, transfer.getTenantId())
                .eq(AstTransfer::getId, transfer.getId())
                .eq(AstTransfer::getVersion, version)
                .eq(AstTransfer::getStatus, transfer.getStatus())
                .eq(AstTransfer::getActiveFlag, 1)
                .eq(AstTransfer::getDeleted, 0)
                .set(AstTransfer::getStatus, targetStatus)
                .set(AstTransfer::getActiveFlag, 0)
                .set(AstTransfer::getCompletedTime, completedTime)
                .set(AstTransfer::getUpdateTime, LocalDateTime.now())
                .set(AstTransfer::getUpdateBy, AssetAuditSupport.operator())
                .setSql("version = version + 1");
        accessGuard.requireAffected(transferMapper.update(null, update), "调拨申请已被其他请求修改");
    }

    private void restoreAsset(AstAsset asset, Long operationId, String previousStatus, String remark) {
        AssetStateMachine.requireTransition(AssetStateMachine.TRANSFER, previousStatus);
        int affected = assetMapper.update(null, new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, asset.getTenantId())
                .eq(AstAsset::getId, asset.getId())
                .eq(AstAsset::getVersion, asset.getVersion())
                .eq(AstAsset::getStatus, AssetStateMachine.TRANSFER)
                .eq(AstAsset::getActiveOperationType,
                        AssetOperationWorkflowStateServiceImpl.TRANSFER)
                .eq(AstAsset::getActiveOperationId, operationId)
                .eq(AstAsset::getDeleted, 0)
                .set(AstAsset::getStatus, previousStatus)
                .set(AstAsset::getActiveOperationType, null)
                .set(AstAsset::getActiveOperationId, null)
                .set(AstAsset::getUpdateTime, LocalDateTime.now())
                .set(AstAsset::getUpdateBy, AssetAuditSupport.operator())
                .setSql("version = version + 1"));
        accessGuard.requireAffected(affected, "资产调拨占位已发生变化");
        appendHistory(asset, AssetStateMachine.TRANSFER, previousStatus, remark);
        asset.setStatus(previousStatus);
        asset.setActiveOperationType(null);
        asset.setActiveOperationId(null);
        asset.setVersion(asset.getVersion() + 1);
    }

    private AstTransfer requireLocked(Long tenantId, Long id) {
        return accessGuard.requireVisible(transferMapper.selectForUpdate(tenantId, id), "调拨申请不存在");
    }

    private AstAsset requireOccupiedAsset(Long tenantId, Long assetId, Long operationId) {
        AstAsset asset = accessGuard.requireVisible(
                assetMapper.selectForUpdate(tenantId, assetId), "关联资产不存在");
        if (!AssetOperationWorkflowStateServiceImpl.TRANSFER.equals(asset.getActiveOperationType())
                || !operationId.equals(asset.getActiveOperationId())
                || !AssetStateMachine.TRANSFER.equals(asset.getStatus())) {
            throw new BusinessException(409, "资产调拨占位与申请不一致");
        }
        return asset;
    }

    private void requireApprovalIdentity(AstTransfer identity) {
        if (identity == null) {
            throw new BusinessException(404, "调拨申请不存在");
        }
        if (!AssetOperationStateMachine.PENDING_APPROVAL.equals(identity.getStatus())
                || !AssetOperationStateMachine.START_STARTED.equals(identity.getWorkflowStartStatus())
                || trimToNull(identity.getWorkflowBusinessKey()) == null
                || trimToNull(identity.getProcessInstanceId()) == null) {
            throw new BusinessException(409, "调拨申请当前不处于有效审批中状态");
        }
    }

    private <T> T withTenantScope(Long userId, Long tenantId,
                                  java.util.function.Supplier<T> action) {
        ServiceDataScopeContext.ScopeInfo previous = ServiceDataScopeContext.get();
        ServiceDataScopeContext.set(new ServiceDataScopeContext.ScopeInfo(
                userId, tenantId, "asset:transfer:approve", null, "TENANT", Set.of(), null));
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
        accessGuard.requireAffected(historyMapper.insert(history), "记录资产调拨历史失败");
    }

    private void publishCompleted(AstTransfer transfer, LocalDateTime occurredAt) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferId", transfer.getId());
        payload.put("transferNo", transfer.getTransferNo());
        payload.put("assetId", transfer.getAssetId());
        payload.put("toUserId", transfer.getToUserId());
        payload.put("toUnitId", transfer.getToUnitId());
        payload.put("status", AssetOperationStateMachine.COMPLETED);
        payload.put("processInstanceId", transfer.getProcessInstanceId());
        AssetDomainEvent event = AssetDomainEvent.builder()
                .eventId(eventId)
                .eventType("asset.transfer.completed.v1")
                .occurredAt(occurredAt)
                .tenantId(transfer.getTenantId())
                .producer(PRODUCER)
                .payload(payload)
                .build();
        reliableMessageRelay.send(DOMAIN_BINDING, event, transfer.getTenantId(), eventId);
    }

    private void requireVersion(Integer actual, Integer expected) {
        if (expected == null || expected < 0 || !Objects.equals(actual, expected)) {
            throw new BusinessException(409, "调拨申请已被其他请求修改，请刷新后重试");
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
