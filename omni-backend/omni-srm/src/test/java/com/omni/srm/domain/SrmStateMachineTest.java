package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;
import com.omni.srm.domain.SrmStateMachine.SupplierStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 供应商生命周期状态机测试。 */
class SrmStateMachineTest {

    /** 门户失败、重试和重新提交链路必须合法。 */
    @Test
    void shouldAllowPortalFailureRetryAndRejectedResubmit() {
        assertThatCode(() -> SrmStateMachine.requireTransition(
                SupplierStatus.REGISTERING, SupplierStatus.REGISTERING_FAILED)).doesNotThrowAnyException();
        assertThatCode(() -> SrmStateMachine.requireTransition(
                SupplierStatus.REGISTERING_FAILED, SupplierStatus.REGISTERING)).doesNotThrowAnyException();
        assertThatCode(() -> SrmStateMachine.requireTransition(
                SupplierStatus.PENDING_REVIEW, SupplierStatus.REJECTED)).doesNotThrowAnyException();
        assertThatCode(() -> SrmStateMachine.requireTransition(
                SupplierStatus.REJECTED, SupplierStatus.PENDING_REVIEW)).doesNotThrowAnyException();
    }

    /** 黑名单只能从已批准进入并恢复到已批准。 */
    @Test
    void shouldRestrictBlacklistTransitions() {
        assertThatCode(() -> SrmStateMachine.requireTransition(
                SupplierStatus.APPROVED, SupplierStatus.BLACKLISTED)).doesNotThrowAnyException();
        assertThatCode(() -> SrmStateMachine.requireTransition(
                SupplierStatus.BLACKLISTED, SupplierStatus.APPROVED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SrmStateMachine.requireTransition(
                SupplierStatus.SUSPENDED, SupplierStatus.BLACKLISTED))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 淘汰是不可恢复的终态。 */
    @Test
    void shouldKeepEliminatedTerminal() {
        assertThatThrownBy(() -> SrmStateMachine.requireTransition(
                SupplierStatus.ELIMINATED, SupplierStatus.APPROVED))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }
}
