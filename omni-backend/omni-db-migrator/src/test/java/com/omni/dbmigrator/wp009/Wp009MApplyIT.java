package com.omni.dbmigrator.wp009;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * WP-009 R13 M 受控增量真实执行入口（test-classpath，D04；默认禁用，仅显式确认开关+环境凭据时运行）。
 *
 * <p>安全设计：凭据经环境变量注入（不进命令行参数/日志）；连接与守卫由
 * {@link Wp009MigrationExecutor} 完成——错 UUID/错 schema/未知或额外 pending 在执行任何写前抛错停止。
 * 仅当 {@code -Dwp009.m.apply=YES} 且提供 {@code WP009_APPLY_ADMIN_URL/USER/PASSWORD} 与
 * {@code -Dwp009.m.expectedUuid} 时运行，防止误触发。执行结果打印脱敏（changeSet id 与计数），不回显数据/PII。</p>
 *
 * @author Omni-Stack Team
 */
@EnabledIfSystemProperty(named = "wp009.m.apply", matches = "YES")
class Wp009MApplyIT {

    @Test
    @DisplayName("R13: 对 M 执行本包受控 Auth 增量（守卫前置，写前失败关闭）")
    void applyControlledIncrement() {
        // 优先从环境变量读取 URL/用户/UUID（避免 JDBC URL 中 '&' 被 Windows cmd 拆断）；回退到系统属性。
        String url = envOrProp("WP009_M_ADMIN_URL", "wp009.m.adminUrl");
        String user = envOrProp("WP009_M_ADMIN_USER", "wp009.m.adminUser");
        String pass = System.getenv("WP009_APPLY_ADMIN_PASSWORD");
        String expectedUuid = envOrProp("WP009_M_EXPECTED_UUID", "wp009.m.expectedUuid");
        requireNonBlank("adminUrl", url);
        requireNonBlank("adminUser", user);
        requireNonBlank("expectedUuid", expectedUuid);
        if (pass == null || pass.isBlank()) {
            throw new IllegalStateException("WP009_APPLY_ADMIN_PASSWORD 环境变量缺失，拒绝执行（不硬编码凭据）");
        }

        Wp009MigrationExecutor.ExecutionResult r = Wp009MigrationExecutor.runIncrement(
                new Wp009MigrationExecutor.ExecutorConfig(url, user, pass, expectedUuid));

        System.out.println("WP009_M_APPLY_RESULT applied=" + r.applied()
                + " pendingCount=" + r.pendingCount()
                + " appliedIds=" + r.appliedIds()
                + " uuid=" + r.serverUuid());
    }

    private static String envOrProp(String envName, String propName) {
        String v = System.getenv(envName);
        return (v != null && !v.isBlank()) ? v : System.getProperty(propName);
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少必需参数 " + name);
        }
    }
}
