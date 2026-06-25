package com.omni.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 * <p>
 * 使用 BCrypt 算法进行密码加密和校验。
 * BCrypt 内置随机盐值，每次加密结果不同，有效防止彩虹表攻击。
 * 默认强度因子为 10（2^10 = 1024 轮迭代），可根据服务器性能调整。</p>
 *
 * @see BCryptPasswordEncoder
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 创建 BCrypt 密码编码器实例。
     * <p>
     * 默认强度因子 10，适用于大多数场景。
     * 用于用户注册时加密密码、登录时验证密码、修改密码等场景。</p>
     *
     * @return {@link BCryptPasswordEncoder} 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
