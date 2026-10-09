package com.omni.common.result;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
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
 * 全局异常处理器，将各类异常统一转换为标准的 {@link R} 响应格式。
 * <p>
 * 处理的异常类型包括：
 * <ul>
 *   <li>{@link BusinessException} — 业务规则异常，返回业务自定义错误码</li>
 *   <li>{@link MethodArgumentNotValidException} — 请求体参数校验失败（@Valid）</li>
 *   <li>{@link BindException} — 表单/查询参数绑定失败</li>
 *   <li>{@link MissingServletRequestParameterException} — 缺少必需的请求参数，返回 HTTP 400</li>
 *   <li>{@link NoResourceFoundException} — 请求的资源不存在，返回 HTTP 404</li>
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
     * 处理权限不足异常（{@code @PreAuthorize} 校验失败时抛出）。
     * <p>
     * Spring Security 6+ 使用 {@link AuthorizationDeniedException}，
     * 旧版本使用 {@link AccessDeniedException}，两者统一处理返回 403。
     * </p>
     *
     * @param e 权限不足异常
     * @return HTTP 403 响应
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDeniedException(Exception e) {
        log.warn("权限不足: {}", e.getMessage());
        return R.fail(403, "权限不足，拒绝访问");
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
     * 处理缺少必需请求参数的异常。
     * <p>例如 {@code GET /api/base/dict/data/list} 缺少 {@code typeCode} 参数时，
     * Spring 抛出此异常。映射为 HTTP 400 + {@code R.code=400}，
     * 与 {@code docs/api-contract.md} 的错误码速查表一致。</p>
     *
     * @param e 缺少请求参数异常
     * @return HTTP 400 响应，包含缺失参数名和期望类型
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        log.warn("缺少必需请求参数: {} (类型: {})", e.getParameterName(), e.getParameterType());
        return R.fail(400, "缺少必需参数: " + e.getParameterName());
    }

    /**
     * 处理请求的资源不存在的异常。
     * <p>Spring 6.2+ 在服务内路径无匹配处理器时抛出 {@link NoResourceFoundException}。
     * 映射为 HTTP 404 + {@code R.code=404}，区别于网关层的 404 和兜底的 500。</p>
     *
     * @param e 资源未找到异常
     * @return HTTP 404 响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return R.fail(404, "请求的资源不存在");
    }

    /**
     * 处理路径/查询参数类型不匹配的异常。
     *
     * <p>典型场景：{@code GET /api/auth/tenant/page} 被 {@code @GetMapping("/{id}")} 捕获，
     * Spring 无法把 {@code "page"} 转成 {@code Long}。此类请求属于调用方错误，
     * 必须映射为 HTTP 400 + {@code R.code=400}，而不是泄漏到兜底分支变成 HTTP 500。</p>
     *
     * @param e 参数类型不匹配异常
     * @return HTTP 400 响应，包含出错的参数名
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("方法参数类型不匹配: {} (期望 {})", e.getName(),
                e.getRequiredType() == null ? "unknown" : e.getRequiredType().getSimpleName());
        return R.fail(400, "参数格式错误: " + e.getName());
    }

    /**
     * 处理请求体媒体类型不被支持的异常。
     *
     * <p>典型场景：客户端以 {@code Content-Type: text/plain} 发送 JSON 请求体。
     * 属于调用方错误，必须映射为 HTTP 415，不得经兜底分支变成 HTTP 500。</p>
     *
     * @param e 媒体类型不支持异常
     * @return HTTP 415 响应
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public R<Void> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("不支持的请求媒体类型: {}", e.getContentType());
        return R.fail(415, "不支持的 Content-Type，请使用 application/json");
    }

    /**
     * 处理请求方法不被支持。
     * <p>
     * 路径存在但 HTTP 方法不匹配属于客户端错误，必须返回 405，
     * 不能堕入兜底处理器伪装成 500。
     * </p>
     *
     * @param e 请求方法不支持异常
     * @return HTTP 405 响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不被支持: {}{}", e.getMethod(), e.getSupportedHttpMethods() == null
                ? "" : "，允许的方法: " + e.getSupportedHttpMethods());
        return R.fail(405, "请求方法不允许: " + e.getMethod());
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
        log.error("未预期的系统异常: {}", e.getClass().getSimpleName(), e);
        return R.fail("服务器内部错误，请稍后重试");
    }
}
