package com.omni.crm.domain;

import com.omni.common.core.result.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * CRM 固定生命周期状态机。
 *
 * @author Omni-Stack Team
 */
public final class CrmStateMachine {

    private static final Map<LeadStatus, Set<LeadStatus>> LEAD_TRANSITIONS = Map.of(
            LeadStatus.NEW, Set.of(LeadStatus.FOLLOWING, LeadStatus.DISQUALIFIED),
            LeadStatus.FOLLOWING, Set.of(LeadStatus.QUALIFIED, LeadStatus.DISQUALIFIED),
            LeadStatus.QUALIFIED, Set.of(LeadStatus.CONVERTED, LeadStatus.DISQUALIFIED),
            LeadStatus.DISQUALIFIED, Set.of(LeadStatus.FOLLOWING),
            LeadStatus.CONVERTED, Set.of());

    private static final Map<CustomerStatus, Set<CustomerStatus>> CUSTOMER_TRANSITIONS = Map.of(
            CustomerStatus.POTENTIAL, Set.of(CustomerStatus.ACTIVE),
            CustomerStatus.ACTIVE, Set.of(CustomerStatus.DORMANT, CustomerStatus.LOST, CustomerStatus.BLACKLISTED),
            CustomerStatus.DORMANT, Set.of(CustomerStatus.ACTIVE),
            CustomerStatus.LOST, Set.of(CustomerStatus.ACTIVE),
            CustomerStatus.BLACKLISTED, Set.of(CustomerStatus.ACTIVE));

    private static final Map<ActivityStatus, Set<ActivityStatus>> ACTIVITY_TRANSITIONS = Map.of(
            ActivityStatus.PLANNED, Set.of(ActivityStatus.COMPLETED, ActivityStatus.CANCELLED),
            ActivityStatus.CANCELLED, Set.of(ActivityStatus.PLANNED),
            ActivityStatus.COMPLETED, Set.of());

    private CrmStateMachine() {
    }

    /**
     * 校验线索状态迁移。
     *
     * @param from 原状态
     * @param to 目标状态
     */
    public static void requireLeadTransition(LeadStatus from, LeadStatus to) {
        require(LEAD_TRANSITIONS.getOrDefault(from, Set.of()).contains(to), "非法的线索状态迁移");
    }

    /**
     * 校验客户状态迁移。
     *
     * @param from 原状态
     * @param to 目标状态
     */
    public static void requireCustomerTransition(CustomerStatus from, CustomerStatus to) {
        require(CUSTOMER_TRANSITIONS.getOrDefault(from, Set.of()).contains(to), "非法的客户状态迁移");
    }

    /**
     * 校验活动状态迁移。
     *
     * @param from 原状态
     * @param to 目标状态
     */
    public static void requireActivityTransition(ActivityStatus from, ActivityStatus to) {
        require(ACTIVITY_TRANSITIONS.getOrDefault(from, Set.of()).contains(to), "非法的活动状态迁移");
    }

    /**
     * 校验开放商机阶段排序，可前进或回退，回退必须填写原因。
     *
     * @param fromSort 原阶段排序
     * @param toSort 新阶段排序
     * @param reason 变更原因
     */
    public static void requireOpportunityOpenTransition(Integer fromSort, Integer toSort, String reason) {
        if (fromSort != null && toSort != null && toSort < fromSort && (reason == null || reason.isBlank())) {
            throw new BusinessException(400, "商机阶段回退必须填写原因");
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new BusinessException(409, message);
        }
    }
}
