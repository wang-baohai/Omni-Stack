package com.omni.srm.workflow;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.domain.SrmStateMachine;
import com.omni.srm.domain.SrmStateMachine.SupplierStatus;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.mapper.SrmSupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 供应商 Workflow 启动本地事务状态服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierWorkflowStateServiceImpl implements SupplierWorkflowStateService {

    private final SrmSupplierMapper supplierMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStarted(SupplierWorkflowCommand command, String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank() || processInstanceId.length() > 64) {
            throw new BusinessException(503, "Workflow 返回了无效的流程实例 ID");
        }
        LambdaUpdateWrapper<SrmSupplier> update = matchingPending(command)
                .set(SrmSupplier::getProcessInstanceId, processInstanceId)
                .set(SrmSupplier::getWorkflowStartStatus, SrmStateMachine.START_STARTED)
                .set(SrmSupplier::getStatus, SupplierStatus.APPROVING.name())
                .set(SrmSupplier::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1");
        int affected = supplierMapper.update(null, update);
        if (affected != 1) {
            throw new BusinessException(409, "供应商审批启动状态已变化，请刷新后重试");
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(SupplierWorkflowCommand command) {
        LambdaUpdateWrapper<SrmSupplier> update = matchingPending(command)
                .set(SrmSupplier::getWorkflowStartStatus, SrmStateMachine.START_FAILED)
                .set(SrmSupplier::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1");
        int affected = supplierMapper.update(null, update);
        if (affected == 0) {
            log.warn("供应商 Workflow 启动失败状态未命中当前快照: tenantId={}, supplierId={}, businessKey={}",
                    command.tenantId(), command.supplierId(), command.businessKey());
        }
    }

    private LambdaUpdateWrapper<SrmSupplier> matchingPending(SupplierWorkflowCommand command) {
        return new LambdaUpdateWrapper<SrmSupplier>()
                .eq(SrmSupplier::getTenantId, command.tenantId())
                .eq(SrmSupplier::getId, command.supplierId())
                .eq(SrmSupplier::getStatus, SupplierStatus.PENDING_REVIEW.name())
                .eq(SrmSupplier::getWorkflowStartStatus, SrmStateMachine.START_PENDING)
                .eq(SrmSupplier::getApprovalAttempt, command.approvalAttempt())
                .eq(SrmSupplier::getWorkflowRequestId, command.requestId())
                .eq(SrmSupplier::getWorkflowBusinessKey, command.businessKey())
                .eq(SrmSupplier::getWorkflowModelVersionId, command.modelVersionId())
                .eq(SrmSupplier::getDeleted, 0);
    }
}
