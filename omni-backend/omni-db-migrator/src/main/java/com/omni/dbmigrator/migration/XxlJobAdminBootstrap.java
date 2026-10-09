package com.omni.dbmigrator.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HexFormat;

import com.omni.dbmigrator.config.DbMigratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 将调度管理员的公开引导密码替换为本地配置，只处理尚未修改的正式种子账号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XxlJobAdminBootstrap {

    /** 正式种子中公开引导密码的摘要。 */
    static final String SEED_PASSWORD_HASH =
            "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";

    private final DbMigratorProperties properties;
    private final LiquibaseMigrationService migrationService;
    private final Environment environment;

    /**
     * 在迁移完成后初始化固定 admin 账号；未配置密码时兼容独立迁移命令。
     */
    public void initialize() {
        String password = environment.getProperty("XXL_JOB_ADMIN_PASSWORD", "");
        if (password.isBlank()) {
            return;
        }
        if (!"admin".equals(environment.getProperty("XXL_JOB_ADMIN_USERNAME", "admin"))) {
            throw new IllegalArgumentException("正式 XXL-JOB 引导账号固定为 admin");
        }
        try (Connection connection = DriverManager.getConnection(
                migrationService.targetUrl("xxl_job"),
                properties.adminUsername(), properties.adminPassword())) {
            int updated = rotateSeedPassword(connection, password);
            log.info("XXL-JOB 管理员引导密码处理完成: updated={}，已修改的账号保持原值", updated);
        } catch (Exception exception) {
            throw new IllegalStateException("XXL-JOB 管理员引导密码初始化失败", exception);
        }
    }

    /**
     * 条件更新避免迁移重放覆盖已配置的管理员密码。
     *
     * @param connection 迁移管理连接
     * @param password 本地管理员密码
     * @return 更新行数
     * @throws Exception 摘要计算或数据库更新失败
     */
    static int rotateSeedPassword(Connection connection, String password) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(password.getBytes(StandardCharsets.UTF_8)));
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE xxl_job_user SET password = ? WHERE id = 1 AND username = 'admin' "
                        + "AND password = ?")) {
            statement.setString(1, hash);
            statement.setString(2, SEED_PASSWORD_HASH);
            return statement.executeUpdate();
        }
    }
}
