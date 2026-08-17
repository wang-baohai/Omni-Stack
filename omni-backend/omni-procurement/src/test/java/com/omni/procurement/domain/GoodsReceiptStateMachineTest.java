package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 收货单与质检状态迁移约束测试。 */
class GoodsReceiptStateMachineTest {

    /** 草稿可以确认。 */
    @Test
    void shouldAllowDraftConfirmation() {
        assertThatCode(() -> GoodsReceiptStateMachine.requireConfirmable("DRAFT"))
                .doesNotThrowAnyException();
    }

    /** 已确认收货单不能重复确认。 */
    @Test
    void shouldRejectRepeatedConfirmation() {
        assertThatThrownBy(() -> GoodsReceiptStateMachine.requireConfirmable("CONFIRMED"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 只有待质检行可以登记后续结果。 */
    @Test
    void shouldOnlyAllowPendingQualityResult() {
        assertThatCode(() -> GoodsReceiptStateMachine.requirePendingQuality("PENDING"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GoodsReceiptStateMachine.requirePendingQuality("PASS"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }
}
