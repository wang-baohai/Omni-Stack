package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 采购订单状态迁移约束测试。 */
class PurchaseOrderStateMachineTest {

    /** 草稿可以编辑、删除和发送。 */
    @Test
    void shouldAllowDraftCommands() {
        assertThatCode(() -> PurchaseOrderStateMachine.requireEditable("DRAFT"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PurchaseOrderStateMachine.requireDeletable("DRAFT"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PurchaseOrderStateMachine.requireSendable("DRAFT"))
                .doesNotThrowAnyException();
    }

    /** 只有已发送订单可以进入供应商确认。 */
    @Test
    void shouldRejectConfirmWhenOrderNotSent() {
        assertThatThrownBy(() -> PurchaseOrderStateMachine.requireConfirmable("DRAFT"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 已开始收货后不能取消订单。 */
    @Test
    void shouldRejectCancelAfterReceivingStarted() {
        assertThatThrownBy(() -> PurchaseOrderStateMachine.requireCancellable("PARTIAL_RECEIVED"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 已确认和部分收货订单均可继续收货。 */
    @Test
    void shouldAllowReceivableStatuses() {
        assertThatCode(() -> PurchaseOrderStateMachine.requireReceivable("CONFIRMED"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PurchaseOrderStateMachine.requireReceivable("PARTIAL_RECEIVED"))
                .doesNotThrowAnyException();
    }

    /** 累计未收满与全部收满必须分别推进到部分收货和全部收货。 */
    @Test
    void shouldResolveReceiptProgressStatus() {
        assertThat(PurchaseOrderStateMachine.receiptProgressStatus(false))
                .isEqualTo(PurchaseOrderStateMachine.PARTIAL_RECEIVED);
        assertThat(PurchaseOrderStateMachine.receiptProgressStatus(true))
                .isEqualTo(PurchaseOrderStateMachine.RECEIVED);
    }
}
