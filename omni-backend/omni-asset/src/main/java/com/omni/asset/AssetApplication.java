package com.omni.asset;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 资产管理微服务启动类。
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.omni.asset.client")
@MapperScan("com.omni.asset.mapper")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AssetApplication {

    /**
     * 启动资产管理微服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AssetApplication.class, args);
    }
}
