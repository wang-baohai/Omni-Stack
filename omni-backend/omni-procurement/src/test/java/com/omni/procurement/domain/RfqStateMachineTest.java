package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 询价单与供应商邀请状态机测试。 */
class RfqStateMachineTest {

    /** 草稿允许编辑、删除和发送。 */
    @Test
    void shouldAllowDraftCommands() {
        assertThatCode(() -> RfqStateMachine.requireEditable(RfqStateMachine.DRAFT))
                .doesNotThrowAnyException();
        assertThatCode(() -> RfqStateMachine.requireDeletable(RfqStateMachine.DRAFT))
                .doesNotThrowAnyException();
        assertThatCode(() -> RfqStateMachine.requireSendable(RfqStateMachine.DRAFT))
                .doesNotThrowAnyException();
    }

    /** 已发送询价禁止编辑、删除或重复发送，但仍允许取消。 */
    @Test
    void shouldFreezeSentRfq() {
        assertThatThrownBy(() -> RfqStateMachine.requireEditable(RfqStateMachine.SENT))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        assertThatThrownBy(() -> RfqStateMachine.requireDeletable(RfqStateMachine.SENT))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        assertThatThrownBy(() -> RfqStateMachine.requireSendable(RfqStateMachine.SENT))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        assertThatCode(() -> RfqStateMachine.requireCancellable(RfqStateMachine.SENT))
                .doesNotThrowAnyException();
        assertThatCode(() -> RfqStateMachine.requireComparable(RfqStateMachine.SENT))
                .doesNotThrowAnyException();
        assertThatCode(() -> RfqStateMachine.requireAwardable(RfqStateMachine.SENT))
                .doesNotThrowAnyException();
    }

    /** 只有 INVITED 和 QUOTED 邀请可以继续协作报价。 */
    @Test
    void shouldRecognizeOnlyActiveInvitationStates() {
        assertThat(RfqStateMachine.isActiveInvitation(RfqStateMachine.INVITED)).isTrue();
        assertThat(RfqStateMachine.isActiveInvitation(RfqStateMachine.QUOTED)).isTrue();
        assertThat(RfqStateMachine.isActiveInvitation(RfqStateMachine.EXPIRED)).isFalse();
    }

    /** 已发送后的终态和失效邀请仍可供门户历史只读展示。 */
    @Test
    void shouldRecognizePortalHistoryStates() {
        assertThat(RfqStateMachine.isPortalVisibleRfq(RfqStateMachine.DRAFT)).isFalse();
        assertThat(RfqStateMachine.isPortalVisibleRfq(RfqStateMachine.SENT)).isTrue();
        assertThat(RfqStateMachine.isPortalVisibleRfq(RfqStateMachine.CLOSED)).isTrue();
        assertThat(RfqStateMachine.isPortalVisibleRfq(RfqStateMachine.AWARDED)).isTrue();
        assertThat(RfqStateMachine.isPortalVisibleRfq(RfqStateMachine.CANCELLED)).isTrue();
        assertThat(RfqStateMachine.isPortalVisibleInvitation(RfqStateMachine.INVITED)).isTrue();
        assertThat(RfqStateMachine.isPortalVisibleInvitation(RfqStateMachine.QUOTED)).isTrue();
        assertThat(RfqStateMachine.isPortalVisibleInvitation(RfqStateMachine.AWARDED)).isTrue();
        assertThat(RfqStateMachine.isPortalVisibleInvitation(RfqStateMachine.REJECTED)).isTrue();
        assertThat(RfqStateMachine.isPortalVisibleInvitation(RfqStateMachine.EXPIRED)).isTrue();
    }

    /** 终态询价不得再次进入比价或定点。 */
    @Test
    void shouldRejectComparisonAndAwardOutsideSentState() {
        assertThatThrownBy(() -> RfqStateMachine.requireComparable(RfqStateMachine.AWARDED))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        assertThatThrownBy(() -> RfqStateMachine.requireAwardable(RfqStateMachine.CANCELLED))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }
}
