package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;

import java.util.Set;

/**
 * 询价单与供应商邀请状态机。
 *
 * @author Omni-Stack Team
 */
public final class RfqStateMachine {

    /** 草稿。 */ public static final String DRAFT = "DRAFT";
    /** 已发送。 */ public static final String SENT = "SENT";
    /** 已关闭。 */ public static final String CLOSED = "CLOSED";
    /** 已定点。 */ public static final String AWARDED = "AWARDED";
    /** 已取消。 */ public static final String CANCELLED = "CANCELLED";
    /** 已邀请。 */ public static final String INVITED = "INVITED";
    /** 已报价。 */ public static final String QUOTED = "QUOTED";
    /** 已失效。 */ public static final String EXPIRED = "EXPIRED";
    /** 未中标。 */ public static final String REJECTED = "REJECTED";

    private static final Set<String> ACTIVE_INVITATIONS = Set.of(INVITED, QUOTED);
    private static final Set<String> PORTAL_VISIBLE_RFQ_STATUSES = Set.of(
            SENT, CLOSED, AWARDED, CANCELLED);
    private static final Set<String> PORTAL_VISIBLE_INVITATION_STATUSES = Set.of(
            INVITED, QUOTED, AWARDED, REJECTED, EXPIRED);

    private RfqStateMachine() {
    }

    /**
     * 要求询价单仍可编辑。
     *
     * @param status 当前状态
     */
    public static void requireEditable(String status) {
        if (!DRAFT.equals(status)) {
            throw new BusinessException(409, "询价单发送后不可编辑");
        }
    }

    /**
     * 要求询价单仍可删除。
     *
     * @param status 当前状态
     */
    public static void requireDeletable(String status) {
        if (!DRAFT.equals(status)) {
            throw new BusinessException(409, "仅草稿询价单可以删除");
        }
    }

    /**
     * 要求询价单可发送。
     *
     * @param status 当前状态
     */
    public static void requireSendable(String status) {
        if (!DRAFT.equals(status)) {
            throw new BusinessException(409, "仅草稿询价单可以发送");
        }
    }

    /**
     * 要求询价单可取消。
     *
     * @param status 当前状态
     */
    public static void requireCancellable(String status) {
        if (!DRAFT.equals(status) && !SENT.equals(status)) {
            throw new BusinessException(409, "当前状态不允许取消询价单");
        }
    }

    /**
     * 要求询价单可进入比价视图。
     *
     * @param status 当前状态
     */
    public static void requireComparable(String status) {
        if (!SENT.equals(status)) {
            throw new BusinessException(409, "仅已发送询价单可以比价");
        }
    }

    /**
     * 要求询价单可定点。
     *
     * @param status 当前状态
     */
    public static void requireAwardable(String status) {
        if (!SENT.equals(status)) {
            throw new BusinessException(409, "仅已发送询价单可以定点");
        }
    }

    /**
     * 判断供应商邀请是否仍允许提交或更新报价。
     *
     * @param status 邀请状态
     * @return 是否有效
     */
    public static boolean isActiveInvitation(String status) {
        return ACTIVE_INVITATIONS.contains(status);
    }

    /**
     * 判断询价单是否已经发送过，可供供应商门户历史只读展示。
     *
     * @param status 询价单状态
     * @return 是否属于已发布状态
     */
    public static boolean isPortalVisibleRfq(String status) {
        return PORTAL_VISIBLE_RFQ_STATUSES.contains(status);
    }

    /**
     * 判断邀请状态是否可供供应商门户历史只读展示。
     *
     * @param status 邀请状态
     * @return 是否属于已发送邀请状态
     */
    public static boolean isPortalVisibleInvitation(String status) {
        return PORTAL_VISIBLE_INVITATION_STATUSES.contains(status);
    }
}
