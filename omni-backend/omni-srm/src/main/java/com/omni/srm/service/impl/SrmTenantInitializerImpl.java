package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.entity.SrmEvaluationDimension;
import com.omni.srm.entity.SrmEvaluationTemplate;
import com.omni.srm.mapper.SrmEvaluationDimensionMapper;
import com.omni.srm.mapper.SrmEvaluationTemplateMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.SrmTenantInitializer;
import com.omni.srm.service.support.SrmAuditSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SRM 租户默认评估模板的数据库幂等初始化实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class SrmTenantInitializerImpl implements SrmTenantInitializer {

    private static final String DEFAULT_TEMPLATE_NAME = "默认供应商评估模板";
    private static final List<DimensionSeed> DEFAULT_DIMENSIONS = List.of(
            new DimensionSeed("质量", new BigDecimal("30.00"), 10),
            new DimensionSeed("交期", new BigDecimal("30.00"), 20),
            new DimensionSeed("价格", new BigDecimal("20.00"), 30),
            new DimensionSeed("服务", new BigDecimal("20.00"), 40));

    private final SrmEvaluationTemplateMapper templateMapper;
    private final SrmEvaluationDimensionMapper dimensionMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Long ensureInitialized() {
        Long tenantId = SrmTenantContext.requireTenantId();
        SrmEvaluationTemplate template = findDefaultTemplate();
        if (template == null) {
            template = resolveDefaultTemplate(tenantId);
        }
        ensureSingleDefault(tenantId, template.getId());
        for (DimensionSeed seed : DEFAULT_DIMENSIONS) {
            ensureDimension(tenantId, template.getId(), seed);
        }
        disableUnexpectedDimensions(tenantId, template.getId());
        return template.getId();
    }

    private void ensureSingleDefault(Long tenantId, Long templateId) {
        templateMapper.update(null, new LambdaUpdateWrapper<SrmEvaluationTemplate>()
                .eq(SrmEvaluationTemplate::getTenantId, tenantId)
                .ne(SrmEvaluationTemplate::getId, templateId)
                .eq(SrmEvaluationTemplate::getDefaultFlag, true)
                .eq(SrmEvaluationTemplate::getDeleted, 0)
                .set(SrmEvaluationTemplate::getDefaultFlag, false)
                .set(SrmEvaluationTemplate::getUpdateTime, LocalDateTime.now())
                .set(SrmEvaluationTemplate::getUpdateBy, operator())
                .setSql("version = version + 1"));
    }

    private SrmEvaluationTemplate resolveDefaultTemplate(Long tenantId) {
        SrmEvaluationTemplate existing = findTemplateByName();
        if (existing != null) {
            activateTemplate(existing);
            return existing;
        }
        SrmEvaluationTemplate template = new SrmEvaluationTemplate();
        template.setTenantId(tenantId);
        template.setName(DEFAULT_TEMPLATE_NAME);
        template.setStatus(1);
        template.setDefaultFlag(true);
        template.setVersion(0);
        template.setDeleted(0);
        SrmAuditSupport.created(template);
        try {
            templateMapper.insert(template);
            return template;
        } catch (DuplicateKeyException exception) {
            SrmEvaluationTemplate concurrent = findTemplateByName();
            if (concurrent == null) {
                throw new BusinessException(500, "SRM 默认评估模板初始化冲突");
            }
            activateTemplate(concurrent);
            return concurrent;
        }
    }

    private void activateTemplate(SrmEvaluationTemplate template) {
        if (Integer.valueOf(1).equals(template.getStatus()) && Boolean.TRUE.equals(template.getDefaultFlag())) {
            return;
        }
        template.setStatus(1);
        template.setDefaultFlag(true);
        SrmAuditSupport.updated(template);
        if (templateMapper.updateById(template) != 1) {
            throw new BusinessException(500, "SRM 默认评估模板启用失败");
        }
    }

    private void ensureDimension(Long tenantId, Long templateId, DimensionSeed seed) {
        SrmEvaluationDimension dimension = findDimension(templateId, seed.name());
        if (dimension == null) {
            dimension = new SrmEvaluationDimension();
            dimension.setTenantId(tenantId);
            dimension.setTemplateId(templateId);
            dimension.setIndicatorName(seed.name());
            dimension.setWeight(seed.weight());
            dimension.setSort(seed.sort());
            dimension.setStatus(1);
            dimension.setDeleted(0);
            SrmAuditSupport.created(dimension);
            try {
                dimensionMapper.insert(dimension);
                return;
            } catch (DuplicateKeyException exception) {
                dimension = findDimension(templateId, seed.name());
                if (dimension == null) {
                    throw new BusinessException(500, "SRM 默认评估维度初始化冲突");
                }
            }
        }
        normalizeDimension(dimension, seed);
    }

    private void normalizeDimension(SrmEvaluationDimension dimension, DimensionSeed seed) {
        if (seed.weight().compareTo(dimension.getWeight()) == 0
                && seed.sort().equals(dimension.getSort())
                && Integer.valueOf(1).equals(dimension.getStatus())) {
            return;
        }
        dimension.setWeight(seed.weight());
        dimension.setSort(seed.sort());
        dimension.setStatus(1);
        SrmAuditSupport.updated(dimension);
        if (dimensionMapper.updateById(dimension) != 1) {
            throw new BusinessException(500, "SRM 默认评估维度修复失败");
        }
    }

    private void disableUnexpectedDimensions(Long tenantId, Long templateId) {
        List<String> expectedNames = DEFAULT_DIMENSIONS.stream().map(DimensionSeed::name).toList();
        dimensionMapper.update(null, new LambdaUpdateWrapper<SrmEvaluationDimension>()
                .eq(SrmEvaluationDimension::getTenantId, tenantId)
                .eq(SrmEvaluationDimension::getTemplateId, templateId)
                .eq(SrmEvaluationDimension::getStatus, 1)
                .notIn(SrmEvaluationDimension::getIndicatorName, expectedNames)
                .eq(SrmEvaluationDimension::getDeleted, 0)
                .set(SrmEvaluationDimension::getStatus, 0)
                .set(SrmEvaluationDimension::getUpdateTime, LocalDateTime.now())
                .set(SrmEvaluationDimension::getUpdateBy, operator()));
    }

    private SrmEvaluationTemplate findDefaultTemplate() {
        return templateMapper.selectOne(new LambdaQueryWrapper<SrmEvaluationTemplate>()
                .eq(SrmEvaluationTemplate::getStatus, 1)
                .eq(SrmEvaluationTemplate::getDefaultFlag, true)
                .orderByAsc(SrmEvaluationTemplate::getId)
                .last("LIMIT 1"));
    }

    private SrmEvaluationTemplate findTemplateByName() {
        return templateMapper.selectOne(new LambdaQueryWrapper<SrmEvaluationTemplate>()
                .eq(SrmEvaluationTemplate::getName, DEFAULT_TEMPLATE_NAME)
                .last("LIMIT 1"));
    }

    private SrmEvaluationDimension findDimension(Long templateId, String name) {
        return dimensionMapper.selectOne(new LambdaQueryWrapper<SrmEvaluationDimension>()
                .eq(SrmEvaluationDimension::getTemplateId, templateId)
                .eq(SrmEvaluationDimension::getIndicatorName, name)
                .last("LIMIT 1"));
    }

    private String operator() {
        String username = SrmTenantContext.require().username();
        return username == null || username.isBlank()
                ? String.valueOf(SrmTenantContext.require().userId()) : username;
    }

    /**
     * 默认评估维度种子。
     *
     * @param name 维度名称
     * @param weight 权重
     * @param sort 排序
     */
    private record DimensionSeed(String name, BigDecimal weight, Integer sort) {
    }
}
