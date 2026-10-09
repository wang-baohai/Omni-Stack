package com.omni.procurement.fixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProcurementFixtureTool/FixtureSupport 本地行为保护测试（R04）。
 *
 * <p>覆盖返修 Goal 3.2/3.3 可离线验证的行为：registry 空文件/坏 JSON/缺失拒绝、
 * 原子持久化往返与无临时残留、规范化 SHA-256 摘要与常量时间比较、
 * 多层扁平树叶子判定、OS 文件锁互斥、固定目标索引、schema 校验、
 * ID 精度转换（超 Long 快速失败）。全部直接调用真实实现——
 * 无反射、不在测试内重写被测逻辑（旧交付测试的两类反例）。</p>
 *
 * @author Omni-Stack WP-005 Repair
 */
@DisplayName("采购 Fixture 工具本地行为保护（R04）")
class ProcurementFixtureToolTest {

    @TempDir
    Path tempDir;

    /** 构造扁平品类行（id/parent_id/deleted，与 JDBC 查询列一致）。 */
    private static Map<String, Object> row(long id, long parentId, int deleted) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("parent_id", parentId);
        row.put("deleted", deleted);
        return row;
    }

    // ==================== registry 加载拒绝 ====================

    @Test
    @DisplayName("registry 空文件不能当作空 registry")
    void emptyRegistryFileRejected() throws Exception {
        Path registry = tempDir.resolve("registry.json");
        Files.writeString(registry, "", StandardCharsets.UTF_8);
        IOException ex = assertThrows(IOException.class, () -> FixtureSupport.loadJsonMap(registry));
        assertTrue(ex.getMessage().contains("为空"), "应说明空文件拒绝: " + ex.getMessage());
    }

    @Test
    @DisplayName("registry 损坏 JSON 不能当作空 registry")
    void corruptRegistryFileRejected() throws Exception {
        Path registry = tempDir.resolve("registry.json");
        Files.writeString(registry, "{broken json", StandardCharsets.UTF_8);
        IOException ex = assertThrows(IOException.class, () -> FixtureSupport.loadJsonMap(registry));
        assertTrue(ex.getMessage().contains("JSON 解析失败"), "应说明解析失败: " + ex.getMessage());
    }

    @Test
    @DisplayName("registry 文件缺失必须失败而非返回空")
    void missingRegistryFileRejected() {
        Path registry = tempDir.resolve("not-exist.json");
        IOException ex = assertThrows(IOException.class, () -> FixtureSupport.loadJsonMap(registry));
        assertTrue(ex.getMessage().contains("不存在"), "应说明文件缺失: " + ex.getMessage());
    }

    // ==================== 原子持久化 ====================

    @Test
    @DisplayName("atomicWriteJson 写入后可读回且无临时文件残留")
    void atomicWriteRoundTripWithoutTempResidue() throws Exception {
        Path registry = tempDir.resolve("nested").resolve("registry.json");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", FixtureSupport.SCHEMA_VERSION);
        data.put("fixtureId", "wp005-gen1");
        data.put("entries", new ArrayList<Map<String, Object>>());

        FixtureSupport.atomicWriteJson(registry, data);

        assertTrue(Files.exists(registry), "registry 应存在（父目录自动创建）");
        Map<String, Object> loaded = FixtureSupport.loadJsonMap(registry);
        assertEquals(FixtureSupport.SCHEMA_VERSION,
                ((Number) loaded.get("schemaVersion")).intValue());
        assertEquals("wp005-gen1", loaded.get("fixtureId"));
        try (var files = Files.list(registry.getParent())) {
            long tmpCount = files.filter(p -> p.getFileName().toString().contains(".tmp-")).count();
            assertEquals(0L, tmpCount, "成功路径不应残留临时文件");
        }
    }

    // ==================== 规范化摘要 ====================

    @Test
    @DisplayName("canonicalDigest 稳定且为 sha256: 前缀 64 hex（旧 digest- 已废弃）")
    void canonicalDigestStableAndPrefixed() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("fixtureId", "wp005-gen1");
        plan.put("tenantId", 1L);
        String d1 = FixtureSupport.canonicalDigest(plan);
        String d2 = FixtureSupport.canonicalDigest(plan);
        assertEquals(d1, d2, "同内容摘要必须一致");
        assertTrue(d1.startsWith("sha256:"), "摘要应为 sha256: 前缀: " + d1);
        assertEquals("sha256:".length() + 64, d1.length(), "SHA-256 hex 长度应为 64");
    }

    @Test
    @DisplayName("canonicalDigest 与键序无关")
    void canonicalDigestKeyOrderIndependent() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("alpha", 1);
        a.put("beta", "x");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("beta", "x");
        b.put("alpha", 1);
        assertEquals(FixtureSupport.canonicalDigest(a), FixtureSupport.canonicalDigest(b),
                "规范化序列化应排序键，键序不影响摘要");
    }

    @Test
    @DisplayName("内容不同的计划摘要不同")
    void canonicalDigestChangesWithContent() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("generation", 1);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("generation", 2);
        assertNotEquals(FixtureSupport.canonicalDigest(a), FixtureSupport.canonicalDigest(b),
                "不同内容不得产生相同摘要");
    }

    @Test
    @DisplayName("digestEquals 常量时间比较不误判前缀/长度/空值")
    void digestEqualsRejectsPrefixAndNull() {
        String full = "sha256:" + "a".repeat(64);
        assertTrue(FixtureSupport.digestEquals(full, full));
        assertFalse(FixtureSupport.digestEquals(full, full.substring(0, 20)),
                "前缀子串不得误判相等");
        assertFalse(FixtureSupport.digestEquals(null, full), "空期望必须为 false");
        assertFalse(FixtureSupport.digestEquals(full, null), "空实际必须为 false");
        assertFalse(FixtureSupport.digestEquals(full, "sha256:" + "b".repeat(64)));
    }

    // ==================== 叶子判定（多层扁平树） ====================

    @Test
    @DisplayName("isLeaf 基于扁平 parent_id 行判定且支持多层树（不限两层）")
    void isLeafJudgesMultiLevelFlatRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(1, 0, 0));
        rows.add(row(2, 1, 0));
        rows.add(row(3, 2, 0));
        assertTrue(ProcurementFixtureTool.isLeaf(rows, row(3, 2, 0)), "第三层无未删子应为叶子");
        assertFalse(ProcurementFixtureTool.isLeaf(rows, row(2, 1, 0)), "第二层有未删子不是叶子");
        assertFalse(ProcurementFixtureTool.isLeaf(rows, row(1, 0, 0)), "第一层有未删子不是叶子");
    }

    @Test
    @DisplayName("isLeaf 不被软删子品类阻断")
    void isLeafIgnoresSoftDeletedChildren() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(2, 1, 0));
        rows.add(row(4, 2, 1));
        assertTrue(ProcurementFixtureTool.isLeaf(rows, row(2, 1, 0)),
                "仅软删子指向时仍应视为叶子");
    }

    // ==================== 目标锁 ====================

    @Test
    @DisplayName("同 JVM 同目标锁互斥，释放后可重取")
    void targetLockMutexAndReuse() throws Exception {
        String targetKey = FixtureSupport.physicalTargetKey("uuid-mutex", "omni_procurement", 1L);
        FixtureSupport.TargetLock first = FixtureSupport.TargetLock.tryAcquire(tempDir, targetKey);
        assertNotNull(first, "首次应获得锁");
        FixtureSupport.TargetLock second = FixtureSupport.TargetLock.tryAcquire(tempDir, targetKey);
        assertNull(second, "同目标第二把锁必须被拒绝而非排队等待");
        first.close();
        FixtureSupport.TargetLock third = FixtureSupport.TargetLock.tryAcquire(tempDir, targetKey);
        assertNotNull(third, "释放后应可重取");
        third.close();
    }

    @Test
    @DisplayName("不同物理目标锁互不影响")
    void targetLocksIndependentAcrossTargets() throws Exception {
        String keyA = FixtureSupport.physicalTargetKey("uuid-a", "db", 1L);
        String keyB = FixtureSupport.physicalTargetKey("uuid-b", "db", 1L);
        FixtureSupport.TargetLock a = FixtureSupport.TargetLock.tryAcquire(tempDir, keyA);
        FixtureSupport.TargetLock b = FixtureSupport.TargetLock.tryAcquire(tempDir, keyB);
        assertNotNull(a);
        assertNotNull(b, "不同目标不得互相阻塞");
        a.close();
        b.close();
    }

    // ==================== 固定目标索引 ====================

    @Test
    @DisplayName("固定目标索引写读一致，缺失返回 null")
    void activeRegistryIndexRoundTrip() throws Exception {
        String targetKey = FixtureSupport.physicalTargetKey("uuid-idx", "omni_procurement", 7L);
        assertNull(FixtureSupport.readActiveRegistryIndex(tempDir, targetKey), "无索引应为 null");
        Path registry = tempDir.resolve("artifacts").resolve("registry-1.json");
        Files.createDirectories(registry.getParent());
        Files.writeString(registry, "{}", StandardCharsets.UTF_8);
        FixtureSupport.writeActiveRegistryIndex(tempDir, targetKey, registry);
        Path read = FixtureSupport.readActiveRegistryIndex(tempDir, targetKey);
        assertNotNull(read);
        assertEquals(registry.toAbsolutePath().toString(), String.valueOf(read),
                "索引必须精确保留未决 registry 路径（不随 artifact 目录漂移）");
    }

    // ==================== schema 校验 ====================

    @Test
    @DisplayName("checkRegistrySchema 接受 v4 结构并拒绝旧版本/缺 plan")
    void checkRegistrySchemaAcceptsV4Only() {
        Map<String, Object> good = new LinkedHashMap<>();
        good.put("schemaVersion", FixtureSupport.SCHEMA_VERSION);
        good.put("entries", new ArrayList<Map<String, Object>>());
        good.put("plan", new LinkedHashMap<String, Object>());
        assertDoesNotThrow(() -> FixtureSupport.checkRegistrySchema(good, tempDir.resolve("g.json")));

        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("schemaVersion", FixtureSupport.LEGACY_SCHEMA_VERSION);
        legacy.put("entries", new ArrayList<Map<String, Object>>());
        IllegalStateException legacyEx = assertThrows(IllegalStateException.class,
                () -> FixtureSupport.checkRegistrySchema(legacy, tempDir.resolve("l.json")));
        assertTrue(legacyEx.getMessage().contains("不重置为空"), "未知 schema 不得静默重置为空");

        Map<String, Object> missingPlan = new LinkedHashMap<>();
        missingPlan.put("schemaVersion", FixtureSupport.SCHEMA_VERSION);
        missingPlan.put("entries", new ArrayList<Map<String, Object>>());
        assertThrows(IllegalStateException.class,
                () -> FixtureSupport.checkRegistrySchema(missingPlan, tempDir.resolve("m.json")),
                "缺 plan 结构必须拒绝");
    }

    // ==================== ID 精度 ====================

    @Test
    @DisplayName("asLong 对超 Long 的 BigInteger 快速失败而非静默截断")
    void asLongRejectsBigIntegerOverflow() {
        BigInteger overflow = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThrows(ArithmeticException.class, () -> FixtureSupport.asLong(overflow),
                "超 Long 范围必须抛异常而非截断");
        assertEquals(12345L, FixtureSupport.asLong(BigInteger.valueOf(12345L)));
    }

    @Test
    @DisplayName("asLong/asInt 精确转换 BigDecimal 与字符串")
    void asLongExactConversions() {
        assertEquals(123456789012345L, FixtureSupport.asLong(new BigDecimal("123456789012345")));
        assertEquals(42L, FixtureSupport.asLong("42"));
        assertEquals(7, FixtureSupport.asInt("7"));
        assertEquals(9, FixtureSupport.asInt(9L));
    }

    // ==================== 文件 SHA-256 ====================

    @Test
    @DisplayName("sha256File 缺失返回 null，存在时确定且格式正确")
    void sha256FileBehavior() throws Exception {
        assertNull(FixtureSupport.sha256File(tempDir.resolve("missing.bin")));
        Path file = tempDir.resolve("artifact.json");
        Files.writeString(file, "{\"schemaVersion\":4}", StandardCharsets.UTF_8);
        String d1 = FixtureSupport.sha256File(file);
        String d2 = FixtureSupport.sha256File(file);
        assertEquals(d1, d2, "文件摘要必须确定");
        assertTrue(d1.startsWith("sha256:"));
        assertEquals("sha256:".length() + 64, d1.length());
    }

    // ==================== 物理目标键 ====================

    @Test
    @DisplayName("物理目标键含固定 namespace 且区分租户，哈希 32 hex 确定")
    void physicalTargetKeyFormat() {
        String key = FixtureSupport.physicalTargetKey("uuid-x", "omni_procurement", 1L);
        assertEquals("WP005|uuid-x|omni_procurement|1", key);
        assertNotEquals(key, FixtureSupport.physicalTargetKey("uuid-x", "omni_procurement", 2L),
                "不同租户必须不同目标键");
        String hash = FixtureSupport.targetKeyHash(key);
        assertEquals(32, hash.length());
        assertTrue(hash.matches("[0-9a-f]{32}"), "哈希应为 32 位十六进制: " + hash);
        assertEquals(hash, FixtureSupport.targetKeyHash(key), "哈希必须确定");
    }
}
