package com.omni.asset.domain;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 资产生命周期状态机完整迁移测试。 */
class AssetStateMachineTest {

    private static final List<Transition> ALLOWED = List.of(
            new Transition(AssetStateMachine.IN_STOCK, AssetStateMachine.ALLOCATED),
            new Transition(AssetStateMachine.IN_STOCK, AssetStateMachine.TRANSFER),
            new Transition(AssetStateMachine.IN_STOCK, AssetStateMachine.DISPOSAL_PENDING),
            new Transition(AssetStateMachine.ALLOCATED, AssetStateMachine.IN_USE),
            new Transition(AssetStateMachine.ALLOCATED, AssetStateMachine.IN_STOCK),
            new Transition(AssetStateMachine.ALLOCATED, AssetStateMachine.TRANSFER),
            new Transition(AssetStateMachine.ALLOCATED, AssetStateMachine.DISPOSAL_PENDING),
            new Transition(AssetStateMachine.IN_USE, AssetStateMachine.IN_STOCK),
            new Transition(AssetStateMachine.IN_USE, AssetStateMachine.MAINTENANCE),
            new Transition(AssetStateMachine.IN_USE, AssetStateMachine.TRANSFER),
            new Transition(AssetStateMachine.IN_USE, AssetStateMachine.DISPOSAL_PENDING),
            new Transition(AssetStateMachine.MAINTENANCE, AssetStateMachine.IN_USE),
            new Transition(AssetStateMachine.TRANSFER, AssetStateMachine.IN_STOCK),
            new Transition(AssetStateMachine.TRANSFER, AssetStateMachine.ALLOCATED),
            new Transition(AssetStateMachine.TRANSFER, AssetStateMachine.IN_USE),
            new Transition(AssetStateMachine.DISPOSAL_PENDING, AssetStateMachine.IN_STOCK),
            new Transition(AssetStateMachine.DISPOSAL_PENDING, AssetStateMachine.ALLOCATED),
            new Transition(AssetStateMachine.DISPOSAL_PENDING, AssetStateMachine.IN_USE),
            new Transition(AssetStateMachine.DISPOSAL_PENDING, AssetStateMachine.DISPOSED),
            new Transition(AssetStateMachine.DISPOSAL_PENDING, AssetStateMachine.SCRAPPED));

    private static final Set<String> STATUSES = Set.of(
            AssetStateMachine.IN_STOCK, AssetStateMachine.ALLOCATED,
            AssetStateMachine.IN_USE, AssetStateMachine.MAINTENANCE,
            AssetStateMachine.TRANSFER, AssetStateMachine.DISPOSAL_PENDING,
            AssetStateMachine.DISPOSED, AssetStateMachine.SCRAPPED);

    /** 所有设计允许的生命周期迁移均应通过。 */
    @Test
    void shouldAllowEveryDesignedTransition() {
        ALLOWED.forEach(transition -> assertThatCode(
                () -> AssetStateMachine.requireTransition(transition.from(), transition.to()))
                .as(transition.from() + " -> " + transition.to())
                .doesNotThrowAnyException());
    }

    /** 除白名单以外的迁移全部应以 409 拒绝。 */
    @Test
    void shouldRejectEveryTransitionOutsideWhitelist() {
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                Transition transition = new Transition(from, to);
                if (!ALLOWED.contains(transition)) {
                    assertThatThrownBy(() -> AssetStateMachine.requireTransition(from, to))
                            .as(from + " -> " + to)
                            .isInstanceOf(BusinessException.class)
                            .extracting("code").isEqualTo(409);
                }
            }
        }
    }

    /** 未知状态必须失败关闭。 */
    @Test
    void shouldRejectUnknownStatus() {
        assertThatThrownBy(() -> AssetStateMachine.requireTransition("UNKNOWN", AssetStateMachine.IN_STOCK))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    private record Transition(String from, String to) {
    }
}
