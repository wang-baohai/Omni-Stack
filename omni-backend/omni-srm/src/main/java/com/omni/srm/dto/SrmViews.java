package com.omni.srm.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SRM 视图对象集合，所有返回给前端的数据结构。
 *
 * @author Omni-Stack Team
 */
public final class SrmViews {

    private SrmViews() {
    }

    /**
     * 带 owner 展示名称的基类。
     */
    @Data
    public static abstract class OwnedVO {
        private Long ownerUserId;
        private Long ownerUnitId;
        private String ownerName;
        private String ownerUnitName;
    }

    /**
     * 供应商列表/卡片视图。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SupplierVO extends OwnedVO {
        private Long id;
        private String supplierNo;
        private String name;
        private String supplierType;
        private String industryCode;
        private String creditCode;
        private String website;
        private String phone;
        private String email;
        private String region;
        private String address;
        private String categoryCode;
        private String levelCode;
        private String status;
        private LocalDateTime assignedTime;
        private LocalDateTime lastEvaluationTime;
        private Integer version;
        private LocalDateTime createTime;
        private String createBy;
    }

    /**
     * 供应商详情视图（含子表汇总）。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SupplierDetailVO extends SupplierVO {
        private List<ContactVO> contacts;
        private List<QualificationVO> qualifications;
        private List<BankAccountVO> bankAccounts;
    }

    /**
     * 联系人视图。
     */
    @Data
    public static class ContactVO {
        private Long id;
        private Long supplierId;
        private String name;
        private String department;
        private String jobTitle;
        private String mobile;
        private String phone;
        private String email;
        private String decisionRole;
        private Boolean primaryFlag;
        private String status;
        private Integer version;
    }

    /**
     * 资质视图。
     */
    @Data
    public static class QualificationVO {
        private Long id;
        private Long supplierId;
        private String qualificationName;
        private String certificateNo;
        private String issuingAuthority;
        private LocalDate issueDate;
        private LocalDate expiryDate;
        private String status;
        private Integer version;
    }

    /**
     * 银行账户视图（PII 掩码由调用方控制）。
     */
    @Data
    public static class BankAccountVO {
        private Long id;
        private Long supplierId;
        private String accountName;
        private String accountNo;
        private String bankName;
        private String bankBranch;
        private String bankCode;
        private Boolean primaryFlag;
        private String status;
        private Integer version;
    }

    /**
     * 供应商 360 聚合视图。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SupplierOverviewVO extends SupplierVO {
        private List<ContactVO> contacts;
        private List<QualificationVO> qualifications;
        private List<BankAccountVO> bankAccounts;
        private List<EvaluationVO> recentEvaluations;
        private List<RiskIndicatorVO> riskIndicators;
        private RiskAssessmentVO latestRiskAssessment;
    }

    /**
     * 评估列表视图。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EvaluationVO extends OwnedVO {
        private Long id;
        private Long supplierId;
        private String supplierName;
        private Long templateId;
        private String evaluationPeriod;
        private BigDecimal totalScore;
        private Long evaluatorUserId;
        private LocalDateTime evaluationTime;
        private String status;
        private Integer version;
        private List<EvaluationItemVO> items;
    }

    /**
     * 评估评分明细。
     */
    @Data
    public static class EvaluationItemVO {
        private Long id;
        private Long evaluationId;
        private Long dimensionId;
        private String indicatorName;
        private BigDecimal score;
        private BigDecimal weight;
        private String remark;
    }

    /**
     * 默认评估模板视图。
     */
    @Data
    public static class EvaluationTemplateVO {
        private Long id;
        private String name;
        private Integer version;
        private List<EvaluationDimensionVO> dimensions;
    }

    /**
     * 评估维度视图。
     */
    @Data
    public static class EvaluationDimensionVO {
        private Long id;
        private String indicatorName;
        private BigDecimal weight;
        private Integer sort;
    }

    /**
     * 风险指标视图。
     */
    @Data
    public static class RiskIndicatorVO {
        private Long id;
        private Long supplierId;
        private String indicatorType;
        private String indicatorValue;
        private String riskLevel;
        private LocalDateTime assessmentTime;
        private String remark;
        private Integer version;
    }

    /**
     * 综合风险评估视图。
     */
    @Data
    public static class RiskAssessmentVO {
        private Long id;
        private Long supplierId;
        private String overallLevel;
        private LocalDateTime assessmentTime;
        private Long assessorUserId;
        private String remark;
        private Integer version;
    }

    /**
     * 概览统计视图。
     */
    @Data
    public static class OverviewSummaryVO {
        private Long totalSuppliers;
        private Long approvedCount;
        private Long pendingReviewCount;
        private Long suspendedCount;
        private Long blacklistedCount;
        private Long eliminatedCount;
        private Long strategicCount;
        private Long preferredCount;
        private Long qualifiedCount;
    }

    /**
     * 风险看板视图。
     */
    @Data
    public static class RiskDashboardVO {
        private Long redCount;
        private Long yellowCount;
        private Long greenCount;
        private List<RiskSupplierSummaryVO> topRiskSuppliers;
    }

    /**
     * 供应商风险摘要。
     */
    @Data
    public static class RiskSupplierSummaryVO {
        private Long supplierId;
        private String supplierName;
        private String overallLevel;
        private LocalDateTime assessmentTime;
        private Long redIndicatorCount;
    }

    /**
     * 单个供应商风险详情。
     */
    @Data
    public static class RiskProfileVO {
        private Long supplierId;
        private List<RiskIndicatorVO> indicators;
        private RiskAssessmentVO latestAssessment;
        private List<RiskAssessmentVO> history;
    }

    /**
     * 门户企业信息视图。
     */
    @Data
    public static class PortalProfileVO {
        private Long supplierId;
        private String supplierNo;
        private String name;
        private String supplierType;
        private String creditCode;
        private String website;
        private String phone;
        private String email;
        private String region;
        private String address;
        private String status;
        private Integer version;
    }

    /**
     * 门户入驻进度视图。
     */
    @Data
    public static class EnrollmentVO {
        private String requestId;
        private Long supplierId;
        private String status;
        private Integer retryCount;
        private String lastErrorCode;
        private LocalDateTime nextRetryTime;
    }

    /**
     * 邀请列表视图。
     */
    @Data
    public static class InviteVO {
        private Long id;
        private String inviteToken;
        private String status;
        private LocalDateTime expiresTime;
        private Integer maxUses;
        private Integer usedCount;
        private LocalDateTime createTime;
        private String createBy;
    }

    /**
     * 负责人候选选项。
     */
    @Data
    public static class OwnerOptionVO {
        private Long userId;
        private String username;
        private String nickname;
        private String unitName;
    }
}
