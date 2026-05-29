package com.omni.common.core.result;

import lombok.Getter;

/**
 * Business exception with an error code.
 * <p>Throw this for business rule violations. GlobalExceptionHandler in
 * {@code omni-common} catches it and converts to {@link R}.
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
