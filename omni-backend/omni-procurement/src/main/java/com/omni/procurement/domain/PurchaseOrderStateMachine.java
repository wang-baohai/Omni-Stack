package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;

/**
 * 采购订单状态机。
 *
 * @author Omni-Stack Team
 */
public final class PurchaseOrderStateMachine {

    /** 草稿。 */ public static final String DRAFT = "DRAFT";
    /** 已发送。 */ public static final String SENT = "SENT";
    /** 供应商已确认。 */ public static final String CONFIRMED = "CONFIRMED";
    /** 部分收货。 */ public static final String PARTIAL_RECEIVED = "PARTIAL_RECEIVED";
    /** 全部收货。 */ public static final String RECEIVED = "RECEIVED";
    /** 已关闭。 */ public static final String CLOSED = "CLOSED";
    /** 已取消。 */ public static final String CANCELLED = "CANCELLED";

    private PurchaseOrderStateMachine() {
    }

    /**
     * 要求订单可编辑。
     *
     * @param status 当前状态
     */
    public static void requireEditable(String status) {
        if (!DRAFT.equals(status)) {
            throw new BusinessException(409, "仅草稿采购订单可以编辑");
        }
    }

    /**
     * 要求订单可删除。
     *
     * @param status 当前状态
     */
    public static void requireDeletable(String status) {
        if (!DRAFT.equals(status)) {
            throw new BusinessException(409, "仅草稿采购订单可以删除");
        }
    }

    /**
     * 要求订单可发送。
     *
     * @param status 当前状态
     */
    public static void requireSendable(String status) {
        if (!DRAFT.equals(status)) {
            throw new BusinessException(409, "仅草稿采购订单可以发送");
        }
    }

    /**
     * 要求订单可确认。
     *
     * @param status 当前状态
     */
    public static void requireConfirmable(String status) {
        if (!SENT.equals(status)) {
            throw new BusinessException(409, "仅已发送采购订单可以确认");
        }
    }

    /**
     * 要求订单可取消。
     *
     * @param status 当前状态
     */
    public static void requireCancellable(String status) {
        if (!DRAFT.equals(status) && !SENT.equals(status) && !CONFIRMED.equals(status)) {
            throw new BusinessException(409, "当前状态不允许取消采购订单");
        }
    }

    /**
     * 要求订单可创建或确认收货。
     *
     * @param status 当前状态
     */
    public static void requireReceivable(String status) {
        if (!CONFIRMED.equals(status) && !PARTIAL_RECEIVED.equals(status)) {
            throw new BusinessException(409, "仅已确认或部分收货订单可以继续收货");
        }
    }

    /**
     * 根据累计收货进度计算订单目标状态。
     *
     * @param fullyReceived 是否全部收货
     * @return 部分收货或全部收货状态
     */
    public static String receiptProgressStatus(boolean fullyReceived) {
        return fullyReceived ? RECEIVED : PARTIAL_RECEIVED;
    }
}
