package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.entity.ProcMaterialCategory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购物料品类 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcMaterialCategoryMapper extends BaseMapper<ProcMaterialCategory> {

    /**
     * 按租户锁定品类行，供引用校验与停用、删除操作串行化。
     *
     * @param tenantId 租户 ID
     * @param id 品类 ID
     * @return 品类，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM proc_material_category
            WHERE tenant_id = #{tenantId}
              AND id = #{id}
              AND deleted = 0
            FOR UPDATE
            """)
    ProcMaterialCategory selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
