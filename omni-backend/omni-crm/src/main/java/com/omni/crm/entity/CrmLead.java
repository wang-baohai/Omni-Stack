package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/** CRM 线索聚合根实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead")
public class CrmLead extends CrmOwnedEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 业务编号 */ private String leadNo;
    /** 姓名 */ private String fullName;
    /** 公司名称 */ private String companyName;
    /** 职位 */ private String jobTitle;
    /** 手机 */ private String mobile;
    /** 电话 */ private String phone;
    /** 邮箱 */ private String email;
    /** 地区 */ private String region;
    /** 地址 */ private String address;
    /** 来源编码 */ private String sourceCode;
    /** 行业编码 */ private String industryCode;
    /** 评级 */ private String rating;
    /** 生命周期状态 */ private String status;
    /** 无效原因 */ private String disqualifyReason;
    /** 分配时间 */ private LocalDateTime assignedTime;
    /** 最后活动时间 */ private LocalDateTime lastActivityTime;
    /** 下次跟进时间 */ private LocalDateTime nextFollowupTime;
    /** 转换时间 */ private LocalDateTime convertedTime;
}
