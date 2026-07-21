package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 绩效评估业务准入策略测试。 */
class SrmEvaluationPolicyTest {

    /** 仅审核通过且 owner 完整的供应商可以评估。 */
    @Test
    void shouldRequireApprovedSupplierWithCompleteOwner() {
        assertThatCode(() -> SrmEvaluationPolicy.requireEligible("APPROVED", 7L, 8L))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SrmEvaluationPolicy.requireEligible("PENDING_REVIEW", 7L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        assertThatThrownBy(() -> SrmEvaluationPolicy.requireEligible("APPROVED", null, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
        assertThatThrownBy(() -> SrmEvaluationPolicy.requireEligible("APPROVED", 7L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }
}
