package com.omni.auth.entity;

/**
 * 单个租户模块的初始化状态。
 */
public enum TenantModuleProvisionStatusEnum {
    /** 等待目标模块处理。 */
    PENDING,
    /** 目标模块处理成功。 */
    SUCCESS,
    /** 目标模块处理失败，可重试。 */
    FAILED
}
