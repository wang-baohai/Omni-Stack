package com.omni.procurement.service.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 审批规则技术编码生成器测试。 */
class ApprovalRouteCodeGeneratorTest {

    /** 生成结果必须符合 APR-ULID 格式且连续调用不重复。 */
    @Test
    void shouldGenerateUniqueAprUlidCodes() {
        ApprovalRouteCodeGenerator generator = new ApprovalRouteCodeGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).matches("APR-[0-7][0-9A-HJKMNP-TV-Z]{25}");
        assertThat(second).matches("APR-[0-7][0-9A-HJKMNP-TV-Z]{25}");
        assertThat(second).isNotEqualTo(first);
    }
}
