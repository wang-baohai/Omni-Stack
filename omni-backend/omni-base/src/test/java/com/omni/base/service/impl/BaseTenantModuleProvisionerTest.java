package com.omni.base.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.base.entity.SysDictData;
import com.omni.base.entity.SysDictType;
import com.omni.base.mapper.SysDictDataMapper;
import com.omni.base.mapper.SysDictTypeMapper;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;

/**
 * Base 租户字典目录初始化测试。
 */
class BaseTenantModuleProvisionerTest {

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(SysDictType.class, "SysDictTypeMapper");
        initialize(SysDictData.class, "SysDictDataMapper");
    }

    @Test
    void shouldCloneTemplateDictionaryWithoutOverwritingTarget() {
        SysDictTypeMapper typeMapper = mock(SysDictTypeMapper.class);
        SysDictDataMapper dataMapper = mock(SysDictDataMapper.class);
        SysDictType type = new SysDictType();
        type.setTenantId(1L);
        type.setTypeCode("asset_category");
        type.setTypeName("资产品类");
        type.setRemark("模板备注");
        type.setSort(30);
        type.setStatus(1);
        SysDictData data = new SysDictData();
        data.setTenantId(1L);
        data.setTypeCode("asset_category");
        data.setDictValue("IT_DEVICE");
        data.setDictLabel("IT 设备");
        data.setTagType("primary");
        data.setSort(10);
        data.setStatus(1);
        when(typeMapper.selectList(any())).thenReturn(List.of(type));
        when(dataMapper.selectList(any())).thenReturn(List.of(data));
        when(typeMapper.selectCount(any())).thenReturn(0L);
        when(dataMapper.selectCount(any())).thenReturn(0L);

        new BaseTenantModuleProvisioner(typeMapper, dataMapper).provision(request(9L));

        ArgumentCaptor<SysDictType> typeCaptor = ArgumentCaptor.forClass(SysDictType.class);
        verify(typeMapper).insert(typeCaptor.capture());
        assertThat(typeCaptor.getValue().getTenantId()).isEqualTo(9L);
        assertThat(typeCaptor.getValue().getTypeCode()).isEqualTo("asset_category");
        ArgumentCaptor<SysDictData> dataCaptor = ArgumentCaptor.forClass(SysDictData.class);
        verify(dataMapper).insert(dataCaptor.capture());
        assertThat(dataCaptor.getValue().getTenantId()).isEqualTo(9L);
        assertThat(dataCaptor.getValue().getDictValue()).isEqualTo("IT_DEVICE");
        assertThat(dataCaptor.getValue().getCreateBy()).isEqualTo("tenant-provisioning");
    }

    private static ProvisionRequestedEvent request(Long tenantId) {
        return new ProvisionRequestedEvent(
                "event-1", "request-1", tenantId, "tenant-9", "租户 9", List.of("base"), Instant.now());
    }

    private static void initialize(Class<?> entityType, String mapperName) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "base-provision-" + mapperName);
        assistant.setCurrentNamespace("com.omni.base.mapper." + mapperName);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
