package com.omni.crm.client;

import com.omni.common.core.internal.InternalDataScopeDTO;
import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.R;
import com.omni.common.core.security.XssSettings;
import com.omni.common.service.config.ServiceIdentityProperties;
import com.omni.common.service.internal.InternalFeignHeadersFactory;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.optionals.OptionalDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Auth 权威身份、组织、权限范围和 XSS 配置内部客户端。
 *
 * @author Omni-Stack Team
 */
@FeignClient(name = "omni-auth", contextId = "crmAuthInternalClient",
        configuration = AuthInternalClient.FeignConfig.class)
public interface AuthInternalClient {

    /**
     * 按完整功能权限解析用户数据范围。
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @param permissionCode 完整权限码
     * @return 数据范围
     */
    @GetMapping("/internal/data-scopes/{userId}")
    R<InternalDataScopeDTO> resolveDataScope(@PathVariable("userId") Long userId,
                                             @RequestParam("tenantId") Long tenantId,
                                             @RequestParam("permissionCode") String permissionCode);

    /**
     * 查询租户内用户。
     *
     * @param id 用户 ID
     * @param tenantId 租户 ID
     * @return 用户信息
     */
    @GetMapping("/internal/users/{id}")
    R<InternalUserDTO> getUser(@PathVariable("id") Long id, @RequestParam("tenantId") Long tenantId);

    /**
     * 批量查询租户内用户。
     *
     * @param ids 用户 ID 文本
     * @param tenantId 租户 ID
     * @return 用户列表
     */
    @GetMapping("/internal/users/batch")
    R<List<InternalUserDTO>> getUsers(@RequestParam("ids") String ids,
                                      @RequestParam("tenantId") Long tenantId);

    /**
     * 查询租户内组织。
     *
     * @param id 组织 ID
     * @param tenantId 租户 ID
     * @return 组织信息
     */
    @GetMapping("/internal/orgs/{id}")
    R<InternalOrgDTO> getOrg(@PathVariable("id") Long id, @RequestParam("tenantId") Long tenantId);

    /**
     * 批量查询租户内组织。
     *
     * @param ids 组织 ID 文本
     * @param tenantId 租户 ID
     * @return 组织列表
     */
    @GetMapping("/internal/orgs/batch")
    R<List<InternalOrgDTO>> getOrgs(@RequestParam("ids") String ids,
                                    @RequestParam("tenantId") Long tenantId);

    /**
     * 查询负责人候选。
     *
     * @param tenantId 租户 ID
     * @param keyword 关键词
     * @param limit 数量限制
     * @return 用户选项
     */
    @GetMapping("/internal/users/options")
    R<List<InternalUserOptionDTO>> listOwnerOptions(@RequestParam("tenantId") Long tenantId,
                                                    @RequestParam(value = "keyword", required = false) String keyword,
                                                    @RequestParam("limit") Integer limit);

    /**
     * 从 Auth 数据库回源租户 XSS 设置。
     *
     * @param tenantId 租户 ID
     * @return XSS 设置
     */
    @GetMapping("/internal/xss/settings")
    R<XssSettings> getXssSettings(@RequestParam("tenantId") Long tenantId);

    /**
     * Feign 内部认证配置。
     */
    class FeignConfig {

        /**
         * 注入服务间认证头。
         *
         * @return Feign 请求拦截器
         */
        @Bean
        public RequestInterceptor internalTokenInterceptor(
                ServiceIdentityProperties properties, InternalFeignHeadersFactory factory) {
            return factory.create(properties.getInternalApi().getToken());
        }

        /**
         * 在单线程 Bean 初始化阶段预先装载 Feign 消息转换器，规避首次并发响应解码竞态。
         *
         * @param messageConverters Feign 消息转换器提供器
         * @return Feign 响应解码器
         */
        @Bean
        public Decoder feignDecoder(ObjectProvider<FeignHttpMessageConverters> messageConverters) {
            messageConverters.getObject().getConverters();
            return new OptionalDecoder(new ResponseEntityDecoder(new SpringDecoder(messageConverters)));
        }
    }
}
