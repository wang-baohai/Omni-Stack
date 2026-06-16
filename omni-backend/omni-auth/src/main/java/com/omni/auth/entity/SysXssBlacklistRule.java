package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * XSS 黑名单规则实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_xss_blacklist_rule")
public class SysXssBlacklistRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 规则名称 */
    private String ruleName;

    /** 规则类型 */
    private String ruleType;

    /** 匹配正则表达式 */
    private String pattern;

    /** 是否启用（1-启用，0-禁用） */
    private Integer enabled;

    /** 规则描述 */
    private String description;

    /** 排序序号 */
    private Integer sortOrder;
}
