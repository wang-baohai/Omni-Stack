package com.omni.common.observability;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** OTLP Trace 导出器自动配置测试。 */
class OtlpTracingExporterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OtlpTracingExporterAutoConfiguration.class));

    /** HTTP 传输开启时应创建唯一导出器并绑定官方属性。 */
    @Test
    void shouldCreateHttpExporterWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "management.tracing.export.otlp.enabled=true",
                        "management.opentelemetry.tracing.export.otlp.transport=http",
                        "management.opentelemetry.tracing.export.otlp.endpoint=http://collector:4318/v1/traces",
                        "management.opentelemetry.tracing.export.otlp.headers.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
                    assertThat(context).hasNotFailed();
                });
    }

    /** 显式关闭 OTLP 时不得创建导出器。 */
    @Test
    void shouldNotCreateExporterWhenDisabled() {
        contextRunner
                .withPropertyValues("management.tracing.export.otlp.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class));
    }
}
