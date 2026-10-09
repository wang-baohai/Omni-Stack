package com.omni.auth.consumer;

import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.omni.auth.service.TenantProvisionService;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 租户模块初始化结果消费者。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TenantProvisionResultConsumer {

    private final TenantProvisionService tenantProvisionService;

    /**
     * 消费各模块的初始化终态并交给 Auth 汇总。
     *
     * @return 结果消费函数
     */
    @Bean(name = "tenantProvisionResultFunction")
    public Consumer<ProvisionResultEvent> tenantProvisionResultFunction() {
        return event -> {
            log.info("收到租户模块初始化结果: tenantId={}, requestId={}, moduleId={}, status={}",
                    event.tenantId(), event.requestId(), event.moduleId(), event.status());
            try {
                tenantProvisionService.handleResult(event);
            } catch (RuntimeException exception) {
                log.error("处理租户模块初始化结果失败: tenantId={}, requestId={}, moduleId={}",
                        event.tenantId(), event.requestId(), event.moduleId(), exception);
                throw exception;
            }
        };
    }
}
