package com.omni.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建租户请求 DTO。
 * <p>用于超级管理员创建新的租户，租户编码全局唯一。创建后自动初始化租户默认配置。</p>
 *
 * @author Omni-Stack Team
 * @see UpdateTenantRequest
 * @see com.omni.auth.entity.SysTenant
 */
@Data
public class CreateTenantRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户编码（唯一标识） */
    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;

    /** 租户名称 */
    @NotBlank(message = "租户名称不能为空")
    private String tenantName;

    /** 域名 */
    private String domain;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 状态：1-启用, 0-禁用 */
    private Integer status;
}
