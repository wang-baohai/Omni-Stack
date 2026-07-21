package com.omni.srm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * SRM 供应商关系管理微服务启动类。
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.omni.srm.client")
@MapperScan("com.omni.srm.mapper")
@SpringBootApplication
public class SrmApplication {

    /**
     * 启动 SRM 微服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SrmApplication.class, args);
    }
}
