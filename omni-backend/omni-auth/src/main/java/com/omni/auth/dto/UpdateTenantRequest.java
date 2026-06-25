package com.omni.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新租户请求 DTO。
 * <p>所有字段可选，{@code null} 表示不修改该字段。不支持修改 {@code tenantCode}（不可变）。</p>
 *
 * @author Omni-Stack Team
 * @see CreateTenantRequest
 * @see com.omni.auth.entity.SysTenant
 */
@Data
public class UpdateTenantRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户名称 */
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
