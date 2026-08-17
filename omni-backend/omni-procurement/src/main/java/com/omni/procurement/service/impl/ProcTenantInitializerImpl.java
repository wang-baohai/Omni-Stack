package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.entity.ProcTenantConfig;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcTenantConfigMapper;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.service.ProcTenantInitializer;
import com.omni.procurement.service.support.ProcAuditSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 采购租户默认配置的数据库幂等初始化实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class ProcTenantInitializerImpl implements ProcTenantInitializer {

    private static final String DEFAULT_CURRENCY = "CNY";

    private final ProcTenantConfigMapper configMapper;
    private final ProcMaterialCategoryMapper categoryMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureInitialized() {
        Long tenantId = ProcTenantContext.requireTenantId();
        if (findConfig(tenantId) != null) {
            return;
        }
        if (!insertConfigOrObserveConcurrent(tenantId)) {
            return;
        }
        ensureCategory(tenantId, "IT_DEVICE", "IT 设备", 10);
        ensureCategory(tenantId, "OFFICE_SUPPLY", "办公用品", 20);
        ensureCategory(tenantId, "RAW_MATERIAL", "原材料", 30);
        ensureCategory(tenantId, "OTHER", "其他", 40);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public String currencyCode() {
        ensureInitialized();
        ProcTenantConfig config = configMapper.selectForUpdateByTenant(ProcTenantContext.requireTenantId());
        if (config == null || config.getCurrencyCode() == null || config.getCurrencyCode().length() != 3) {
            throw new BusinessException(500, "采购租户默认币种未正确初始化");
        }
        return config.getCurrencyCode();
    }

    private void ensureCategory(Long tenantId, String code, String name, int sort) {
        if (findCategory(tenantId, code) != null) {
            return;
        }
        ProcMaterialCategory category = new ProcMaterialCategory();
        category.setTenantId(tenantId);
        category.setParentId(0L);
        category.setCategoryCode(code);
        category.setCategoryName(name);
        category.setSort(sort);
        category.setStatus(1);
        category.setVersion(0);
        category.setDeleted(0);
        ProcAuditSupport.created(category);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException exception) {
            if (findCategoryForUpdate(tenantId, code) == null) {
                throw new BusinessException(500, "采购默认物料品类初始化冲突");
            }
        }
    }

    private boolean insertConfigOrObserveConcurrent(Long tenantId) {
        ProcTenantConfig config = new ProcTenantConfig();
        config.setTenantId(tenantId);
        config.setCurrencyCode(DEFAULT_CURRENCY);
        config.setInitializedTime(LocalDateTime.now());
        config.setVersion(0);
        config.setDeleted(0);
        ProcAuditSupport.created(config);
        try {
            configMapper.insert(config);
            return true;
        } catch (DuplicateKeyException exception) {
            if (configMapper.selectForUpdateByTenant(tenantId) == null) {
                throw new BusinessException(500, "采购租户默认配置初始化冲突");
            }
            return false;
        }
    }

    private ProcTenantConfig findConfig(Long tenantId) {
        return configMapper.selectOne(new LambdaQueryWrapper<ProcTenantConfig>()
                .eq(ProcTenantConfig::getTenantId, tenantId));
    }

    private ProcMaterialCategory findCategory(Long tenantId, String code) {
        return categoryMapper.selectOne(new LambdaQueryWrapper<ProcMaterialCategory>()
                .eq(ProcMaterialCategory::getTenantId, tenantId)
                .eq(ProcMaterialCategory::getCategoryCode, code));
    }

    private ProcMaterialCategory findCategoryForUpdate(Long tenantId, String code) {
        return categoryMapper.selectOne(new LambdaQueryWrapper<ProcMaterialCategory>()
                .eq(ProcMaterialCategory::getTenantId, tenantId)
                .eq(ProcMaterialCategory::getCategoryCode, code)
                .last("FOR UPDATE"));
    }
}
