package com.omni.crm.security;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CRM 租户上下文失败关闭测试。 */
class CrmTenantContextTest {

    /** 清理线程上下文。 */
    @AfterEach
    void clear() { CrmTenantContext.clear(); }

    /** 缺少身份上下文必须拒绝。 */
    @Test
    void shouldFailClosedWhenMissing() {
        assertThatThrownBy(CrmTenantContext::requireTenantId).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(403));
    }

    /** 合法身份应原样返回。 */
    @Test
    void shouldExposeBoundIdentity() {
        CrmTenantContext.set(new CrmTenantContext.RequestIdentity(7L, 9L, "sales"));
        assertThat(CrmTenantContext.requireTenantId()).isEqualTo(9L);
        assertThat(CrmTenantContext.require().userId()).isEqualTo(7L);
    }
}
