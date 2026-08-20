package com.omni.dbmigrator.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据库迁移命令解析测试。
 */
class MigrationCommandTest {

    /**
     * 应解析全部公开命令名。
     */
    @Test
    void should_parse_all_commands_when_value_is_supported() {
        assertThat(MigrationCommand.parse("validate")).isEqualTo(MigrationCommand.VALIDATE);
        assertThat(MigrationCommand.parse("status")).isEqualTo(MigrationCommand.STATUS);
        assertThat(MigrationCommand.parse("migrate")).isEqualTo(MigrationCommand.MIGRATE);
        assertThat(MigrationCommand.parse("adopt-current")).isEqualTo(MigrationCommand.ADOPT_CURRENT);
        assertThat(MigrationCommand.parse("verify-seed")).isEqualTo(MigrationCommand.VERIFY_SEED);
    }

    /**
     * 应拒绝未知命令。
     */
    @Test
    void should_reject_command_when_value_is_unknown() {
        assertThatThrownBy(() -> MigrationCommand.parse("repair"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
    }
}
