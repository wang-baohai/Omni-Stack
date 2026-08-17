package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;

/**
 * 收货单与质检状态机。
 *
 * @author Omni-Stack Team
 */
public final class GoodsReceiptStateMachine {

    /** 草稿。 */ public static final String DRAFT = "DRAFT";
    /** 已确认。 */ public static final String CONFIRMED = "CONFIRMED";
    /** 质检通过。 */ public static final String PASS = "PASS";
    /** 质检失败。 */ public static final String FAIL = "FAIL";
    /** 待质检。 */ public static final String PENDING = "PENDING";

    private GoodsReceiptStateMachine() {
    }

    /**
     * 要求收货单可确认。
     *
     * @param status 当前状态
     */
    public static void requireConfirmable(String status) {
        if (!DRAFT.equals(status)) {
            throw new BusinessException(409, "仅草稿收货单可以确认");
        }
    }

    /**
     * 要求收货单已确认后再登记后续质检结果。
     *
     * @param status 当前状态
     */
    public static void requireQualityUpdatable(String status) {
        if (!CONFIRMED.equals(status)) {
            throw new BusinessException(409, "仅已确认收货单可以登记质检结果");
        }
    }

    /**
     * 要求行仍处于待质检状态。
     *
     * @param status 当前质检状态
     */
    public static void requirePendingQuality(String status) {
        if (!PENDING.equals(status)) {
            throw new BusinessException(409, "仅待质检行可以登记质检结果");
        }
    }
}
