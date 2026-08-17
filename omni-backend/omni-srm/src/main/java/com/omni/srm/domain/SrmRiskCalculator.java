package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * SRM 风险等级计算器。
 * <p>指标类型已改为动态配置（数据库管理），本类仅保留风险等级枚举和计算逻辑。</p>
 *
 * @author Omni-Stack Team
 */
public final class SrmRiskCalculator {

    /** 风险等级。 */
    public enum RiskLevel {
        GREEN, YELLOW, RED
    }

    private SrmRiskCalculator() {
    }

    /**
     * 校验并解析风险等级。
     *
     * @param value 风险等级字符串
     * @return 风险等级
     */
    public static RiskLevel parseLevel(String value) {
        try {
            return RiskLevel.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(400, "风险等级无效：" + value);
        }
    }

    /**
     * 根据总分和阈值表计算综合风险等级。
     *
     * @param totalScore 总分
     * @param thresholds 阈值列表（riskLevel, minScore, maxScore）
     * @return 匹配的风险等级，未匹配时返回 GREEN
     */
    public static RiskLevel computeFromScore(int totalScore, List<ScoreThreshold> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            return RiskLevel.GREEN;
        }
        for (ScoreThreshold threshold : thresholds) {
            if (totalScore >= threshold.minScore() && totalScore <= threshold.maxScore()) {
                return parseLevel(threshold.riskLevel());
            }
        }
        // 如果总分超出所有阈值范围，取最高等级
        return thresholds.stream()
                .max(Comparator.comparingInt(ScoreThreshold::maxScore))
                .map(t -> totalScore > t.maxScore() ? parseLevel(t.riskLevel()) : RiskLevel.GREEN)
                .orElse(RiskLevel.GREEN);
    }

    /**
     * 取全部指标中的最高风险等级（保留兼容，供旧调用方使用）。
     *
     * @param levels 风险等级集合
     * @return 最高风险等级
     */
    public static RiskLevel highest(java.util.Collection<RiskLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return RiskLevel.GREEN;
        }
        return levels.stream().max(Comparator.comparingInt(RiskLevel::ordinal)).orElse(RiskLevel.GREEN);
    }

    /**
     * 校验风险指标是否允许人工维护（根据 autoCalc 标记）。
     *
     * @param autoCalc 是否自动计算（1=自动）
     */
    public static void requireManuallyEditable(Integer autoCalc) {
        if (autoCalc != null && autoCalc == 1) {
            throw new BusinessException(400, "该指标由系统自动计算，不允许手工修改");
        }
    }

    /**
     * 根据资质到期日计算证书风险等级。
     *
     * @param today 评估日期
     * @param expiryDate 资质到期日
     * @return 证书风险等级
     */
    public static RiskLevel certificateLevel(LocalDate today, LocalDate expiryDate) {
        if (today == null || expiryDate == null) {
            return RiskLevel.GREEN;
        }
        long days = ChronoUnit.DAYS.between(today, expiryDate);
        if (days < 0) {
            return RiskLevel.RED;
        }
        if (days <= 30) {
            return RiskLevel.YELLOW;
        }
        return RiskLevel.GREEN;
    }

    /**
     * 判断是否需要发布"风险转红"事件。
     *
     * @param previousLevel 上一次综合等级，可空
     * @param currentLevel 当前综合等级
     * @return 仅首次或从非红等级转换为红色时返回 true
     */
    public static boolean shouldNotifyRed(String previousLevel, RiskLevel currentLevel) {
        return RiskLevel.RED == currentLevel && !RiskLevel.RED.name().equals(previousLevel);
    }

    /**
     * 得分阈值快照。
     *
     * @param riskLevel 风险等级
     * @param minScore 最小分（含）
     * @param maxScore 最大分（含）
     */
    public record ScoreThreshold(String riskLevel, int minScore, int maxScore) {
    }
}
