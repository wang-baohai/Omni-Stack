package com.omni.auth.service;

import com.omni.auth.dto.CaptchaResult;

/**
 * Captcha generation and validation service.
 */
public interface CaptchaService {

    /**
     * Generate a new captcha image.
     *
     * @return captcha key and base64-encoded image
     */
    CaptchaResult generate();

    /**
     * Validate a captcha code against the stored value.
     *
     * @param captchaKey  the captcha identifier
     * @param captchaCode the user-submitted code
     * @throws com.omni.common.core.result.BusinessException if invalid or expired
     */
    void validate(String captchaKey, String captchaCode);
}
