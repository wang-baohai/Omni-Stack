package com.omni.dbmigrator.wp009;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * WP-009 受控增量执行器（D04 / RF08，test-classpath；不进入产品服务端点）。
 *
 * <p>仅对 Auth 正式 changelog 执行本包批准的精确 pending，复用当前 Liquibase 与
 * {@link Wp009MigrationGuard} 目标/集合守卫；在同一受信任连接上连接后立即核 UUID/schema、
 * 计算并在执行前复核 pending 集合；守卫不通过即在执行任何写操作前抛错停止。绝不
 * 调用 {@code ensureApplicationAccount}、ALTER/GRANT、{@code changeLogSync}、MARK_RAN、
 * clearCheckSums，也不执行其他模块增量。</p>
 *
 * @author Omni-Stack Team
 */
public final class Wp009MigrationExecutor {

    /** Auth 模块 changelog（本包增量所在）。 */
    private static final String AUTH_CHANGELOG = "database/changelog/auth/db.changelog-auth.yaml";
    /** Auth 更新 context。 */
    private static final String AUTH_CONTEXT = "auth";

    /** 执行配置：管理连接与期望 UUID。凭据来自受控环境，不落盘、不回显。 */
    public record ExecutorConfig(String adminUrl, String adminUser, String adminPassword, String expectedUuid) {
    }

    /** 执行结果。 */
    public record ExecutionResult(boolean applied, int pendingCount, List<String> appliedIds, String serverUuid) {
    }

    private Wp009MigrationExecutor() {
    }

    /**
     * 执行受控增量：目标绑定校验 → pending 集合校验 → 同连接复核 → Liquibase update（仅 Auth）。
     *
     * @param cfg 执行配置
     * @return 执行结果
     * @throws IllegalStateException 任一守卫不通过（错 UUID/错 schema/未知 pending）
     */
    public static ExecutionResult runIncrement(ExecutorConfig cfg) {
        try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor(
                Wp009MigrationExecutor.class.getClassLoader());
             Connection conn = DriverManager.getConnection(cfg.adminUrl(), cfg.adminUser(), cfg.adminPassword())) {

            // 1) 连接后立即核目标指纹（UUID/schema），错目标在任何写查询前停止
            Wp009MigrationGuard.TargetFingerprint actual = readFingerprint(conn);
            Wp009MigrationGuard.GuardResult targetResult =
                    Wp009MigrationGuard.verifyTarget(cfg.expectedUuid(), actual);
            if (!targetResult.ok()) {
                throw new IllegalStateException("目标绑定校验失败，拒绝执行: " + targetResult.rejections());
            }

            // 单连接、单 Liquibase 实例：pending 校验 → 同连接复核 → update，避免提前关闭共享连接
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            try (Liquibase liquibase = new Liquibase(AUTH_CHANGELOG, accessor, database)) {
                // 2) pending 集合必须恰等于本包批准集合
                List<ChangeSet> pending = liquibase.listUnrunChangeSets(new Contexts(AUTH_CONTEXT), new LabelExpression());
                Wp009MigrationGuard.GuardResult pendingResult =
                        Wp009MigrationGuard.verifyPendingMatchesApproved(pending);
                if (!pendingResult.ok()) {
                    throw new IllegalStateException("pending 集合与本包批准不一致，拒绝执行: " + pendingResult.rejections());
                }
                // 3) 同一实例执行前复核（缩短竞态窗口）
                List<ChangeSet> rechecked = liquibase.listUnrunChangeSets(new Contexts(AUTH_CONTEXT), new LabelExpression());
                Wp009MigrationGuard.GuardResult recheck =
                        Wp009MigrationGuard.verifyPendingMatchesApproved(rechecked);
                if (!recheck.ok()) {
                    throw new IllegalStateException("执行前复核 pending 漂移，拒绝执行: " + recheck.rejections());
                }
                // 4) 仅对 Auth changelog 执行 update（无账号管理、无 baseline 同步）
                List<String> appliedIds = pending.stream().map(ChangeSet::getId).toList();
                liquibase.update(new Contexts(AUTH_CONTEXT), new LabelExpression());
                return new ExecutionResult(true, pending.size(), appliedIds, actual.serverUuid());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("WP-009 受控增量执行失败", e);
        }
    }

    private static Wp009MigrationGuard.TargetFingerprint readFingerprint(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            String uuid;
            try (ResultSet rs = st.executeQuery("SELECT @@server_uuid")) {
                rs.next();
                uuid = rs.getString(1);
            }
            String schema;
            try (ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                rs.next();
                schema = rs.getString(1);
            }
            return new Wp009MigrationGuard.TargetFingerprint(uuid, schema);
        }
    }
}
