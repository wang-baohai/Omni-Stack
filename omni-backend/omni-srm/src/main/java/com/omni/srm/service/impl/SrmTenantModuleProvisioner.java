package com.omni.srm.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.srm.entity.SrmRiskCriterion;
import com.omni.srm.entity.SrmRiskIndicatorType;
import com.omni.srm.entity.SrmRiskScoreThreshold;
import com.omni.srm.entity.SrmTenantEntity;
import com.omni.srm.mapper.SrmRiskCriterionMapper;
import com.omni.srm.mapper.SrmRiskIndicatorTypeMapper;
import com.omni.srm.mapper.SrmRiskScoreThresholdMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.srm.service.SrmTenantInitializer;
import lombok.RequiredArgsConstructor;

/**
 * SRM 租户模块事件初始化适配器。
 */
@Component
@RequiredArgsConstructor
public class SrmTenantModuleProvisioner implements TenantModuleProvisioner {

    private static final Long TEMPLATE_TENANT_ID = 1L;
    private static final String SYSTEM_OPERATOR = "tenant-provisioning";

    private final SrmTenantInitializer tenantInitializer;
    private final SrmRiskIndicatorTypeMapper riskTypeMapper;
    private final SrmRiskCriterionMapper riskCriterionMapper;
    private final SrmRiskScoreThresholdMapper riskThresholdMapper;

    /** {@inheritDoc} */
    @Override
    public String moduleId() {
        return "srm";
    }

    /** {@inheritDoc} */
    @Override
    public void provision(ProvisionRequestedEvent event) {
        RiskCatalogSnapshot snapshot = loadTemplateSnapshot();
        ServiceIdentityContext.set(new ServiceRequestIdentity(
                0L, event.tenantId(), "tenant-provisioning"));
        try {
            tenantInitializer.ensureInitialized();
            cloneRiskCatalog(event.tenantId(), snapshot);
        } finally {
            ServiceIdentityContext.clear();
        }
    }

