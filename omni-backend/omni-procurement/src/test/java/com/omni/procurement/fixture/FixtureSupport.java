package com.omni.procurement.fixture;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购 fixture 工具公共支撑设施（WP-005 返修）。
 *
 * <p>职责边界（对应返修 Goal 第 3.2/3.3 节合同）：</p>
 * <ul>
 *   <li>规范化 JSON 序列化与 SHA-256 计划摘要（替换旧 String.hashCode 伪摘要）</li>
 *   <li>registry 原子持久化：同目录临时文件 + ATOMIC_MOVE，平台不支持则 fail closed</li>
 *   <li>同物理目标 namespace 的 OS 文件锁与固定目标索引（不随 artifact 目录漂移）</li>
 *   <li>只读 JDBC 连接与固定范围参数化查询（零初始化、含软删）</li>
 *   <li>物理目标键：server_uuid + database + tenant + WP005 固定 namespace</li>
 * </ul>
 *
 * <p>本类不含任何业务写操作；全部 DML 由调用方通过受控 HTTP 完成。</p>
 *
 * @author Omni-Stack WP-005 Repair
 */
public final class FixtureSupport {

    /** 物理目标固定 namespace。 */
    public static final String TARGET_NAMESPACE = "WP005";

    /** 新 registry schema 版本（含 planDigest/写开关/恢复语义）。 */
    public static final int SCHEMA_VERSION = 4;

    /** 旧交付顶层 schema 版本（无嵌套 plan）。 */
    public static final int LEGACY_SCHEMA_VERSION = 3;

    /** 规范化 JSON 序列化器：POJO 属性与 Map 键都排序、去空白，用于摘要计算。 */
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(JsonInclude.Include.ALWAYS)
            .build();

    /** 输出序列化器：缩进、忽略 null。 */
    static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private static final SecureRandom RANDOM = new SecureRandom();

    private FixtureSupport() {
    }

    // ==================== 规范化摘要 ====================

    /**
     * 计算规范化 SHA-256 摘要。
     *
     * <p>使用键排序 + 紧凑 JSON 的稳定规范化序列化；摘要前缀 sha256:。
     * 调用方必须保证传入对象不含秘密值与动态 registry phase/时间。</p>
     *
     * @param value 待摘要对象
     * @return sha256: 开头的十六进制摘要
     */
    public static String canonicalDigest(Object value) {
        try {
            String canonical = CANONICAL_MAPPER.writeValueAsString(value);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("计划摘要计算失败", e);
        }
    }

    /**
     * 摘要常量时间比较（防时序侧信道；同时防前缀/长度误判）。
     *
     * @param expected 期望摘要
     * @param actual 实际摘要
     * @return 是否一致
     */
    public static boolean digestEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /**
     * 计算物理目标键：固定 namespace + server_uuid + database + tenant。
     *
     * <p>localhost/IP 别名、generation、runStamp、worker 或新目录不能绕开同目标旧未决项。</p>
     *
     * @param serverUuid 现场 server UUID
     * @param database 数据库名
     * @param tenantId 租户 ID
     * @return 目标键字符串
     */
    public static String physicalTargetKey(String serverUuid, String database, long tenantId) {
        return TARGET_NAMESPACE + "|" + serverUuid + "|" + database + "|" + tenantId;
    }

    /**
     * 物理目标键的文件系统安全哈希（用于锁目录命名）。
     *
     * @param targetKey 目标键
     * @return 32 位十六进制
     */
    public static String targetKeyHash(String targetKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(targetKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("目标键哈希计算失败", e);
        }
    }

    // ==================== 原子持久化 ====================

