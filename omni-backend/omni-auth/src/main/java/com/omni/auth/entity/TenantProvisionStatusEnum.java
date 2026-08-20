package com.omni.auth.entity;

/**
 * 租户整体初始化状态，与租户业务启停状态相互独立。
 */
public enum TenantProvisionStatusEnum {
    /** 初始化进行中，禁止登录。 */
    PROVISIONING,
    /** 全部目标模块初始化成功，可正常使用。 */
    ACTIVE,
    /** 至少一个目标模块初始化失败，等待重试。 */
    FAILED
}
