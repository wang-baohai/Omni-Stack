package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SRM 风险指标类型实体。
 * <p>动态维护的风险指标分类，替代硬编码枚举。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_risk_indicator_type")
public class SrmRiskIndicatorType extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 指标编码 */ private String typeCode;
    /** 指标名称 */ private String typeName;
    /** 指标说明 */ private String description;
    /** 排序 */ private Integer sort;
    /** 是否自动计算：0=手动 1=自动 */ private Integer autoCalc;
    /** 状态：1=启用 0=禁用 */ private Integer status;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
