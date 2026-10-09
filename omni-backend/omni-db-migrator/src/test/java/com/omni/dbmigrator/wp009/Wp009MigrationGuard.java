package com.omni.dbmigrator.wp009;

import liquibase.changelog.ChangeSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * WP-009 受控增量迁移守卫（D04 / RF08，纯逻辑、可离线单测）。
 *
 * <p>本类只做“执行前判定”，不持有连接、不执行任何写操作，因此可在无数据库环境下
 * 用正/负例证明固定集合、错 UUID/错计划/未知 pending 的拒绝语义。实际 Liquibase
 * 执行由 {@link Wp009MigrationExecutor} 在同一受信任连接上、复核通过后完成。</p>
 *
 * <p>安全合同：批准集合不可被外部参数放宽；未知或额外 pending 一律拒绝；
 * 不调用 ensureApplicationAccount / ALTER / GRANT / changeLogSync / MARK_RAN / clearCheckSums。</p>
 *
 * @author Omni-Stack Team
 */
public final class Wp009MigrationGuard {

    /** 一个 changeSet 的稳定身份三元组。 */
    public record ChangeSetKey(String id, String author, String path) {
    }

    /** 本包批准的唯一 Auth 增量集合（已初始化库上 M 待跑；F 已跑）。固定，不可外部放宽。 */
    public static final Set<ChangeSetKey> APPROVED_AUTH_INCREMENT = Set.of(
            new ChangeSetKey("auth-0006-wp009-login-policy-table", "omni-stack",
                    "database/changelog/auth/0006-wp009-auth-management.yaml"),
            new ChangeSetKey("auth-0006-wp009-permissions", "omni-stack",
                    "database/changelog/auth/0006-wp009-auth-management.yaml"),
            new ChangeSetKey("auth-0006-wp009-oauth2-metadata-widen", "omni-stack",
                    "database/changelog/auth/0006-wp009-auth-management.yaml"));

    /** Auth 库 allowlist 目标 schema。 */
    public static final String ALLOWED_SCHEMA = "omni_auth";

    /** UUID 与 schema 校验（由调用方从受信任连接读取实际值后传入）。 */
    public record TargetFingerprint(String serverUuid, String schema) {
    }

    /** 校验结果：拒绝原因列表；为空表示可执行。 */
    public record GuardResult(List<String> rejections) {
        public boolean ok() {
            return rejections.isEmpty();
        }
    }

    private Wp009MigrationGuard() {
    }

    /**
     * 校验目标绑定：期望 UUID 与实际一致，schema 命中 allowlist。
     *
     * @param expectedUuid 期望 server_uuid
     * @param actual       实际目标指纹
     * @return 拒绝原因（不匹配即拒绝，调用方须在发起任何写查询前停止）
     */
    public static GuardResult verifyTarget(String expectedUuid, TargetFingerprint actual) {
        Set<String> reasons = new LinkedHashSet<>();
        if (expectedUuid == null || expectedUuid.isBlank()) {
            reasons.add("expected UUID must not be blank");
        }
        if (actual == null) {
            reasons.add("actual target fingerprint is null");
        } else {
            if (actual.serverUuid() == null || !actual.serverUuid().equalsIgnoreCase(expectedUuid)) {
                reasons.add("server_uuid mismatch: expected=" + expectedUuid + " actual=" + actual.serverUuid());
            }
            if (!ALLOWED_SCHEMA.equals(actual.schema())) {
                reasons.add("schema not in allowlist: actual=" + actual.schema() + " allowed=" + ALLOWED_SCHEMA);
            }
        }
        return new GuardResult(List.copyOf(reasons));
    }

    /**
     * 校验 Liquibase 报告的 pending 集合恰等于本包批准集合。
     * 任何未知/额外/缺失项都拒绝，避免“顺手”跑其他模块增量。
     *
     * @param pending Liquibase listUnrunChangeSets 结果
     * @return 拒绝原因；为空表示 pending == APPROVED
     */
    public static GuardResult verifyPendingMatchesApproved(List<ChangeSet> pending) {
        Set<String> reasons = new LinkedHashSet<>();
        Set<ChangeSetKey> actual = new LinkedHashSet<>();
        if (pending != null) {
            for (ChangeSet cs : pending) {
                actual.add(new ChangeSetKey(cs.getId(), cs.getAuthor(), normalizePath(cs.getFilePath())));
            }
        }
        for (ChangeSetKey key : APPROVED_AUTH_INCREMENT) {
            if (!actual.contains(key)) {
                reasons.add("approved changeSet not present in pending: " + key.id());
            }
        }
        for (ChangeSetKey key : actual) {
            if (!APPROVED_AUTH_INCREMENT.contains(key)) {
                reasons.add("unknown/extra pending changeSet (refuse to run non-approved increment): " + key.id());
            }
        }
        return new GuardResult(List.copyOf(reasons));
    }

    private static String normalizePath(String filePath) {
        if (filePath == null) {
            return null;
        }
        String p = filePath.replace('\\', '/');
        int idx = p.indexOf("database/changelog");
        return idx >= 0 ? p.substring(idx) : p;
    }
}
