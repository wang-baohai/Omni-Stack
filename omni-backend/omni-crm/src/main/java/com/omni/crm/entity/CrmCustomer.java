package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/** CRM 客户聚合根实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer")
public class CrmCustomer extends CrmOwnedEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 业务编号 */ private String customerNo;
    /** 名称 */ private String name;
    /** 归一化名称 */ private String normalizedName;
    /** 客户类型 */ private String customerType;
    /** 行业编码 */ private String industryCode;
    /** 客户级别 */ private String levelCode;
    /** 来源编码 */ private String sourceCode;
    /** 统一信用代码 */ private String creditCode;
    /** 网站 */ private String website;
    /** 电话 */ private String phone;
    /** 邮箱 */ private String email;
    /** 地区 */ private String region;
    /** 地址 */ private String address;
    /** 生命周期状态 */ private String status;
    /** 最后活动时间 */ private LocalDateTime lastActivityTime;
    /** 下次跟进时间 */ private LocalDateTime nextFollowupTime;
}
