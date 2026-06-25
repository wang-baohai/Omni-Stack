package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 验证码生成结果 DTO。
 * <p>包含验证码 Key（UUID）和 Base64 编码的 PNG 图片，
 * 由 {@code /api/auth/captcha} 接口返回，前端登录时提交 captchaKey + captchaCode 进行校验。</p>
 *
 * @author Omni-Stack Team
 * @see LoginRequest
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
