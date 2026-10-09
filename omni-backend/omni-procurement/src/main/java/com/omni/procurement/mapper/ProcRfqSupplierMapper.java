package com.omni.procurement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.procurement.entity.ProcRfqSupplier;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 询价供应商邀请 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface ProcRfqSupplierMapper extends BaseMapper<ProcRfqSupplier> {

    /**
     * 锁定指定询价和供应商的有效邀请。
     *
     * @param tenantId 租户 ID
     * @param rfqId 询价单 ID
     * @param supplierId 供应商 ID
     * @return 已锁定的邀请
     */
    @Select("SELECT * FROM proc_rfq_supplier WHERE tenant_id = #{tenantId} "
            + "AND rfq_id = #{rfqId} AND supplier_id = #{supplierId} "
            + "AND deleted = 0 FOR UPDATE")
    ProcRfqSupplier selectForUpdate(@Param("tenantId") Long tenantId,
                                    @Param("rfqId") Long rfqId,
                                    @Param("supplierId") Long supplierId);

    /**
     * 按询价单锁定全部有效供应商邀请，避免定点与报价事件并发覆盖。
     * <p>使用 XML 映射定义以确保 TenantLine 拦截器在 FOR UPDATE 之前正确插入租户条件。</p>
     *
     * @param tenantId 租户 ID（保留用于服务层签名，XML 中由拦截器自动注入）
     * @param rfqId 询价单 ID
     * @return 已锁定的供应商邀请
     */
    List<ProcRfqSupplier> selectForUpdateByRfqXml(@Param("tenantId") Long tenantId,
                                                   @Param("rfqId") Long rfqId);
}
