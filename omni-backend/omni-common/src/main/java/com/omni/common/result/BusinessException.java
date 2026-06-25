package com.omni.common.result;

import lombok.Getter;

/**
 * 业务异常类，携带错误码信息。
 * <p>
 * 用于表示业务规则校验失败的场景（如验证码错误、认证失败、权限不足等）。
 * 由 {@link GlobalExceptionHandler} 统一捕获并转换为标准的 {@link R} 响应格式，
 * 返回 {@code {"code": xxx, "message": "..."}} 结构给前端。
 * </p>
 * <p>使用示例：</p>
 * <pre>{@code
 * throw new BusinessException("验证码已过期");         // 默认错误码 500
 * throw new BusinessException(401, "认证失败");       // 指定错误码
 * }</pre>
 *
 * @see R#fail(int, String)
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
