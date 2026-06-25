package com.omni.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omni.common.core.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * XSS 黑名单规则实体，映射 {@code sys_xss_blacklist_rule} 表。
 * <p>
 * 定义 XSS 防护的正则匹配规则，支持多租户独立配置。
 * 规则类型包括：标签过滤（tag）、属性过滤（attribute）、事件过滤（event）等。
 * 启用状态由 {@code enabled} 字段控制，规则按 {@code sortOrder} 排序执行。</p>
 *
 * @author Omni-Stack Team
 * @see SysXssConfig
 * @see com.omni.common.core.model.BaseEntity
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
