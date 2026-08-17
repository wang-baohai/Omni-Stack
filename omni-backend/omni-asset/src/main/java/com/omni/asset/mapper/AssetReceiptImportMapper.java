package com.omni.asset.mapper;

import com.omni.asset.entity.AstAsset;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Procurement 收货资产化专用 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface AssetReceiptImportMapper {

    /**
     * 依靠来源单位唯一键幂等写入一张资产卡片。
     *
     * @param asset 资产卡片
     * @return JDBC 影响行数；调用方必须回查来源单位判断新建或重放
     */
    @Insert("""
            INSERT INTO ast_asset
                (id, tenant_id, asset_no, name, category_code,
                 supplier_id, supplier_name_snapshot,
                 source_po_id, source_gr_id, source_gr_line_id, source_unit_sequence,
                 source_po_no, source_gr_no, purchase_date, purchase_amount, currency_code,
                 status, remark, owner_user_id, owner_unit_id, version, deleted,
                 create_time, update_time, create_by, update_by)
            VALUES
                (#{id}, #{tenantId}, #{assetNo}, #{name}, #{categoryCode},
                 #{supplierId}, #{supplierNameSnapshot},
                 #{sourcePoId}, #{sourceGrId}, #{sourceGrLineId}, #{sourceUnitSequence},
                 #{sourcePoNo}, #{sourceGrNo}, #{purchaseDate}, #{purchaseAmount}, #{currencyCode},
                 #{status}, #{remark}, #{ownerUserId}, #{ownerUnitId}, #{version}, #{deleted},
                 #{createTime}, #{updateTime}, #{createBy}, #{updateBy})
            ON DUPLICATE KEY UPDATE id = ast_asset.id
            """)
    int insertIdempotent(AstAsset asset);

    /**
     * 按来源单位锁定已有资产，用于校验幂等重放意图。
     *
     * @param tenantId 租户 ID
     * @param sourceGrLineId 来源收货行 ID
     * @param sourceUnitSequence 行内单位序号
     * @return 已有资产
     */
    @Select("""
            SELECT *
            FROM ast_asset
            WHERE tenant_id = #{tenantId}
              AND source_gr_line_id = #{sourceGrLineId}
              AND source_unit_sequence = #{sourceUnitSequence}
              AND deleted = 0
            FOR UPDATE
            """)
    AstAsset selectForUpdateBySource(@Param("tenantId") Long tenantId,
                                     @Param("sourceGrLineId") Long sourceGrLineId,
                                     @Param("sourceUnitSequence") Integer sourceUnitSequence);
}
