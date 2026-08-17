package com.omni.asset.domain;

import com.omni.common.core.result.BusinessException;

import java.util.Set;

/**
 * 资产调拨与处置申请状态规则。
 *
 * @author Omni-Stack Team
 */
public final class AssetOperationStateMachine {

    /** 等待 Workflow 审批。 */
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    /** Workflow 启动失败。 */
    public static final String START_FAILED = "START_FAILED";
    /** 审批通过，等待业务完成。 */
    public static final String APPROVED = "APPROVED";
    /** 审批拒绝。 */
    public static final String REJECTED = "REJECTED";
    /** 业务完成。 */
    public static final String COMPLETED = "COMPLETED";
    /** 已取消。 */
    public static final String CANCELLED = "CANCELLED";
    /** Workflow 启动待确认。 */
    public static final String START_PENDING = "PENDING";
    /** Workflow 已启动。 */
    public static final String START_STARTED = "STARTED";
    /** Workflow 启动失败。 */
    public static final String START_FAILED_FLAG = "FAILED";

    private static final Set<String> OPERATION_SOURCE_STATUSES = Set.of(
            AssetStateMachine.IN_STOCK, AssetStateMachine.ALLOCATED, AssetStateMachine.IN_USE);

    private AssetOperationStateMachine() {
    }

    /**
     * 校验资产可发起调拨或处置。
     *
     * @param assetStatus 资产状态
     */
    public static void requireOperationSource(String assetStatus) {
        AssetStateMachine.requireKnown(assetStatus);
        if (!OPERATION_SOURCE_STATUSES.contains(assetStatus)) {
            throw new BusinessException(409, "当前资产状态不能发起调拨或处置");
        }
    }

    /**
     * 校验申请可重试启动。
     *
     * @param status 申请状态
     * @param startStatus 启动状态
     */
    public static void requireRetryable(String status, String startStatus) {
        boolean failed = START_FAILED.equals(status) && START_FAILED_FLAG.equals(startStatus);
        boolean outcomeUnknown =
                PENDING_APPROVAL.equals(status) && START_PENDING.equals(startStatus);
        if (!failed && !outcomeUnknown) {
            throw new BusinessException(409, "当前申请不能重试启动 Workflow");
        }
    }

    /**
     * 校验申请可由本地取消。
     *
     * @param status 申请状态
     * @param startStatus 启动状态
     */
    public static void requireLocallyCancellable(String status, String startStatus) {
        boolean failed = START_FAILED.equals(status) && START_FAILED_FLAG.equals(startStatus);
        if (!failed) {
            throw new BusinessException(409,
                    "仅 Workflow 明确启动失败的申请可本地取消，启动结果待确认或已启动时请勿取消");
        }
    }

    /**
     * 校验申请可以完成业务动作。
     *
     * @param status 申请状态
     */
    public static void requireCompletable(String status) {
        if (!APPROVED.equals(status)) {
            throw new BusinessException(409, "仅审批通过的申请可以完成业务动作");
        }
    }

    /**
     * 判断申请是否为不可再处理终态。
     *
     * @param status 申请状态
     * @return 是否终态
     */
    public static boolean isTerminal(String status) {
        return REJECTED.equals(status) || COMPLETED.equals(status) || CANCELLED.equals(status);
    }
}
