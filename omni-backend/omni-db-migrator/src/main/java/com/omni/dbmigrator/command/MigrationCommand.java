package com.omni.dbmigrator.command;

import java.util.Arrays;

/**
 * 数据库迁移器支持的命令。
 */
public enum MigrationCommand {

    /** 校验全部 changelog。 */
    VALIDATE("validate"),
    /** 显示各目标库待执行 changeSet。 */
    STATUS("status"),
    /** 执行平台和目标库迁移。 */
    MIGRATE("migrate"),
    /** 对已存在且指纹匹配的数据库建立 Liquibase 接管记录。 */
    ADOPT_CURRENT("adopt-current"),
    /** 校验模块种子 manifest 与数据库状态。 */
    VERIFY_SEED("verify-seed");

    /** 外部命令名。 */
    private final String value;

    /**
     * 创建命令枚举。
     *
     * @param value 外部命令名
     */
    MigrationCommand(String value) {
        this.value = value;
    }

    /**
     * 解析外部命令。
     *
     * @param raw 原始命令
     * @return 命令枚举
     */
    public static MigrationCommand parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("数据库迁移命令不能为空");
        }
        String normalized = raw.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(command -> command.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的数据库迁移命令: " + raw));
    }

    /**
     * 获取外部命令名。
     *
     * @return 外部命令名
     */
    public String value() {
        return value;
    }
}
