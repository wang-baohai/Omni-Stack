package com.omni.srm.config;

import com.omni.common.core.result.R;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SRM 请求格式及方法参数约束异常处理器。
 *
 * @author Omni-Stack Team
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.omni.srm.controller")
public class SrmExceptionHandler {

    /**
     * 将未知字段或格式错误的 JSON 请求统一转换为 HTTP 400。
     *
     * @return 参数错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleUnreadableRequest() {
        return R.fail(400, "请求体包含未知字段或字段格式错误");
    }

    /**
     * 将已声明的方法参数约束失败转换为HTTP 400，不暴露参数值或内部调用链。
     *
     * @return 参数错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation() {
        return R.fail(400, "请求参数不符合约束");
    }
}
