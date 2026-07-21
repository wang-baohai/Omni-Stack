package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;
import com.omni.srm.domain.SrmEvaluationScoreCalculator.WeightedScore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 绩效评分边界和等级映射测试。 */
class SrmEvaluationScoreCalculatorTest {

    private static final List<BigDecimal> WEIGHTS = List.of(
            BigDecimal.valueOf(30), BigDecimal.valueOf(30),
            BigDecimal.valueOf(20), BigDecimal.valueOf(20));

    /** 全部一分必须映射为二十分。 */
    @Test
    void shouldNormalizeAllOneScoresToTwenty() {
        assertThat(SrmEvaluationScoreCalculator.calculate(scores(BigDecimal.ONE)))
                .isEqualByComparingTo("20.00");
    }

    /** 全部五分必须映射为一百分。 */
    @Test
    void shouldNormalizeAllFiveScoresToOneHundred() {
        assertThat(SrmEvaluationScoreCalculator.calculate(scores(BigDecimal.valueOf(5))))
                .isEqualByComparingTo("100.00");
    }

    /** 60、75、90 分等级阈值必须精确生效。 */
    @Test
    void shouldMapLevelThresholdsExactly() {
        assertThat(SrmEvaluationScoreCalculator.mapLevel(new BigDecimal("59.99"))).isEqualTo("ELIMINATED");
        assertThat(SrmEvaluationScoreCalculator.mapLevel(new BigDecimal("60.00"))).isEqualTo("QUALIFIED");
        assertThat(SrmEvaluationScoreCalculator.mapLevel(new BigDecimal("75.00"))).isEqualTo("PREFERRED");
        assertThat(SrmEvaluationScoreCalculator.mapLevel(new BigDecimal("90.00"))).isEqualTo("STRATEGIC");
    }

    /** 评分越界和权重不完整必须拒绝。 */
    @Test
    void shouldRejectInvalidScoreAndWeight() {
        assertThatThrownBy(() -> SrmEvaluationScoreCalculator.calculate(List.of(
                new WeightedScore(BigDecimal.ZERO, BigDecimal.valueOf(100)))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> SrmEvaluationScoreCalculator.calculate(List.of(
                new WeightedScore(BigDecimal.valueOf(5), BigDecimal.valueOf(90)))))
                .isInstanceOf(BusinessException.class);
    }

    /** 默认模板启用维度权重必须精确等于一百。 */
    @Test
    void shouldRequireExactTemplateWeightTotal() {
        SrmEvaluationScoreCalculator.requireWeightTotal(WEIGHTS);
        assertThatThrownBy(() -> SrmEvaluationScoreCalculator.requireWeightTotal(
                List.of(BigDecimal.valueOf(30), BigDecimal.valueOf(30), BigDecimal.valueOf(39))))
                .isInstanceOf(BusinessException.class);
    }

    private List<WeightedScore> scores(BigDecimal score) {
        return WEIGHTS.stream().map(weight -> new WeightedScore(score, weight)).toList();
    }
}
