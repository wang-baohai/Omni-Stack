package com.omni.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Omni-Auth 认证授权服务应用入口。
 * <p>
 * 提供 OAuth 2.0 / OIDC 认证授权服务，包括用户登录、JWT 令牌签发、
 * 验证码生成校验、多租户管理等功能。通过 Nacos 注册中心对外提供服务。
 * </p>
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.omni.auth.mapper")
public class AuthApplication {

    /**
     * 认证服务主入口方法。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
