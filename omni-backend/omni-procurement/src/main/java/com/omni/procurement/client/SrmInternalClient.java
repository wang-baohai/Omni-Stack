package com.omni.procurement.client;

import com.omni.common.core.result.R;
import com.omni.procurement.dto.PurchaseOrderContracts;
import com.omni.procurement.dto.SrmSupplierContracts;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * SRM 供应商内部查询客户端。
 *
 * @author Omni-Stack Team
 */
@FeignClient(name = "omni-srm", contextId = "procurementSrmInternalClient",
        configuration = SrmInternalClient.FeignConfig.class)
public interface SrmInternalClient {

    /**
     * 搜索当前租户的合格供应商摘要。
     *
     * @param tenantIdHeader 租户请求头
     * @param tenantId 租户查询参数
     * @param status 生命周期状态
     * @param categoryCode 供应品类
     * @param limit 返回数量上限
     * @return 供应商摘要
     */
    @GetMapping("/api/internal/supplier/search")
    R<List<SrmSupplierContracts.Summary>> search(
            @RequestHeader("X-Tenant-Id") Long tenantIdHeader,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("status") String status,
            @RequestParam(value = "categoryCode", required = false) String categoryCode,
            @RequestParam("limit") int limit);

    /**
     * 批量查询供应商无 PII 摘要。
     *
     * @param tenantId 租户请求头
     * @param request 批量请求
     * @return 供应商摘要
     */
    @PostMapping("/api/internal/supplier/batch")
    R<List<SrmSupplierContracts.Summary>> batch(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestBody SrmSupplierContracts.BatchRequest request);

    /**
     * 查询指定询价单的当前有效报价快照。
     *
     * @param tenantIdHeader 租户请求头
     * @param tenantId 租户查询参数
     * @param rfqId 询价单 ID
     * @return 当前有效报价快照
     */
    @GetMapping("/api/internal/quotation/batch")
    R<List<PurchaseOrderContracts.QuotationSnapshot>> listValidQuotations(
            @RequestHeader("X-Tenant-Id") Long tenantIdHeader,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("rfqId") Long rfqId);

    /** SRM Feign 内部认证配置。 */
    @Configuration
    class FeignConfig {

        @Value("${omni.internal.api.token:}")
        private String internalToken;

        /**
         * 注入服务间共享认证令牌。
         *
         * @return Feign 请求拦截器
         */
        @Bean
        public RequestInterceptor srmInternalTokenInterceptor() {
            return template -> template.header("X-Internal-Token", internalToken);
        }
    }
}