    private RiskCatalogSnapshot loadTemplateSnapshot() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(
                0L, TEMPLATE_TENANT_ID, "tenant-provisioning-template"));
        try {
            List<RiskTypeSeed> types = riskTypeMapper.selectList(
                            new LambdaQueryWrapper<SrmRiskIndicatorType>()
                                    .eq(SrmRiskIndicatorType::getTenantId, TEMPLATE_TENANT_ID)
                                    .eq(SrmRiskIndicatorType::getDeleted, 0)
                                    .orderByAsc(SrmRiskIndicatorType::getSort))
                    .stream().map(RiskTypeSeed::from).toList();
            List<RiskCriterionSeed> criteria = riskCriterionMapper.selectList(
                            new LambdaQueryWrapper<SrmRiskCriterion>()
                                    .eq(SrmRiskCriterion::getTenantId, TEMPLATE_TENANT_ID)
                                    .eq(SrmRiskCriterion::getDeleted, 0)
                                    .orderByAsc(SrmRiskCriterion::getSort))
                    .stream().map(RiskCriterionSeed::from).toList();
            List<RiskThresholdSeed> thresholds = riskThresholdMapper.selectList(
                            new LambdaQueryWrapper<SrmRiskScoreThreshold>()
                                    .eq(SrmRiskScoreThreshold::getTenantId, TEMPLATE_TENANT_ID)
                                    .eq(SrmRiskScoreThreshold::getDeleted, 0)
                                    .orderByAsc(SrmRiskScoreThreshold::getMinScore))
                    .stream().map(RiskThresholdSeed::from).toList();
            if (types.isEmpty()) {
                throw new IllegalStateException("默认租户缺少 SRM 风险类型目录");
            }
            return new RiskCatalogSnapshot(types, criteria, thresholds);
        } finally {
            ServiceIdentityContext.clear();
        }
    }

    private void cloneRiskCatalog(Long tenantId, RiskCatalogSnapshot snapshot) {
        Map<Long, Long> typeIdMapping = cloneRiskTypes(tenantId, snapshot.types());
        cloneRiskCriteria(tenantId, typeIdMapping, snapshot.criteria());
        cloneRiskThresholds(tenantId, snapshot.thresholds());
    }

    private Map<Long, Long> cloneRiskTypes(Long tenantId, List<RiskTypeSeed> templates) {
        Map<Long, Long> mapping = new HashMap<>();
        for (RiskTypeSeed template : templates) {
            SrmRiskIndicatorType target = riskTypeMapper.selectOne(
                    new LambdaQueryWrapper<SrmRiskIndicatorType>()
                            .eq(SrmRiskIndicatorType::getTenantId, tenantId)
                            .eq(SrmRiskIndicatorType::getTypeCode, template.typeCode())
                            .eq(SrmRiskIndicatorType::getDeleted, 0));
            if (target == null) {
                target = new SrmRiskIndicatorType();
                target.setTenantId(tenantId);
                target.setTypeCode(template.typeCode());
                target.setTypeName(template.typeName());
                target.setDescription(template.description());
                target.setSort(template.sort());
                target.setAutoCalc(template.autoCalc());
                target.setStatus(template.status());
                target.setDeleted(0);
                applyAudit(target);
                riskTypeMapper.insert(target);
            }
            mapping.put(template.templateId(), requireId(target, "SRM 风险类型"));
        }
        return mapping;
    }

    private void cloneRiskCriteria(
            Long tenantId, Map<Long, Long> typeIdMapping, List<RiskCriterionSeed> templates) {
        for (RiskCriterionSeed template : templates) {
            Long targetTypeId = typeIdMapping.get(template.templateTypeId());
            if (targetTypeId == null) {
                throw new IllegalStateException("SRM 风险标准引用了未知模板类型");
            }
            if (riskCriterionMapper.selectCount(new LambdaQueryWrapper<SrmRiskCriterion>()
                    .eq(SrmRiskCriterion::getTenantId, tenantId)
                    .eq(SrmRiskCriterion::getIndicatorTypeId, targetTypeId)
                    .eq(SrmRiskCriterion::getCriterionLabel, template.criterionLabel())
                    .eq(SrmRiskCriterion::getDeleted, 0)) > 0) {
                continue;
            }
            SrmRiskCriterion target = new SrmRiskCriterion();
            target.setTenantId(tenantId);
            target.setIndicatorTypeId(targetTypeId);
            target.setCriterionLabel(template.criterionLabel());
            target.setScore(template.score());
            target.setRiskLevel(template.riskLevel());
            target.setSort(template.sort());
            target.setStatus(template.status());
            target.setDeleted(0);
            applyAudit(target);
            riskCriterionMapper.insert(target);
        }
    }

    private void cloneRiskThresholds(Long tenantId, List<RiskThresholdSeed> templates) {
        for (RiskThresholdSeed template : templates) {
            if (riskThresholdMapper.selectCount(new LambdaQueryWrapper<SrmRiskScoreThreshold>()
                    .eq(SrmRiskScoreThreshold::getTenantId, tenantId)
                    .eq(SrmRiskScoreThreshold::getRiskLevel, template.riskLevel())
                    .eq(SrmRiskScoreThreshold::getDeleted, 0)) > 0) {
                continue;
            }
            SrmRiskScoreThreshold target = new SrmRiskScoreThreshold();
            target.setTenantId(tenantId);
            target.setRiskLevel(template.riskLevel());
            target.setMinScore(template.minScore());
            target.setMaxScore(template.maxScore());
            target.setDeleted(0);
            applyAudit(target);
            riskThresholdMapper.insert(target);
        }
    }

    private static Long requireId(SrmTenantEntity entity, String catalogName) {
        if (entity.getId() == null) {
            throw new IllegalStateException(catalogName + "初始化后缺少 ID");
        }
        return entity.getId();
    }

    private static void applyAudit(SrmTenantEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateBy(SYSTEM_OPERATOR);
        entity.setUpdateBy(SYSTEM_OPERATOR);
    }

    /** 默认风险目录不可变快照。 */
    private record RiskCatalogSnapshot(
            List<RiskTypeSeed> types,
            List<RiskCriterionSeed> criteria,
            List<RiskThresholdSeed> thresholds) {
    }

    /** 默认风险类型快照。 */
    private record RiskTypeSeed(
            Long templateId,
            String typeCode,
            String typeName,
            String description,
            Integer sort,
            Integer autoCalc,
            Integer status) {

        private static RiskTypeSeed from(SrmRiskIndicatorType source) {
            return new RiskTypeSeed(
                    source.getId(), source.getTypeCode(), source.getTypeName(), source.getDescription(),
                    source.getSort(), source.getAutoCalc(), source.getStatus());
        }
    }

    /** 默认风险标准快照。 */
    private record RiskCriterionSeed(
            Long templateTypeId,
            String criterionLabel,
            Integer score,
            String riskLevel,
            Integer sort,
            Integer status) {

        private static RiskCriterionSeed from(SrmRiskCriterion source) {
            return new RiskCriterionSeed(
                    source.getIndicatorTypeId(), source.getCriterionLabel(), source.getScore(),
                    source.getRiskLevel(), source.getSort(), source.getStatus());
        }
    }

    /** 默认风险阈值快照。 */
    private record RiskThresholdSeed(String riskLevel, Integer minScore, Integer maxScore) {

        private static RiskThresholdSeed from(SrmRiskScoreThreshold source) {
            return new RiskThresholdSeed(source.getRiskLevel(), source.getMinScore(), source.getMaxScore());
        }
    }
}
