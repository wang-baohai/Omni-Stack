package com.omni.common.core.internal;

import lombok.Data;

import java.io.Serializable;

/**
 * 内部 API 组织单元信息 DTO。
 * <p>用于服务间调用时传递组织单元基本信息，避免跨库查询。</p>
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalOrgDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组织单元 ID */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 父节点 ID */
    private Long parentId;

    /** 组织单元名称 */
    private String name;

    /** 组织单元类型 */
    private String type;

    /** 单元编码 */
    private String unitCode;

    /** 物化路径 */
    private String path;

    /** 状态（1-启用，0-禁用） */
    private Integer status;
}
