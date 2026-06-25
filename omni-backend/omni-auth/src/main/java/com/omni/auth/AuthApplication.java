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
 * <p>本服务基于 Spring Boot + Spring Cloud 架构，集成 MyBatis 持久层、
 * Redis 缓存、XXL-Job 任务调度等组件。通过 {@code @MapperScan} 自动扫描
 * {@code com.omni.auth.mapper} 包下的 Mapper 接口，
 * 通过 {@code @EnableDiscoveryClient} 注册至 Nacos 服务中心。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.oauth.OAuth2ProviderHandler
 * @see com.omni.auth.event.AuditEvent
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
