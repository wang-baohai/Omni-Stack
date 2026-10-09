package com.omni.auth.config;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
 *   <li>{@link MissingServletRequestParameterException} — 缺少必需的请求参数，返回 HTTP 400</li>
 *   <li>{@link NoResourceFoundException} — 请求的资源不存在，返回 HTTP 404</li>
 *   <li>{@link Exception} — 兜底处理所有未预期的异常（记录 ERROR 级别日志）</li>
 * </ul>
 *
 * <h3>与 {@code GlobalExceptionHandler} 的契约对齐：</h3>
 * <ul>
 *   <li>兜底 {@link Exception} 返回 HTTP 500 + {@code code=500}，与
 *       {@code docs/api-contract.md} 的错误码速查表（500 | 500 | 兜底 Exception）一致，
 *       使前端可用 HTTP 层分类；{@code message} 维持既有中文不变，
 *       面向用户的语言转换由前端展示层（{@code src/utils/error-display.ts}）承接。</li>
 *   <li>校验/绑定错误在无字段错误时给出兜底文案，避免返回空串提示。</li>
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
     * 例如：{@code "username: 用户名不能为空; password: 密码不能为空"}。
     * 无字段错误时使用与 {@code GlobalExceptionHandler} 一致的兜底文案，避免空串。</p>
     *
     * @param e 参数校验异常，包含字段错误详情
     * @return HTTP 400 响应，包含聚合后的校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return R.fail(400, message);
    }

    /**
     * 处理表单/查询参数绑定错误。
     * <p>无字段错误时使用与 {@code GlobalExceptionHandler} 一致的兜底文案，避免空串。</p>
     *
     * @param e 参数绑定异常，包含字段错误详情
     * @return HTTP 400 响应，包含聚合后的绑定错误信息
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        log.warn("参数绑定异常: {}", message);
        return R.fail(400, message);
    }

    /**
     * 处理权限不足异常（{@code @PreAuthorize} 校验失败时抛出）。
     * <p>返回 HTTP 403 状态码，而非被兜底的 {@link Exception} 处理器捕获返回 500。</p>
     *
     * @param e 权限不足异常
     * @return HTTP 403 响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return R.fail(403, "权限不足，拒绝访问");
    }

    /**
     * 处理缺少必需请求参数的异常。
     * <p>与 {@code GlobalExceptionHandler} 契约对齐，
     * 映射为 HTTP 400 + {@code R.code=400}。</p>
     *
     * @param e 缺少请求参数异常
     * @return HTTP 400 响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        log.warn("缺少必需请求参数: {} (类型: {})", e.getParameterName(), e.getParameterType());
        return R.fail(400, "缺少必需参数: " + e.getParameterName());
    }

    /**
     * 处理请求的资源不存在的异常。
     * <p>与 {@code GlobalExceptionHandler} 契约对齐，
     * 映射为 HTTP 404 + {@code R.code=404}。</p>
     *
     * @param e 资源未找到异常
     * @return HTTP 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    public R<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return R.fail(404, "请求的资源不存在");
    }

    /**
     * 处理路径/查询参数类型不匹配的异常。
     * <p>典型场景：{@code GET /api/auth/tenant/page} 被 {@code @GetMapping("/{id}")} 捕获，
     * "page" 无法转为 Long。该类请求属于调用方错误，必须映射为 HTTP 400，
     * 不得泄露到兜底分支变成 HTTP 500。</p>
     *
     * @param e 参数类型不匹配异常
     * @return HTTP 400 响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public R<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("方法参数类型不匹配: {}", e.getName());
        return R.fail(400, "参数格式错误: " + e.getName());
    }

    /**
     * 处理请求体媒体类型不被支持的异常。
     * <p>典型场景：客户端以 {@code Content-Type: text/plain} 发送 JSON 请求体；
     * 属于调用方错误，映射为 HTTP 415 而非 500。</p>
     *
     * @param e 媒体类型不支持异常
     * @return HTTP 415 响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public R<Void> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的请求媒体类型: {}", e.getContentType());
        return R.fail(415, "不支持的 Content-Type，请使用 application/json");
    }

    /**
     * 处理请求方法不被支持的异常。
     * <p>路径存在但 HTTP 方法不匹配属于调用方错误，必须映射为 HTTP 405，
     * 不能堕入兜底处理器伪装成 500；omni-common 的 GlobalExceptionHandler
     * 已有同构处理器，本 advice 必须同步补齐，否则 auth 服务行为与其他服务不一致。</p>
     *
     * @param e 请求方法不支持异常
     * @return HTTP 405 响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不被支持: {}{}", e.getMethod(), e.getSupportedHttpMethods() == null
                ? "" : "，允许的方法: " + e.getSupportedHttpMethods());
        return R.fail(405, "请求方法不允许: " + e.getMethod());
    }

    /**
     * 兜底处理所有未被特定处理器捕获的异常。
     * <p>以 ERROR 级别记录完整异常堆栈，但向客户端返回通用错误消息，
     * 避免泄露内部实现细节。</p>
     *
     * <p>HTTP 状态必须与 {@code docs/api-contract.md} 一致：500 系统错误对应
     * HTTP 500 + {@code code=500}，使前端能按 HTTP 层分类错误。</p>
     *
     * @param e 未处理的异常
     * @return HTTP 500 服务器内部错误响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("未预期的系统异常", e);
        return R.fail("服务器内部错误");
    }
}
