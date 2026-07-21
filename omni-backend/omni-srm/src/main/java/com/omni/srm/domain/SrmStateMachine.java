package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * SRM 供应商生命周期状态机。
 *
 * @author Omni-Stack Team
 */
public final class SrmStateMachine {

    /**
     * 供应商状态枚举。
     */
    public enum SupplierStatus {
        REGISTERING, REGISTERING_FAILED, PENDING_REVIEW, REJECTED,
        APPROVED, SUSPENDED, BLACKLISTED, ELIMINATED
    }

    private static final Map<SupplierStatus, Set<SupplierStatus>> TRANSITIONS = Map.of(
            SupplierStatus.REGISTERING, Set.of(SupplierStatus.PENDING_REVIEW, SupplierStatus.REGISTERING_FAILED),
            SupplierStatus.REGISTERING_FAILED, Set.of(SupplierStatus.REGISTERING),
            SupplierStatus.PENDING_REVIEW, Set.of(SupplierStatus.APPROVED, SupplierStatus.REJECTED),
            SupplierStatus.REJECTED, Set.of(SupplierStatus.PENDING_REVIEW),
            SupplierStatus.APPROVED, Set.of(SupplierStatus.SUSPENDED, SupplierStatus.BLACKLISTED, SupplierStatus.ELIMINATED),
            SupplierStatus.SUSPENDED, Set.of(SupplierStatus.APPROVED, SupplierStatus.ELIMINATED),
            SupplierStatus.BLACKLISTED, Set.of(SupplierStatus.APPROVED),
            SupplierStatus.ELIMINATED, Set.of());

    private SrmStateMachine() {
    }

    /**
     * 校验供应商状态迁移。
     *
     * @param from 原状态
     * @param to 目标状态
     */
    public static void requireTransition(SupplierStatus from, SupplierStatus to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(409, "非法的供应商状态迁移：" + from + " → " + to);
        }
    }

    /**
     * 解析状态字符串。
     *
     * @param status 状态字符串
     * @return 状态枚举
     */
    public static SupplierStatus parse(String status) {
        try {
            return SupplierStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(400, "供应商状态无效：" + status);
        }
    }
}
