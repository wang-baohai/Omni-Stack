package com.omni.dbmigrator.migration;

import java.util.List;

/**
 * 数据库迁移目标的单一目录。
 */
public final class MigrationTargetCatalog {

    /** 按依赖顺序排列的九个目标数据库。 */
    private static final List<MigrationTarget> TARGETS = List.of(
            target("auth", "omni_auth", false),
            target("base", "omni_base", false),
            target("workflow", "omni_workflow", false),
            target("crm", "omni_crm", false),
            target("srm", "omni_srm", false),
            target("procurement", "omni_procurement", false),
            target("asset", "omni_asset", false),
            target("nacos", "nacos_config", true),
            target("xxl-job", "xxl_job", true));

    /** 工具类禁止实例化。 */
    private MigrationTargetCatalog() {
    }

    /**
     * 返回不可变迁移目标列表。
     *
     * @return 九个目标数据库
     */
    public static List<MigrationTarget> targets() {
        return TARGETS;
    }

    /**
     * 创建标准目标定义。
     *
     * @param id       目标 ID
     * @param database 数据库名
     * @param vendor   vendor 标记
     * @return 目标定义
     */
    private static MigrationTarget target(String id, String database, boolean vendor) {
        return new MigrationTarget(
                id,
                database,
                "database/changelog/" + id + "/db.changelog-" + id + ".yaml",
                vendor);
    }
}
