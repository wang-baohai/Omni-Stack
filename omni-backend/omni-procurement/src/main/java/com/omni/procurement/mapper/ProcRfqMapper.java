package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.entity.ProcRfq;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 询价单 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcRfqMapper extends BaseMapper<ProcRfq> {

    /**
     * 按当前租户与数据范围锁定询价单。
     *
     * @param tenantId 租户 ID
     * @param id 询价单 ID
     * @return 已锁定的询价单
     */
    @Select("SELECT * FROM proc_rfq WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND deleted = 0 FOR UPDATE")
    ProcRfq selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
