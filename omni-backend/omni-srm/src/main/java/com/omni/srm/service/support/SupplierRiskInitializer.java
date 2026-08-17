package com.omni.srm.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.srm.entity.SrmRiskIndicator;
import com.omni.srm.entity.SrmRiskIndicatorType;
import com.omni.srm.mapper.SrmRiskIndicatorMapper;
import com.omni.srm.mapper.SrmRiskIndicatorTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商默认风险指标初始化器。
 * <p>从 srm_risk_indicator_type 表读取启用的指标类型来初始化。</p>
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class SupplierRiskInitializer {

    private final SrmRiskIndicatorMapper riskIndicatorMapper;
    private final SrmRiskIndicatorTypeMapper indicatorTypeMapper;

    /**
     * 幂等补齐供应商的默认绿色风险指标（从配置表读取）。
     *
     * @param tenantId 租户 ID
     * @param supplierId 供应商 ID
     */
    public void initialize(Long tenantId, Long supplierId) {
        // 从 DB 读取启用的指标类型
        List<SrmRiskIndicatorType> enabledTypes = indicatorTypeMapper.selectList(
                new LambdaQueryWrapper<SrmRiskIndicatorType>()
                        .eq(SrmRiskIndicatorType::getTenantId, tenantId)
                        .eq(SrmRiskIndicatorType::getStatus, 1)
                        .orderByAsc(SrmRiskIndicatorType::getSort));
        if (enabledTypes.isEmpty()) {
            return;
        }

        Set<String> existingTypes = riskIndicatorMapper.selectList(
                        new LambdaQueryWrapper<SrmRiskIndicator>()
                                .eq(SrmRiskIndicator::getTenantId, tenantId)
                                .eq(SrmRiskIndicator::getSupplierId, supplierId)
                                .select(SrmRiskIndicator::getIndicatorType))
                .stream()
                .map(SrmRiskIndicator::getIndicatorType)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        for (SrmRiskIndicatorType type : enabledTypes) {
            if (existingTypes.contains(type.getTypeCode())) {
                continue;
            }
            SrmRiskIndicator indicator = new SrmRiskIndicator();
            indicator.setTenantId(tenantId);
            indicator.setSupplierId(supplierId);
            indicator.setIndicatorType(type.getTypeCode());
            indicator.setRiskLevel("GREEN");
            indicator.setScore(type.getAutoCalc() != null && type.getAutoCalc() == 1 ? null : 1);
            indicator.setAssessmentTime(now);
            indicator.setRemark("供应商创建时初始化");
            indicator.setVersion(0);
            indicator.setDeleted(0);
            SrmAuditSupport.created(indicator);
            riskIndicatorMapper.insert(indicator);
        }
    }
}
