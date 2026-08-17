package com.omni.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.asset.entity.AstAsset;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 资产台账 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface AstAssetMapper extends BaseMapper<AstAsset> {

    /**
     * 按租户锁定一条未删除资产。
     *
     * @param tenantId 租户 ID
     * @param id 资产 ID
     * @return 资产，不存在或不可见时返回 null
     */
    @Select("""
            SELECT *
            FROM ast_asset
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
              AND deleted = 0
            FOR UPDATE
            """)
    AstAsset selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 以版本和空活动槽为条件原子占用资产。
     *
     * @param asset 资产身份、版本和状态快照
     * @param targetStatus 占位后的状态
     * @param operationType TRANSFER/DISPOSAL
     * @param operationId 申请 ID
     * @param updateBy 更新人
     * @return 受影响行数
     */
    @Update("""
            UPDATE ast_asset
            SET status = #{targetStatus},
                active_operation_type = #{operationType},
                active_operation_id = #{operationId},
                version = version + 1,
                update_time = CURRENT_TIMESTAMP,
                update_by = #{updateBy}
            WHERE tenant_id = #{asset.tenantId}
              AND id = #{asset.id}
              AND version = #{asset.version}
              AND status = #{asset.status}
              AND active_operation_type IS NULL
              AND active_operation_id IS NULL
              AND deleted = 0
            """)
    int occupyOperation(@Param("asset") AstAsset asset,
                        @Param("targetStatus") String targetStatus,
                        @Param("operationType") String operationType,
                        @Param("operationId") Long operationId,
                        @Param("updateBy") String updateBy);

    /**
     * 插入后按主键设置由 ID 派生的资产编号，不改变业务版本。
     *
     * @param tenantId 租户 ID
     * @param id 资产 ID
     * @param assetNo 资产编号
     * @return 受影响行数
     */
    @Update("""
            UPDATE ast_asset
            SET asset_no = #{assetNo}
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
              AND deleted = 0
            """)
    int setAssetNoAfterInsert(@Param("tenantId") Long tenantId,
                              @Param("id") Long id,
                              @Param("assetNo") String assetNo);
}
