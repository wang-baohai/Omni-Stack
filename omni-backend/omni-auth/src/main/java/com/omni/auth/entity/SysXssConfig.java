package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * XSS 防护配置实体，映射 {@code sys_xss_config} 表。
 * <p>
 * 租户级别的 XSS 防护开关，控制该租户是否启用 XSS 过滤。
 * 具体的过滤规则由 {@link SysXssBlacklistRule} 定义。
 * 当 {@code enabled = 1} 时，该租户的所有请求入参会经过 XSS 过滤处理。</p>
 *
 * @author Omni-Stack Team
 * @see SysXssBlacklistRule
 * @see com.omni.common.core.model.BaseEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_xss_config")
public class SysXssConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 是否启用（1-启用，0-禁用） */
    private Integer enabled;
}
