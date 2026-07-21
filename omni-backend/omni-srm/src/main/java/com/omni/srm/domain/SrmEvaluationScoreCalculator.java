package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * SRM 绩效评分与等级计算器。
 *
 * @author Omni-Stack Team
 */
public final class SrmEvaluationScoreCalculator {

    private static final BigDecimal MAX_ITEM_SCORE = BigDecimal.valueOf(5);
    private static final BigDecimal EXPECTED_WEIGHT = BigDecimal.valueOf(100);

    private SrmEvaluationScoreCalculator() {
    }

    /**
     * 计算百分制加权总分。
     *
     * @param items 评分和权重快照
     * @return 两位小数的百分制总分
     */
    public static BigDecimal calculate(Collection<WeightedScore> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(400, "评分明细不能为空");
        }
        requireWeightTotal(items.stream().map(WeightedScore::weight).toList());
        BigDecimal scoreTotal = BigDecimal.ZERO;
        for (WeightedScore item : items) {
            requireScore(item.score());
            scoreTotal = scoreTotal.add(item.score().divide(MAX_ITEM_SCORE, 8, RoundingMode.HALF_UP)
                    .multiply(item.weight()));
        }
        return scoreTotal.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 校验模板启用维度的权重总和。
     *
     * @param weights 启用维度权重
     */
    public static void requireWeightTotal(Collection<BigDecimal> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new BusinessException(400, "评估模板无可用维度配置");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal weight : weights) {
            if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "评估维度权重必须大于 0");
            }
            total = total.add(weight);
        }
        if (total.compareTo(EXPECTED_WEIGHT) != 0) {
            throw new BusinessException(400, "评估模板维度权重之和必须等于 100");
        }
    }

    /**
     * 按 MVP 阈值映射供应商等级。
     *
     * @param totalScore 百分制总分
     * @return 等级编码
     */
    public static String mapLevel(BigDecimal totalScore) {
        if (totalScore.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "STRATEGIC";
        }
        if (totalScore.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return "PREFERRED";
        }
        if (totalScore.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "QUALIFIED";
        }
        return "ELIMINATED";
    }

    private static void requireScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ONE) < 0 || score.compareTo(MAX_ITEM_SCORE) > 0) {
            throw new BusinessException(400, "评分必须在 1 到 5 之间");
        }
    }

    /**
     * 评分和权重快照。
     *
     * @param score 1-5 分
     * @param weight 百分比权重
     */
    public record WeightedScore(BigDecimal score, BigDecimal weight) {
    }
}
