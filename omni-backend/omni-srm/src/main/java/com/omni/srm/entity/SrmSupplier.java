package com.omni.srm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * SRM 供应商聚合根实体。
 *
 * @author Omni-Stack Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier")
public class SrmSupplier extends SrmOwnedEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 供应商编号 */ private String supplierNo;
    /** 供应商名称 */ private String name;
    /** 规范化供应商名称 */ private String normalizedName;
    /** 供应商类型 */ private String supplierType;
    /** 行业编码 */ private String industryCode;
    /** 统一社会信用代码 */ private String creditCode;
    /** 网站 */ private String website;
    /** 电话 */ private String phone;
    /** 邮箱 */ private String email;
    /** 地区 */ private String region;
    /** 地址 */ private String address;
    /** 品类编码 */ private String categoryCode;
    /** 等级编码 STRATEGIC/PREFERRED/QUALIFIED/ELIMINATED */ private String levelCode;
    /** 生命周期状态 */ private String status;
    /** 分配时间 */ private LocalDateTime assignedTime;
    /** 最近评估时间 */ private LocalDateTime lastEvaluationTime;
}
