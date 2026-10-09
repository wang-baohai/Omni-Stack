package com.omni.procurement.dto;

import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审批路由响应 DTO 集合。
 *
 * @author Omni-Stack Team
 */
public final class ApprovalRouteViews {

    private ApprovalRouteViews() {
    }

    /** 审批路由视图。 */
    @Data
    public static class RouteVO implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /** 主键 ID。 */ private Long id;
        /** 稳定路由编码。 */ private String routeCode;
        /** 业务可读的审批规则名称。 */ private String routeName;
        /** 精确品类编码或通配符 *。 */ private String categoryCode;
        /** 金额下界，包含。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal minAmount;
        /** 金额上界，不包含；null 表示无上限。 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        @JsonSerialize(using = ToStringSerializer.class)
        private BigDecimal maxAmount;
        /** 已发布工作流模型版本 ID。 */ private Long modelVersionId;
        /** 流程业务名称；依赖不可用时为空。 */ private String modelName;
        /** 流程发布版本号。 */ private Integer modelVersion;
        /** 流程发布时间。 */ private LocalDateTime modelPublishTime;
        /** AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND/LEGACY_CATEGORY。 */
        private String workflowAvailability;
        /** 管理列表排序优先级。 */ private Integer priority;
        /** ACTIVE/INACTIVE。 */ private String status;
        /** 乐观锁版本。 */ private Integer version;
        /** 创建时间。 */ private LocalDateTime createTime;
        /** 更新时间。 */ private LocalDateTime updateTime;
    }
}
