package com.omni.auth.config;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 认证模块的异常处理器。
 *
 * <p>由于 auth 模块依赖 {@code omni-common-core}（而非 {@code omni-common}），
 * {@code omni-common} 中的 {@code GlobalExceptionHandler} 不在组件扫描范围内。
 * 因此本类作为局部的 {@code @RestControllerAdvice}，为 {@code com.omni.auth.controller} 包
 * 提供等价的异常到 {@link R} 响应转换。</p>
 *
 * <h3>处理的异常类型：</h3>
 * <ul>
 *   <li>{@link BusinessException} — 业务规则违反（如验证码无效、认证失败）</li>
 *   <li>{@link MethodArgumentNotValidException} — Jakarta Bean Validation 校验失败</li>
 *   <li>{@link BindException} — 表单绑定错误</li>
 *   <li>{@link Exception} — 兜底处理所有未预期的异常（记录 ERROR 级别日志）</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.omni.auth.controller")
public class AuthExceptionHandler {

    /**
     * 处理业务规则异常。
     * <p>常见场景：验证码过期/无效、认证失败。</p>
     *
     * @param e 业务异常，包含错误码和消息
     * @return 标准响应格式的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理请求体参数校验异常（{@code @Valid @RequestBody} 触发的校验失败）。
     * <p>将所有字段级别的校验错误聚合为分号分隔的字符串，
     * 例如：{@code "username: 用户名不能为空; password: 密码不能为空"}。</p>
     *
     * @param e 参数校验异常，包含字段错误详情
     * @return HTTP 400 响应，包含聚合后的校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(se -> se.getField() + ": " + se.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return R.fail(400, message);
    }

    /**
     * 处理表单/查询参数绑定错误。
     *
     * @param e 参数绑定异常，包含字段错误详情
     * @return HTTP 400 响应，包含聚合后的绑定错误信息
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数绑定异常: {}", message);
        return R.fail(400, message);
    }

    /**
     * 兜底处理所有未被特定处理器捕获的异常。
     * <p>以 ERROR 级别记录完整异常堆栈，但向客户端返回通用错误消息，
     * 避免泄露内部实现细节。</p>
     *
     * @param e 未处理的异常
     * @return 服务器内部错误响应
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("未预期的系统异常", e);
        return R.fail("服务器内部错误");
    }
}
