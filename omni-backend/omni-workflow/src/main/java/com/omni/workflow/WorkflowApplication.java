package com.omni.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 工作流服务启动类。
 * <p>
 * 提供企业审批流全流程管理能力，包括流程定义部署、流程实例发起、
 * 任务审批（通过/驳回/加签/减签/委托）、流程监控等。
 * 依赖 {@code omni-common-workflow} 自动装配 Flowable 引擎。
 * </p>
 * <p>
 * 启动时通过 {@code @MapperScan} 自动扫描 {@code com.omni.workflow.mapper} 包下的
 * MyBatis Mapper 接口，并注册为 Nacos 服务发现实例。
 * </p>
 *
 * @author Omni-Stack Team
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.omni.workflow.client")
@SpringBootApplication
@MapperScan("com.omni.workflow.mapper")
public class WorkflowApplication {

    /**
     * 应用程序入口方法。
     * <p>
     * 启动 Spring Boot 应用上下文，初始化 Flowable 引擎及所有 Bean，
     * 并注册到 Nacos 服务中心。
     * </p>
     *
     * @param args 命令行参数，支持 Spring Boot 标准参数格式
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
