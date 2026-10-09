package com.omni.base.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.base.entity.SysDictData;
import com.omni.base.entity.SysDictType;
import com.omni.base.mapper.SysDictDataMapper;
import com.omni.base.mapper.SysDictTypeMapper;
import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import lombok.RequiredArgsConstructor;

/**
 * Base 租户字典目录初始化器。
 *
 * <p>默认租户是正式字典模板；新租户只补齐缺失自然键，不覆盖已经存在的租户自定义值。</p>
 */
@Component
@RequiredArgsConstructor
public class BaseTenantModuleProvisioner implements TenantModuleProvisioner {

    private static final Long TEMPLATE_TENANT_ID = 1L;
    private static final String SYSTEM_OPERATOR = "tenant-provisioning";

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    /** {@inheritDoc} */
    @Override
    public String moduleId() {
        return "base";
    }

    /** {@inheritDoc} */
    @Override
    public void provision(ProvisionRequestedEvent event) {
        if (TEMPLATE_TENANT_ID.equals(event.tenantId())) {
            return;
        }
        cloneDictionaryTypes(event.tenantId());
        cloneDictionaryData(event.tenantId());
    }

    private void cloneDictionaryTypes(Long tenantId) {
        List<SysDictType> templates = dictTypeMapper.selectList(
                new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getTenantId, TEMPLATE_TENANT_ID)
                        .orderByAsc(SysDictType::getSort)
                        .orderByAsc(SysDictType::getId));
        for (SysDictType template : templates) {
            if (dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                    .eq(SysDictType::getTenantId, tenantId)
                    .eq(SysDictType::getTypeCode, template.getTypeCode())) > 0) {
                continue;
            }
            SysDictType target = new SysDictType();
            target.setTenantId(tenantId);
            target.setTypeCode(template.getTypeCode());
            target.setTypeName(template.getTypeName());
            target.setRemark(template.getRemark());
            target.setSort(template.getSort());
            target.setStatus(template.getStatus());
            applyAudit(target);
            dictTypeMapper.insert(target);
        }
    }

    private void cloneDictionaryData(Long tenantId) {
        List<SysDictData> templates = dictDataMapper.selectList(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getTenantId, TEMPLATE_TENANT_ID)
                        .orderByAsc(SysDictData::getTypeCode)
                        .orderByAsc(SysDictData::getSort)
                        .orderByAsc(SysDictData::getId));
        for (SysDictData template : templates) {
            if (dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                    .eq(SysDictData::getTenantId, tenantId)
                    .eq(SysDictData::getTypeCode, template.getTypeCode())
                    .eq(SysDictData::getDictValue, template.getDictValue())) > 0) {
                continue;
            }
            SysDictData target = new SysDictData();
            target.setTenantId(tenantId);
            target.setTypeCode(template.getTypeCode());
            target.setDictValue(template.getDictValue());
            target.setDictLabel(template.getDictLabel());
            target.setTagType(template.getTagType());
            target.setRemark(template.getRemark());
            target.setSort(template.getSort());
            target.setStatus(template.getStatus());
            applyAudit(target);
            dictDataMapper.insert(target);
        }
    }

    private static void applyAudit(com.omni.common.core.model.BaseEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setCreateBy(SYSTEM_OPERATOR);
        entity.setUpdateBy(SYSTEM_OPERATOR);
    }
}
