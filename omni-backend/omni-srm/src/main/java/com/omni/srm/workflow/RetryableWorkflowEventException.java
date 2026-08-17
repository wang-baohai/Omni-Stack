package com.omni.srm.workflow;

/**
 * 需要由消息中间件稍后重试的 Workflow 完成事件异常。
 *
 * @author Omni-Stack Team
 */
public class RetryableWorkflowEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建可重试异常。
     *
     * @param message 异常说明
     */
    public RetryableWorkflowEventException(String message) {
        super(message);
    }
}
