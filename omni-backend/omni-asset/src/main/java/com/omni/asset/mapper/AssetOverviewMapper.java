package com.omni.asset.mapper;

import com.omni.asset.dto.AssetOverviewViews;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 资产概览聚合 Mapper。
 * <p>所有查询直接命中资产聚合根，使 TenantLine 与管理维度 DataScope 自动生效。</p>
 *
 * @author Omni-Stack Team
 */
public interface AssetOverviewMapper {

    /**
     * 按生命周期状态统计数量。
     *
     * @return 状态计数
     */
    @Select("SELECT asset.status AS status, COUNT(*) AS count FROM ast_asset asset "
            + "WHERE asset.deleted = 0 GROUP BY asset.status")
    List<AssetOverviewViews.StatusCount> selectStatusCounts();

    /**
     * 按币种统计资产原值。
     *
     * @return 币种金额
     */
    @Select("SELECT asset.currency_code AS currency_code, SUM(asset.purchase_amount) AS amount "
            + "FROM ast_asset asset WHERE asset.deleted = 0 "
            + "AND asset.currency_code IS NOT NULL AND asset.purchase_amount IS NOT NULL "
            + "GROUP BY asset.currency_code ORDER BY asset.currency_code")
    List<AssetOverviewViews.CurrencyAmount> selectAmountsByCurrency();

    /**
     * 按状态和币种聚合。
     *
     * @param limit 最大行数
     * @return 分布行
     */
    @Select("SELECT asset.status AS dimension_key, asset.status AS dimension_name, "
            + "asset.status AS status, asset.currency_code AS currency_code, COUNT(*) AS count, "
            + "COALESCE(SUM(asset.purchase_amount), 0) AS amount FROM ast_asset asset "
            + "WHERE asset.deleted = 0 GROUP BY asset.status, asset.currency_code "
            + "ORDER BY count DESC, asset.status, asset.currency_code LIMIT #{limit}")
    List<AssetOverviewViews.DistributionItem> selectStatusDistribution(@Param("limit") int limit);

    /**
     * 按品类和币种聚合。
     *
     * @param limit 最大行数
     * @return 分布行
     */
    @Select("SELECT asset.category_code AS dimension_key, asset.category_code AS dimension_name, "
            + "asset.currency_code AS currency_code, COUNT(*) AS count, "
            + "COALESCE(SUM(asset.purchase_amount), 0) AS amount FROM ast_asset asset "
            + "WHERE asset.deleted = 0 GROUP BY asset.category_code, asset.currency_code "
            + "ORDER BY count DESC, asset.category_code, asset.currency_code LIMIT #{limit}")
    List<AssetOverviewViews.DistributionItem> selectCategoryDistribution(@Param("limit") int limit);

    /**
     * 按管理部门和币种聚合。
     *
     * @param limit 最大行数
     * @return 分布行
     */
    @Select("SELECT CAST(asset.owner_unit_id AS CHAR) AS dimension_key, "
            + "CAST(asset.owner_unit_id AS CHAR) AS dimension_name, "
            + "asset.currency_code AS currency_code, COUNT(*) AS count, "
            + "COALESCE(SUM(asset.purchase_amount), 0) AS amount FROM ast_asset asset "
            + "WHERE asset.deleted = 0 GROUP BY asset.owner_unit_id, asset.currency_code "
            + "ORDER BY count DESC, asset.owner_unit_id, asset.currency_code LIMIT #{limit}")
    List<AssetOverviewViews.DistributionItem> selectDepartmentDistribution(@Param("limit") int limit);

    /**
     * 按位置和币种聚合。
     *
     * @param limit 最大行数
     * @return 分布行
     */
    @Select("SELECT COALESCE(asset.location_code, 'UNASSIGNED') AS dimension_key, "
            + "COALESCE(asset.location_code, 'UNASSIGNED') AS dimension_name, "
            + "asset.currency_code AS currency_code, COUNT(*) AS count, "
            + "COALESCE(SUM(asset.purchase_amount), 0) AS amount FROM ast_asset asset "
            + "WHERE asset.deleted = 0 GROUP BY asset.location_code, asset.currency_code "
            + "ORDER BY count DESC, dimension_key, asset.currency_code LIMIT #{limit}")
    List<AssetOverviewViews.DistributionItem> selectLocationDistribution(@Param("limit") int limit);
}
