package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * SRM 评估模板实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_evaluation_template")
public class SrmEvaluationTemplate extends SrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 模板名称 */ private String name;
    /** 状态 */ private Integer status;
    /** 默认模板标记 */ private Boolean defaultFlag;
    /** 乐观锁版本 */ @Version private Integer version;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
