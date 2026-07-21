package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * SRM 风险枚举与综合等级计算器。
 *
 * @author Omni-Stack Team
 */
public final class SrmRiskCalculator {

    /** 风险指标类型。 */
    public enum IndicatorType {
        FINANCIAL, COMPLIANCE, SUPPLY, COOPERATION, QUALITY, CERTIFICATE
    }

    /** 风险等级。 */
    public enum RiskLevel {
        GREEN, YELLOW, RED
    }

    private SrmRiskCalculator() {
    }

    /**
     * 校验并解析指标类型。
     *
     * @param value 指标类型字符串
     * @return 指标类型
     */
    public static IndicatorType parseType(String value) {
        try {
            return IndicatorType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(400, "风险指标类型无效：" + value);
        }
    }

    /**
     * 校验风险指标是否允许人工维护。
     *
     * @param type 指标类型
     */
    public static void requireManuallyEditable(IndicatorType type) {
        if (IndicatorType.CERTIFICATE == type) {
            throw new BusinessException(400, "资质风险由系统根据资质到期日自动计算，不允许手工修改");
        }
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
     * 取全部指标中的最高风险等级。
     *
     * @param levels 风险等级集合
     * @return 最高风险等级
     */
    public static RiskLevel highest(Collection<RiskLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return RiskLevel.GREEN;
        }
        return levels.stream().max(java.util.Comparator.comparingInt(RiskLevel::ordinal)).orElse(RiskLevel.GREEN);
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
     * 判断是否需要发布“风险转红”事件。
     *
     * @param previousLevel 上一次综合等级，可空
     * @param currentLevel 当前综合等级
     * @return 仅首次或从非红等级转为红色时返回 true
     */
    public static boolean shouldNotifyRed(String previousLevel, RiskLevel currentLevel) {
        return RiskLevel.RED == currentLevel && !RiskLevel.RED.name().equals(previousLevel);
    }
}
