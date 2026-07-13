package com.omni.crm.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CRM PII 掩码工具测试。 */
class PiiMaskerTest {

    /** 验证手机号、邮箱和地址均在后端掩码。 */
    @Test
    void shouldMaskPiiValues() {
        assertThat(PiiMasker.phone("13812341234")).isEqualTo("138****1234");
        assertThat(PiiMasker.email("alice@example.com")).isEqualTo("a***@example.com");
        assertThat(PiiMasker.address("上海市浦东新区世纪大道100号")).isEqualTo("上海市浦东新******");
    }

    /** 验证空值保持空值。 */
    @Test
    void shouldKeepNullValues() {
        assertThat(PiiMasker.phone(null)).isNull();
        assertThat(PiiMasker.email("")).isEmpty();
    }
}
