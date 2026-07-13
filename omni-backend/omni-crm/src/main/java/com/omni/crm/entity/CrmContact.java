package com.omni.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/** CRM 客户联系人实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contact")
public class CrmContact extends CrmOwnedEntity {
    @Serial private static final long serialVersionUID = 1L;
    /** 客户 ID */ private Long customerId;
    /** 姓名 */ private String name;
    /** 部门 */ private String department;
    /** 职位 */ private String jobTitle;
    /** 手机 */ private String mobile;
    /** 电话 */ private String phone;
    /** 邮箱 */ private String email;
    /** 决策角色 */ private String decisionRole;
    /** 主要联系人标记 */ private Integer primaryFlag;
    /** 状态 */ private Integer status;
}
