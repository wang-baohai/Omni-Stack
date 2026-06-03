package com.omni.common.core.result;

import lombok.Getter;

/**
 * 业务异常类，携带错误码信息。
 * <p>
 * 用于表示业务规则校验失败的场景（如验证码错误、认证失败等）。
 * 由全局异常处理器统一捕获并转换为标准的 {@link R} 响应格式。
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码，默认 500 */
    private final int code;

    /**
     * 使用默认错误码（500）构造业务异常。
     *
     * @param message 异常描述信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 使用指定错误码构造业务异常。
     *
     * @param code    错误码
     * @param message 异常描述信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
