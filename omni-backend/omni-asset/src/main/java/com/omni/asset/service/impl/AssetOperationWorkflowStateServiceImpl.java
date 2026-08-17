package com.omni.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.asset.domain.AssetOperationStateMachine;
import com.omni.asset.domain.AssetStateMachine;
import com.omni.asset.dto.AssetOperationRequests;
import com.omni.asset.entity.AstAsset;
import com.omni.asset.entity.AstAssetHistory;
import com.omni.asset.entity.AstDisposal;
import com.omni.asset.entity.AstTransfer;
import com.omni.asset.mapper.AstAssetHistoryMapper;
import com.omni.asset.mapper.AstAssetMapper;
import com.omni.asset.mapper.AstDisposalMapper;
import com.omni.asset.mapper.AstTransferMapper;
import com.omni.asset.security.AssetTenantContext;
import com.omni.asset.service.AssetOperationWorkflowStateService;
import com.omni.asset.service.support.AssetAuditSupport;
import com.omni.asset.service.support.AssetRecordAccessGuard;
import com.omni.asset.workflow.AssetWorkflowCommand;
import com.omni.asset.workflow.AssetWorkflowCoordinator;
import com.omni.common.core.result.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 资产操作 Workflow 本地状态服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetOperationWorkflowStateServiceImpl
        implements AssetOperationWorkflowStateService {

    /** 调拨活动类型。 */
    public static final String TRANSFER = "TRANSFER";
    /** 处置活动类型。 */
    public static final String DISPOSAL = "DISPOSAL";

    private final AstAssetMapper assetMapper;
    private final AstAssetHistoryMapper historyMapper;
    private final AstTransferMapper transferMapper;
    private final AstDisposalMapper disposalMapper;
    private final AssetRecordAccessGuard accessGuard;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetWorkflowCommand prepareTransfer(AssetOperationRequests.CreateTransferRequest request) {
        AssetTenantContext.RequestIdentity identity = AssetTenantContext.require();
        Long tenantId = identity.tenantId();
        AstAsset asset = requireAsset(tenantId, request.getAssetId());
        requireAvailable(asset);
        AssetOperationStateMachine.requireOperationSource(asset.getStatus());
        String requestId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        AstTransfer transfer = new AstTransfer();
        transfer.setTenantId(tenantId);
        transfer.setTransferNo("TMP-" + requestId);
        transfer.setAssetId(asset.getId());
        transfer.setFromUserId(asset.getCurrentUserId());
        transfer.setFromUnitId(asset.getCurrentUnitId());
        transfer.setToUserId(request.getToUserId());
        transfer.setToUnitId(request.getToUnitId());
        transfer.setFromLocation(asset.getLocationCode());
        transfer.setToLocation(trimToNull(request.getToLocation()));
        transfer.setReason(requiredText(request.getReason(), "调拨原因"));
        transfer.setStatus(AssetOperationStateMachine.PENDING_APPROVAL);
        transfer.setPreviousAssetStatus(asset.getStatus());
        transfer.setActiveFlag(1);
        transfer.setModelVersionId(request.getModelVersionId());
        transfer.setWorkflowRequestId(requestId);
        String pendingBusinessKey = "PENDING-" + requestId;
        transfer.setWorkflowBusinessKey(pendingBusinessKey);
        transfer.setWorkflowStartUserId(identity.userId());
        transfer.setWorkflowStartUserName(trimToNull(identity.username()));
        transfer.setWorkflowStartStatus(AssetOperationStateMachine.START_PENDING);
        transfer.setVersion(0);
        transfer.setDeleted(0);
        AssetAuditSupport.created(transfer);
        if (transferMapper.insert(transfer) != 1) {
            throw new BusinessException(409, "创建调拨申请失败");
        }
        String businessKey = String.valueOf(transfer.getId());
        String transferNo = "AT-" + tenantId + "-" + transfer.getId();
        if (transferMapper.setTransferNoAfterInsert(tenantId, transfer.getId(), transferNo) != 1
                || transferMapper.update(null, new LambdaUpdateWrapper<AstTransfer>()
                .eq(AstTransfer::getTenantId, tenantId)
                .eq(AstTransfer::getId, transfer.getId())
                .eq(AstTransfer::getWorkflowBusinessKey, pendingBusinessKey)
                .eq(AstTransfer::getDeleted, 0)
                .set(AstTransfer::getWorkflowBusinessKey, businessKey)) != 1) {
            throw new BusinessException(409, "生成调拨申请幂等标识失败");
        }
        occupy(asset, transfer.getId(), TRANSFER, AssetStateMachine.TRANSFER);
        appendHistory(asset, AssetStateMachine.TRANSFER, "发起调拨 " + transferNo);
        transfer.setTransferNo(transferNo);
        transfer.setWorkflowBusinessKey(businessKey);
        return transferCommand(transfer, asset);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetWorkflowCommand prepareDisposal(AssetOperationRequests.CreateDisposalRequest request) {
        AssetTenantContext.RequestIdentity identity = AssetTenantContext.require();
        Long tenantId = identity.tenantId();
        AstAsset asset = requireAsset(tenantId, request.getAssetId());
        requireAvailable(asset);
        AssetOperationStateMachine.requireOperationSource(asset.getStatus());
        BigDecimal residualValue = normalizeResidualValue(request.getResidualValue());
        String requestId = UUID.randomUUID().toString();

        AstDisposal disposal = new AstDisposal();
        disposal.setTenantId(tenantId);
        disposal.setDisposalNo("TMP-" + requestId);
        disposal.setAssetId(asset.getId());
        disposal.setDisposalType(request.getDisposalType());
        disposal.setReason(requiredText(request.getReason(), "处置原因"));
        disposal.setPreviousAssetStatus(asset.getStatus());
        disposal.setResidualValue(residualValue);
        disposal.setDisposalMethod(trimToNull(request.getDisposalMethod()));
        disposal.setStatus(AssetOperationStateMachine.PENDING_APPROVAL);
        disposal.setActiveFlag(1);
        disposal.setModelVersionId(request.getModelVersionId());
        disposal.setWorkflowRequestId(requestId);
        String pendingBusinessKey = "PENDING-" + requestId;
        disposal.setWorkflowBusinessKey(pendingBusinessKey);
        disposal.setWorkflowStartUserId(identity.userId());
        disposal.setWorkflowStartUserName(trimToNull(identity.username()));
        disposal.setWorkflowStartStatus(AssetOperationStateMachine.START_PENDING);
        disposal.setVersion(0);
        disposal.setDeleted(0);
        AssetAuditSupport.created(disposal);
        if (disposalMapper.insert(disposal) != 1) {
            throw new BusinessException(409, "创建处置申请失败");
        }
        String businessKey = String.valueOf(disposal.getId());
        String disposalNo = "AD-" + tenantId + "-" + disposal.getId();
        if (disposalMapper.setDisposalNoAfterInsert(tenantId, disposal.getId(), disposalNo) != 1
                || disposalMapper.update(null, new LambdaUpdateWrapper<AstDisposal>()
                .eq(AstDisposal::getTenantId, tenantId)
                .eq(AstDisposal::getId, disposal.getId())
                .eq(AstDisposal::getWorkflowBusinessKey, pendingBusinessKey)
                .eq(AstDisposal::getDeleted, 0)
                .set(AstDisposal::getWorkflowBusinessKey, businessKey)) != 1) {
            throw new BusinessException(409, "生成处置申请幂等标识失败");
        }
        occupy(asset, disposal.getId(), DISPOSAL, AssetStateMachine.DISPOSAL_PENDING);
        appendHistory(asset, AssetStateMachine.DISPOSAL_PENDING, "发起处置 " + disposalNo);
        disposal.setDisposalNo(disposalNo);
        disposal.setWorkflowBusinessKey(businessKey);
        return disposalCommand(disposal, asset);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssetWorkflowCommand prepareRetry(String operationType, Long id, Integer version) {
        return switch (operationType) {
            case TRANSFER -> prepareTransferRetry(id, version);
            case DISPOSAL -> prepareDisposalRetry(id, version);
            default -> throw new IllegalArgumentException("不支持的资产操作类型");
        };
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStarted(AssetWorkflowCommand command, String processInstanceId) {
        requireCommandTenant(command);
        if (trimToNull(processInstanceId) == null || processInstanceId.length() > 64) {
            throw new BusinessException(503, "Workflow 返回了无效的流程实例 ID");
        }
        int affected = switch (command.operationType()) {
            case TRANSFER -> transferMapper.update(null, matchingPendingTransfer(command)
                    .set(AstTransfer::getProcessInstanceId, processInstanceId)
                    .set(AstTransfer::getWorkflowStartStatus, AssetOperationStateMachine.START_STARTED)
                    .set(AstTransfer::getUpdateTime, LocalDateTime.now())
                    .set(AstTransfer::getUpdateBy, AssetAuditSupport.operator())
                    .setSql("version = version + 1"));
            case DISPOSAL -> disposalMapper.update(null, matchingPendingDisposal(command)
                    .set(AstDisposal::getProcessInstanceId, processInstanceId)
                    .set(AstDisposal::getWorkflowStartStatus, AssetOperationStateMachine.START_STARTED)
                    .set(AstDisposal::getUpdateTime, LocalDateTime.now())
                    .set(AstDisposal::getUpdateBy, AssetAuditSupport.operator())
                    .setSql("version = version + 1"));
            default -> throw new IllegalArgumentException("不支持的资产操作类型");
        };
        accessGuard.requireAffected(affected, "资产审批启动状态已变化，请刷新后重试");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(AssetWorkflowCommand command) {
        requireCommandTenant(command);
        int affected = switch (command.operationType()) {
            case TRANSFER -> transferMapper.update(null, matchingPendingTransfer(command)
                    .set(AstTransfer::getStatus, AssetOperationStateMachine.START_FAILED)
                    .set(AstTransfer::getWorkflowStartStatus,
                            AssetOperationStateMachine.START_FAILED_FLAG)
                    .set(AstTransfer::getUpdateTime, LocalDateTime.now())
                    .set(AstTransfer::getUpdateBy, AssetAuditSupport.operator())
                    .setSql("version = version + 1"));
            case DISPOSAL -> disposalMapper.update(null, matchingPendingDisposal(command)
                    .set(AstDisposal::getStatus, AssetOperationStateMachine.START_FAILED)
                    .set(AstDisposal::getWorkflowStartStatus,
                            AssetOperationStateMachine.START_FAILED_FLAG)
                    .set(AstDisposal::getUpdateTime, LocalDateTime.now())
                    .set(AstDisposal::getUpdateBy, AssetAuditSupport.operator())
                    .setSql("version = version + 1"));
            default -> throw new IllegalArgumentException("不支持的资产操作类型");
        };
        if (affected == 0) {
            log.warn("Asset Workflow 启动失败状态未命中当前快照: tenantId={}, operationType={}, operationId={}",
                    command.tenantId(), command.operationType(), command.operationId());
        }
    }

    private AssetWorkflowCommand prepareTransferRetry(Long id, Integer version) {
        Long tenantId = AssetTenantContext.requireTenantId();
        AstTransfer transfer = accessGuard.requireVisible(
                transferMapper.selectForUpdate(tenantId, id), "调拨申请不存在");
        AssetOperationStateMachine.requireRetryable(
                transfer.getStatus(), transfer.getWorkflowStartStatus());
        requireVersion(transfer.getVersion(), version);
        requireSnapshot(transfer.getWorkflowRequestId(), transfer.getWorkflowBusinessKey(),
                transfer.getModelVersionId(), transfer.getWorkflowStartUserId());
        AstAsset asset = requireOccupiedAsset(tenantId, transfer.getAssetId(), TRANSFER, id);
        if (AssetOperationStateMachine.START_FAILED.equals(transfer.getStatus())) {
            int affected = transferMapper.update(null, new LambdaUpdateWrapper<AstTransfer>()
                    .eq(AstTransfer::getTenantId, tenantId)
                    .eq(AstTransfer::getId, id)
                    .eq(AstTransfer::getVersion, version)
                    .eq(AstTransfer::getStatus, AssetOperationStateMachine.START_FAILED)
                    .eq(AstTransfer::getWorkflowStartStatus,
                            AssetOperationStateMachine.START_FAILED_FLAG)
                    .eq(AstTransfer::getDeleted, 0)
                    .set(AstTransfer::getStatus, AssetOperationStateMachine.PENDING_APPROVAL)
                    .set(AstTransfer::getWorkflowStartStatus,
                            AssetOperationStateMachine.START_PENDING)
                    .set(AstTransfer::getUpdateTime, LocalDateTime.now())
                    .set(AstTransfer::getUpdateBy, AssetAuditSupport.operator())
                    .setSql("version = version + 1"));
            accessGuard.requireAffected(affected, "调拨申请已被其他请求修改");
        }
        return transferCommand(transfer, asset);
    }

    private AssetWorkflowCommand prepareDisposalRetry(Long id, Integer version) {
        Long tenantId = AssetTenantContext.requireTenantId();
        AstDisposal disposal = accessGuard.requireVisible(
                disposalMapper.selectForUpdate(tenantId, id), "处置申请不存在");
        AssetOperationStateMachine.requireRetryable(
                disposal.getStatus(), disposal.getWorkflowStartStatus());
        requireVersion(disposal.getVersion(), version);
        requireSnapshot(disposal.getWorkflowRequestId(), disposal.getWorkflowBusinessKey(),
                disposal.getModelVersionId(), disposal.getWorkflowStartUserId());
        AstAsset asset = requireOccupiedAsset(tenantId, disposal.getAssetId(), DISPOSAL, id);
        if (AssetOperationStateMachine.START_FAILED.equals(disposal.getStatus())) {
            int affected = disposalMapper.update(null, new LambdaUpdateWrapper<AstDisposal>()
                    .eq(AstDisposal::getTenantId, tenantId)
                    .eq(AstDisposal::getId, id)
                    .eq(AstDisposal::getVersion, version)
                    .eq(AstDisposal::getStatus, AssetOperationStateMachine.START_FAILED)
                    .eq(AstDisposal::getWorkflowStartStatus,
                            AssetOperationStateMachine.START_FAILED_FLAG)
                    .eq(AstDisposal::getDeleted, 0)
                    .set(AstDisposal::getStatus, AssetOperationStateMachine.PENDING_APPROVAL)
                    .set(AstDisposal::getWorkflowStartStatus,
                            AssetOperationStateMachine.START_PENDING)
                    .set(AstDisposal::getUpdateTime, LocalDateTime.now())
                    .set(AstDisposal::getUpdateBy, AssetAuditSupport.operator())
                    .setSql("version = version + 1"));
            accessGuard.requireAffected(affected, "处置申请已被其他请求修改");
        }
        return disposalCommand(disposal, asset);
    }

    private void occupy(AstAsset asset, Long operationId, String operationType, String targetStatus) {
        int affected = assetMapper.occupyOperation(asset, targetStatus,
                operationType, operationId, AssetAuditSupport.operator());
        accessGuard.requireAffected(affected, "资产已被其他调拨或处置申请占用");
    }

    private AstAsset requireAsset(Long tenantId, Long assetId) {
        return accessGuard.requireVisible(assetMapper.selectForUpdate(tenantId, assetId), "资产不存在");
    }

    private AstAsset requireOccupiedAsset(Long tenantId, Long assetId,
                                          String operationType, Long operationId) {
        AstAsset asset = requireAsset(tenantId, assetId);
        if (!operationType.equals(asset.getActiveOperationType())
                || !operationId.equals(asset.getActiveOperationId())) {
            throw new BusinessException(409, "资产活动操作占位与申请不一致");
        }
        return asset;
    }

    private void requireAvailable(AstAsset asset) {
        if (asset.getActiveOperationId() != null || trimToNull(asset.getActiveOperationType()) != null) {
            throw new BusinessException(409, "资产已存在活动中的调拨或处置申请");
        }
    }

    private void appendHistory(AstAsset asset, String targetStatus, String remark) {
        AstAssetHistory history = new AstAssetHistory();
        history.setTenantId(asset.getTenantId());
        history.setAssetId(asset.getId());
        history.setFromStatus(asset.getStatus());
        history.setToStatus(targetStatus);
        history.setChangedByUserId(AssetTenantContext.require().userId());
        history.setChangedTime(LocalDateTime.now());
        history.setRemark(remark);
        AssetAuditSupport.created(history);
        accessGuard.requireAffected(historyMapper.insert(history), "记录资产操作历史失败");
    }

    private AssetWorkflowCommand transferCommand(AstTransfer transfer, AstAsset asset) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("transferId", transfer.getId());
        variables.put("assetId", transfer.getAssetId());
        variables.put("toUserId", transfer.getToUserId());
        variables.put("toUnitId", transfer.getToUnitId());
        variables.put("previousAssetStatus", transfer.getPreviousAssetStatus());
        return new AssetWorkflowCommand(TRANSFER, transfer.getId(), transfer.getTenantId(),
                transfer.getWorkflowRequestId(), AssetWorkflowCoordinator.TRANSFER_BUSINESS_TYPE,
                transfer.getWorkflowBusinessKey(), transfer.getModelVersionId(),
                transfer.getWorkflowStartUserId(), transfer.getWorkflowStartUserName(),
                "资产调拨 " + transfer.getTransferNo() + " - " + asset.getName(),
                variables);
    }

    private AssetWorkflowCommand disposalCommand(AstDisposal disposal, AstAsset asset) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("disposalId", disposal.getId());
        variables.put("assetId", disposal.getAssetId());
        variables.put("disposalType", disposal.getDisposalType());
        variables.put("residualValue", disposal.getResidualValue() == null
                ? null : disposal.getResidualValue().setScale(2).toPlainString());
        variables.put("previousAssetStatus", disposal.getPreviousAssetStatus());
        return new AssetWorkflowCommand(DISPOSAL, disposal.getId(), disposal.getTenantId(),
                disposal.getWorkflowRequestId(), AssetWorkflowCoordinator.DISPOSAL_BUSINESS_TYPE,
                disposal.getWorkflowBusinessKey(), disposal.getModelVersionId(),
                disposal.getWorkflowStartUserId(), disposal.getWorkflowStartUserName(),
                "资产处置 " + disposal.getDisposalNo() + " - " + asset.getName(),
                variables);
    }

    private LambdaUpdateWrapper<AstTransfer> matchingPendingTransfer(AssetWorkflowCommand command) {
        return new LambdaUpdateWrapper<AstTransfer>()
                .eq(AstTransfer::getTenantId, command.tenantId())
                .eq(AstTransfer::getId, command.operationId())
                .eq(AstTransfer::getStatus, AssetOperationStateMachine.PENDING_APPROVAL)
                .eq(AstTransfer::getWorkflowStartStatus, AssetOperationStateMachine.START_PENDING)
                .eq(AstTransfer::getWorkflowRequestId, command.requestId())
                .eq(AstTransfer::getWorkflowBusinessKey, command.businessKey())
                .eq(AstTransfer::getModelVersionId, command.modelVersionId())
                .eq(AstTransfer::getDeleted, 0);
    }

    private LambdaUpdateWrapper<AstDisposal> matchingPendingDisposal(AssetWorkflowCommand command) {
        return new LambdaUpdateWrapper<AstDisposal>()
                .eq(AstDisposal::getTenantId, command.tenantId())
                .eq(AstDisposal::getId, command.operationId())
                .eq(AstDisposal::getStatus, AssetOperationStateMachine.PENDING_APPROVAL)
                .eq(AstDisposal::getWorkflowStartStatus, AssetOperationStateMachine.START_PENDING)
                .eq(AstDisposal::getWorkflowRequestId, command.requestId())
                .eq(AstDisposal::getWorkflowBusinessKey, command.businessKey())
                .eq(AstDisposal::getModelVersionId, command.modelVersionId())
                .eq(AstDisposal::getDeleted, 0);
    }

    private void requireCommandTenant(AssetWorkflowCommand command) {
        if (!command.tenantId().equals(AssetTenantContext.requireTenantId())) {
            throw new BusinessException(403, "Workflow 启动快照与当前租户不一致");
        }
    }

    private void requireVersion(Integer actual, Integer expected) {
        if (expected == null || expected < 0 || !Objects.equals(actual, expected)) {
            throw new BusinessException(409, "申请已被其他请求修改，请刷新后重试");
        }
    }

    private void requireSnapshot(String requestId, String businessKey, Long modelVersionId,
                                 Long startUserId) {
        if (trimToNull(requestId) == null || trimToNull(businessKey) == null
                || modelVersionId == null || modelVersionId <= 0
                || startUserId == null || startUserId <= 0) {
            throw new BusinessException(409, "申请缺少可重试的 Workflow 幂等快照");
        }
    }

    private BigDecimal normalizeResidualValue(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.precision() - value.scale() > 16) {
            throw new BusinessException(400, "残值超出允许范围");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(400, "残值最多保留 2 位小数");
        }
    }

    private String requiredText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
