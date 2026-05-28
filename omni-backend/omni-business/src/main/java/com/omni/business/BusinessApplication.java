package com.omni.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Business microservice application entry point.
 * Provides core business APIs with Nacos discovery, OpenFeign, and Sentinel integration.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class BusinessApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessApplication.class, args);
    }
}
