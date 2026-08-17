package com.omni.asset.client;

import com.omni.asset.dto.AssetViews;
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

/** Asset 使用的 SRM 内部供应商只读客户端。 */
@FeignClient(name = "omni-srm", contextId = "assetSrmInternalClient",
        configuration = SrmInternalClient.FeignConfig.class)
public interface SrmInternalClient {

    /** 按租户搜索可选供应商。 */
    @GetMapping("/api/internal/supplier/search")
    R<List<AssetViews.SupplierOptionVO>> search(
            @RequestHeader("X-Tenant-Id") Long headerTenantId,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("status") String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam("limit") int limit);

    /** Feign 内部认证配置。 */
    @Configuration
    class FeignConfig {
        @Value("${omni.internal.api.token:}")
        private String internalToken;

        /** 注入服务间共享认证令牌。 */
        @Bean
        public RequestInterceptor assetSrmInternalTokenInterceptor() {
            return template -> template.header("X-Internal-Token", internalToken);
        }
    }
}
