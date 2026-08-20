package com.omni.dbmigrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.omni.dbmigrator.config.DbMigratorProperties;

/**
 * Omni-Stack 一次性数据库迁移程序入口。
 *
 * <p>该模块不启动 Web 服务，不注册 Nacos，也不依赖 Redis、MQ 或 XXL-JOB。
 * 所有数据库变更均由显式命令触发，执行完成后进程退出。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(DbMigratorProperties.class)
public class DbMigratorApplication {

    /**
     * 启动数据库迁移命令。
     *
     * @param args Spring Boot 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DbMigratorApplication.class, args);
    }
}
