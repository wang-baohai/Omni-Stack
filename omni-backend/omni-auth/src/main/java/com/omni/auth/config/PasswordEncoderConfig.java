package com.omni.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 * <p>使用 BCrypt 算法进行密码加密和校验。</p>
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 创建 BCrypt 密码编码器实例。
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
