package com.omni.procurement.dto;

import lombok.Builder;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 请购审批规则业务化分析视图集合。
 *
 * @author Omni-Stack Team
 */
public final class ApprovalRouteInsightViews {

    private ApprovalRouteInsightViews() {
    }

    /** 可选择的当前已发布审批流程。 */
    @Data
    @Builder
    public static class WorkflowOption implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 模型版本 ID。 */ private Long modelVersionId;
        /** 模型 ID。 */ private Long modelId;
        /** 模型技术标识。 */ private String modelKey;
        /** 流程业务名称。 */ private String modelName;
        /** 固定为 purchase。 */ private String category;
        /** 发布版本号。 */ private Integer version;
        /** 发布时间。 */ private LocalDateTime publishTime;
        /** 安全预览契约版本。 */ private Integer approvalPreviewVersion;
    }

    /** 无副作用匹配结果。 */
    @Data
    @Builder
    public static class MatchPreview implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** MATCHED/NO_MATCH/AMBIGUOUS/WORKFLOW_UNAVAILABLE。 */ private String outcome;
        /** 唯一命中规则 ID。 */ private Long routeId;
        /** 规则名称。 */ private String routeName;
        /** 稳定技术编码。 */ private String routeCode;
        /** 输入品类。 */ private String categoryCode;
        /** 实际生效品类或通配符。 */ private String effectiveCategoryCode;
        /** 是否使用默认规则。 */ private boolean defaultRule;
        /** 金额下界，包含。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal minAmount;
        /** 金额上界，不包含。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal maxAmount;
        /** 模型版本 ID。 */ private Long modelVersionId;
        /** 流程名称。 */ private String modelName;
        /** 发布版本号。 */ private Integer modelVersion;
        /** 发布时间。 */ private LocalDateTime publishTime;
        /** 安全审批图。 */ private WorkflowContracts.ApprovalPreviewResponse approvalGraph;
        /** 可直接展示给业务人员的下一步说明。 */ private String actionMessage;
        /** 重复命中的规则 ID。 */ private List<Long> conflictingRouteIds;
    }

    /** 全租户覆盖分析报告。 */
    @Data
    @Builder
    public static class CoverageReport implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 报告生成时间。 */ private LocalDateTime generatedAt;
        /** AVAILABLE/UNAVAILABLE。 */ private String workflowAvailability;
        /** 是否没有任何启用规则。 */ private boolean allRulesInactive;
        /** 是否没有启用的默认规则。 */ private boolean noDefaultRule;
        /** 引用失效或遗留分类模型的规则 ID。 */ private List<Long> invalidModelRouteIds;
        /** 各启用品类的有效覆盖。 */ private List<CategoryCoverage> categories;
    }

    /** 单个物料品类覆盖。 */
    @Data
    @Builder
    public static class CategoryCoverage implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 品类编码。 */ private String categoryCode;
        /** 品类名称。 */ private String categoryName;
        /** 是否从 0 到无穷均唯一覆盖。 */ private boolean complete;
        /** 覆盖、断档和重复片段。 */ private List<CoverageSegment> segments;
        /** 业务化风险摘要。 */ private List<String> issues;
    }

    /** 半开金额区间覆盖片段。 */
    @Data
    @Builder
    public static class CoverageSegment implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 金额下界，包含。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal minAmount;
        /** 金额上界，不包含；null 表示无穷。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal maxAmount;
        /** COVERED/GAP/AMBIGUOUS。 */ private String outcome;
        /** EXACT/DEFAULT/NONE。 */ private String source;
        /** 生效或冲突规则 ID。 */ private List<Long> routeIds;
        /** 唯一生效规则名称。 */ private String routeName;
        /** 绑定模型可用状态。 */ private String workflowAvailability;
    }

    /** 停用或删除规则后的只读影响分析。 */
    @Data
    @Builder
    public static class ImpactReport implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 被模拟排除的规则 ID。 */ private Long routeId;
        /** 被模拟排除的规则名称。 */ private String routeName;
        /** 排除后的完整覆盖报告。 */ private CoverageReport coverage;
        /** 新增断档片段数量。 */ private long gapSegmentCount;
        /** 新增重复片段数量。 */ private long ambiguousSegmentCount;
        /** 业务化确认提示。 */ private String actionMessage;
    }

}
