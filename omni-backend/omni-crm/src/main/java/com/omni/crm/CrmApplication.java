package com.omni.crm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * CRM 微服务启动类。
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.omni.crm.client")
@MapperScan("com.omni.crm.mapper")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CrmApplication {

    /**
     * 启动 CRM 微服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
