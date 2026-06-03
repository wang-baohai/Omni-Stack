package com.omni.auth.service;

import com.omni.auth.dto.CaptchaResult;

/**
 * 验证码服务接口，定义验证码的生成和校验操作。
 */
public interface CaptchaService {

    /**
     * 生成新的验证码图片。
     *
     * @return 验证码结果，包含 Key（UUID）和 Base64 编码的图片
     */
    CaptchaResult generate();

    /**
     * 校验验证码是否与存储值匹配。
     *
     * @param captchaKey  验证码标识（UUID）
     * @param captchaCode 用户输入的验证码
     * @throws com.omni.common.core.result.BusinessException 验证码无效或已过期时抛出
     */
    void validate(String captchaKey, String captchaCode);
}
