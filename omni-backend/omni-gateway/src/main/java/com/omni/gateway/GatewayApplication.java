package com.omni.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关应用入口。
 * <p>
 * 基于 Spring Cloud Gateway（WebFlux 响应式模型）实现，
 * 负责请求路由转发、JWT 认证过滤和跨域处理等网关职责。
 * 通过 Nacos 注册中心发现下游微服务。</p>
 *
 * <p>核心组件：</p>
 * <ul>
 *   <li>{@link com.omni.gateway.filter.AuthFilter} — JWT 认证 + 用户身份注入</li>
 *   <li>{@link com.omni.gateway.config.CorsConfig} — CORS 跨域处理</li>
 *   <li>{@link com.omni.gateway.config.SecurityHeadersFilter} — 安全响应头</li>
 *   <li>{@link com.omni.gateway.config.JwkKeyProvider} — RSA 公钥获取与缓存</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    /**
     * 网关应用主入口方法。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
