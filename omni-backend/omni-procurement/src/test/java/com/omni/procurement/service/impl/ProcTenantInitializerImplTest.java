package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.entity.ProcTenantConfig;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcTenantConfigMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/** 采购租户默认数据幂等初始化测试。 */
@ExtendWith(MockitoExtension.class)
class ProcTenantInitializerImplTest {

    @Mock private ProcTenantConfigMapper configMapper;
    @Mock private ProcMaterialCategoryMapper categoryMapper;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcTenantConfig.class, "ProcTenantConfigMapper");
        initialize(ProcMaterialCategory.class, "ProcMaterialCategoryMapper");
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** 首次初始化必须给当前租户创建四个预置品类和 CNY 配置。 */
    @Test
    void shouldInitializeDefaultsForCurrentTenant() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 23L, "buyer"));
        when(categoryMapper.selectOne(any())).thenReturn(null);
        when(configMapper.selectOne(any())).thenReturn(null);
        AtomicLong categoryId = new AtomicLong(100L);
        when(categoryMapper.insert(any(ProcMaterialCategory.class))).thenAnswer(invocation -> {
            ProcMaterialCategory category = invocation.getArgument(0);
            category.setId(categoryId.incrementAndGet());
            return 1;
        });
        ProcTenantInitializerImpl service = new ProcTenantInitializerImpl(configMapper, categoryMapper);

        service.ensureInitialized();

        ArgumentCaptor<ProcMaterialCategory> categoryCaptor = ArgumentCaptor.forClass(ProcMaterialCategory.class);
        verify(categoryMapper, org.mockito.Mockito.times(13)).insert(categoryCaptor.capture());
        List<ProcMaterialCategory> categories = categoryCaptor.getAllValues();
        assertThat(categories).extracting(ProcMaterialCategory::getTenantId).containsOnly(23L);
        assertThat(categories).extracting(ProcMaterialCategory::getCategoryCode)
                .containsExactly(
                        "IT_DEVICE", "OFFICE_SUPPLY", "RAW_MATERIAL", "OTHER",
                        "LAPTOP", "MONITOR", "PERIPHERAL", "STATIONERY", "PAPER",
                        "METAL", "ELECTRONIC", "PLASTIC", "SERVICE");
        assertThat(categories.subList(4, categories.size()))
                .allSatisfy(category -> assertThat(category.getParentId()).isPositive());
        ArgumentCaptor<ProcTenantConfig> configCaptor = ArgumentCaptor.forClass(ProcTenantConfig.class);
        verify(configMapper).insert(configCaptor.capture());
        assertThat(configCaptor.getValue().getTenantId()).isEqualTo(23L);
        assertThat(configCaptor.getValue().getCurrencyCode()).isEqualTo("CNY");
    }

    /** 已初始化租户再次调用不得重复写入。 */
    @Test
    void shouldBeIdempotentWhenDefaultsExist() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 23L, "buyer"));
        ProcTenantConfig existingConfig = new ProcTenantConfig();
        existingConfig.setTenantId(23L);
        when(configMapper.selectOne(any())).thenReturn(existingConfig);

        new ProcTenantInitializerImpl(configMapper, categoryMapper).ensureInitialized();

        verify(categoryMapper, never()).insert(any(ProcMaterialCategory.class));
        verify(configMapper, never()).insert(any(ProcTenantConfig.class));
    }

    /** 初始化门闩已存在时，即使预置品类已被删除也不得自动复活。 */
    @Test
    void shouldNotRestoreDeletedDefaultCategoryAfterInitialization() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 23L, "buyer"));
        ProcTenantConfig existingConfig = new ProcTenantConfig();
        existingConfig.setTenantId(23L);
        when(configMapper.selectOne(any())).thenReturn(existingConfig);

        new ProcTenantInitializerImpl(configMapper, categoryMapper).ensureInitialized();

        verifyNoInteractions(categoryMapper);
        verify(configMapper, never()).insert(any(ProcTenantConfig.class));
    }

    /** 并发初始化唯一键失败方必须当前读观察胜者并直接返回，不能重复播种。 */
    @Test
    void shouldObserveConcurrentInitializerWinnerWithoutSeedingAgain() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 23L, "buyer"));
        ProcTenantConfig existingConfig = new ProcTenantConfig();
        existingConfig.setTenantId(23L);
        when(configMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("concurrent tenant initialization"))
                .when(configMapper).insert(any(ProcTenantConfig.class));
        when(configMapper.selectForUpdateByTenant(23L)).thenReturn(existingConfig);

        new ProcTenantInitializerImpl(configMapper, categoryMapper).ensureInitialized();

        verify(configMapper).selectForUpdateByTenant(23L);
        verifyNoInteractions(categoryMapper);
    }

    private static void initialize(Class<?> entityType, String mapperName) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "tenant-init-" + mapperName);
        assistant.setCurrentNamespace("com.omni.procurement.mapper." + mapperName);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
