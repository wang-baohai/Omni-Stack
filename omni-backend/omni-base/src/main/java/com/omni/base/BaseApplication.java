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
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.omni.base.mapper")
public class BaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaseApplication.class, args);
    }
}
