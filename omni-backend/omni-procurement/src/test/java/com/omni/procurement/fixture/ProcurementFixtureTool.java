package com.omni.procurement.fixture;

import com.omni.procurement.fixture.FixtureHttpClient.ApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 采购模块 fixture 工具（WP-005 返修版，合同见本轮证据目录 WP005-REPAIR-20260915T020235-a7k2/
 * 下的 fixture-tool-contract.md 与 legacy-migration-contract.md）。
 *
 * <p>七种模式：</p>
 * <ul>
 *   <li><b>help</b> — 打印真实模式、环境变量与响应合同</li>
 *   <li><b>inspect</b> — 只读 JDBC 巡检品类/物料/配置，零 HTTP 零 DML</li>
 *   <li><b>plan</b> — 只读生成执行计划并原子持久化 registry（零业务写）</li>
 *   <li><b>import-legacy</b> — 一次性受控导入旧交付三行（零业务写）</li>
 *   <li><b>apply</b> — 显式写开关 + 整批预检 + 逐条提交 + UNKNOWN 恢复</li>
 *   <li><b>verify</b> — 逐字段校验目标/计划/registry 与实际状态</li>
 *   <li><b>cleanup</b> — 仅清理本计划 owned 且 retention=CLEANUP 条目</li>
 * </ul>
 *
 * <p>核心保护：物理目标键（server_uuid+database+tenant+WP005 namespace）、
 * 规范化 SHA-256 计划摘要、OS 文件锁互斥、固定目标索引、原子持久化、
 * INTENT/SUBMITTING/UNKNOWN 阶段机、终态 probe 不重建。
 * 只读模式（help/inspect/plan/import-legacy）零业务 DML，不依赖会触发
 * ensureInitialized 的业务 GET。</p>
 *
 * @author Omni-Stack WP-005 Repair
 */
public final class ProcurementFixtureTool {

    /** fixture 版本。 */
    private static final String FIXTURE_VERSION = "wp005-r2";

    /** 物料命名空间前缀。 */
    private static final String MATERIAL_CODE_PREFIX = "WP005FIX_";

    /** 13 个正式品类编码（含父子拓扑，借用品类不恢复不重建）。 */
    private static final List<String> BOOTSTRAP_CODES = List.of(
            "IT_DEVICE", "OFFICE_SUPPLY", "RAW_MATERIAL", "OTHER",
            "LAPTOP", "MONITOR", "PERIPHERAL", "STATIONERY", "PAPER",
            "METAL", "ELECTRONIC", "PLASTIC", "SERVICE");

    /** 写开关期望值。 */
    private static final String WRITE_CONFIRM = "YES";

    private ProcurementFixtureTool() {
    }

    // ==================== 配置 ====================

    /** 工具运行配置（全部来自受控环境变量）。 */
    private record ToolConfig(String mode, String baseUrl, String token, Path registryPath,
                              long tenantId, String serverUuid, String database, String execution,
                              int generation, String dbUrl, String dbUser, String dbPassword,
                              boolean confirmWrite, Path stateRoot, Path legacyRoot,
                              Path legacyRegistry, Path legacyProbePlan) {

        static ToolConfig load(String mode) {
            String baseUrl = optional("FIXTURE_BASE_URL", "http://127.0.0.1:18102");
            String token = optional("FIXTURE_TOKEN", "");
            String registryPath = require("FIXTURE_REGISTRY");
            long tenantId = Long.parseLong(optional("FIXTURE_TENANT_ID", "1"));
            String serverUuid = optional("FIXTURE_SERVER_UUID", "");
            String database = optional("FIXTURE_DATABASE", "omni_procurement");
            String execution = optional("FIXTURE_EXECUTION", "wp005");
            int generation = Integer.parseInt(optional("FIXTURE_GENERATION", "1"));
            String dbUrl = optional("FIXTURE_DB_URL", "");
            String dbUser = optional("FIXTURE_DB_USERNAME", "");
            String dbPassword = optional("FIXTURE_DB_PASSWORD", "");
            boolean confirmWrite = WRITE_CONFIRM.equals(optional("FIXTURE_CONFIRM_WRITE", ""));
            String stateRootStr = optional("FIXTURE_STATE_ROOT",
                    System.getProperty("user.home") + "/.omni-wp005-state");
            Path legacyRoot = optional("FIXTURE_LEGACY_ROOT", "").isBlank()
                    ? null : Path.of(optional("FIXTURE_LEGACY_ROOT", ""));
            Path legacyRegistry = optional("FIXTURE_LEGACY_REGISTRY", "").isBlank()
                    ? null : Path.of(optional("FIXTURE_LEGACY_REGISTRY", ""));
            Path legacyProbePlan = optional("FIXTURE_PROBE_PLAN", "").isBlank()
                    ? null : Path.of(optional("FIXTURE_PROBE_PLAN", ""));
            return new ToolConfig(mode, baseUrl, token, Path.of(registryPath), tenantId,
                    serverUuid, database, execution, generation, dbUrl, dbUser, dbPassword,
                    confirmWrite, Path.of(stateRootStr), legacyRoot, legacyRegistry, legacyProbePlan);
        }
    }

    // ==================== 入口 ====================