    /**
     * 原子写 JSON 文件：同目录临时文件 + ATOMIC_MOVE。
     *
     * <p>平台不支持原子移动时 fail closed（抛异常），绝不降级为 TRUNCATE_EXISTING 直写。
     * 临时文件带随机后缀，进程中断不会留下半写目标文件。</p>
     *
     * @param path 目标文件
     * @param data 数据对象
     * @throws IOException 写入或原子替换失败
     */
    public static void atomicWriteJson(Path path, Object data) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        byte[] payload = MAPPER.writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
        Path tmp = path.resolveSibling("." + path.getFileName() + ".tmp-"
                + Long.toHexString(System.currentTimeMillis()) + "-"
                + Long.toHexString(RANDOM.nextLong()));
        try {
            Files.write(tmp, payload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.deleteIfExists(tmp);
                throw new IOException("平台不支持原子替换，拒绝降级为非原子写: " + path, e);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ==================== 文件锁与固定目标索引 ====================

    /**
     * 目标状态目录：stateRoot/targets/<keyHash>/。
     *
     * <p>根目录由 FIXTURE_STATE_ROOT 指定，默认 ~/.omni-wp005-state；
     * 不随每次执行的 artifact 目录变化（固定目标索引职责）。</p>
     *
     * @param stateRoot 状态根目录
     * @param targetKey 物理目标键
     * @return 目标状态目录
     */
    public static Path targetStateDir(Path stateRoot, String targetKey) {
        return stateRoot.resolve("targets").resolve(targetKeyHash(targetKey));
    }

    /**
     * 目标互斥锁句柄；try-with-resources 释放。
     *
     * <p>两个真实进程竞争时，后到者 tryLock 立即失败并返回空，调用方必须明确拒绝；
     * 不能两个进程同时写同一物理目标。</p>
     */
    public static final class TargetLock implements AutoCloseable {

        private final FileChannel channel;
        private final FileLock lock;
        private final Path lockFile;

        private TargetLock(FileChannel channel, FileLock lock, Path lockFile) {
            this.channel = channel;
            this.lock = lock;
            this.lockFile = lockFile;
        }

        /**
         * 尝试获取目标互斥锁。
         *
         * @param stateRoot 状态根目录
         * @param targetKey 物理目标键
         * @return 获得锁返回句柄；被占用返回 null
         * @throws IOException 锁文件 IO 失败
         */
        public static TargetLock tryAcquire(Path stateRoot, String targetKey) throws IOException {
            Path dir = targetStateDir(stateRoot, targetKey);
            Files.createDirectories(dir);
            Path lockFile = dir.resolve("target.lock");
            // CREATE 幂等创建；竞争由 tryLock 决定，不依赖文件存在性
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                // 同 JVM 已持有该文件锁：拒绝语义与跨进程占用一致（返回 null，不抛出）
                channel.close();
                return null;
            }
            if (lock == null) {
                channel.close();
                return null;
            }
            return new TargetLock(channel, lock, lockFile);
        }

        /**
         * 锁文件路径。
         *
         * @return 路径
         */
        public Path lockFile() {
            return lockFile;
        }

        @Override
        public void close() throws IOException {
            try {
                lock.release();
            } finally {
                channel.close();
            }
        }
    }

    /**
     * 读取固定目标索引中登记的未决 registry 路径。
     *
     * @param stateRoot 状态根目录
     * @param targetKey 物理目标键
     * @return 登记路径；无索引返回 null
     * @throws IOException 读取失败
     */
    public static Path readActiveRegistryIndex(Path stateRoot, String targetKey) throws IOException {
        Path index = targetStateDir(stateRoot, targetKey).resolve("active-registry.txt");
        if (!Files.exists(index)) {
            return null;
        }
        String line = Files.readString(index, StandardCharsets.UTF_8).trim();
        return line.isEmpty() ? null : Path.of(line);
    }

    /**
     * 写固定目标索引（保留未决 registry 路径，即使改 artifact 路径也不能重新当空）。
     *
     * @param stateRoot 状态根目录
     * @param targetKey 物理目标键
     * @param registryPath registry 绝对路径
     * @throws IOException 写入失败
     */
    public static void writeActiveRegistryIndex(Path stateRoot, String targetKey,
                                                Path registryPath) throws IOException {
        Path index = targetStateDir(stateRoot, targetKey).resolve("active-registry.txt");
        Files.createDirectories(index.getParent());
        Files.writeString(index, registryPath.toAbsolutePath().toString(), StandardCharsets.UTF_8);
    }

    // ==================== 只读 JDBC ====================

    /**
     * 打开只读 JDBC 连接并核验实际连接目标。
     *
     * <p>setReadOnly(true) + 固定范围参数化查询；拒绝任意 SQL。
     * 连接后立即查询 @@server_uuid 与 DATABASE()，由调用方与计划比对，
     * 不能把两个调用者填入的相同 UUID 字符串当现场核验。</p>
     *
     * @param dbUrl JDBC URL
     * @param dbUser 用户
     * @param dbPassword 密码
     * @return 只读连接
     * @throws SQLException 连接失败
     */
    public static Connection openReadonlyConnection(String dbUrl, String dbUser,
                                                    String dbPassword) throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        try {
            conn.setReadOnly(true);
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    /**
     * 现场目标核验行：server_uuid 与当前 database。
     *
     * @param conn 只读连接
     * @return {serverUuid, database}
     * @throws SQLException 查询失败
     */
    public static Map<String, String> queryServerIdentity(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT @@server_uuid AS server_uuid, DATABASE() AS db_name");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("目标核验查询无结果");
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("serverUuid", rs.getString("server_uuid"));
            row.put("database", rs.getString("db_name"));
            return row;
        }
    }

    /**
     * 查询租户内品类全表（含软删），完整返回不用 LIMIT 隐藏异常。
     *
     * @param conn 只读连接
     * @param tenantId 租户 ID
     * @return 品类行列表
     * @throws SQLException 查询失败
     */
    public static List<Map<String, Object>> queryCategories(Connection conn,
                                                            long tenantId) throws SQLException {
        String sql = "SELECT id, parent_id, category_code, category_name, sort, status, "
                + "version, deleted FROM proc_material_category WHERE tenant_id = ? "
                + "ORDER BY id";
        return queryRows(conn, sql, tenantId);
    }

    /**
     * 按精确物料编码查询（含软删历史）。
     *
     * @param conn 只读连接
     * @param tenantId 租户 ID
     * @param materialCode 精确编码
     * @return 物料行列表（可能含软删同 code 历史）
     * @throws SQLException 查询失败
     */
    public static List<Map<String, Object>> queryMaterialsByCode(Connection conn, long tenantId,
                                                                 String materialCode)
            throws SQLException {
        String sql = "SELECT id, category_id, material_code, material_name, specification, "
                + "unit, asset_managed, status, version, deleted "
                + "FROM proc_material WHERE tenant_id = ? AND material_code = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tenantId);
            ps.setString(2, materialCode);
            try (ResultSet rs = ps.executeQuery()) {
                return readRows(rs);
            }
        }
    }

