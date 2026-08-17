package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 请购状态机测试。 */
class RequisitionStateMachineTest {

    /** 被拒绝请购允许编辑，编辑后由服务显式转回草稿。 */
    @Test
    void shouldAllowRejectedRequisitionToBeEdited() {
        assertThatCode(() -> RequisitionStateMachine.requireEditable(RequisitionStateMachine.REJECTED))
                .doesNotThrowAnyException();
    }

    /** 仅 FAILED 的已提交请购允许重试启动。 */
    @Test
    void shouldOnlyRetryFailedSubmission() {
        assertThatCode(() -> RequisitionStateMachine.requireStartRetryable(
                RequisitionStateMachine.SUBMITTED, RequisitionStateMachine.START_FAILED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> RequisitionStateMachine.requireStartRetryable(
                RequisitionStateMachine.SUBMITTED, RequisitionStateMachine.START_PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** PENDING 或审批中的请购不能取消。 */
    @Test
    void shouldRejectCancellationWhileWorkflowMayBeStartingOrRunning() {
        assertThatThrownBy(() -> RequisitionStateMachine.requireCancellable(
                RequisitionStateMachine.SUBMITTED, RequisitionStateMachine.START_PENDING))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        assertThatThrownBy(() -> RequisitionStateMachine.requireCancellable(
                RequisitionStateMachine.APPROVING, RequisitionStateMachine.START_STARTED))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }
}
