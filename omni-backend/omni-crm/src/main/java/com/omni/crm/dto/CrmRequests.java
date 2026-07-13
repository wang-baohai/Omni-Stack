package com.omni.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CRM 请求 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class CrmRequests {

    private CrmRequests() {
    }

    /** 通用分页查询。 */
    @Data
    public static class PageQuery implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 页码 */ @Min(1) private int page = 1;
        /** 每页大小 */ @Min(1) @Max(100) private int size = 10;
    }

    /** 线索分页条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LeadQuery extends PageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 关键词 */ private String keyword;
        /** 状态 */ private String status;
        /** 负责人 */ private Long ownerUserId;
        /** 来源 */ private String sourceCode;
    }

    /** 创建线索请求。 */
    @Data
    public static class CreateLeadRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 姓名 */ @NotBlank @Size(max = 100) private String fullName;
        /** 公司 */ @Size(max = 200) private String companyName;
        /** 职位 */ @Size(max = 100) private String jobTitle;
        /** 手机 */ @Size(max = 32) private String mobile;
        /** 电话 */ @Size(max = 32) private String phone;
        /** 邮箱 */ @Email @Size(max = 200) private String email;
        /** 地区 */ @Size(max = 100) private String region;
        /** 地址 */ @Size(max = 500) private String address;
        /** 来源编码 */ @Size(max = 50) private String sourceCode;
        /** 行业编码 */ @Size(max = 50) private String industryCode;
        /** 评级 */ @Size(max = 20) private String rating;
        /** 负责人用户 ID */ private Long ownerUserId;
        /** 下次跟进时间 */ private LocalDateTime nextFollowupTime;
    }

    /** 更新线索请求。 */
    @Data
    public static class UpdateLeadRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本 */ @NotNull private Integer version;
        /** 姓名 */ @Size(max = 100) private String fullName;
        /** 公司 */ @Size(max = 200) private String companyName;
        /** 职位 */ @Size(max = 100) private String jobTitle;
        /** 手机 */ @Size(max = 32) private String mobile;
        /** 电话 */ @Size(max = 32) private String phone;
        /** 邮箱 */ @Email @Size(max = 200) private String email;
        /** 地区 */ @Size(max = 100) private String region;
        /** 地址 */ @Size(max = 500) private String address;
        /** 来源 */ @Size(max = 50) private String sourceCode;
        /** 行业 */ @Size(max = 50) private String industryCode;
        /** 评级 */ @Size(max = 20) private String rating;
        /** 下次跟进时间 */ private LocalDateTime nextFollowupTime;
    }

    /** 通用版本命令。 */
    @Data
    public static class VersionRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 乐观锁版本 */ @NotNull private Integer version;
    }

    /** 负责人分配命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AssignRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 目标负责人 */ @NotNull private Long ownerUserId;
        /** 原因 */ @Size(max = 500) private String reason;
    }

    /** 批量线索分配命令。 */
    @Data
    public static class BatchAssignRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 线索及版本 */ @Valid @NotEmpty @Size(max = 100) private List<VersionedId> items;
        /** 目标负责人 */ @NotNull private Long ownerUserId;
        /** 原因 */ @Size(max = 500) private String reason;
    }

    /** ID 与版本。 */
    @Data
    public static class VersionedId implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** ID */ @NotNull private Long id;
        /** 版本 */ @NotNull private Integer version;
    }

    /** 线索判无效命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DisqualifyLeadRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 无效原因 */ @NotBlank @Size(max = 500) private String reason;
    }

    /** 线索重复检测请求。 */
    @Data
    public static class LeadDuplicateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 公司 */ @Size(max = 200) private String companyName;
        /** 手机 */ @Size(max = 32) private String mobile;
        /** 邮箱 */ @Email @Size(max = 200) private String email;
    }

    /** 线索转换命令。 */
    @Data
    public static class ConvertLeadRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 线索版本 */ @NotNull private Integer version;
        /** 客户模式 CREATE/LINK */ @NotBlank private String customerMode;
        /** 已有客户 ID */ private Long customerId;
        /** 新客户名称 */ private String customerName;
        /** 联系人模式 CREATE/LINK */ @NotBlank private String contactMode;
        /** 已有联系人 ID */ private Long contactId;
        /** 新联系人姓名 */ private String contactName;
        /** 新联系人手机 */ private String contactMobile;
        /** 新联系人邮箱 */ @Email private String contactEmail;
        /** 是否创建商机 */ private boolean createOpportunity;
        /** 商机名称 */ private String opportunityName;
        /** 商机金额 */ @DecimalMin("0.00") private BigDecimal amount;
        /** 预计成交日期 */ private LocalDate expectedCloseDate;
    }

    /** 客户查询条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CustomerQuery extends PageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 关键词 */ private String keyword;
        /** 状态 */ private String status;
        /** 负责人 */ private Long ownerUserId;
    }

    /** 创建客户请求。 */
    @Data
    public static class CreateCustomerRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 客户名称 */ @NotBlank @Size(max = 200) private String name;
        /** 客户类型 */ @Size(max = 50) private String customerType;
        /** 行业 */ @Size(max = 50) private String industryCode;
        /** 级别 */ @Size(max = 50) private String levelCode;
        /** 来源 */ @Size(max = 50) private String sourceCode;
        /** 统一信用代码 */ @Size(max = 50) private String creditCode;
        /** 网站 */ @Size(max = 300) private String website;
        /** 电话 */ @Size(max = 32) private String phone;
        /** 邮箱 */ @Email @Size(max = 200) private String email;
        /** 地区 */ @Size(max = 100) private String region;
        /** 地址 */ @Size(max = 500) private String address;
        /** 负责人 */ private Long ownerUserId;
    }

    /** 更新客户请求。 */
    @Data
    public static class UpdateCustomerRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 版本 */ @NotNull private Integer version;
        /** 名称 */ @Size(max = 200) private String name;
        /** 类型 */ @Size(max = 50) private String customerType;
        /** 行业 */ @Size(max = 50) private String industryCode;
        /** 级别 */ @Size(max = 50) private String levelCode;
        /** 来源 */ @Size(max = 50) private String sourceCode;
        /** 统一信用代码 */ @Size(max = 50) private String creditCode;
        /** 网站 */ @Size(max = 300) private String website;
        /** 电话 */ @Size(max = 32) private String phone;
        /** 邮箱 */ @Email @Size(max = 200) private String email;
        /** 地区 */ @Size(max = 100) private String region;
        /** 地址 */ @Size(max = 500) private String address;
    }

    /** 联系人查询条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ContactQuery extends PageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 客户 ID */ private Long customerId;
        /** 关键词 */ private String keyword;
        /** 状态 */ private Integer status;
    }

    /** 客户状态命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CustomerStatusRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 目标状态 */ @NotBlank private String status;
    }

    /** 客户负责人转移命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TransferCustomerRequest extends AssignRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 是否级联开放商机 */ private boolean cascadeOpenOpportunities;
    }

    /** 客户重复检测请求。 */
    @Data
    public static class CustomerDuplicateRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 名称 */ private String name;
        /** 信用代码 */ private String creditCode;
        /** 电话 */ private String phone;
    }

    /** 创建联系人请求。 */
    @Data
    public static class CreateContactRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 姓名 */ @NotBlank @Size(max = 100) private String name;
        /** 部门 */ @Size(max = 100) private String department;
        /** 职位 */ @Size(max = 100) private String jobTitle;
        /** 手机 */ @Size(max = 32) private String mobile;
        /** 电话 */ @Size(max = 32) private String phone;
        /** 邮箱 */ @Email @Size(max = 200) private String email;
        /** 决策角色 */ @Size(max = 50) private String decisionRole;
        /** 是否主要联系人 */ private boolean primary;
    }

    /** 更新联系人请求。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateContactRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 姓名 */ @Size(max = 100) private String name;
        /** 部门 */ @Size(max = 100) private String department;
        /** 职位 */ @Size(max = 100) private String jobTitle;
        /** 手机 */ @Size(max = 32) private String mobile;
        /** 电话 */ @Size(max = 32) private String phone;
        /** 邮箱 */ @Email @Size(max = 200) private String email;
        /** 决策角色 */ @Size(max = 50) private String decisionRole;
        /** 状态 */ private Integer status;
    }

    /** 商机查询条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OpportunityQuery extends PageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 关键词 */ private String keyword;
        /** 状态 */ private String status;
        /** 阶段 */ private Long stageId;
        /** 负责人 */ private Long ownerUserId;
        /** 客户 */ private Long customerId;
    }

    /** 创建商机请求。 */
    @Data
    public static class CreateOpportunityRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 名称 */ @NotBlank @Size(max = 200) private String name;
        /** 客户 */ @NotNull private Long customerId;
        /** 主要联系人 */ private Long primaryContactId;
        /** 管道 */ private Long pipelineId;
        /** 初始阶段 */ private Long stageId;
        /** 金额 */ @NotNull @DecimalMin("0.00") private BigDecimal amount;
        /** 预计成交日期 */ private LocalDate expectedCloseDate;
        /** 负责人 */ private Long ownerUserId;
    }

    /** 更新商机请求。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateOpportunityRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 名称 */ @Size(max = 200) private String name;
        /** 主要联系人 */ private Long primaryContactId;
        /** 金额 */ @DecimalMin("0.00") private BigDecimal amount;
        /** 预计成交日期 */ private LocalDate expectedCloseDate;
        /** 下次跟进时间 */ private LocalDateTime nextFollowupTime;
    }

    /** 商机阶段命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class OpportunityStageRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 目标阶段 */ @NotNull private Long stageId;
        /** 变更原因 */ @Size(max = 500) private String reason;
        /** 输单原因 */ @Size(max = 500) private String lossReason;
    }

    /** 活动查询条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ActivityQuery extends PageQuery {
        @Serial private static final long serialVersionUID = 1L;
        /** 根类型 */ private String rootType;
        /** 根 ID */ private Long rootId;
        /** 状态 */ private String status;
        /** 负责人 */ private Long ownerUserId;
        /** 开始时间 */ @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime fromTime;
        /** 结束时间 */ @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime toTime;
    }

    /** 创建活动请求。 */
    @Data
    public static class CreateActivityRequest implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 根类型 */ @NotBlank private String rootType;
        /** 根 ID */ @NotNull private Long rootId;
        /** 联系人 */ private Long contactId;
        /** 类型 */ @NotBlank @Size(max = 50) private String activityType;
        /** 主题 */ @NotBlank @Size(max = 200) private String subject;
        /** 纯文本内容 */ @Size(max = 4000) private String content;
        /** 状态 */ private String status;
        /** 计划开始 */ private LocalDateTime plannedStartTime;
        /** 计划结束 */ private LocalDateTime plannedEndTime;
        /** 完成时间 */ private LocalDateTime completedTime;
        /** 下一行动 */ private LocalDateTime nextActionTime;
    }

    /** 更新活动请求。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateActivityRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 类型 */ @Size(max = 50) private String activityType;
        /** 主题 */ @Size(max = 200) private String subject;
        /** 纯文本内容 */ @Size(max = 4000) private String content;
        /** 联系人 */ private Long contactId;
        /** 计划开始 */ private LocalDateTime plannedStartTime;
        /** 计划结束 */ private LocalDateTime plannedEndTime;
        /** 下一行动 */ private LocalDateTime nextActionTime;
    }

    /** 完成活动命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CompleteActivityRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 完成时间 */ private LocalDateTime completedTime;
        /** 下一行动时间 */ private LocalDateTime nextActionTime;
    }

    /** 取消活动命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CancelActivityRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 原因 */ @NotBlank @Size(max = 500) private String reason;
    }

    /** 重新计划活动命令。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RescheduleActivityRequest extends VersionRequest {
        @Serial private static final long serialVersionUID = 1L;
        /** 开始时间 */ @NotNull private LocalDateTime plannedStartTime;
        /** 结束时间 */ private LocalDateTime plannedEndTime;
    }
}
