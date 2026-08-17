package com.omni.srm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.srm.entity.SrmQuotation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SRM 供应商报价头 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SrmQuotationMapper extends BaseMapper<SrmQuotation> {

    /**
     * 按租户、询价单和供应商锁定当前有效报价。
     *
     * @param tenantId 租户 ID
     * @param rfqId 询价单 ID
     * @param supplierId 供应商 ID
     * @return 当前有效报价，不存在时返回 null
     */
    @Select("SELECT * FROM srm_quotation "
            + "WHERE tenant_id = #{tenantId} AND rfq_id = #{rfqId} "
            + "AND supplier_id = #{supplierId} AND deleted = 0 FOR UPDATE")
    SrmQuotation selectForUpdate(@Param("tenantId") Long tenantId,
                                 @Param("rfqId") Long rfqId,
                                 @Param("supplierId") Long supplierId);
}
