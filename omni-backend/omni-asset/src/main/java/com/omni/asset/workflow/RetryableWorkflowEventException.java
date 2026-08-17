package com.omni.asset.workflow;

/**
 * Workflow 完成事件需要 Broker 稍后重试的异常。
 *
 * @author Omni-Stack Team
 */
public class RetryableWorkflowEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建可重试异常。
     *
     * @param message 原因
     */
    public RetryableWorkflowEventException(String message) {
        super(message);
    }
}
