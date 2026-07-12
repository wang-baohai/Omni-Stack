package com.omni.base.client;

import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.result.R;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * omni-auth 内部 API Feign 客户端。
 * <p>用于 omni-base 服务调用 omni-auth 的用户/组织信息查询接口。
 * 通过 {@code X-Internal-Token} 进行服务间认证。</p>
 *
 * @author Omni-Stack Team
 */
@FeignClient(name = "omni-auth", contextId = "authInternalClient",
        configuration = AuthInternalClient.FeignConfig.class)
public interface AuthInternalClient {

    /**
     * 根据用户 ID 获取用户基本信息。
     *
     * @param id 用户 ID
     * @return 用户 DTO
     */
    @GetMapping("/internal/users/{id}")
    R<InternalUserDTO> getUserById(@PathVariable("id") Long id);

    /**
     * 批量获取用户基本信息。
     *
     * @param ids 用户 ID 列表（逗号分隔）
     * @return 用户 DTO 列表
     */
    @GetMapping("/internal/users/batch")
    R<List<InternalUserDTO>> getUsersByIds(@RequestParam("ids") String ids);

    /**
     * 根据组织单元 ID 获取组织信息。
     *
     * @param id 组织单元 ID
     * @return 组织 DTO
     */
    @GetMapping("/internal/orgs/{id}")
    R<InternalOrgDTO> getOrgById(@PathVariable("id") Long id);

    /**
     * 批量获取组织单元信息。
     *
     * @param ids 组织单元 ID 列表（逗号分隔）
     * @return 组织 DTO 列表
     */
    @GetMapping("/internal/orgs/batch")
    R<List<InternalOrgDTO>> getOrgsByIds(@RequestParam("ids") String ids);

    /**
     * Feign 拦截器配置，自动添加 {@code X-Internal-Token} 请求头。
     */
    @Configuration
    class FeignConfig {

        @Value("${omni.internal.api.token:}")
        private String internalToken;

        @Bean
        public RequestInterceptor internalTokenInterceptor() {
            return template -> {
                if (internalToken != null && !internalToken.isBlank()) {
                    template.header("X-Internal-Token", internalToken);
                }
            };
        }
    }
}
