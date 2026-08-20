package com.omni.dbmigrator.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.omni.dbmigrator.config.DbMigratorProperties;
import com.omni.dbmigrator.migration.LiquibaseMigrationService;

/**
 * 执行配置中声明的一次性数据库命令。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationCommandRunner implements ApplicationRunner {

    /** 迁移器配置。 */
    private final DbMigratorProperties properties;
    /** Liquibase 迁移服务。 */
    private final LiquibaseMigrationService migrationService;

    /**
     * 执行数据库命令。
     *
     * @param args Spring Boot 参数
     */
    @Override
    public void run(ApplicationArguments args) {
        MigrationCommand command = MigrationCommand.parse(properties.command());
        log.info("开始执行数据库迁移命令: command={}", command.value());
        switch (command) {
            case VALIDATE -> migrationService.validateAll();
            case STATUS -> migrationService.status();
            case MIGRATE -> migrationService.migrate();
            case ADOPT_CURRENT -> throw new IllegalStateException(
                    "adopt-current 尚未开放：必须先完成 S0-05 指纹基线和 S0-06 备份前置检查");
            case VERIFY_SEED -> throw new IllegalStateException(
                    "verify-seed 尚未开放：必须先完成 S0-05 seed manifest 转换");
        }
        log.info("数据库迁移命令执行成功: command={}", command.value());
    }
}
