package com.omni.crm.domain;

/** 线索生命周期状态。 */
public enum LeadStatus {
    /** 新线索 */ NEW,
    /** 跟进中 */ FOLLOWING,
    /** 已合格 */ QUALIFIED,
    /** 已转换 */ CONVERTED,
    /** 已判无效 */ DISQUALIFIED
}
