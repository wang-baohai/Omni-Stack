package com.omni.crm.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRM 响应 VO 集合，避免直接暴露持久化实体。
 *
 * @author Omni-Stack Team
 */
public final class CrmViews {

    private CrmViews() {
    }

    /** 带负责人展示字段的 VO 契约。 */
    public interface OwnedVO {
        /** 获取负责人用户 ID。 */ Long getOwnerUserId();
        /** 获取负责人组织 ID。 */ Long getOwnerUnitId();
        /** 设置负责人名称。 */ void setOwnerName(String ownerName);
        /** 设置负责人组织名称。 */ void setOwnerUnitName(String ownerUnitName);
    }

    /** 线索视图。 */
    @Data
    public static class LeadVO implements Serializable, OwnedVO {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private String leadNo;
        private String fullName;
        private String companyName;
        private String jobTitle;
        private String mobile;
        private String phone;
        private String email;
        private String region;
        private String address;
        private String sourceCode;
        private String industryCode;
        private String rating;
        private String status;
        private String disqualifyReason;
        private Long ownerUserId;
        private Long ownerUnitId;
        private String ownerName;
        private String ownerUnitName;
        private LocalDateTime lastActivityTime;
        private LocalDateTime nextFollowupTime;
        private LocalDateTime convertedTime;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 客户视图。 */
    @Data
    public static class CustomerVO implements Serializable, OwnedVO {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private String customerNo;
        private String name;
        private String customerType;
        private String industryCode;
        private String levelCode;
        private String sourceCode;
        private String creditCode;
        private String website;
        private String phone;
        private String email;
        private String region;
        private String address;
        private String status;
        private Long ownerUserId;
        private Long ownerUnitId;
        private String ownerName;
        private String ownerUnitName;
        private LocalDateTime lastActivityTime;
        private LocalDateTime nextFollowupTime;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 联系人视图。 */
    @Data
    public static class ContactVO implements Serializable, OwnedVO {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private Long customerId;
        private String name;
        private String department;
        private String jobTitle;
        private String mobile;
        private String phone;
        private String email;
        private String decisionRole;
        private Integer primaryFlag;
        private Integer status;
        private Long ownerUserId;
        private Long ownerUnitId;
        private String ownerName;
        private String ownerUnitName;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 商机视图。 */
    @Data
    public static class OpportunityVO implements Serializable, OwnedVO {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private String opportunityNo;
        private String name;
        private Long customerId;
        private Long primaryContactId;
        private Long sourceLeadId;
        private Long pipelineId;
        private Long stageId;
        private String status;
        private BigDecimal amount;
        private String currencyCode;
        private BigDecimal probability;
        private LocalDate expectedCloseDate;
        private LocalDateTime actualCloseTime;
        private String lossReason;
        private Long ownerUserId;
        private Long ownerUnitId;
        private String ownerName;
        private String ownerUnitName;
        private LocalDateTime stageChangeTime;
        private LocalDateTime nextFollowupTime;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 活动视图。 */
    @Data
    public static class ActivityVO implements Serializable, OwnedVO {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private String rootType;
        private Long rootId;
        private Long contactId;
        private String activityType;
        private String subject;
        private String content;
        private String status;
        private LocalDateTime plannedStartTime;
        private LocalDateTime plannedEndTime;
        private LocalDateTime completedTime;
        private LocalDateTime nextActionTime;
        private Long performedByUserId;
        private Long ownerUserId;
        private Long ownerUnitId;
        private String ownerName;
        private String ownerUnitName;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 管道视图。 */
    @Data
    public static class PipelineVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private String code;
        private String name;
        private Integer defaultFlag;
        private Integer sort;
    }

    /** 阶段视图。 */
    @Data
    public static class PipelineStageVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private Long pipelineId;
        private String stageCode;
        private String stageName;
        private String stageType;
        private BigDecimal probability;
        private Integer sort;
    }

    /** 重复候选最小摘要。 */
    @Data
    public static class DuplicateCandidateVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private String number;
        private String name;
        private String matchedBy;
        private String maskedContact;
    }

    /** 线索转换结果。 */
    @Data
    public static class ConversionResultVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long conversionId;
        private Long leadId;
        private Long customerId;
        private Long contactId;
        private Long opportunityId;
        private LocalDateTime convertedTime;
        private boolean idempotentReplay;
    }

    /** 客户 360 聚合视图。 */
    @Data
    public static class CustomerOverviewVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private CustomerVO customer;
        private List<ContactVO> contacts = new ArrayList<>();
        private List<OpportunityVO> openOpportunities = new ArrayList<>();
        private List<ActivityVO> recentActivities = new ArrayList<>();
        private List<Long> convertedLeadIds = new ArrayList<>();
    }

    /** 商机阶段历史视图。 */
    @Data
    public static class StageHistoryVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private Long opportunityId;
        private Long fromStageId;
        private Long toStageId;
        private String fromStatus;
        private String toStatus;
        private String changeReason;
        private Long changedByUserId;
        private LocalDateTime changedTime;
    }

    /** 商机看板。 */
    @Data
    public static class OpportunityBoardVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private List<PipelineStageVO> stages = new ArrayList<>();
        private Map<Long, List<OpportunityVO>> opportunitiesByStage = new LinkedHashMap<>();
    }

    /** CRM 看板摘要。 */
    @Data
    public static class OverviewSummaryVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private long newLeadCount;
        private long qualifiedLeadCount;
        private long convertedLeadCount;
        private long openOpportunityCount;
        private BigDecimal openOpportunityAmount = BigDecimal.ZERO;
        private long wonOpportunityCount;
        private BigDecimal wonOpportunityAmount = BigDecimal.ZERO;
        private long todayFollowupCount;
        private long overdueFollowupCount;
        private BigDecimal leadConversionRate = BigDecimal.ZERO;
        private BigDecimal opportunityWinRate = BigDecimal.ZERO;
        private String currencyCode;
    }

    /** 漏斗阶段统计。 */
    @Data
    public static class FunnelItemVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long stageId;
        private String stageName;
        private String stageType;
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;
        private String currencyCode;
    }

    /** 待跟进事项摘要。 */
    @Data
    public static class FollowupVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String rootType;
        private Long rootId;
        private String number;
        private String name;
        private LocalDateTime nextFollowupTime;
        private Long ownerUserId;
        private boolean overdue;
    }

    /** 负责人选项。 */
    @Data
    public static class OwnerOptionVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long id;
        private String username;
        private String nickname;
        private Long primaryUnitId;
        private String avatar;
    }

    /** 状态聚合计数结果。 */
    @Data
    public static class StatusCountVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String status;
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    /** 漏斗阶段聚合结果。 */
    @Data
    public static class FunnelAggVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private Long stageId;
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    /** 待跟进事项原始行。 */
    @Data
    public static class FollowupRowVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private String rootType;
        private Long rootId;
        private String number;
        private String name;
        private LocalDateTime nextFollowupTime;
        private Long ownerUserId;
    }
}
