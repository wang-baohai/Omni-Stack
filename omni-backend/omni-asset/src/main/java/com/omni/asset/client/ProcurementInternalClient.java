package com.omni.asset.client;

import com.omni.asset.dto.ProcurementAssetContracts;
import com.omni.common.core.result.R;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Procurement 资产候选内部客户端。
 *
 * @author Omni-Stack Team
 */
@FeignClient(name = "omni-procurement", contextId = "assetProcurementInternalClient",
        configuration = ProcurementInternalClient.FeignConfig.class)
public interface ProcurementInternalClient {

    /**
     * 游标查询已确认的历史资产候选行。
     *
     * @param tenantIdHeader 租户请求头
     * @param tenantId 租户查询参数
     * @param afterId 起始收货行 ID（不含）
     * @param size 返回上限
     * @return 历史资产候选行
     */
    @GetMapping("/api/internal/procurement/goods-receipt/asset-candidates")
    R<List<ProcurementAssetContracts.AssetCandidate>> listAssetCandidates(
            @RequestHeader("X-Tenant-Id") Long tenantIdHeader,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("afterId") Long afterId,
            @RequestParam("size") Integer size);

    /** Procurement Feign 内部认证配置。 */
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
        public RequestInterceptor assetProcurementInternalTokenInterceptor() {
            return template -> template.header("X-Internal-Token", internalToken);
        }
    }
}
