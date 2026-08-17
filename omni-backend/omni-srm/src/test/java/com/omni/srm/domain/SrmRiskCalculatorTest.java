package com.omni.srm.domain;

import com.omni.common.core.result.BusinessException;
import com.omni.srm.domain.SrmRiskCalculator.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 风险枚举和最高等级计算测试。 */
class SrmRiskCalculatorTest {

    /** 综合风险必须取所有指标中的最高等级。 */
    @Test
    void shouldSelectHighestRiskLevel() {
        assertThat(SrmRiskCalculator.highest(List.of(RiskLevel.GREEN, RiskLevel.RED, RiskLevel.YELLOW)))
                .isEqualTo(RiskLevel.RED);
        assertThat(SrmRiskCalculator.highest(List.of())).isEqualTo(RiskLevel.GREEN);
    }

    /** 未定义的类型和等级必须拒绝。 */
    @Test
    void shouldRejectUnknownEnums() {
        assertThatThrownBy(() -> SrmRiskCalculator.parseLevel("ORANGE"))
                .isInstanceOf(BusinessException.class);
    }

    /** 自动计算的指标不允许手工修改。 */
    @Test
    void shouldRejectAutoCalcIndicator() {
        assertThatThrownBy(() -> SrmRiskCalculator.requireManuallyEditable(1))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        SrmRiskCalculator.requireManuallyEditable(0);
        SrmRiskCalculator.requireManuallyEditable(null);
    }

    /** 资质过期、三十天内和三十天外边界必须正确。 */
    @Test
    void shouldCalculateCertificateExpiryBoundaries() {
        LocalDate today = LocalDate.of(2026, 7, 17);
        assertThat(SrmRiskCalculator.certificateLevel(today, today.minusDays(1))).isEqualTo(RiskLevel.RED);
        assertThat(SrmRiskCalculator.certificateLevel(today, today)).isEqualTo(RiskLevel.YELLOW);
        assertThat(SrmRiskCalculator.certificateLevel(today, today.plusDays(30))).isEqualTo(RiskLevel.YELLOW);
        assertThat(SrmRiskCalculator.certificateLevel(today, today.plusDays(31))).isEqualTo(RiskLevel.GREEN);
    }

    /** 事件只能在综合风险从非红状态转为红色时发布。 */
    @Test
    void shouldNotifyOnlyOnTransitionToRed() {
        assertThat(SrmRiskCalculator.shouldNotifyRed(null, RiskLevel.RED)).isTrue();
        assertThat(SrmRiskCalculator.shouldNotifyRed("YELLOW", RiskLevel.RED)).isTrue();
        assertThat(SrmRiskCalculator.shouldNotifyRed("RED", RiskLevel.RED)).isFalse();
        assertThat(SrmRiskCalculator.shouldNotifyRed("RED", RiskLevel.GREEN)).isFalse();
    }
}
