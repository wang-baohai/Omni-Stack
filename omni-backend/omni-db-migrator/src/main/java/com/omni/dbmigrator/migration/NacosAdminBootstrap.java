package com.omni.dbmigrator.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import com.omni.dbmigrator.config.DbMigratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** 将 Nacos 公开引导密码替换为本地秘密，重放时保护已修改账号。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosAdminBootstrap {

    /** 正式不可变种子中的引导密码摘要。 */
    static final String SEED_PASSWORD_HASH =
            "$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu";

    private final DbMigratorProperties properties;
    private final LiquibaseMigrationService migrationService;
    private final Environment environment;

    /** 只在配置了本地密码时处理正式 nacos 账号。 */
    public void initialize() {
        String password = environment.getProperty("NACOS_PASSWORD", "");
        if (password.isBlank()) {
            return;
        }
        if (!"nacos".equals(environment.getProperty("NACOS_USERNAME", "nacos"))) {
            throw new IllegalArgumentException("正式 Nacos 引导账号固定为 nacos");
        }
        try (Connection connection = DriverManager.getConnection(
                migrationService.targetUrl("nacos_config"),
                properties.adminUsername(), properties.adminPassword())) {
            int updated = rotateSeedPassword(connection, password);
            log.info("Nacos 管理员引导密码处理完成: updated={}，已修改账号保持原值", updated);
        } catch (Exception exception) {
            throw new IllegalStateException("Nacos 管理员引导密码初始化失败", exception);
        }
    }

    /** 使用厂商 BCrypt 格式，仅更新仍使用正式引导摘要的账号。 */
    static int rotateSeedPassword(Connection connection, String password) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET password = ? WHERE username = 'nacos' AND password = ?")) {
            statement.setString(1, new BCryptPasswordEncoder().encode(password));
            statement.setString(2, SEED_PASSWORD_HASH);
            return statement.executeUpdate();
        }
    }
}
