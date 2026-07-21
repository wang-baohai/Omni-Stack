package com.omni.srm.security;

import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.entity.SrmSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** SRM PII 掩码规则测试。 */
class PiiMaskerTest {

    /** 手机号仅保留前三位和后四位。 */
    @Test
    void shouldMaskPhone() {
        assertThat(PiiMasker.phone("13812345678")).isEqualTo("138****5678");
    }

    /** 邮箱仅保留首字符和域名。 */
    @Test
    void shouldMaskEmail() {
        assertThat(PiiMasker.email("alice@example.com")).isEqualTo("a***@example.com");
    }

    /** 银行账户仅保留前四位和后四位。 */
    @Test
    void shouldMaskBankAccount() {
        assertThat(PiiMasker.bankAccount("6222020202021234")).isEqualTo("6222********1234");
    }

    /** 信用代码和地址不属于设计中的 PII 掩码范围。 */
    @Test
    void shouldKeepCreditCodeAndAddressVisible() {
        SrmSupplier supplier = new SrmSupplier();
        supplier.setCreditCode("91310000123456789X");
        supplier.setAddress("上海市浦东新区示例路 1 号");
        assertThat(SrmViewAssembler.supplier(supplier, false).getCreditCode())
                .isEqualTo("91310000123456789X");
        assertThat(SrmViewAssembler.supplier(supplier, false).getAddress())
                .isEqualTo("上海市浦东新区示例路 1 号");
    }
}
