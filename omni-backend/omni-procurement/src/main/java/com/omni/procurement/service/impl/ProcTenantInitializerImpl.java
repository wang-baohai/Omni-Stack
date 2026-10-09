package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.entity.ProcTenantConfig;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcTenantConfigMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.procurement.service.ProcTenantInitializer;
import com.omni.procurement.service.support.ProcAuditSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
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
    @Transactional
    public void ensureInitialized() {
        Long tenantId = ServiceIdentityContext.requireTenantId();
        if (findConfig(tenantId) != null) {
            return;
        }
        if (!insertConfigOrObserveConcurrent(tenantId)) {
            return;
        }
        ProcMaterialCategory itDevice = ensureCategory(tenantId, 0L, "IT_DEVICE", "IT 设备", 10);
        ProcMaterialCategory officeSupply = ensureCategory(
                tenantId, 0L, "OFFICE_SUPPLY", "办公用品", 20);
        ProcMaterialCategory rawMaterial = ensureCategory(
                tenantId, 0L, "RAW_MATERIAL", "原材料", 30);
        ProcMaterialCategory other = ensureCategory(tenantId, 0L, "OTHER", "其他", 40);
        ensureCategory(tenantId, requireId(itDevice), "LAPTOP", "笔记本电脑", 10);
        ensureCategory(tenantId, requireId(itDevice), "MONITOR", "显示器", 20);
        ensureCategory(tenantId, requireId(itDevice), "PERIPHERAL", "外设配件", 30);
        ensureCategory(tenantId, requireId(officeSupply), "STATIONERY", "文具", 10);
        ensureCategory(tenantId, requireId(officeSupply), "PAPER", "纸张耗材", 20);
        ensureCategory(tenantId, requireId(rawMaterial), "METAL", "金属材料", 10);
        ensureCategory(tenantId, requireId(rawMaterial), "ELECTRONIC", "电子元器件", 20);
        ensureCategory(tenantId, requireId(rawMaterial), "PLASTIC", "塑料材料", 30);
        ensureCategory(tenantId, requireId(other), "SERVICE", "服务", 10);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public String currencyCode() {
        ensureInitialized();
        ProcTenantConfig config = configMapper.selectForUpdateByTenant(ServiceIdentityContext.requireTenantId());
        if (config == null || config.getCurrencyCode() == null || config.getCurrencyCode().length() != 3) {
            throw new BusinessException(500, "采购租户默认币种未正确初始化");
        }
        return config.getCurrencyCode();
    }

    private ProcMaterialCategory ensureCategory(
            Long tenantId, Long parentId, String code, String name, int sort) {
        ProcMaterialCategory existing = findCategory(tenantId, code);
        if (existing != null) {
            return existing;
        }
        ProcMaterialCategory category = new ProcMaterialCategory();
        category.setTenantId(tenantId);
        category.setParentId(parentId);
        category.setCategoryCode(code);
        category.setCategoryName(name);
        category.setSort(sort);
        category.setStatus(1);
        category.setVersion(0);
        category.setDeleted(0);
        ProcAuditSupport.created(category);
        try {
            categoryMapper.insert(category);
            return category;
        } catch (DuplicateKeyException exception) {
            ProcMaterialCategory concurrent = findCategoryForUpdate(tenantId, code);
            if (concurrent == null) {
                throw new BusinessException(500, "采购默认物料品类初始化冲突");
            }
            return concurrent;
        }
    }

    private Long requireId(ProcMaterialCategory category) {
        if (category == null || category.getId() == null) {
            throw new BusinessException(500, "采购默认父品类初始化后缺少 ID");
        }
        return category.getId();
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
