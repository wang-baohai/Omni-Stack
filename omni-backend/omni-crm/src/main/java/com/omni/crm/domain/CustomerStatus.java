package com.omni.crm.domain;

/** 客户生命周期状态。 */
public enum CustomerStatus {
    /** 潜在客户 */ POTENTIAL,
    /** 活跃客户 */ ACTIVE,
    /** 沉睡客户 */ DORMANT,
    /** 流失客户 */ LOST,
    /** 黑名单 */ BLACKLISTED
}
