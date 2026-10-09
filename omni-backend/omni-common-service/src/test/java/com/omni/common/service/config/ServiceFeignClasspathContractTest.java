package com.omni.common.service.config;

import feign.micrometer.MicrometerObservationCapability;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务服务 Starter 的 Feign 运行时依赖契约。
 *
 * @author Omni-Stack Team
 */
class ServiceFeignClasspathContractTest {

    @Test
    void shouldProvideLoadBalancerForServiceNameFeignClients() {
        assertThat(BlockingLoadBalancerClient.class).isNotNull();
        assertThat(MicrometerObservationCapability.class).isNotNull();
    }
}
