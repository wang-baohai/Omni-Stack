package com.omni.procurement.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.mapper.ProcApprovalRouteMapper;
import com.omni.procurement.security.ProcTenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按租户、品类和金额解析唯一工作流模型路由。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class ApprovalRouteResolver {

    private final ProcApprovalRouteMapper routeMapper;

    /**
     * 解析唯一活动审批路由。
     *
     * @param categoryCode 物料品类编码
     * @param totalAmount 服务端重算的请购总金额
     * @return 唯一审批路由
     */
    public ProcApprovalRoute resolve(String categoryCode, BigDecimal totalAmount) {
        Evaluation evaluation = evaluate(categoryCode, totalAmount);
        return switch (evaluation.outcome()) {
            case MATCHED -> evaluation.route();
            case NO_MATCH -> throw new com.omni.common.core.result.BusinessException(
                    409, "未配置匹配当前品类和金额的审批路由");
            case AMBIGUOUS -> throw new com.omni.common.core.result.BusinessException(
                    409, "当前品类和金额匹配到多条审批路由");
        };
    }

    /**
     * 无副作用评估当前输入的路由命中情况。
     *
     * @param categoryCode 物料品类编码
     * @param totalAmount 服务端重算的请购总金额
     * @return 结构化评估结果
     */
    public Evaluation evaluate(String categoryCode, BigDecimal totalAmount) {
        Long tenantId = ProcTenantContext.requireTenantId();
        String normalizedCategory = ApprovalRoutePolicy.normalizeCategoryCode(categoryCode);
        ApprovalRoutePolicy.validateMatchAmount(normalizedCategory, totalAmount);
        List<ProcApprovalRoute> candidates = routeMapper.selectList(
                new LambdaQueryWrapper<ProcApprovalRoute>()
                        .eq(ProcApprovalRoute::getTenantId, tenantId)
                        .eq(ProcApprovalRoute::getStatus, ApprovalRoutePolicy.ACTIVE)
                        .in(ProcApprovalRoute::getCategoryCode,
                                List.of(normalizedCategory, ApprovalRoutePolicy.WILDCARD_CATEGORY))
                        .orderByAsc(ProcApprovalRoute::getPriority)
                        .orderByAsc(ProcApprovalRoute::getId));
        return evaluateCandidates(normalizedCategory, totalAmount, candidates);
    }

    /**
     * 对已加载候选规则执行与在线解析一致的评估，供覆盖分析复用。
     *
     * @param categoryCode 已规范化的具体品类编码
     * @param totalAmount 金额
     * @param candidates 候选规则
     * @return 结构化评估结果
     */
    public static Evaluation evaluateCandidates(String categoryCode, BigDecimal totalAmount,
                                                List<ProcApprovalRoute> candidates) {
        ApprovalRoutePolicy.validateMatchAmount(categoryCode, totalAmount);
        List<ProcApprovalRoute> exact = ApprovalRoutePolicy.matching(
                candidates, categoryCode, totalAmount);
        if (exact.size() > 1) {
            return new Evaluation(Outcome.AMBIGUOUS, null, categoryCode, false, exact);
        }
        if (exact.size() == 1) {
            return new Evaluation(Outcome.MATCHED, exact.getFirst(), categoryCode, false, exact);
        }
        List<ProcApprovalRoute> defaults = ApprovalRoutePolicy.matching(
                candidates, ApprovalRoutePolicy.WILDCARD_CATEGORY, totalAmount);
        if (defaults.size() > 1) {
            return new Evaluation(Outcome.AMBIGUOUS, null,
                    ApprovalRoutePolicy.WILDCARD_CATEGORY, true, defaults);
        }
        if (defaults.size() == 1) {
            return new Evaluation(Outcome.MATCHED, defaults.getFirst(),
                    ApprovalRoutePolicy.WILDCARD_CATEGORY, true, defaults);
        }
        return new Evaluation(Outcome.NO_MATCH, null, null, false, List.of());
    }

    /** 路由评估结果类型。 */
    public enum Outcome {
        /** 唯一命中。 */ MATCHED,
        /** 没有命中。 */ NO_MATCH,
        /** 同一优先层级命中多条脏数据。 */ AMBIGUOUS
    }

    /**
     * 路由评估快照。
     *
     * @param outcome 结果类型
     * @param route 唯一命中的规则
     * @param effectiveCategoryCode 实际生效的具体品类或通配符
     * @param defaultRule 是否使用默认规则
     * @param matches 当前优先层级的全部命中规则
     */
    public record Evaluation(Outcome outcome, ProcApprovalRoute route,
                             String effectiveCategoryCode, boolean defaultRule,
                             List<ProcApprovalRoute> matches) {
    }
}
