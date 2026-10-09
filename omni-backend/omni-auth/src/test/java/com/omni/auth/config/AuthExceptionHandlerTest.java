package com.omni.auth.config;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuthExceptionHandler} 的错误合同回归测试（WP-012 / AC04-AC05）。
 *
 * <p>本类只断言两类「机器可判别」的稳定事实：</p>
 * <ol>
 *   <li>响应体 {@code R.code} 的取值与 {@code message} 非空（不得返回空串提示）；</li>
 *   <li>处理器方法上的 {@code @ResponseStatus} 元数据与 {@code docs/api-contract.md}
 *       §2 错误码速查表一致（HTTP 状态与业务码对齐）。</li>
 * </ol>
 *
 * <p>说明：不使用 {@code MockMvc}，因为 advice 被
 * {@code @RestControllerAdvice(basePackages = "com.omni.auth.controller")} 限定，
 * 测试用例内的探针 Controller 无法落在该包（本模块测试目录受文件所有权约束），
 * 故 HTTP 状态的端到端行为由 S05 的真实网关/E2E 路径验证，此处锁定注解元数据与 code。</p>
 */
class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    /** 用于构造 {@link MethodArgumentNotValidException} 的探针方法参数。 */
    @SuppressWarnings("unused")
    static void probePayload(String username) {
        // 仅作为 MethodParameter 的来源，不参与断言。
    }

    /** 绑定目标对象（Bean 名与字段名均为 ASCII，不涉及任何自然语言）。 */
    static final class Payload {
        private String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    /** 构造带（或不带）字段错误的校验异常。 */
    private MethodArgumentNotValidException validationException(FieldError... errors) {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Payload(), "payload");
        for (FieldError error : errors) {
            bindingResult.addError(error);
        }
        try {
            Method method = AuthExceptionHandlerTest.class
                    .getDeclaredMethod("probePayload", String.class);
            return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("探针方法缺失", e);
        }
    }

    /** 读取处理器方法上的 {@code @ResponseStatus}，未标注时返回 {@code null}。 */
    private HttpStatus declaredStatus(String methodName, Class<?>... parameterTypes) {
        try {
            Method method = AuthExceptionHandler.class
                    .getDeclaredMethod(methodName, parameterTypes);
            ResponseStatus annotation = method.getAnnotation(ResponseStatus.class);
            return annotation == null ? null : annotation.value();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("处理器方法签名变更: " + methodName, e);
        }
    }

    @Test
    @DisplayName("兜底 Exception 必须 HTTP 500 + code=500（api-contract.md §2）")
    void fallbackExceptionAlignsHttp500WithCode500() {
        R<Void> response = handler.handleException(new IllegalStateException("内部细节"));

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isNotBlank();
        assertThat(declaredStatus("handleException", Exception.class))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("兜底文案不得泄漏内部异常类型与堆栈信息")
    void fallbackExceptionDoesNotLeakInternalDetail() {
        R<Void> response = handler.handleException(new IllegalStateException("SQLSyntaxErrorException near"));

        assertThat(response.getMessage()).doesNotContain("SQLSyntaxError")
                .doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("Bean 校验失败必须 HTTP 400 + code=400 且保留 ASCII 字段名")
    void validationExceptionMapsToBadRequestWithFieldName() {
        MethodArgumentNotValidException exception = validationException(
                new FieldError("payload", "username", "不能为空"));

        R<Void> response = handler.handleValidationException(exception);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).contains("username");
        assertThat(declaredStatus("handleValidationException", MethodArgumentNotValidException.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("校验异常无字段错误时给出兜底文案，禁止空串提示")
    void validationExceptionWithoutFieldErrorsFallsBackToNonBlankMessage() {
        R<Void> response = handler.handleValidationException(validationException());

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isNotBlank();
    }

    /** 构造只有全局错误（无字段错误）的绑定异常，用于验证兜底文案分支。 */
    private BindException bindExceptionWithoutFieldErrors() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Payload(), "payload");
        // 全局错误不进入 getFieldErrors()，因此聚合结果为空 Optional，必须命中 orElse 兜底。
        bindingResult.addError(new ObjectError("payload", "整体校验不通过"));
        return new BindException(bindingResult);
    }

    @Test
    @DisplayName("表单绑定异常无字段错误时给出兜底文案，禁止空串提示")
    void bindExceptionWithoutFieldErrorsFallsBackToNonBlankMessage() {
        R<Void> response = handler.handleBindException(bindExceptionWithoutFieldErrors());

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("表单绑定异常必须 HTTP 400 + code=400")
    void bindExceptionMapsToBadRequest() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Payload(), "payload");
        BindException exception = new BindException(bindingResult);
        exception.addError(new FieldError("payload", "dictType", "不能为空"));

        R<Void> response = handler.handleBindException(exception);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).contains("dictType");
        assertThat(declaredStatus("handleBindException", BindException.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("权限不足必须 HTTP 403 + code=403，且与 500 区分")
    void accessDeniedMapsToForbidden() {
        R<Void> response = handler.handleAccessDeniedException(new AccessDeniedException("denied"));

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getMessage()).isNotBlank();
        assertThat(declaredStatus("handleAccessDeniedException", AccessDeniedException.class))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("业务异常保留 code 且不加 @ResponseStatus：登录凭据失败仍为 HTTP 200 + code=401")
    void businessExceptionKeepsCodeAndDefaultsToHttp200() {
        R<Void> response = handler.handleBusinessException(new BusinessException(401, "认证失败"));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(declaredStatus("handleBusinessException", BusinessException.class)).isNull();
    }

    @Test
    @DisplayName("稳定响应字段冻结：仅 code/message/data，不得新增 subCode")
    void responseEnvelopeFieldsRemainFrozen() {
        Set<String> fields = java.util.Arrays.stream(R.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic() && !"serialVersionUID".equals(field.getName()))
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("code", "message", "data");
    }
}
