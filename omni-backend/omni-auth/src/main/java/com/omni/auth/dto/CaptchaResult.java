package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 验证码生成结果，包含验证码 Key 和 Base64 编码的图片。
 */
@Data
@Builder
public class CaptchaResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 验证码唯一标识（UUID），用于 Redis 存储键 */
    private String captchaKey;
    /** Base64 编码的验证码 PNG 图片 */
    private String captchaImage;
}
