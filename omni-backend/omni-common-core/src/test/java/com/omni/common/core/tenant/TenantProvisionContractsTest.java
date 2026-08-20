package com.omni.common.core.tenant;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户初始化事件安全边界测试。
 */
class TenantProvisionContractsTest {

    /**
     * 请求事件不得暴露密码、令牌或联系人隐私字段，模块集合必须不可变。
     */
    @Test
    void should_exclude_credentials_and_freeze_module_ids() {
        List<String> modules = new ArrayList<>(List.of("base", "crm"));
        TenantProvisionContracts.ProvisionRequestedEvent event =
                new TenantProvisionContracts.ProvisionRequestedEvent(
                        "event-1", "request-1", 9L, "tenant-nine", "第九租户",
                        modules, Instant.parse("2026-08-20T10:00:00Z"));
        modules.add("asset");

        assertThat(event.moduleIds()).containsExactly("base", "crm");
        assertThat(event.moduleIds()).isUnmodifiable();
        assertThat(componentNames(TenantProvisionContracts.ProvisionRequestedEvent.class))
                .noneMatch(name -> name.contains("password")
                        || name.contains("token")
                        || name.contains("contact")
                        || name.contains("phone"));
    }

    /**
     * 返回 record 字段名的小写列表。
     */
    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .toList();
    }
}
