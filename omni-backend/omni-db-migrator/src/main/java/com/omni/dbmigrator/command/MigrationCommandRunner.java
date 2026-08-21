package com.omni.dbmigrator.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.omni.dbmigrator.adoption.AdoptionService;
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
    /** 既有数据库双阶段接管服务。 */
    private final AdoptionService adoptionService;

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
            case MIGRATE -> {
                migrationService.migrate();
                seedVerificationService.verifyAll();
            }
            case ADOPT_CURRENT -> adoptionService.adoptCurrent();
            case VERIFY_SEED -> seedVerificationService.verifyAll();
        }
        log.info("数据库迁移命令执行成功: command={}", command.value());
    }
}
