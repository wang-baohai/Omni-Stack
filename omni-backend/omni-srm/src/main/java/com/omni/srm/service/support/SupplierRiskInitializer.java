package com.omni.srm.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.srm.entity.SrmRiskIndicator;
import com.omni.srm.mapper.SrmRiskIndicatorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商默认风险指标初始化器。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class SupplierRiskInitializer {

    private static final List<String> DEFAULT_INDICATOR_TYPES = List.of(
            "FINANCIAL", "COMPLIANCE", "SUPPLY", "COOPERATION", "QUALITY", "CERTIFICATE");

    private final SrmRiskIndicatorMapper riskIndicatorMapper;

    /**
     * 幂等补齐供应商的六类默认绿色风险指标。
     *
     * @param tenantId 租户 ID
     * @param supplierId 供应商 ID
     */
    public void initialize(Long tenantId, Long supplierId) {
        Set<String> existingTypes = riskIndicatorMapper.selectList(
                        new LambdaQueryWrapper<SrmRiskIndicator>()
                                .eq(SrmRiskIndicator::getTenantId, tenantId)
                                .eq(SrmRiskIndicator::getSupplierId, supplierId)
                                .select(SrmRiskIndicator::getIndicatorType))
                .stream()
                .map(SrmRiskIndicator::getIndicatorType)
                .collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (String indicatorType : DEFAULT_INDICATOR_TYPES) {
            if (existingTypes.contains(indicatorType)) {
                continue;
            }
            SrmRiskIndicator indicator = new SrmRiskIndicator();
            indicator.setTenantId(tenantId);
            indicator.setSupplierId(supplierId);
            indicator.setIndicatorType(indicatorType);
            indicator.setRiskLevel("GREEN");
            indicator.setAssessmentTime(now);
            indicator.setRemark("供应商创建时初始化");
            indicator.setVersion(0);
            indicator.setDeleted(0);
            SrmAuditSupport.created(indicator);
            riskIndicatorMapper.insert(indicator);
        }
    }
}
