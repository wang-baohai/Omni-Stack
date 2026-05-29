package com.omni.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Omni-Auth Authorization Server.
 * <p>Provides OAuth 2.0 / OIDC authentication and authorization services.</p>
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.omni.auth.mapper")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
