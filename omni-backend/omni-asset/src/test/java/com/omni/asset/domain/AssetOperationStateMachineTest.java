package com.omni.asset.domain;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 资产调拨与处置状态规则测试。 */
class AssetOperationStateMachineTest {

    /** 只有三种稳定资产状态可以发起操作。 */
    @Test
    void shouldAllowOnlyStableAssetStatusesToStartOperation() {
        AssetOperationStateMachine.requireOperationSource(AssetStateMachine.IN_STOCK);
        AssetOperationStateMachine.requireOperationSource(AssetStateMachine.ALLOCATED);
        AssetOperationStateMachine.requireOperationSource(AssetStateMachine.IN_USE);

        assertThatThrownBy(() -> AssetOperationStateMachine.requireOperationSource(
                AssetStateMachine.MAINTENANCE)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AssetOperationStateMachine.requireOperationSource(
                AssetStateMachine.TRANSFER)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AssetOperationStateMachine.requireOperationSource(
                AssetStateMachine.DISPOSAL_PENDING)).isInstanceOf(BusinessException.class);
    }

    /** 失败或待确认状态可重试，但只有明确失败状态可本地取消。 */
    @Test
    void shouldRetryAndCancelOnlyWhenWorkflowStartFailed() {
        AssetOperationStateMachine.requireRetryable(
                AssetOperationStateMachine.START_FAILED,
                AssetOperationStateMachine.START_FAILED_FLAG);
        AssetOperationStateMachine.requireRetryable(
                AssetOperationStateMachine.PENDING_APPROVAL,
                AssetOperationStateMachine.START_PENDING);
        AssetOperationStateMachine.requireLocallyCancellable(
                AssetOperationStateMachine.START_FAILED,
                AssetOperationStateMachine.START_FAILED_FLAG);

        assertThatThrownBy(() -> AssetOperationStateMachine.requireRetryable(
                AssetOperationStateMachine.PENDING_APPROVAL,
                AssetOperationStateMachine.START_STARTED))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AssetOperationStateMachine.requireLocallyCancellable(
                AssetOperationStateMachine.PENDING_APPROVAL,
                AssetOperationStateMachine.START_STARTED))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AssetOperationStateMachine.requireLocallyCancellable(
                AssetOperationStateMachine.PENDING_APPROVAL,
                AssetOperationStateMachine.START_PENDING))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("启动结果待确认");
    }

    /** 只有审批通过的申请可以执行最终业务完成。 */
    @Test
    void shouldCompleteOnlyApprovedOperation() {
        AssetOperationStateMachine.requireCompletable(AssetOperationStateMachine.APPROVED);

        assertThatThrownBy(() -> AssetOperationStateMachine.requireCompletable(
                AssetOperationStateMachine.PENDING_APPROVAL))
                .isInstanceOf(BusinessException.class);
    }
}