    /**
     * 入口：模式来自 args[0] 或 FIXTURE_MODE；失败打印结构化 JSON 并退出非零。
     *
     * @param args 可选的第一个位置参数为模式名
     * @throws Exception 分类为其他运行期异常时同样以非零退出
     */
    public static void main(String[] args) throws Exception {
        String mode = (args.length > 0 ? args[0] : requireEnvRaw("FIXTURE_MODE"))
                .toLowerCase(Locale.ROOT);
        int exitCode;
        try {
            Map<String, Object> result = switch (mode) {
                case "help" -> runHelp();
                case "inspect" -> runInspect(ToolConfig.load(mode));
                case "plan" -> runPlan(ToolConfig.load(mode));
                case "import-legacy" -> runImportLegacy(ToolConfig.load(mode));
                case "apply" -> runApply(ToolConfig.load(mode));
                case "verify" -> runVerify(ToolConfig.load(mode));
                case "cleanup" -> runCleanup(ToolConfig.load(mode));
                default -> throw new IllegalArgumentException("未知模式: " + mode);
            };
            System.out.println(FixtureSupport.MAPPER.writeValueAsString(result));
            exitCode = 0;
        } catch (Exception e) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("mode", mode);
            failure.put("status", "FAILED");
            failure.put("error", e.getMessage());
            failure.put("errorType", e.getClass().getSimpleName());
            failure.put("timestamp", Instant.now().toString());
            System.out.println(FixtureSupport.MAPPER.writeValueAsString(failure));
            exitCode = 2;
        }
        System.exit(exitCode);
    }

    private static String requireEnvRaw(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填环境变量: " + name);
        }
        return value;
    }

    private static String require(String name) {
        return requireEnvRaw(name);
    }

    private static String optional(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    // ==================== help ====================

    private static Map<String, Object> runHelp() {
        Map<String, Object> help = new LinkedHashMap<>();
        help.put("fixtureVersion", FIXTURE_VERSION);
        help.put("schemaVersion", FixtureSupport.SCHEMA_VERSION);
        help.put("modes", List.of(
                "help — 打印本说明；零连接",
                "inspect — 只读 JDBC 巡检（FIXTURE_DB_URL/USERNAME/PASSWORD 必需）",
                "plan — 只读生成计划与 registry（DB 凭证必需；不写业务库）",
                "import-legacy — 受控导入旧交付（FIXTURE_LEGACY_ROOT 必需；不写业务库）",
                "apply — 写模式（FIXTURE_CONFIRM_WRITE=YES + TOKEN + DB 凭证必需）",
                "verify — 逐字段校验（TOKEN + DB 凭证必需；零写）",
                "cleanup — 写模式（FIXTURE_CONFIRM_WRITE=YES + TOKEN + DB 凭证必需）"));
        help.put("requiredEnv", Map.of(
                "FIXTURE_REGISTRY", "registry 文件路径（必需）",
                "FIXTURE_TOKEN", "Bearer token（HTTP 模式必需；品类请求同样携带）",
                "FIXTURE_CONFIRM_WRITE", "apply/cleanup 必须显式设为 YES",
                "FIXTURE_DB_URL", "只读 JDBC URL（inspect/plan/import-legacy/apply/verify/cleanup 必需）",
                "FIXTURE_DB_USERNAME / FIXTURE_DB_PASSWORD", "只读 JDBC 凭证（经环境注入，不落日志）",
                "FIXTURE_STATE_ROOT", "锁与固定目标索引根（默认 ~/.omni-wp005-state）",
                "FIXTURE_LEGACY_ROOT", "旧交付 JSON 路径（import-legacy 必需）",
                "FIXTURE_LEGACY_REGISTRY", "旧执行 registry 路径（import-legacy 可选交叉核对）"));
        help.put("responseContract", "所有 HTTP 请求携带 Bearer；先验 HTTP 200，再验 R.code=200，"
                + "再验 data；DELETE 为 R<Void>（data 可为空）；列表 page/size≤100，"
                + "keyword 为模糊匹配、客户端精确比较并完整分页");
        help.put("idempotencyContract", "物理目标键=server_uuid|database|tenant|WP005；同目标"
                + " namespace 使用固定状态根下的 OS 文件锁与固定目标索引；计划摘要为规范化 "
                + "SHA-256；未决项（INTENT/SUBMITTING/UNKNOWN/CLEAN_FAILED）恢复按 API+含软删"
                + " DB 精确判定，零列表不证明未提交；CLEANED 终态不重建");
        return help;
    }

    // ==================== inspect ====================

    private static Map<String, Object> runInspect(ToolConfig config) throws Exception {
        try (Connection conn = FixtureSupport.openReadonlyConnection(
                config.dbUrl(), config.dbUser(), config.dbPassword())) {
            Map<String, String> identity = FixtureSupport.queryServerIdentity(conn);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", "inspect");
            result.put("connectedTarget", identity);
            List<Map<String, Object>> categories = FixtureSupport.queryCategories(conn, config.tenantId());
            result.put("categoryCount", categories.size());
            result.put("categories", categories);
            List<Map<String, Object>> configRows = FixtureSupport.queryConfig(conn, config.tenantId());
            result.put("procConfig", configRows);
            List<Map<String, Object>> fixtures = new ArrayList<>();
            for (Map<String, Object> category : categories) {
                String code = String.valueOf(category.get("category_code"));
                if (BOOTSTRAP_CODES.contains(code)) {
                    fixtures.add(category);
                }
            }
            result.put("bootstrapCategoryCount", fixtures.size());
            result.put("bootstrapComplete", fixtures.size() == BOOTSTRAP_CODES.size());
            return result;
        }
    }

    // ==================== plan ====================

    private static Map<String, Object> runPlan(ToolConfig config) throws Exception {
        PlanContext ctx = openTargetForPlan(config);
        try (Connection conn = ctx.connection()) {
            // 现场品类解析：JDBC 树构建，不依赖会初始化的业务 GET
            List<Map<String, Object>> rows = FixtureSupport.queryCategories(conn, config.tenantId());
            Map<String, Object> laptop = findCategoryByCode(rows, "LAPTOP");
            if (laptop == null || !isLeaf(rows, laptop)) {
                throw new IllegalStateException("LAPTOP 品类不存在或不是叶子，拒绝生成计划");
            }
            // 现有物料冲突检测（含软删）
            String fixtureId = config.execution();
            String genSuffix = "WP005A" + "GEN" + config.generation();
            String base = fixtureId.replace("-", "").toUpperCase(Locale.ROOT);
            String assetCode = MATERIAL_CODE_PREFIX + "ASET_" + base + genSuffix;
            String nonAssetCode = MATERIAL_CODE_PREFIX + "NOAS_" + base + genSuffix;
            for (String code : List.of(assetCode, nonAssetCode)) {
                List<Map<String, Object>> existing = FixtureSupport.queryMaterialsByCode(
                        conn, config.tenantId(), code);
                if (!existing.isEmpty()) {
                    throw new IllegalStateException("物料编码 " + code + " 已存在（含软删历史），"
                            + "请改用 import-legacy 而不是重新 plan");
                }
            }
            // 构建计划
            Map<String, Object> plan = buildBasePlan(config, List.of(
                    materialEntry("MATERIAL_ASSET", assetCode, laptop, true,
                            "WP005 Fixture Asset Material", "UNIT",
                            "RETAINED_FOR_WP006_WP007", null),
                    materialEntry("MATERIAL_NON_ASSET", nonAssetCode, laptop, false,
                            "WP005 Fixture Non-Asset Material", "UNIT",
                            "RETAINED_FOR_WP006_WP007", null)));
            plan.put("categorySnapshot", categorySnapshot(rows));
            plan.put("procConfigSnapshot", FixtureSupport.queryConfig(conn, config.tenantId()));
            plan.put("expectedNewCount", 2);
            String digest = FixtureSupport.canonicalDigest(plan);
            // registry 持久化（原子写 + 锁 + 固定目标索引）
            Map<String, Object> registry = newRegistry(config, plan, digest);
            persistRegistry(ctx, registry);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", "plan");
            result.put("registryPath", ctx.registryPath().toString());
            result.put("planDigest", digest);
            result.put("expectedNewCount", 2);
            return result;
        } finally {
            ctx.close();
        }
    }

    // ==================== import-legacy ====================

    private static Map<String, Object> runImportLegacy(ToolConfig config) throws Exception {
        if (config.legacyRoot() == null) {
            throw new IllegalArgumentException("import-legacy 需要 FIXTURE_LEGACY_ROOT");
        }
        Map<String, Object> legacy = FixtureSupport.loadJsonMap(config.legacyRoot());
        checkLegacySchema(legacy);
        String legacyDigest = String.valueOf(legacy.getOrDefault("planDigest", ""));
        List<Map<String, Object>> legacyEntries = castList(legacy.get("entries"), "旧 entries");
        // 可选交叉核对：旧执行 registry（同 schema 严格比对）与旧 probe 计划（文本包含弱核对）
        if (config.legacyRegistry() != null) {
            Map<String, Object> legacyRegistry = FixtureSupport.loadJsonMap(config.legacyRegistry());
            checkLegacySchema(legacyRegistry);
            if (!Objects.equals(String.valueOf(legacyRegistry.get("fixtureId")),
                    String.valueOf(legacy.get("fixtureId")))) {
                throw new IllegalArgumentException("旧 registry fixtureId 与旧根文件不一致，交叉核对失败");
            }
            Set<String> registryCodes = entryCodes(
                    castList(legacyRegistry.get("entries"), "旧 registry entries"));
            if (!registryCodes.equals(entryCodes(legacyEntries))) {
                throw new IllegalArgumentException("旧 registry entries 编码集合与旧根文件不一致，交叉核对失败");
            }
        }
        if (config.legacyProbePlan() != null) {
            Map<String, Object> probePlan = FixtureSupport.loadJsonMap(config.legacyProbePlan());
            String probeText = FixtureSupport.MAPPER.writeValueAsString(probePlan);
            for (Map<String, Object> legacyEntry : legacyEntries) {
                if ("CLEANUP_PROBE".equals(String.valueOf(legacyEntry.get("fixtureKey")))) {
                    String code = String.valueOf(legacyEntry.get("materialCode"));
                    if (!probeText.contains(code)) {
                        throw new IllegalArgumentException("旧 probe 计划不包含旧 probe 编码 " + code
                                + "，交叉核对失败");
                    }
                }
            }
        }
        PlanContext ctx = openTargetForPlan(config);
        try (Connection conn = ctx.connection()) {
            List<Map<String, Object>> rows = FixtureSupport.queryCategories(conn, config.tenantId());
            Map<String, Object> laptop = findCategoryByCode(rows, "LAPTOP");
            if (laptop == null) {
                throw new IllegalStateException("现场缺少 LAPTOP 品类，导入中止");
            }
            // 旧文件期望行 → 现场精确核验（任一差异即停止导入）
            List<Map<String, Object>> planMaterials = new ArrayList<>();
            for (Map<String, Object> legacyEntry : legacyEntries) {
                String key = String.valueOf(legacyEntry.get("fixtureKey"));
                String code = String.valueOf(legacyEntry.get("materialCode"));
                List<Map<String, Object>> dbRows = FixtureSupport.queryMaterialsByCode(
                        conn, config.tenantId(), code);
                Map<String, Object> live = null;
                for (Map<String, Object> row : dbRows) {
                    if (((Number) row.get("deleted")).intValue() == 0) {
                        if (live != null) {
                            throw new IllegalStateException("物料 " + code + " 现场存在多行有效记录，"
                                    + "所有权不明，导入中止");
                        }
                        live = row;
                    }
                }
                boolean cleaned = "CLEANUP_PROBE".equals(key);
                if (cleaned) {
                    // 旧 probe 终态：现场必须只有软删行
                    boolean hasSoftDeleted = false;
                    for (Map<String, Object> row : dbRows) {
                        if (((Number) row.get("deleted")).intValue() == 1) {
                            hasSoftDeleted = true;
                        }
                    }
                    if (!hasSoftDeleted) {
                        throw new IllegalStateException("旧 probe " + code + " 现场无软删记录，"
                                + "与历史 CLEANED 状态不符，导入中止");
                    }
                    planMaterials.add(materialEntry(key, code, laptop, false,
                            String.valueOf(legacyEntry.get("materialName")), "EA",
                            "CLEANUP", legacyEntry));
                    continue;
                }
                if (live == null) {
                    throw new IllegalStateException("保留物料 " + code + " 现场缺失，导入中止");
                }
                // 逐字段核验（category/unit/assetManaged/status）
                if (FixtureSupport.asLong(live.get("category_id"))
                        != FixtureSupport.asLong(laptop.get("id"))) {
                    throw new IllegalStateException("物料 " + code + " 品类与现场不一致，导入中止");
                }
                boolean expectedAsset = "MATERIAL_ASSET".equals(key);
                if (((Number) live.get("asset_managed")).intValue() == 1 != expectedAsset) {
                    throw new IllegalStateException("物料 " + code + " assetManaged 与历史不符，导入中止");
                }
                planMaterials.add(materialEntry(key, code, laptop, expectedAsset,
                        String.valueOf(live.get("material_name")),
                        String.valueOf(live.get("unit")),
                        "RETAINED_FOR_WP006_WP007", legacyEntry));
            }
            Map<String, Object> plan = buildBasePlan(config, planMaterials);
            plan.put("categorySnapshot", categorySnapshot(rows));
            plan.put("procConfigSnapshot", FixtureSupport.queryConfig(conn, config.tenantId()));
            plan.put("expectedNewCount", 0);
            Map<String, Object> importedFrom = new LinkedHashMap<>();
            importedFrom.put("legacyRootPath", config.legacyRoot().toString());
            importedFrom.put("legacyDigest", legacyDigest);
            importedFrom.put("legacyDigestVerdict", "legacyUnverifiedDigest（旧 String.hashCode 文本，不可验证，"
                    + "不作有效摘要引用）");
            importedFrom.put("importedAt", Instant.now().toString());
            if (config.legacyRegistry() != null) {
                importedFrom.put("legacyRegistryPath", config.legacyRegistry().toString());
                importedFrom.put("legacyRegistryCrossCheck", "PASSED");
            }
            if (config.legacyProbePlan() != null) {
                importedFrom.put("legacyProbePlanPath", config.legacyProbePlan().toString());
                importedFrom.put("legacyProbePlanCrossCheck", "PASSED（文本包含弱核对）");
            }
            plan.put("importedFrom", importedFrom);
            String digest = FixtureSupport.canonicalDigest(plan);
            Map<String, Object> registry = newRegistry(config, plan, digest);
            // 生成与旧文件同 key 的 registry 条目：保留物直接 COMMITTED（导入语义），probe 直接 CLEANED
            List<Map<String, Object>> entries = castList(registry.get("entries"), "entries");
            for (Map<String, Object> material : planMaterials) {
                String code = String.valueOf(material.get("materialCode"));
                String resourceId = null;
                for (Map<String, Object> row : FixtureSupport.queryMaterialsByCode(
                        conn, config.tenantId(), code)) {
                    int deleted = ((Number) row.get("deleted")).intValue();
                    if (cleaned(material) ? deleted == 1 : deleted == 0) {
                        resourceId = String.valueOf(FixtureSupport.asLong(row.get("id")));
                    }
                }
                if (resourceId == null) {
                    throw new IllegalStateException("导入条目 " + code + " 现场无对应记录，无法保留实际ID");
                }
                Map<String, Object> entry = importEntry(material, cleaned(material), resourceId);
                entries.add(entry);
            }
            registry.put("status", "IMPORTED");
            persistRegistry(ctx, registry);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", "import-legacy");
            result.put("registryPath", ctx.registryPath().toString());
            result.put("planDigest", digest);
            result.put("importedEntries", entries.size());
            result.put("businessDbWrites", 0);
            return result;
        } finally {
            ctx.close();
        }
    }

    private static boolean cleaned(Map<String, Object> material) {
        return "CLEANUP".equals(String.valueOf(material.get("retention")));
    }

    /** 条目编码集合（交叉核对用）。 */
    private static Set<String> entryCodes(List<Map<String, Object>> entries) {
        Set<String> codes = new LinkedHashSet<>();
        for (Map<String, Object> entry : entries) {
            codes.add(String.valueOf(entry.get("materialCode")));
        }
        return codes;
    }

    /** 从旧文件条目构建导入 registry 条目（保留实际ID + reportedHistorical/attempt 记录）。 */
    private static Map<String, Object> importEntry(Map<String, Object> material, boolean cleaned,
                                                   String resourceId) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fixtureKey", material.get("fixtureKey"));
        entry.put("materialCode", material.get("materialCode"));
        entry.put("phase", cleaned ? "CLEANED" : "COMMITTED");
        entry.put("resourceId", resourceId);
        entry.put("retention", material.get("retention"));
        entry.put("imported", true);
        entry.put("importedAt", Instant.now().toString());
        entry.put("reportedHistorical", true);
        entry.put("attempts", List.of(Map.of(
                "attempt", 1,
                "type", "IMPORT_LEGACY",
                "at", Instant.now().toString(),
                "note", "导入非新建；旧文件时间为 reportedHistorical")));
        return entry;
    }

    private static void checkLegacySchema(Map<String, Object> legacy) {
        Object version = legacy.get("schemaVersion");
        if (!Integer.valueOf(FixtureSupport.LEGACY_SCHEMA_VERSION).equals(
                ((Number) Objects.requireNonNull(version, "旧文件缺少 schemaVersion")).intValue())) {
            throw new IllegalArgumentException("旧文件 schemaVersion 不是 "
                    + FixtureSupport.LEGACY_SCHEMA_VERSION + "，无法按旧交付合同导入");
        }
        if (legacy.containsKey("plan")) {
            throw new IllegalArgumentException("旧文件已包含嵌套 plan，不属于旧交付顶层 schema，"
                    + "请直接以 registry 模式处理而不是 import-legacy");
        }
        if (!legacy.containsKey("entries")) {
            throw new IllegalArgumentException("旧文件缺少顶层 entries");
        }
    }

    // ==================== apply ====================

    private static Map<String, Object> runApply(ToolConfig config) throws Exception {
        requireWriteSwitch(config, "apply");
        if (config.token().isBlank()) {
            throw new IllegalArgumentException("apply 需要 FIXTURE_TOKEN");
        }
        PlanContext ctx = openTargetForWrite(config);
        try (Connection conn = ctx.connection()) {
            Map<String, Object> registry = ctx.registry();
            Map<String, Object> plan = castMap(registry.get("plan"), "registry.plan");
            // 整批预检（第一条 POST 之前全部完成）
            preflight(ctx, conn, plan);
            List<Map<String, Object>> materials = castList(plan.get("materials"), "plan.materials");
            List<Map<String, Object>> entries = castList(registry.get("entries"), "entries");
            int expectedNew = ((Number) plan.getOrDefault("expectedNewCount", 0)).intValue();
            List<String> pendingCodes = new ArrayList<>();
            for (Map<String, Object> material : materials) {
                String code = String.valueOf(material.get("materialCode"));
                Map<String, Object> entry = findEntryByCode(entries, code);
                if (entry == null) {
                    pendingCodes.add(code);
                    continue;
                }
                String phase = String.valueOf(entry.get("phase"));
                if ("COMMITTED".equals(phase) || "CLEANED".equals(phase)) {
                    continue;
                }
                // 未决项恢复判定（UNKNOWN/INTENT/SUBMITTING/CLEAN_FAILED）
                resolvePending(ctx, conn, entry, material);
                if (!"COMMITTED".equals(String.valueOf(entry.get("phase")))) {
                    pendingCodes.add(code);
                }
            }
            if (pendingCodes.size() != expectedNew) {
                throw new IllegalStateException("待新建数 " + pendingCodes.size()
                        + " 与计划 expectedNewCount " + expectedNew + " 不一致，拒绝执行");
            }
            FixtureHttpClient client = new FixtureHttpClient(config.baseUrl(), config.token());
            int created = 0;
            for (Map<String, Object> material : materials) {
                String code = String.valueOf(material.get("materialCode"));
                if (!pendingCodes.contains(code)) {
                    continue;
                }
                Map<String, Object> material0 = material;
                Map<String, Object> entry = findEntryByCode(entries, code);
                if (entry == null) {
                    entry = new LinkedHashMap<>();
                    entry.put("fixtureKey", material0.get("fixtureKey"));
                    entry.put("materialCode", code);
                    entry.put("retention", material0.get("retention"));
                    entry.put("attempts", new ArrayList<Map<String, Object>>());
                    entries.add(entry);
                }
                addAttempt(entry, "INTENT");
                entry.put("phase", "INTENT");
                persistRegistry(ctx, registry);
                addAttempt(entry, "SUBMITTING");
                entry.put("phase", "SUBMITTING");
                persistRegistry(ctx, registry);
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("categoryId", FixtureSupport.asLong(material.get("categoryId")));
                requestBody.put("materialCode", code);
                requestBody.put("materialName", material.get("materialName"));
                requestBody.put("unit", material.get("unit"));
                requestBody.put("assetManaged", material.get("assetManaged"));
                requestBody.put("status", material.get("status"));
                if (material.get("specification") != null) {
                    requestBody.put("specification", material.get("specification"));
                }
                try {
                    Map<String, Object> vo = client.createMaterial(requestBody);
                    entry.put("phase", "COMMITTED");
                    entry.put("resourceId", String.valueOf(FixtureSupport.asLong(vo.get("id"))));
                    entry.put("version", FixtureSupport.asInt(vo.get("version")));
                    addAttempt(entry, "COMMITTED id=" + entry.get("resourceId")
                            + " version=" + entry.get("version"));
                    persistRegistry(ctx, registry);
                    created++;
                } catch (IOException e) {
                    boolean definite = e instanceof ApiException api && api.httpStatus() != -1;
                    entry.put("phase", definite ? "COMMIT_FAILED" : "COMMIT_UNKNOWN");
                    entry.put("lastError", String.valueOf(e.getMessage()));
                    addAttempt(entry, entry.get("phase") + " " + e.getMessage());
                    persistRegistry(ctx, registry);
                    throw new IllegalStateException("创建 " + code + " 阶段=" + entry.get("phase")
                            + "，按持久状态恢复，不自动重试", e);
                }
            }
            registry.put("status", "APPLIED");
            persistRegistry(ctx, registry);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", "apply");
            result.put("created", created);
            result.put("totalEntries", entries.size());
            return result;
        } finally {
            ctx.close();
        }
    }

    // ==================== verify ====================

    private static Map<String, Object> runVerify(ToolConfig config) throws Exception {
        if (config.token().isBlank()) {
            throw new IllegalArgumentException("verify 需要 FIXTURE_TOKEN");
        }
        PlanContext ctx = openTargetForWrite(config);
        try (Connection conn = ctx.connection()) {
            Map<String, Object> registry = ctx.registry();
            Map<String, Object> plan = castMap(registry.get("plan"), "registry.plan");
            preflight(ctx, conn, plan);
            List<Map<String, Object>> materials = castList(plan.get("materials"), "plan.materials");
            List<Map<String, Object>> entries = castList(registry.get("entries"), "entries");
            FixtureHttpClient client = new FixtureHttpClient(config.baseUrl(), config.token());
            List<Map<String, Object>> checks = new ArrayList<>();
            int pass = 0;
            int fail = 0;
            for (Map<String, Object> material : materials) {
                String code = String.valueOf(material.get("materialCode"));
                Map<String, Object> check = new LinkedHashMap<>();
                check.put("materialCode", code);
                Map<String, Object> entry = findEntryByCode(entries, code);
                if (entry == null) {
                    check.put("status", "MISSING_ENTRY");
                    fail++;
                    checks.add(check);
                    continue;
                }
                String phase = String.valueOf(entry.get("phase"));
                check.put("phase", phase);
                switch (phase) {
                    case "COMMITTED" -> {
                        // API 逐字段 + DB 含软删复核
                        String id = String.valueOf(entry.get("resourceId"));
                        Map<String, Object> vo = client.getMaterial(id);
                        String problem = null;
                        if (vo == null) {
                            problem = "API 详情缺失";
                        } else if (!code.equals(vo.get("materialCode"))) {
                            problem = "materialCode 漂移";
                        } else if (FixtureSupport.asLong(vo.get("categoryId"))
                                != FixtureSupport.asLong(material.get("categoryId"))) {
                            problem = "categoryId 漂移";
                        } else if (!Objects.equals(vo.get("categoryCode"),
                                material.get("categoryCode"))) {
                            problem = "categoryCode 漂移";
                        } else if (!Objects.equals(vo.get("unit"), material.get("unit"))) {
                            problem = "unit 漂移";
                        } else if (!Objects.equals(vo.get("assetManaged"),
                                material.get("assetManaged"))) {
                            problem = "assetManaged 漂移";
                        } else if (!Objects.equals(vo.get("status"), material.get("status"))) {
                            problem = "status 漂移";
                        } else if (!Objects.equals(vo.get("materialName"),
                                material.get("materialName"))) {
                            problem = "materialName 漂移";
                        }
                        // DB 精确行核验（tenant 内含软删查同 code）
                        List<Map<String, Object>> dbRows = FixtureSupport.queryMaterialsByCode(
                                conn, config.tenantId(), code);
                        Map<String, Object> liveRow = null;
                        for (Map<String, Object> row : dbRows) {
                            if (((Number) row.get("deleted")).intValue() == 0
                                    && String.valueOf(FixtureSupport.asLong(row.get("id"))).equals(id)) {
                                liveRow = row;
                            }
                        }
                        if (problem == null && liveRow == null) {
                            problem = "DB 有效行缺失";
                        }
                        if (problem != null) {
                            check.put("status", "DRIFTED");
                            check.put("problem", problem);
                            fail++;
                        } else {
                            check.put("status", "VERIFIED");
                            check.put("resourceId", id);
                            check.put("version", FixtureSupport.asInt(vo.get("version")));
                            pass++;
                        }
                    }
                    case "CLEANED" -> {
                        List<Map<String, Object>> dbRows = FixtureSupport.queryMaterialsByCode(
                                conn, config.tenantId(), code);
                        boolean softDeleted = false;
                        for (Map<String, Object> row : dbRows) {
                            if (((Number) row.get("deleted")).intValue() == 1) {
                                softDeleted = true;
                            }
                        }
                        if (!softDeleted) {
                            check.put("status", "DRIFTED");
                            check.put("problem", "终态 CLEANED 但现场无软删记录（可能被复活）");
                            fail++;
                        } else {
                            check.put("status", "VERIFIED_CLEANED");
                            pass++;
                        }
                    }
                    default -> {
                        check.put("status", "PENDING_STATE");
                        check.put("problem", "条目处于未决阶段 " + phase + "，verify 不通过");
                        fail++;
                    }
                }
                checks.add(check);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", "verify");
            result.put("status", fail == 0 ? "VERIFIED" : "FAILED");
            result.put("pass", pass);
            result.put("fail", fail);
            result.put("checks", checks);
            if (fail > 0) {
                throw new IllegalStateException("verify 存在 " + fail + " 项失败（结构化结果见上方 checks）");
            }
            return result;
        } finally {
            ctx.close();
        }
    }

    // ==================== cleanup ====================

    private static Map<String, Object> runCleanup(ToolConfig config) throws Exception {
        requireWriteSwitch(config, "cleanup");
        if (config.token().isBlank()) {
            throw new IllegalArgumentException("cleanup 需要 FIXTURE_TOKEN");
        }
        PlanContext ctx = openTargetForWrite(config);
        try (Connection conn = ctx.connection()) {
            Map<String, Object> registry = ctx.registry();
            Map<String, Object> plan = castMap(registry.get("plan"), "registry.plan");
            preflight(ctx, conn, plan);
            List<Map<String, Object>> materials = castList(plan.get("materials"), "plan.materials");
            List<Map<String, Object>> entries = castList(registry.get("entries"), "entries");
            FixtureHttpClient client = new FixtureHttpClient(config.baseUrl(), config.token());
            int cleaned = 0;
            int alreadyCleaned = 0;
            int skippedRetained = 0;
            List<Map<String, Object>> actions = new ArrayList<>();
            for (Map<String, Object> material : materials) {
                String code = String.valueOf(material.get("materialCode"));
                String retention = String.valueOf(material.get("retention"));
                Map<String, Object> entry = findEntryByCode(entries, code);
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("materialCode", code);
                if (!"CLEANUP".equals(retention)) {
                    action.put("result", "SKIPPED_RETAINED");
                    skippedRetained++;
                    actions.add(action);
                    continue;
                }
                if (entry == null) {
                    throw new IllegalStateException("cleanup 条目 " + code + " 不在 registry，"
                            + "不能清理计划外对象");
                }
                String phase = String.valueOf(entry.get("phase"));
                if ("CLEANED".equals(phase)) {
                    // 重复 cleanup：确认软删后零 DELETE 安全结束
                    boolean softDeleted = hasSoftDeletedRow(conn, config.tenantId(), code);
                    if (!softDeleted) {
                        throw new IllegalStateException("条目 " + code + " 已标记 CLEANED 但现场无软删记录");
                    }
                    action.put("result", "ALREADY_CLEANED");
                    action.put("deletedCount", 0);
                    alreadyCleaned++;
                    actions.add(action);
                    continue;
                }
                if (!"COMMITTED".equals(phase) && !"CLEAN_FAILED".equals(phase)
                        && !"CLEAN_UNKNOWN".equals(phase)) {
                    throw new IllegalStateException("条目 " + code + " 阶段 " + phase
                            + " 不允许 cleanup");
                }
                // 取最新 version：先 API 再 DB，version 缺失不默认 0
                String id = String.valueOf(entry.get("resourceId"));
                Map<String, Object> vo = client.getMaterial(id);
                int latestVersion;
                if (vo != null) {
                    latestVersion = FixtureSupport.asInt(vo.get("version"));
                } else {
                    List<Map<String, Object>> dbRows = FixtureSupport.queryMaterialsByCode(
                            conn, config.tenantId(), code);
                    Map<String, Object> live = null;
                    for (Map<String, Object> row : dbRows) {
                        if (((Number) row.get("deleted")).intValue() == 0
                                && String.valueOf(FixtureSupport.asLong(row.get("id"))).equals(id)) {
                            live = row;
                        }
                    }
                    if (live == null) {
                        // API 404 + DB 无有效行：凭本目标 DB 历史判定
                        if (hasSoftDeletedRow(conn, config.tenantId(), code)) {
                            entry.put("phase", "CLEANED");
                            addAttempt(entry, "CLEANED（API 404 + DB 软删历史确认，零 DELETE）");
                            persistRegistry(ctx, registry);
                            action.put("result", "ALREADY_CLEANED");
                            action.put("deletedCount", 0);
                            alreadyCleaned++;
                            actions.add(action);
                            continue;
                        }
                        throw new IllegalStateException("条目 " + code + " API 与 DB 均无记录且无软删历史，"
                                + "状态不明，不当作已清理");
                    }
                    latestVersion = ((Number) live.get("version")).intValue();
                }
                try {
                    client.deleteMaterial(id, latestVersion);
                } catch (IOException e) {
                    boolean definite = e instanceof ApiException api && api.httpStatus() != -1;
                    entry.put("phase", definite ? "CLEAN_FAILED" : "CLEAN_UNKNOWN");
                    entry.put("lastError", String.valueOf(e.getMessage()));
                    addAttempt(entry, entry.get("phase") + " " + e.getMessage());
                    persistRegistry(ctx, registry);
                    throw new IllegalStateException("删除 " + code + " 失败（" + entry.get("phase")
                            + "），不登记 CLEANED", e);
                }
                // 删除后 API+DB 双核验
                Map<String, Object> after = client.getMaterial(id);
                boolean dbSoftDeleted = hasSoftDeletedRow(conn, config.tenantId(), code);
                if (after != null || !dbSoftDeleted) {
                    entry.put("phase", "CLEAN_UNKNOWN");
                    addAttempt(entry, "CLEAN_UNKNOWN（DELETE 后核验不一致）");
                    persistRegistry(ctx, registry);
                    throw new IllegalStateException("删除 " + code + " 后核验不一致，保持待核状态");
                }
                entry.put("phase", "CLEANED");
                entry.put("version", latestVersion + 1);
                addAttempt(entry, "CLEANED version=" + (latestVersion + 1));
                persistRegistry(ctx, registry);
                cleaned++;
                action.put("result", "CLEANED");
                actions.add(action);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", "cleanup");
            result.put("cleaned", cleaned);
            result.put("alreadyCleaned", alreadyCleaned);
            result.put("skippedRetained", skippedRetained);
            result.put("actions", actions);
            return result;
        } finally {
            ctx.close();
        }
    }

    // ==================== 公共目标/预检设施 ====================

    /** 目标上下文：连接 + 锁 + registry（加载即绑定固定目标索引）。 */
    private record PlanContext(Connection connection, TargetLockCloseable lock,
                               Path registryPath, Map<String, Object> registry,
                               String targetKey, String baseUrl, String token,
                               long tenantId) implements AutoCloseable {

        @Override
        public void close() {
            try {
                lock.close();
            } catch (IOException e) {
                // 关闭失败不掩盖主流程结果，但保留痕迹
                System.err.println("锁释放失败: " + e.getMessage());
            }
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("连接关闭失败: " + e.getMessage());
            }
        }
    }

    /** AutoCloseable 锁包装（失败 fail closed：进程退出由 JVM 释放 OS 锁）。 */
    private static final class TargetLockCloseable implements AutoCloseable {

        private final FixtureSupport.TargetLock delegate;

        TargetLockCloseable(FixtureSupport.TargetLock delegate) {
            this.delegate = delegate;
        }

        FixtureSupport.TargetLock delegate() {
            return delegate;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    /** 只读计划上下文（plan/import-legacy 用；同样持锁与索引，保护 registry 不被并发覆写）。 */
    private static PlanContext openTargetForPlan(ToolConfig config) throws Exception {
        if (config.dbUrl().isBlank() || config.dbUser().isBlank() || config.dbPassword().isBlank()) {
            throw new IllegalArgumentException("缺少只读 DB 凭证 FIXTURE_DB_URL/USERNAME/PASSWORD");
        }
        Connection conn = FixtureSupport.openReadonlyConnection(
                config.dbUrl(), config.dbUser(), config.dbPassword());
        return bindTarget(config, conn);
    }

    /** 写模式上下文（apply/verify/cleanup；verify 零写但共用完整前置）。 */
    private static PlanContext openTargetForWrite(ToolConfig config) throws Exception {
        PlanContext ctx = openTargetForPlan(config);
        if (ctx.registry() == null) {
            throw new IllegalStateException("registry 不存在，先运行 plan 或 import-legacy 生成计划: "
                    + ctx.registryPath());
        }
        return ctx;
    }

    private static PlanContext bindTarget(ToolConfig config, Connection conn) throws Exception {
        Map<String, String> identity = FixtureSupport.queryServerIdentity(conn);
        String liveUuid = identity.get("serverUuid");
        String liveDb = identity.get("database");
        if (config.serverUuid() != null && !config.serverUuid().isBlank()
                && !config.serverUuid().equals(liveUuid)) {
            throw new IllegalStateException("调用者声明的 serverUuid 与现场不一致: 声明="
                    + config.serverUuid() + " 现场=" + liveUuid);
        }
        if (!config.database().equals(liveDb)) {
            throw new IllegalStateException("调用者声明的 database 与现场不一致: 声明="
                    + config.database() + " 现场=" + liveDb);
        }
        String targetKey = FixtureSupport.physicalTargetKey(liveUuid, liveDb, config.tenantId());
        FixtureSupport.TargetLock raw = FixtureSupport.TargetLock.tryAcquire(config.stateRoot(), targetKey);
        if (raw == null) {
            throw new IllegalStateException("目标 " + targetKey + " 正被其他进程持有锁，拒绝并发写入");
        }
        TargetLockCloseable lock = new TargetLockCloseable(raw);
        // 固定目标索引：同目标已有未决 registry 时不得静默换文件当空
        Path indexed = FixtureSupport.readActiveRegistryIndex(config.stateRoot(), targetKey);
        Path registryPath = config.registryPath();
        if (indexed != null && !Files.exists(indexed)) {
            throw new IllegalStateException("固定目标索引指向的 registry 缺失: " + indexed
                    + "，疑似被移动/删除，拒绝当空处理");
        }
        if (indexed != null && !indexed.equals(registryPath.toAbsolutePath().normalize())) {
            registryPath = indexed;
        }
        Map<String, Object> registry;
        if (Files.exists(registryPath)) {
            registry = FixtureSupport.loadJsonMap(registryPath);
            checkRegistrySchema(registry, registryPath);
        } else {
            registry = null;
        }
        FixtureSupport.writeActiveRegistryIndex(config.stateRoot(), targetKey, registryPath);
        return new PlanContext(conn, lock, registryPath, registry, targetKey,
                config.baseUrl(), config.token(), config.tenantId());
    }

    private static void checkRegistrySchema(Map<String, Object> registry, Path path) {
        FixtureSupport.checkRegistrySchema(registry, path);
    }

    /** 整批预检：目标、摘要、13 品类键、config、物料冲突、计划条目一致性。 */
    private static void preflight(PlanContext ctx, Connection conn,
                                  Map<String, Object> plan) throws SQLException {
        // 1) planDigest 重算比对（不只看前缀/长度）
        String stored = String.valueOf(castMap(plan.get("planDigestRef"), "planDigestRef")
                .get("digest"));
        Map<String, Object> copy = new LinkedHashMap<>(plan);
        copy.remove("planDigestRef");
        String actual = FixtureSupport.canonicalDigest(copy);
        if (!FixtureSupport.digestEquals(stored, actual)) {
            throw new IllegalStateException("planDigest 不匹配: 记录=" + stored + " 现算=" + actual
                    + "，源/计划漂移使旧验证失效");
        }
        // 2) 现场目标核验
        Map<String, String> identity = FixtureSupport.queryServerIdentity(conn);
        Map<String, Object> target = castMap(plan.get("target"), "plan.target");
        if (!Objects.equals(target.get("serverUuid"), identity.get("serverUuid"))
                || !Objects.equals(target.get("database"), identity.get("database"))) {
            throw new IllegalStateException("物理目标漂移: 计划=" + target.get("serverUuid")
                    + "/" + target.get("database") + " 现场=" + identity.get("serverUuid")
                    + "/" + identity.get("database"));
        }
        // 3) 13 正式品类键逐字段
        Map<String, Object> snapshot = castMap(plan.get("categorySnapshot"), "categorySnapshot");
        List<Map<String, Object>> liveCategories = FixtureSupport.queryCategories(
                ctx.connection(), ((Number) target.get("tenantId")).longValue());
        for (Map<String, Object> expected : castList(snapshot.get("bootstrap"), "snapshot.bootstrap")) {
            String code = String.valueOf(expected.get("categoryCode"));
            Map<String, Object> live = null;
            for (Map<String, Object> row : liveCategories) {
                if (code.equals(String.valueOf(row.get("category_code")))) {
                    live = row;
                }
            }
            if (live == null) {
                throw new IllegalStateException("品类 " + code + " 现场缺失（预检失败）");
            }
            if (((Number) live.get("deleted")).intValue() != 0
                    || ((Number) live.get("status")).intValue() != 1
                    || FixtureSupport.asLong(live.get("id")) != FixtureSupport.asLong(expected.get("id"))
                    || FixtureSupport.asLong(live.get("parent_id"))
                    != FixtureSupport.asLong(expected.get("parentId"))
                    || ((Number) live.get("version")).intValue()
                    != ((Number) expected.get("version")).intValue()) {
                throw new IllegalStateException("品类 " + code + " 版本/状态/删除位/拓扑漂移（预检失败）");
            }
        }
        // 4) config 核验
        List<Map<String, Object>> snapshotConfig = castList(plan.get("procConfigSnapshot"),
                "procConfigSnapshot");
        List<Map<String, Object>> liveConfig = FixtureSupport.queryConfig(ctx.connection(),
                ((Number) target.get("tenantId")).longValue());
        if (liveConfig.size() != snapshotConfig.size()) {
            throw new IllegalStateException("proc_config 行数漂移: 计划=" + snapshotConfig.size()
                    + " 现场=" + liveConfig.size());
        }
        for (int i = 0; i < snapshotConfig.size(); i++) {
            Map<String, Object> expectedRow = snapshotConfig.get(i);
            Map<String, Object> liveRow = liveConfig.get(i);
            if (FixtureSupport.asLong(expectedRow.get("id")) != FixtureSupport.asLong(liveRow.get("id"))
                    || ((Number) liveRow.get("version")).intValue()
                    != ((Number) expectedRow.get("version")).intValue()
                    || ((Number) liveRow.get("deleted")).intValue() != 0) {
                throw new IllegalStateException("proc_config 内容漂移（预检失败）");
            }
        }
        // 5) 计划物料冲突（含软删同 code 历史）
        for (Map<String, Object> material : castList(plan.get("materials"), "plan.materials")) {
            String code = String.valueOf(material.get("materialCode"));
            List<Map<String, Object>> rows = FixtureSupport.queryMaterialsByCode(ctx.connection(),
                    ((Number) target.get("tenantId")).longValue(), code);
            for (Map<String, Object> row : rows) {
                boolean deleted = ((Number) row.get("deleted")).intValue() == 1;
                Map<String, Object> entry = findEntryByCode(
                        castList(ctx.registry().get("entries"), "entries"), code);
                boolean ownedByPlan = entry != null;
                if (!deleted && !ownedByPlan) {
                    throw new IllegalStateException("物料 " + code + " 已存在且非本计划所有（预检失败）");
                }
                if (!deleted) {
                    // 已登记物料必须全字段一致才复用原 ID
                    if (FixtureSupport.asLong(row.get("category_id"))
                            != FixtureSupport.asLong(material.get("categoryId"))
                            || !Objects.equals(String.valueOf(row.get("unit")),
                            String.valueOf(material.get("unit")))
                            || ((Number) row.get("asset_managed")).intValue() != (Boolean.parseBoolean(
                            String.valueOf(material.get("assetManaged"))) ? 1 : 0)) {
                        throw new IllegalStateException("物料 " + code + " 字段漂移，拒绝自动认领");
                    }
                }
            }
        }
    }

    /** 未决项恢复判定：API+含软删 DB 精确查询；零列表不证明未提交。 */
    private static void resolvePending(PlanContext ctx, Connection conn,
                                       Map<String, Object> entry,
                                       Map<String, Object> material) throws IOException, SQLException {
        String code = String.valueOf(entry.get("materialCode"));
        String phase = String.valueOf(entry.get("phase"));
        if ("COMMIT_FAILED".equals(phase)) {
            // 确定性失败保留现场，禁止自动重 POST
            throw new IllegalStateException("条目 " + code + " 处于 COMMIT_FAILED，"
                    + "需人工核验后处理，不自动重试");
        }
        if ("CLEAN_FAILED".equals(phase) || "CLEAN_UNKNOWN".equals(phase)) {
            throw new IllegalStateException("条目 " + code + " 清理未决（" + phase + "），"
                    + "先运行 cleanup 核验恢复");
        }
        // HTTP 探测（keyword 精确比较）
        FixtureHttpClient client = new FixtureHttpClient(ctx.baseUrl(), ctx.token());
        List<Map<String, Object>> apiRows = client.listMaterialsByExactCode(code, 50);
        boolean apiExists = !apiRows.isEmpty();
        // DB 含软删
        List<Map<String, Object>> dbRows = FixtureSupport.queryMaterialsByCode(
                conn, ctx.tenantId(), code);
        Map<String, Object> dbLive = null;
        Map<String, Object> dbDeleted = null;
        for (Map<String, Object> row : dbRows) {
            if (((Number) row.get("deleted")).intValue() == 0) {
                dbLive = row;
            } else {
                dbDeleted = row;
            }
        }
        if (apiExists && dbLive != null) {
            // 接管为 COMMITTED（字段必须一致）
            if (FixtureSupport.asLong(dbLive.get("category_id"))
                    != FixtureSupport.asLong(material.get("categoryId"))) {
                throw new IllegalStateException("未决恢复发现 " + code + " 字段漂移，拒绝认领");
            }
            entry.put("phase", "COMMITTED");
            entry.put("resourceId", String.valueOf(FixtureSupport.asLong(dbLive.get("id"))));
            entry.put("version", ((Number) dbLive.get("version")).intValue());
            addAttempt(entry, "UNKNOWN→COMMITTED（API+DB 双确认接管）");
            persistRegistry(ctx, ctx.registry());
            return;
        }
        if (apiExists != (dbLive != null)) {
            throw new IllegalStateException("未决恢复 API/DB 结论不一致（" + code + "），保留阻断");
        }
        if (apiExists) {
            throw new IllegalStateException("未决恢复发现 " + code + " 存在但无法精确接管，保留阻断");
        }
        if (dbDeleted != null) {
            throw new IllegalStateException("未决恢复发现 " + code + " 软删历史，归属不明，保留阻断");
        }
        // 双零 + 连接正常：可判定未提交（结合原请求已结束事实：本进程重启后首次推进）
        addAttempt(entry, "UNKNOWN→NOT_SUBMITTED（API+DB 双零判定）");
        entry.put("phase", "INTENT");
        persistRegistry(ctx, ctx.registry());
    }

    // ==================== 构建辅助 ====================

    private static Map<String, Object> buildBasePlan(ToolConfig config,
                                                     List<Map<String, Object>> materials) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("schemaVersion", FixtureSupport.SCHEMA_VERSION);
        plan.put("fixtureVersion", FIXTURE_VERSION);
        plan.put("fixtureId", config.execution());
        plan.put("generation", config.generation());
        plan.put("repairExecution", "WP005-REPAIR-20260915T020235-a7k2");
        plan.put("planRevision", 1);
        plan.put("createdAt", Instant.now().toString());
        plan.put("timezone", "Asia/Shanghai");
        plan.put("target", Map.of(
                "serverUuid", config.serverUuid() == null ? "" : config.serverUuid(),
                "database", config.database(),
                "tenantId", config.tenantId(),
                "baseURL", config.baseUrl(),
                "namespace", FixtureSupport.TARGET_NAMESPACE));
        plan.put("materials", materials);
        plan.put("requests", buildRequests(materials));
        plan.put("retentionRules", "RETAINED_FOR_WP006_WP007 不清理；CLEANUP 由 cleanup 模式软删");
        plan.put("authorizationRef", "authorization-map.json (WP005-REPAIR-20260915T020235-a7k2)");
        Map<String, Object> validity = new LinkedHashMap<>();
        validity.put("invalidation", "source/target/plan 任一 SHA 变化即失效；registry 未决时本计划不得覆盖");
        validity.put("expiresAt", "UNSET（由执行阶段绑定产物与授权后确定）");
        plan.put("validity", validity);
        Map<String, Object> inputBindings = new LinkedHashMap<>();
        String legacySha = FixtureSupport.sha256File(config.legacyRoot());
        if (legacySha != null) {
            inputBindings.put("legacyFixtureSha256", legacySha);
        }
        inputBindings.put("toolSourceManifest", "R05 build-inputs.json 固定被执行产物副本/hash后绑定；"
                + "source 变更使旧验证失效（见 verification-plan.json sourceInvalidationRules）");
        plan.put("inputBindings", inputBindings);
        return plan;
    }

    /** 请求集合：计划将发出的受控 HTTP 请求预览（条件发送，仅 pendingCodes 命中时）。 */
    private static List<Map<String, Object>> buildRequests(List<Map<String, Object>> materials) {
        List<Map<String, Object>> requests = new ArrayList<>();
        for (Map<String, Object> material : materials) {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", "POST");
            request.put("path", "/procurement/material");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("materialCode", material.get("materialCode"));
            body.put("categoryId", material.get("categoryId"));
            body.put("materialName", material.get("materialName"));
            body.put("unit", material.get("unit"));
            body.put("assetManaged", material.get("assetManaged"));
            body.put("status", material.get("status"));
            request.put("bodyPreview", body);
            request.put("permission", "procurement:material:create");
            request.put("conditional", true);
            requests.add(request);
            if ("CLEANUP".equals(String.valueOf(material.get("retention")))) {
                Map<String, Object> delete = new LinkedHashMap<>();
                delete.put("method", "DELETE");
                delete.put("path", "/procurement/material/{id}?version=<latest>");
                delete.put("permission", "procurement:material:delete");
                delete.put("conditional", true);
                requests.add(delete);
            }
        }
        return requests;
    }

    private static Map<String, Object> materialEntry(String key, String code,
                                                     Map<String, Object> laptopCategory,
                                                     boolean assetManaged, String name,
                                                     String unit, String retention,
                                                     Map<String, Object> legacyRef) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fixtureKey", key);
        entry.put("materialCode", code);
        entry.put("categoryId", FixtureSupport.asLong(laptopCategory.get("id")));
        entry.put("categoryCode", String.valueOf(laptopCategory.get("category_code")));
        entry.put("materialName", name);
        entry.put("unit", unit);
        entry.put("assetManaged", assetManaged);
        entry.put("status", "ACTIVE");
        entry.put("retention", retention);
        if (legacyRef != null) {
            entry.put("legacyRef", Map.of(
                    "reportedHistorical", true,
                    "legacyEntry", legacyRef));
        }
        return entry;
    }

    private static Map<String, Object> categorySnapshot(List<Map<String, Object>> rows) {
        List<Map<String, Object>> bootstrap = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.get("category_code"));
            if (BOOTSTRAP_CODES.contains(code)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", FixtureSupport.asLong(row.get("id")));
                item.put("parentId", FixtureSupport.asLong(row.get("parent_id")));
                item.put("categoryCode", code);
                item.put("status", ((Number) row.get("status")).intValue());
                item.put("deleted", ((Number) row.get("deleted")).intValue());
                item.put("version", ((Number) row.get("version")).intValue());
                bootstrap.add(item);
            }
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("bootstrap", bootstrap);
        snapshot.put("expectedCount", BOOTSTRAP_CODES.size());
        return snapshot;
    }

    private static Map<String, Object> newRegistry(ToolConfig config, Map<String, Object> plan,
                                                   String digest) {
        Map<String, Object> digestRef = new LinkedHashMap<>();
        digestRef.put("digest", digest);
        digestRef.put("algorithm", "canonical-json-sha256");
        Map<String, Object> planWithRef = new LinkedHashMap<>(plan);
        planWithRef.put("planDigestRef", digestRef);
        Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("schemaVersion", FixtureSupport.SCHEMA_VERSION);
        registry.put("fixtureVersion", FIXTURE_VERSION);
        registry.put("fixtureId", config.execution());
        registry.put("generation", config.generation());
        registry.put("plan", planWithRef);
        registry.put("planDigest", digest);
        registry.put("entries", new ArrayList<Map<String, Object>>());
        registry.put("status", "PLANNED");
        return registry;
    }

    private static void persistRegistry(PlanContext ctx, Map<String, Object> registry)
            throws IOException {
        FixtureSupport.atomicWriteJson(ctx.registryPath(), registry);
    }

    private static void addAttempt(Map<String, Object> entry, String note) {
        Object attemptsObj = entry.get("attempts");
        if (!(attemptsObj instanceof List<?>)) {
            entry.put("attempts", new ArrayList<Map<String, Object>>());
            attemptsObj = entry.get("attempts");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attempts = (List<Map<String, Object>>) attemptsObj;
        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("attempt", attempts.size() + 1);
        attempt.put("note", note);
        attempt.put("at", Instant.now().toString());
        attempts.add(attempt);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findEntryByCode(List<Map<String, Object>> entries,
                                                       String code) {
        for (Map<String, Object> entry : entries) {
            if (code.equals(entry.get("materialCode"))) {
                return entry;
            }
        }
        return null;
    }

    private static boolean hasSoftDeletedRow(Connection conn, long tenantId,
                                             String code) throws SQLException {
        List<Map<String, Object>> rows = FixtureSupport.queryMaterialsByCode(conn, tenantId, code);
        for (Map<String, Object> row : rows) {
            if (((Number) row.get("deleted")).intValue() == 1) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> findCategoryByCode(List<Map<String, Object>> rows,
                                                          String code) {
        for (Map<String, Object> row : rows) {
            if (code.equals(String.valueOf(row.get("category_code")))
                    && ((Number) row.get("deleted")).intValue() == 0) {
                return row;
            }
        }
        return null;
    }

    /** 判断品类是否为叶子：同租户内无任何未删除子品类指向它（包级可见，供行为测试直接验证）。 */
    static boolean isLeaf(List<Map<String, Object>> rows, Map<String, Object> category) {
        long id = FixtureSupport.asLong(category.get("id"));
        for (Map<String, Object> row : rows) {
            if (FixtureSupport.asLong(row.get("parent_id")) == id
                    && ((Number) row.get("deleted")).intValue() == 0) {
                return false;
            }
        }
        return true;
    }

    private static void requireWriteSwitch(ToolConfig config, String mode) {
        if (!config.confirmWrite()) {
            throw new IllegalStateException(mode + " 是写模式，必须显式设置 FIXTURE_CONFIRM_WRITE=YES"
                    + "（缺失开关在首写前非零退出）");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("字段 " + field + " 缺失或结构错误");
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value, String field) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("字段 " + field + " 缺失或结构错误");
        }
        return (List<Map<String, Object>>) list;
    }
}
