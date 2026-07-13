package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/** CRM 销售管道实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_pipeline")
public class CrmPipeline extends CrmTenantEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 稳定编码 */ private String code;
    /** 名称 */ private String name;
    /** 状态 */ private Integer status;
    /** 默认标记 */ private Integer defaultFlag;
    /** 排序 */ private Integer sort;
    /** 乐观锁版本 */ @Version private Integer version;
    /** 逻辑删除标记 */ @TableLogic private Integer deleted;
}
