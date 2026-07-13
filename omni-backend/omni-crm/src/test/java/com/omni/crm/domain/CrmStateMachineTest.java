package com.omni.crm.domain;

import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CRM 固定状态机单元测试。 */
class CrmStateMachineTest {

    /** 验证线索合法闭环。 */
    @Test
    void shouldAllowLegalLeadLifecycle() {
        assertThatCode(() -> CrmStateMachine.requireLeadTransition(LeadStatus.NEW, LeadStatus.FOLLOWING))
                .doesNotThrowAnyException();
        assertThatCode(() -> CrmStateMachine.requireLeadTransition(LeadStatus.FOLLOWING, LeadStatus.QUALIFIED))
                .doesNotThrowAnyException();
        assertThatCode(() -> CrmStateMachine.requireLeadTransition(LeadStatus.QUALIFIED, LeadStatus.CONVERTED))
                .doesNotThrowAnyException();
    }

    /** 验证终态不可非法迁移。 */
    @Test
    void shouldRejectConvertedLeadReopen() {
        assertThatThrownBy(() -> CrmStateMachine.requireLeadTransition(LeadStatus.CONVERTED, LeadStatus.FOLLOWING))
                .isInstanceOf(BusinessException.class).hasMessageContaining("非法");
    }

    /** 验证商机回退必须填写原因。 */
    @Test
    void shouldRequireReasonForOpportunityRollback() {
        assertThatThrownBy(() -> CrmStateMachine.requireOpportunityOpenTransition(30, 10, ""))
                .isInstanceOf(BusinessException.class).hasMessageContaining("回退");
        assertThatCode(() -> CrmStateMachine.requireOpportunityOpenTransition(30, 10, "客户重新评估"))
                .doesNotThrowAnyException();
    }

    /** 验证活动完成终态。 */
    @Test
    void shouldRejectCompletedActivityMutation() {
        assertThatThrownBy(() -> CrmStateMachine.requireActivityTransition(
                ActivityStatus.COMPLETED, ActivityStatus.PLANNED)).isInstanceOf(BusinessException.class);
    }
}
