package com.omni.dbmigrator.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.omni.dbmigrator.adoption.SchemaFingerprintService;
import com.omni.dbmigrator.config.DbMigratorProperties;
import com.omni.dbmigrator.migration.LiquibaseMigrationService;
import com.omni.dbmigrator.seed.SeedVerificationService;

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
    /** 种子数据校验服务。 */
    private final SeedVerificationService seedVerificationService;
    /** 既有数据库强结构指纹服务。 */
    private final SchemaFingerprintService schemaFingerprintService;

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
            case ADOPT_CURRENT -> {
                schemaFingerprintService.verifyAll();
                seedVerificationService.verifyAll();
                throw new IllegalStateException(
                        "adopt-current 写入仍关闭：必须完成 S0-06 备份证据和双阶段人工确认");
            }
            case VERIFY_SEED -> seedVerificationService.verifyAll();
        }
        log.info("数据库迁移命令执行成功: command={}", command.value());
    }
}
