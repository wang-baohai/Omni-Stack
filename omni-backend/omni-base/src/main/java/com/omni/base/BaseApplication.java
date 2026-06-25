package com.omni.base;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Base 服务启动类。
 * <p>
 * 提供基础数据管理功能，首个模块为数据字典（字典类型 + 字典项）的增删改查。
 * 依赖 {@code omni-common-mybatis} 和 {@code omni-common-redis} 自动装配。
 * </p>
 * <p>
 * 启动时通过 {@code @MapperScan} 自动扫描 {@code com.omni.base.mapper} 包下的
 * MyBatis Mapper 接口，并注册为 Nacos 服务发现实例。
 * </p>
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.omni.base.mapper")
public class BaseApplication {

    /**
     * 应用程序入口方法。
     * <p>
     * 启动 Spring Boot 应用上下文，初始化所有 Bean 并注册到 Nacos 服务中心。
     * </p>
     *
     * @param args 命令行参数，支持 Spring Boot 标准参数格式
     */
    public static void main(String[] args) {
        SpringApplication.run(BaseApplication.class, args);
    }
}
