package com.omni.common.result;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，将各类异常统一转换为标准的 {@link R} 响应格式。
 * <p>
 * 处理的异常类型包括：
 * <ul>
 *   <li>{@link BusinessException} — 业务规则异常，返回业务自定义错误码</li>
 *   <li>{@link MethodArgumentNotValidException} — 请求体参数校验失败（@Valid）</li>
 *   <li>{@link BindException} — 表单/查询参数绑定失败</li>
 *   <li>{@link Exception} — 兜底处理所有未捕获的异常</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param e 业务异常实例
     * @return 包含业务错误码和消息的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理请求体参数校验异常（@Valid 注解触发的校验失败）。
     * <p>
     * 将所有字段级别的校验错误聚合为分号分隔的字符串返回。
     * </p>
     *
     * @param e 参数校验异常
     * @return HTTP 400 响应，包含聚合后的校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return R.fail(400, message);
    }

    /**
     * 处理表单/查询参数绑定异常。
     *
     * @param e 参数绑定异常
     * @return HTTP 400 响应，包含聚合后的绑定错误信息
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        return R.fail(400, message);
    }

    /**
     * 兜底处理所有未被特定处理器捕获的异常。
     * <p>
     * 记录完整异常堆栈到日志，向客户端返回通用错误提示以避免泄露内部实现细节。
     * </p>
     *
     * @param e 未处理的异常
     * @return HTTP 500 响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("未预期的系统异常", e);
        return R.fail("服务器内部错误");
    }
}
