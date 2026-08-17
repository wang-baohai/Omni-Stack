package com.omni.srm.service;

import com.omni.srm.dto.InternalSupplierSummary;

import java.util.List;

/**
 * 服务间供应商只读查询服务。
 *
 * @author Omni-Stack Team
 */
public interface InternalSupplierService {

    /**
     * 按租户和主键查询供应商摘要。
     *
     * @param tenantId 租户 ID
     * @param supplierId 供应商 ID
     * @return 供应商摘要
     */
    InternalSupplierSummary get(Long tenantId, Long supplierId);

    /**
     * 按状态和品类搜索供应商摘要。
     *
     * @param tenantId 租户 ID
     * @param status 生命周期状态
     * @param categoryCode 品类编码
     * @param limit 返回上限
     * @return 供应商摘要列表
     */
    List<InternalSupplierSummary> search(Long tenantId, String status, String categoryCode, int limit);

    /**
     * 按名称或编号关键词搜索供应商摘要。
     *
     * @param tenantId 租户 ID
     * @param status 生命周期状态
     * @param categoryCode 品类编码
     * @param keyword 供应商名称或编号关键词
     * @param limit 返回上限
     * @return 供应商摘要列表
     */
    List<InternalSupplierSummary> searchOptions(Long tenantId, String status,
                                                String categoryCode, String keyword, int limit);

    /**
     * 按租户和供应商 ID 批量查询摘要。
     *
     * @param tenantId 租户 ID
     * @param supplierIds 供应商 ID 列表
     * @return 去重且保持输入顺序的供应商摘要列表
     */
    List<InternalSupplierSummary> batch(Long tenantId, List<Long> supplierIds);
}
