package com.omni.srm.service.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 供应商规范化名称测试。 */
class SupplierNameNormalizerTest {

    /** 全角字符、大小写和连续空白必须归一化为同一检索值。 */
    @Test
    void shouldNormalizeUnicodeCaseAndWhitespace() {
        assertThat(SupplierNameNormalizer.normalize("  ＡＣＭＥ   Trading  "))
                .isEqualTo("acme trading");
    }
}
