package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.crm.entity.CrmPipeline;
import com.omni.crm.entity.CrmPipelineStage;
import com.omni.crm.entity.CrmTenantConfig;
import com.omni.crm.mapper.CrmPipelineMapper;
import com.omni.crm.mapper.CrmPipelineStageMapper;
import com.omni.crm.mapper.CrmTenantConfigMapper;
import com.omni.crm.security.CrmTenantContext;
import com.omni.crm.service.CrmTenantInitializer;
import com.omni.crm.service.support.CrmAuditSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** CRM 租户默认配置的数据库幂等初始化实现。 */
@Service
@RequiredArgsConstructor
public class CrmTenantInitializerImpl implements CrmTenantInitializer {

    private final CrmTenantConfigMapper configMapper;
    private final CrmPipelineMapper pipelineMapper;
    private final CrmPipelineStageMapper stageMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Long ensureInitialized() {
        Long tenantId = CrmTenantContext.requireTenantId();
        CrmTenantConfig existing = findConfig(tenantId);
        if (existing != null) {
            return existing.getDefaultPipelineId();
        }
        CrmPipeline pipeline = resolveDefaultPipeline(tenantId);
        ensureStage(tenantId, pipeline.getId(), "DISCOVERY", "需求发现", "OPEN", 10, 10);
        ensureStage(tenantId, pipeline.getId(), "QUALIFICATION", "资格确认", "OPEN", 30, 20);
        ensureStage(tenantId, pipeline.getId(), "PROPOSAL", "方案报价", "OPEN", 50, 30);
        ensureStage(tenantId, pipeline.getId(), "NEGOTIATION", "商务谈判", "OPEN", 80, 40);
        ensureStage(tenantId, pipeline.getId(), "WON", "赢单", "WON", 100, 50);
        ensureStage(tenantId, pipeline.getId(), "LOST", "输单", "LOST", 0, 60);
        return insertConfigOrReadConcurrent(tenantId, pipeline.getId());
    }

    /** {@inheritDoc} */
    @Override
    public String currencyCode() {
        ensureInitialized();
        CrmTenantConfig config = findConfig(CrmTenantContext.requireTenantId());
        if (config == null || config.getCurrencyCode() == null || config.getCurrencyCode().length() != 3) {
            throw new BusinessException(500, "CRM 租户默认币种未正确初始化");
        }
        return config.getCurrencyCode();
    }

    private CrmPipeline resolveDefaultPipeline(Long tenantId) {
        CrmPipeline existing = findDefaultPipeline(tenantId);
        if (existing != null) {
            return existing;
        }
        CrmPipeline pipeline = new CrmPipeline();
        pipeline.setTenantId(tenantId);
        pipeline.setCode("DEFAULT");
        pipeline.setName("默认销售管道");
        pipeline.setStatus(1);
        pipeline.setDefaultFlag(1);
        pipeline.setSort(0);
        pipeline.setVersion(0);
        pipeline.setDeleted(0);
        CrmAuditSupport.created(pipeline);
        try {
            pipelineMapper.insert(pipeline);
            return pipeline;
        } catch (DuplicateKeyException exception) {
            CrmPipeline concurrent = findDefaultPipeline(tenantId);
            if (concurrent == null) {
                throw new BusinessException(500, "CRM 默认销售管道初始化冲突");
            }
            return concurrent;
        }
    }

    private void ensureStage(Long tenantId, Long pipelineId, String code, String name,
                             String type, int probability, int sort) {
        if (countStage(tenantId, pipelineId, code) > 0) {
            return;
        }
        CrmPipelineStage stage = new CrmPipelineStage();
        stage.setTenantId(tenantId);
        stage.setPipelineId(pipelineId);
        stage.setStageCode(code);
        stage.setStageName(name);
        stage.setStageType(type);
        stage.setProbability(BigDecimal.valueOf(probability));
        stage.setSort(sort);
        stage.setStatus(1);
        stage.setDeleted(0);
        CrmAuditSupport.created(stage);
        try {
            stageMapper.insert(stage);
        } catch (DuplicateKeyException exception) {
            if (countStage(tenantId, pipelineId, code) == 0) {
                throw new BusinessException(500, "CRM 默认阶段初始化冲突");
            }
        }
    }

    private Long insertConfigOrReadConcurrent(Long tenantId, Long pipelineId) {
        CrmTenantConfig config = new CrmTenantConfig();
        config.setTenantId(tenantId);
        config.setDefaultPipelineId(pipelineId);
        config.setCurrencyCode("CNY");
        config.setLeadDuplicatePolicy("WARN");
        config.setInitializedTime(LocalDateTime.now());
        CrmAuditSupport.created(config);
        try {
            configMapper.insert(config);
            return pipelineId;
        } catch (DuplicateKeyException exception) {
            CrmTenantConfig concurrent = findConfig(tenantId);
            if (concurrent == null) {
                throw new BusinessException(500, "CRM 租户默认配置初始化冲突");
            }
            return concurrent.getDefaultPipelineId();
        }
    }

    private CrmTenantConfig findConfig(Long tenantId) {
        return configMapper.selectOne(new LambdaQueryWrapper<CrmTenantConfig>()
                .eq(CrmTenantConfig::getTenantId, tenantId));
    }

    private CrmPipeline findDefaultPipeline(Long tenantId) {
        return pipelineMapper.selectOne(new LambdaQueryWrapper<CrmPipeline>()
                .eq(CrmPipeline::getTenantId, tenantId)
                .eq(CrmPipeline::getCode, "DEFAULT"));
    }

    private long countStage(Long tenantId, Long pipelineId, String stageCode) {
        return stageMapper.selectCount(new LambdaQueryWrapper<CrmPipelineStage>()
                .eq(CrmPipelineStage::getTenantId, tenantId)
                .eq(CrmPipelineStage::getPipelineId, pipelineId)
                .eq(CrmPipelineStage::getStageCode, stageCode));
    }
}
