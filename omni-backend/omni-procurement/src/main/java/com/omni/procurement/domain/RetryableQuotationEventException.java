package com.omni.procurement.domain;

import java.io.Serial;

/**
 * 报价事件早到、依赖记录暂缺或并发变化时抛出的可重试异常。
 *
 * @author Omni-Stack Team
 */
public class RetryableQuotationEventException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建可重试异常。
     *
     * @param message 错误说明
     */
    public RetryableQuotationEventException(String message) {
        super(message);
    }
}
