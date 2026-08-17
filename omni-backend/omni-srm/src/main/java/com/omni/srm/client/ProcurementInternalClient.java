package com.omni.srm.client;

import com.omni.common.core.result.R;
import com.omni.srm.dto.quotation.ProcurementRfqInvitationDetail;
import com.omni.srm.dto.quotation.ProcurementRfqInvitationSummary;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Procurement RFQ 邀请内部客户端。
 * <p>所有调用同时携带内部令牌和租户请求头，SRM 不直接访问 Procurement 数据库。</p>
 *
 * @author Omni-Stack Team
 */
@FeignClient(name = "omni-procurement", contextId = "srmProcurementInternalClient",
        configuration = ProcurementInternalClient.FeignConfig.class)
public interface ProcurementInternalClient {

    /**
     * 查询当前供应商收到的 RFQ 邀请。
     *
     * @param tenantId 租户 ID 请求头
     * @param supplierId 供应商 ID
     * @return RFQ 邀请摘要
     */
    @GetMapping("/api/internal/procurement/rfq/invitations")
    R<List<ProcurementRfqInvitationSummary>> listInvitations(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam("supplierId") Long supplierId);

    /**
     * 查询并校验单个 RFQ 邀请及完整行快照。
     *
     * @param tenantId 租户 ID 请求头
     * @param rfqId RFQ ID
     * @param supplierId 供应商 ID
     * @return RFQ 邀请详情
     */
    @GetMapping("/api/internal/procurement/rfq/{rfqId}/invitation")
    R<ProcurementRfqInvitationDetail> getInvitation(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PathVariable("rfqId") Long rfqId,
            @RequestParam("supplierId") Long supplierId);

    /** Feign 内部认证配置。 */
    @Configuration
    class FeignConfig {

        @Value("${omni.internal.api.token:}")
        private String internalToken;

        /**
         * 注入内部服务令牌。
         *
         * @return Feign 请求拦截器
         */
        @Bean
        public RequestInterceptor procurementInternalHeadersInterceptor() {
            return template -> template.header("X-Internal-Token", internalToken);
        }
    }
}
