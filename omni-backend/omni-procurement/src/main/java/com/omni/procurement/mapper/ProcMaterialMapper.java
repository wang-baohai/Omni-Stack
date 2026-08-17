package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.entity.ProcMaterial;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购物料 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcMaterialMapper extends BaseMapper<ProcMaterial> {

    /**
     * 按租户锁定物料行，供品类依赖变更与物料更新串行化。
     *
     * @param tenantId 租户 ID
     * @param id 物料 ID
     * @return 物料，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM proc_material
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
              AND deleted = 0
            FOR UPDATE
            """)
    ProcMaterial selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
