package com.omni.procurement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 采购管理微服务启动类。
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.omni.procurement.client")
@MapperScan("com.omni.procurement.mapper")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ProcurementApplication {

    /**
     * 启动采购管理微服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ProcurementApplication.class, args);
    }
}
