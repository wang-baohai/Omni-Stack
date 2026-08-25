package com.omni.common.job.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.omni.common.job.SystemJobRegistry;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * XXL-JOB 自动配置功能开关测试。
 */
class XxlJobAutoConfigurationTest {

    /** 禁用执行器时不得创建执行器或任务自动注册器。 */
    @Test
    void shouldNotCreateExecutorOrRegistrarWhenDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(XxlJobAutoConfiguration.class))
                .withPropertyValues("xxl.job.executor.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(XxlJobSpringExecutor.class);
                    assertThat(context).doesNotHaveBean("systemJobAutoRegistrar");
                    assertThat(context).hasSingleBean(SystemJobRegistry.class);
                });
    }
}