    /**
     * 查询租户采购配置（config 核验用）。
     *
     * @param conn 只读连接
     * @param tenantId 租户 ID
     * @return 配置行列表
     * @throws SQLException 查询失败
     */
    public static List<Map<String, Object>> queryConfig(Connection conn,
                                                        long tenantId) throws SQLException {
        String sql = "SELECT id, currency_code, version, deleted FROM proc_tenant_config WHERE tenant_id = ? "
                + "ORDER BY id";
        return queryRows(conn, sql, tenantId);
    }

    private static List<Map<String, Object>> queryRows(Connection conn, String sql,
                                                       long tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return readRows(rs);
            }
        }
    }

    private static List<Map<String, Object>> readRows(ResultSet rs) throws SQLException {
        int columns = rs.getMetaData().getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                String label = rs.getMetaData().getColumnLabel(i);
                int type = rs.getMetaData().getColumnType(i);
                Object value = switch (type) {
                    case java.sql.Types.TINYINT, java.sql.Types.SMALLINT, java.sql.Types.INTEGER ->
                            rs.getInt(i);
                    case java.sql.Types.BIGINT -> rs.getLong(i);
                    default -> rs.getObject(i);
                };
                row.put(label, value);
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 计算文件 SHA-256（计划 inputBindings 用；不存在返回 null）。
     *
     * @param path 文件路径
     * @return sha256: 前缀十六进制，或 null
     */
    public static String sha256File(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(path, StandardOpenOption.READ)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("文件 SHA-256 计算失败: " + path, e);
        }
    }

    // ==================== 通用 JSON IO ====================

    /**
     * 加载 JSON 文件；空文件/坏 JSON/未知结构不当作空对象。
     *
     * @param path 文件路径
     * @return 解析后的 Map
     * @throws IOException 读取或解析失败
     */
    public static Map<String, Object> loadJsonMap(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("文件不存在: " + path);
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new IOException("文件为空，不能当作空 registry: " + path);
        }
        try {
            return MAPPER.readValue(content, Map.class);
        } catch (IOException e) {
            throw new IOException("JSON 解析失败（损坏文件不能当作空 registry）: " + path, e);
        }
    }

    /**
     * 生成短随机后缀（临时文件/执行标识用）。
     *
     * @return 8 位十六进制
     */
    public static String randomSuffix() {
        return Long.toHexString(RANDOM.nextLong());
    }

    /**
     * 当前时刻 ISO-8601 字符串（UTC）。
     *
     * @return 时刻字符串
     */
    public static Instant now() {
        return Instant.now();
    }

    /**
     * registry schema 校验（版本必须匹配、结构必须完整；损坏/未知 schema 不重置为空）。
     *
     * @param registry 待校验 registry
     * @param path 文件路径（错误消息用）
     * @throws IllegalStateException 版本或结构不符合
     */
    public static void checkRegistrySchema(Map<String, Object> registry, Path path) {
        Object version = registry.get("schemaVersion");
        if (!(version instanceof Number n)
                || n.intValue() != SCHEMA_VERSION) {
            throw new IllegalStateException("registry schemaVersion=" + version + " 不受支持（期望 "
                    + SCHEMA_VERSION + "），损坏/未知 schema 不重置为空: " + path);
        }
        if (!registry.containsKey("entries") || !registry.containsKey("plan")) {
            throw new IllegalStateException("registry 缺少 plan/entries 结构: " + path);
        }
    }

    // ==================== 通用数值转换 ====================

    /**
     * 将 Object 数值安全转为 long（ID 精度：JSON 数字经 Jackson 默认到 Long，超长用 BigDecimal）。
     *
     * @param value 原始值
     * @return long 值
     */
    public static long asLong(Object value) {
        if (value instanceof Number n) {
            if (value instanceof BigDecimal bd) {
                return bd.longValueExact();
            }
            if (value instanceof java.math.BigInteger bi) {
                // 超 long 范围时抛 ArithmeticException，绝不静默截断 ID
                return bi.longValueExact();
            }
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * 将 Object 数值/字符串安全转为 int（version 用）。
     *
     * @param value 原始值
     * @return int 值
     */
    public static int asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
