package com.omni.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新租户请求 DTO，所有字段可选（null 表示不修改）。
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
