package com.omni.common.mqlog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.mqlog.relay.MqMessageRelayJob;
import com.omni.common.mqlog.relay.MqMessageRelayService;
import com.omni.common.mqlog.sender.MessageSender;
import com.omni.common.mqlog.tenant.TenantProvisionRequestHandler;

/**
 * 租户初始化通用消费者自动装配测试。
 */
class MqLogTenantProvisionAutoConfigurationTest {

    /** 声明模块初始化 SPI 时必须自动暴露处理器和 Cloud Function。 */
    @Test
    void shouldCreateTenantProvisionConsumerWhenProvisionerExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MqLogAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("omni.mqlog.relay.auto-register=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TenantProvisionRequestHandler.class);
                    assertThat(context).hasBean("tenantProvisionRequestedFunction");
                });
    }

    /** 禁用中继时保留 Outbox 写入边界，但不创建投递服务和任务。 */
    @Test
    void shouldKeepOutboxBoundaryWithoutRelayBeansWhenRelayDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MqLogAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "omni.mqlog.relay.enabled=false",
                        "omni.mqlog.relay.auto-register=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("reliableMessageTemplate");
                    assertThat(context.getBean("reliableMessageTemplate"))
                            .isInstanceOf(ReliableMessageRelay.class);
                    assertThat(context).doesNotHaveBean(MqMessageRelayService.class);
                    assertThat(context).doesNotHaveBean(MqMessageRelayJob.class);
                    assertThat(context).doesNotHaveBean("mqRelayJobRegistrar");
                });
    }

    /** 自动装配测试所需的最小外部边界。 */
    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        TenantModuleProvisioner tenantModuleProvisioner() {
            TenantModuleProvisioner provisioner = mock(TenantModuleProvisioner.class);
            when(provisioner.moduleId()).thenReturn("test-module");
            return provisioner;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory() {
            SqlSessionFactory factory = mock(SqlSessionFactory.class);
            org.apache.ibatis.session.Configuration configuration =
                    new org.apache.ibatis.session.Configuration();
            configuration.setEnvironment(new Environment(
                    "test", new JdbcTransactionFactory(), mock(DataSource.class)));
            when(factory.getConfiguration()).thenReturn(configuration);
            return factory;
        }

        @Bean
        ReliableMessageRelay reliableMessageRelay() {
            return mock(ReliableMessageRelay.class);
        }

        @Bean
        MessageSender messageSender() {
            return mock(MessageSender.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
