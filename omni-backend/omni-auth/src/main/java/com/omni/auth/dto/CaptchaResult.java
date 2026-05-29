package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Captcha generation result containing the key and base64-encoded image.
 */
@Data
@Builder
public class CaptchaResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String captchaKey;
    private String captchaImage;
}
