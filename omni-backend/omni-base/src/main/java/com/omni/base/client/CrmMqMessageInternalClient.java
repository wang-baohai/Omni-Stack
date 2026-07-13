package com.omni.base.client;

import com.omni.common.core.result.PageResult;
import com.omni.common.core.result.R;
import com.omni.common.mqlog.entity.SysMqMessage;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * CRM 发件箱内部 API 客户端。
 * <p>供 Base 运维页面聚合 CRM 的 MQ 消息记录，不经过网关。</p>
 *
 * @author Omni-Stack Team
 */
@FeignClient(name = "omni-crm", contextId = "crmMqMessageInternalClient",
        configuration = CrmMqMessageInternalClient.FeignConfig.class)
public interface CrmMqMessageInternalClient {

    /**
     * 分页查询 CRM 发件箱。
     */
    @GetMapping("/api/internal/mq-message/list")
    R<PageResult<SysMqMessage>> list(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "msgKey", required = false) String msgKey,
            @RequestParam(value = "serviceName", required = false) String serviceName,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam("page") int page,
            @RequestParam("size") int size);

    /**
     * 查询 CRM 消息详情。
     */
    @GetMapping("/api/internal/mq-message/{msgId}")
    R<SysMqMessage> getByMsgId(@RequestParam("tenantId") Long tenantId,
                               @PathVariable("msgId") String msgId);

    /**
     * 重置 CRM 消息为待投递。
     */
    @PostMapping("/api/internal/mq-message/{msgId}/resend")
    R<Void> resend(@RequestParam("tenantId") Long tenantId,
                   @PathVariable("msgId") String msgId);

    /**
     * 将 CRM 死信标记为已忽略。
     */
    @PostMapping("/api/internal/mq-message/{msgId}/skip")
    R<Void> skip(@RequestParam("tenantId") Long tenantId,
                 @PathVariable("msgId") String msgId);

    /**
     * Feign 服务间认证配置。
     */
    @Configuration
    class FeignConfig {

        @Value("${omni.internal.api.token:}")
        private String internalToken;

        /**
         * 添加内部服务共享密钥请求头。
         *
         * @return Feign 请求拦截器
         */
        @Bean
        public RequestInterceptor crmInternalTokenInterceptor() {
            return template -> {
                if (internalToken != null && !internalToken.isBlank()) {
                    template.header("X-Internal-Token", internalToken);
                }
            };
        }
    }
}
