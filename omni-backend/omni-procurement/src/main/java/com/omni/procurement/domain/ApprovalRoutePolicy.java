package com.omni.procurement.domain;

import com.omni.common.core.result.BusinessException;
import com.omni.procurement.entity.ProcApprovalRoute;

import java.math.BigDecimal;
import java.util.List;

/**
 * 审批路由区间与选择策略。
 *
 * @author Omni-Stack Team
 */
public final class ApprovalRoutePolicy {

    /** 通配品类编码。 */
    public static final String WILDCARD_CATEGORY = "*";

    /** 活动状态。 */
    public static final String ACTIVE = "ACTIVE";

    /** 停用状态。 */
    public static final String INACTIVE = "INACTIVE";

    private ApprovalRoutePolicy() {
    }

    /**
     * 规范化审批路由状态。
     *
     * @param status 原始状态
     * @return 规范化状态
     */
    public static String normalizeStatus(String status) {
        String normalized = MaterialDomainPolicy.normalizeCode(status, "审批路由状态");
        if (!ACTIVE.equals(normalized) && !INACTIVE.equals(normalized)) {
            throw new BusinessException(400, "审批路由状态仅支持 ACTIVE/INACTIVE");
        }
        return normalized;
    }

    /**
     * 规范化精确品类或通配符。
     *
     * @param categoryCode 原始品类编码
     * @return 规范化品类编码
     */
    public static String normalizeCategoryCode(String categoryCode) {
        String normalized = MaterialDomainPolicy.trimToNull(categoryCode);
        if (normalized == null) {
            throw new BusinessException(400, "审批路由品类编码不能为空");
        }
        return WILDCARD_CATEGORY.equals(normalized)
                ? WILDCARD_CATEGORY : MaterialDomainPolicy.normalizeCode(normalized, "审批路由品类编码");
    }

    /**
     * 校验路由金额区间和模型版本。
     *
     * @param minAmount 包含下界
     * @param maxAmount 不包含上界，可为 null
     * @param modelVersionId 工作流模型版本 ID
     */
    public static void validateDefinition(BigDecimal minAmount, BigDecimal maxAmount, Long modelVersionId) {
        if (minAmount == null || minAmount.signum() < 0) {
            throw new BusinessException(400, "审批路由金额下界不能小于 0");
        }
        validateAmountShape(minAmount);
        if (maxAmount != null) {
            validateAmountShape(maxAmount);
        }
        if (maxAmount != null && maxAmount.compareTo(minAmount) <= 0) {
            throw new BusinessException(400, "审批路由金额上界必须大于下界");
        }
        if (modelVersionId == null || modelVersionId <= 0) {
            throw new BusinessException(400, "工作流模型版本 ID 必须为正整数");
        }
    }

    private static void validateAmountShape(BigDecimal amount) {
        int fractionDigits = Math.max(amount.scale(), 0);
        int integerDigits = Math.max(amount.precision() - amount.scale(), 0);
        if (fractionDigits > 4 || integerDigits > 15) {
            throw new BusinessException(400, "审批路由金额必须符合 DECIMAL(19,4)");
        }
    }

    /**
     * 判断两个半开区间是否重叠。
     *
     * @param leftMin 左区间下界
     * @param leftMax 左区间上界
     * @param rightMin 右区间下界
     * @param rightMax 右区间上界
     * @return 是否重叠
     */
    public static boolean overlaps(BigDecimal leftMin, BigDecimal leftMax,
                                   BigDecimal rightMin, BigDecimal rightMax) {
        boolean leftBeforeRightEnd = rightMax == null || leftMin.compareTo(rightMax) < 0;
        boolean rightBeforeLeftEnd = leftMax == null || rightMin.compareTo(leftMax) < 0;
        return leftBeforeRightEnd && rightBeforeLeftEnd;
    }

    /**
     * 从活动路由中按精确品类优先规则选择唯一匹配项。
     *
     * @param categoryCode 请求品类编码
     * @param amount 请购金额
     * @param routes 当前租户的活动候选路由
     * @return 唯一路由
     */
    public static ProcApprovalRoute select(String categoryCode, BigDecimal amount,
                                           List<ProcApprovalRoute> routes) {
        String normalizedCategory = normalizeCategoryCode(categoryCode);
        if (WILDCARD_CATEGORY.equals(normalizedCategory)) {
            throw new BusinessException(400, "请购物料品类不能使用通配符");
        }
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException(400, "请购总金额不能小于 0");
        }
        List<ProcApprovalRoute> exact = matching(routes, normalizedCategory, amount);
        if (!exact.isEmpty()) {
            return requireSingle(exact);
        }
        return requireSingle(matching(routes, WILDCARD_CATEGORY, amount));
    }

    private static List<ProcApprovalRoute> matching(List<ProcApprovalRoute> routes,
                                                    String categoryCode, BigDecimal amount) {
        if (routes == null) {
            return List.of();
        }
        return routes.stream()
                .filter(route -> ACTIVE.equals(route.getStatus()))
                .filter(route -> categoryCode.equals(route.getCategoryCode()))
                .filter(route -> contains(route, amount))
                .toList();
    }

    private static boolean contains(ProcApprovalRoute route, BigDecimal amount) {
        return route.getMinAmount() != null
                && amount.compareTo(route.getMinAmount()) >= 0
                && (route.getMaxAmount() == null || amount.compareTo(route.getMaxAmount()) < 0);
    }

    private static ProcApprovalRoute requireSingle(List<ProcApprovalRoute> matches) {
        if (matches.isEmpty()) {
            throw new BusinessException(409, "未配置匹配当前品类和金额的审批路由");
        }
        if (matches.size() > 1) {
            throw new BusinessException(409, "当前品类和金额匹配到多条审批路由");
        }
        return matches.getFirst();
    }
}
