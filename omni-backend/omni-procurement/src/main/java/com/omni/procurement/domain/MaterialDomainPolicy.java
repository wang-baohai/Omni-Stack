package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;

import java.util.Locale;
import java.util.Set;

/**
 * 物料目录不变量策略。
 *
 * @author Omni-Stack Team
 */
public final class MaterialDomainPolicy {

    /** 启用状态。 */
    public static final String ACTIVE = "ACTIVE";

    /** 停用状态。 */
    public static final String INACTIVE = "INACTIVE";

    /** 顶级品类的父 ID。 */
    public static final long ROOT_PARENT_ID = 0L;

    private static final Set<String> DISCRETE_ASSET_UNITS = Set.of("EA", "PCS", "UNIT", "SET");

    private MaterialDomainPolicy() {
    }

    /**
     * 规范化稳定业务编码。
     *
     * @param value 原始编码
     * @param fieldName 字段名称
     * @return 大写编码
     */
    public static String normalizeCode(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    /**
     * 规范化计量单位。
     *
     * @param unit 原始计量单位
     * @return 大写计量单位
     */
    public static String normalizeUnit(String unit) {
        String normalized = trimToNull(unit);
        if (normalized == null) {
            throw new BusinessException(400, "计量单位不能为空");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    /**
     * 校验资产管理与计量单位的组合。
     *
     * @param assetManaged 是否资产管理
     * @param unit 已规范化计量单位
     */
    public static void validateAssetManaged(Boolean assetManaged, String unit) {
        if (Boolean.TRUE.equals(assetManaged) && !DISCRETE_ASSET_UNITS.contains(normalizeUnit(unit))) {
            throw new BusinessException(400, "资产管理物料的计量单位仅支持 EA/PCS/UNIT/SET");
        }
    }

    /**
     * 校验物料状态。
     *
     * @param status 状态
     * @return 规范化状态
     */
    public static String normalizeStatus(String status) {
        String normalized = normalizeCode(status, "物料状态");
        if (!ACTIVE.equals(normalized) && !INACTIVE.equals(normalized)) {
            throw new BusinessException(400, "物料状态仅支持 ACTIVE/INACTIVE");
        }
        return normalized;
    }

    /**
     * 校验父品类合法性（存在且启用），不限层级深度。
     *
     * @param parentId 父品类 ID
     * @param parent 父品类实体，顶级品类时可为 null
     */
    public static void validateCategoryLevel(Long parentId, ProcMaterialCategory parent) {
        if (parentId == null || parentId < ROOT_PARENT_ID) {
            throw new BusinessException(400, "父品类 ID 非法");
        }
        if (parentId == ROOT_PARENT_ID) {
            return;
        }
        if (parent == null || !parentId.equals(parent.getId())) {
            throw new BusinessException(404, "父品类不存在");
        }
        if (!Integer.valueOf(1).equals(parent.getStatus())) {
            throw new BusinessException(409, "父品类已停用");
        }
    }

    /**
     * 校验品类是叶子节点（无子品类），物料只能关联到叶子品类。
     *
     * @param hasChildren 该品类是否存在子品类
     */
    public static void requireLeafCategory(boolean hasChildren) {
        if (hasChildren) {
            throw new BusinessException(400, "物料只能关联到叶子品类");
        }
    }

    /**
     * 校验品类可用于物料。
     *
     * @param category 品类实体
     */
    public static void requireActiveCategory(ProcMaterialCategory category) {
        if (category == null) {
            throw new BusinessException(404, "物料品类不存在");
        }
        if (!Integer.valueOf(1).equals(category.getStatus())) {
            throw new BusinessException(409, "物料品类已停用");
        }
    }

    /**
     * 校验物料可加入请购明细。
     *
     * @param material 物料实体
     */
    public static void requireActiveMaterial(ProcMaterial material) {
        if (material == null) {
            throw new BusinessException(404, "物料不存在");
        }
        if (!ACTIVE.equals(material.getStatus())) {
            throw new BusinessException(409, "仅 ACTIVE 物料可用于请购");
        }
    }

    /**
     * 去除首尾空白并把空文本转换为 null。
     *
     * @param value 原始文本
     * @return 规范化文本
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
