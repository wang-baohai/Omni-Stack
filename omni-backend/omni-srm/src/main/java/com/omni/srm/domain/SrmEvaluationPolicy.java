package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;

/**
 * SRM 绩效评估业务准入策略。
 *
 * @author Omni-Stack Team
 */
public final class SrmEvaluationPolicy {

    private SrmEvaluationPolicy() {
    }

    /**
     * 校验供应商是否具备评估条件。
     *
     * @param status 供应商生命周期状态
     * @param ownerUserId 内部负责人用户 ID
     * @param ownerUnitId 内部负责人组织 ID
     */
    public static void requireEligible(String status, Long ownerUserId, Long ownerUnitId) {
        if (!"APPROVED".equals(status)) {
            throw new BusinessException(409, "仅审核通过的供应商可以发起绩效评估");
        }
        if (ownerUserId == null || ownerUnitId == null) {
            throw new BusinessException(409, "供应商尚未分配完整的内部负责人，不能发起绩效评估");
        }
    }
}
