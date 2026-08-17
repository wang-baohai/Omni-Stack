package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;

/**
 * 请购申请状态迁移规则。
 *
 * @author Omni-Stack Team
 */
public final class RequisitionStateMachine {

    /** 草稿。 */
    public static final String DRAFT = "DRAFT";
    /** 已提交但尚未确认启动工作流。 */
    public static final String SUBMITTED = "SUBMITTED";
    /** 审批中。 */
    public static final String APPROVING = "APPROVING";
    /** 审批通过。 */
    public static final String APPROVED = "APPROVED";
    /** 审批拒绝。 */
    public static final String REJECTED = "REJECTED";
    /** 已取消。 */
    public static final String CANCELLED = "CANCELLED";

    /** 尚未启动。 */
    public static final String START_NOT_STARTED = "NOT_STARTED";
    /** 启动中。 */
    public static final String START_PENDING = "PENDING";
    /** 启动失败。 */
    public static final String START_FAILED = "FAILED";
    /** 已启动。 */
    public static final String START_STARTED = "STARTED";

    private RequisitionStateMachine() {
    }

    /**
     * 要求当前状态允许编辑。
     *
     * @param status 当前状态
     */
    public static void requireEditable(String status) {
        if (!DRAFT.equals(status) && !REJECTED.equals(status)) {
            throw conflict("仅草稿或审批拒绝的请购可以编辑");
        }
    }

    /**
     * 要求当前状态允许删除。
     *
     * @param status 当前状态
     */
    public static void requireDeletable(String status) {
        if (!DRAFT.equals(status)) {
            throw conflict("仅草稿请购可以删除");
        }
    }

    /**
     * 要求当前状态允许提交。
     *
     * @param status 当前状态
     */
    public static void requireSubmittable(String status) {
        if (!DRAFT.equals(status)) {
            throw conflict("仅草稿请购可以提交");
        }
    }

    /**
     * 要求当前状态允许重试工作流启动。
     *
     * @param status 当前业务状态
     * @param workflowStartStatus 当前 Workflow 启动状态
     */
    public static void requireStartRetryable(String status, String workflowStartStatus) {
        if (!SUBMITTED.equals(status) || !START_FAILED.equals(workflowStartStatus)) {
            throw conflict("仅工作流启动失败的已提交请购可以重试");
        }
    }

    /**
     * 要求当前状态允许取消。
     *
     * @param status 当前业务状态
     * @param workflowStartStatus 当前 Workflow 启动状态
     */
    public static void requireCancellable(String status, String workflowStartStatus) {
        boolean draft = DRAFT.equals(status);
        boolean failedSubmission = SUBMITTED.equals(status) && START_FAILED.equals(workflowStartStatus);
        if (!draft && !failedSubmission) {
            throw conflict("仅草稿或工作流启动失败的已提交请购可以取消");
        }
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
