package com.omni.dbmigrator.command;

import com.omni.dbmigrator.adoption.AdoptionService;
import com.omni.dbmigrator.config.DbMigratorProperties;
import com.omni.dbmigrator.migration.LiquibaseMigrationService;
import com.omni.dbmigrator.seed.SeedManifestLoader;
import com.omni.dbmigrator.seed.SeedVerificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

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
    /** 一次性迁移指标注册表。 */
    private final MeterRegistry meterRegistry;
    /** 受版本控制的种子清单加载器。 */
    private final SeedManifestLoader seedManifestLoader;

    /**
     * 执行数据库命令。
     *
     * @param args Spring Boot 参数
     */
    @Override
    public void run(ApplicationArguments args) {
        MigrationCommand command = MigrationCommand.parse(properties.command());
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "failure";
        log.info("开始执行数据库迁移命令: command={}", command.value());
        try {
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
            result = "success";
            registerSchemaVersionInfo();
            log.info("数据库迁移命令执行成功: command={}", command.value());
        } finally {
            Counter.builder("omni.db.migration.operations")
                    .description("Database migration command outcomes")
                    .tag("result", result)
                    .register(meterRegistry)
                    .increment();
            sample.stop(Timer.builder("omni.db.migration.duration")
                    .description("Database migration command duration")
                    .publishPercentileHistogram()
                    .register(meterRegistry));
        }
    }

    /**
     * 暴露受控种子清单版本；版本值来源固定配置，不来自外部请求。
     */
    private void registerSchemaVersionInfo() {
        try {
            String version = seedManifestLoader.load(properties.seedManifest()).version();
            Gauge.builder("omni.db.schema.version.info", new AtomicInteger(1), AtomicInteger::get)
                    .description("Current managed database schema manifest version")
                    .tag("version", version)
                    .strongReference(true)
                    .register(meterRegistry);
        } catch (RuntimeException exception) {
            log.warn("数据库清单版本指标注册失败，迁移结果不受影响: {}", exception.getMessage());
        }
    }
}
