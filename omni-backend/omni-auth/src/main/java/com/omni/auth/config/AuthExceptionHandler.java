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
 * Exception handler for the auth module controllers.
 *
 * <p>Since the auth module depends on {@code omni-common-core} (not {@code omni-common}),
 * the {@code GlobalExceptionHandler} from {@code omni-common} is not within the component
 * scan scope. This local {@code @RestControllerAdvice} provides equivalent exception-to-{@link R}
 * conversion for the {@code com.omni.auth.controller} package.</p>
 *
 * <h3>Handled exception types:</h3>
 * <ul>
 *   <li>{@link BusinessException} — business rule violations (e.g., invalid captcha, wrong credentials)</li>
 *   <li>{@link MethodArgumentNotValidException} — Jakarta Bean Validation failures on {@code @Valid @RequestBody}</li>
 *   <li>{@link BindException} — form binding errors</li>
 *   <li>{@link Exception} — catch-all for unexpected errors (logged at ERROR level)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.omni.auth.controller")
public class AuthExceptionHandler {

    /**
     * Handle business rule violations thrown by service layer methods.
     * <p>Common scenarios: captcha expired/invalid, authentication failure.</p>
     *
     * @param e the business exception with error code and message
     * @return {@code R.fail(code, message)} wrapped in the standard response envelope
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * Handle Jakarta Bean Validation errors on {@code @Valid @RequestBody} parameters.
     * <p>Aggregates all field-level validation errors into a single semicolon-separated message,
     * e.g., {@code "username: Username is required; password: Password is required"}.</p>
     *
     * @param e the validation exception containing field error details
     * @return {@code R.fail(400, aggregated message)}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return R.fail(400, message);
    }

    /**
     * Handle form binding errors (query parameter or form data binding failures).
     *
     * @param e the bind exception containing field error details
     * @return {@code R.fail(400, aggregated message)}
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Bind exception: {}", message);
        return R.fail(400, message);
    }

    /**
     * Catch-all handler for unexpected errors not covered by specific handlers.
     * <p>Logs the full exception at ERROR level but returns a generic message to the client
     * to avoid leaking internal implementation details.</p>
     *
     * @param e the unhandled exception
     * @return {@code R.fail("Internal server error")}
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return R.fail("Internal server error");
    }
}
