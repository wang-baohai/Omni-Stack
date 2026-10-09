package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.procurement.domain.MaterialDomainPolicy;
import com.omni.procurement.dto.MaterialRequests;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcMaterialMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.procurement.service.ProcTenantInitializer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 物料服务租户、稳定编码与唯一冲突测试。 */
@ExtendWith(MockitoExtension.class)
class MaterialServiceImplTest {

    @Mock private ProcTenantInitializer tenantInitializer;
    @Mock private ProcMaterialCategoryMapper categoryMapper;
    @Mock private ProcMaterialMapper materialMapper;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(ProcMaterial.class, "ProcMaterialMapper");
        initialize(ProcMaterialCategory.class, "ProcMaterialCategoryMapper");
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        ServiceIdentityContext.clear();
    }

    /** 数据库并发唯一冲突必须稳定翻译为 409，并保持当前租户和规范化编码。 */
    @Test
    void shouldTranslateConcurrentMaterialCodeConflict() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "buyer"));
        ProcMaterialCategory category = activeCategory(100L, 31L);
        when(categoryMapper.selectForUpdate(31L, 100L)).thenReturn(category);
        when(materialMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate material code"))
                .when(materialMapper).insert(any(ProcMaterial.class));
        MaterialServiceImpl service = new MaterialServiceImpl(tenantInitializer, categoryMapper, materialMapper);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.create(createMaterialRequest()));

        assertThat(exception.getCode()).isEqualTo(409);
        ArgumentCaptor<ProcMaterial> captor = ArgumentCaptor.forClass(ProcMaterial.class);
        verify(materialMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(31L);
        assertThat(captor.getValue().getMaterialCode()).isEqualTo("IT-NB-001");
        assertThat(captor.getValue().getUnit()).isEqualTo("EA");
    }

    /** 更新请求不得暴露物料编码，从契约层保证创建后不可修改。 */
    @Test
    void shouldNotExposeMaterialCodeOnUpdateRequest() {
        assertThat(Arrays.stream(MaterialRequests.UpdateMaterialRequest.class.getDeclaredFields())
                .map(Field::getName)).doesNotContain("materialCode");
    }

    /** 请购选择必须同时要求活动物料和活动品类。 */
    @Test
    void shouldRejectInactiveMaterialForRequisition() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "buyer"));
        ProcMaterial material = new ProcMaterial();
        material.setId(1L);
        material.setTenantId(31L);
        material.setCategoryId(100L);
        material.setStatus(MaterialDomainPolicy.INACTIVE);
        when(materialMapper.selectOne(any())).thenReturn(material);
        MaterialServiceImpl service = new MaterialServiceImpl(tenantInitializer, categoryMapper, materialMapper);

        assertThatThrownBy(() -> service.requireActiveForRequisition(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(409);
    }

    /** 品类删除必须以 tenant、id、version、deleted 条件更新并原子递增版本。 */
    @Test
    void shouldDeleteCategoryWithOptimisticCondition() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "buyer"));
        ProcMaterialCategory category = activeCategory(100L, 31L);
        category.setVersion(4);
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(categoryMapper.selectForUpdate(31L, 100L)).thenReturn(category);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(materialMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.update(
                org.mockito.ArgumentMatchers.<ProcMaterialCategory>isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<ProcMaterialCategory>>any()))
                .thenAnswer(invocation -> {
                    LambdaUpdateWrapper<ProcMaterialCategory> update = invocation.getArgument(1);
                    assertThat(update.getSqlSegment()).contains("tenant_id").contains("version").contains("deleted");
                    assertThat(update.getSqlSet()).contains("version = version + 1").contains("deleted");
                    return 1;
                });
        MaterialServiceImpl service = new MaterialServiceImpl(tenantInitializer, categoryMapper, materialMapper);

        service.deleteCategory(100L, 4);

        verify(categoryMapper).update(
                org.mockito.ArgumentMatchers.<ProcMaterialCategory>isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<ProcMaterialCategory>>any());
    }

    /** 品类移动必须按 ID 升序锁定新父、旧父和当前品类，阻断停用或删除穿透。 */
    @Test
    void shouldLockOldAndNewCategoryDependenciesInAscendingOrder() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "buyer"));
        ProcMaterialCategory current = activeCategory(50L, 31L);
        current.setParentId(30L);
        current.setVersion(2);
        ProcMaterialCategory oldParent = activeCategory(30L, 31L);
        ProcMaterialCategory newParent = activeCategory(10L, 31L);
        when(categoryMapper.selectOne(any())).thenReturn(current);
        when(categoryMapper.selectForUpdate(31L, 10L)).thenReturn(newParent);
        when(categoryMapper.selectForUpdate(31L, 30L)).thenReturn(oldParent);
        when(categoryMapper.selectForUpdate(31L, 50L)).thenReturn(current);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.update(
                org.mockito.ArgumentMatchers.<ProcMaterialCategory>isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<ProcMaterialCategory>>any())).thenReturn(1);
        MaterialServiceImpl service = new MaterialServiceImpl(tenantInitializer, categoryMapper, materialMapper);
        MaterialRequests.UpdateCategoryRequest request = updateCategoryRequest(10L, 2);

        service.updateCategory(50L, request);

        InOrder order = inOrder(categoryMapper);
        order.verify(categoryMapper).selectForUpdate(31L, 10L);
        order.verify(categoryMapper).selectForUpdate(31L, 30L);
        order.verify(categoryMapper).selectForUpdate(31L, 50L);
    }

    /** 物料变更必须先按升序锁定旧、新品类，再锁定物料当前行。 */
    @Test
    void shouldLockOldAndNewMaterialCategoriesBeforeMaterialRow() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(7L, 31L, "buyer"));
        ProcMaterial material = activeMaterial(1L, 31L, 30L);
        ProcMaterialCategory oldCategory = activeCategory(30L, 31L);
        ProcMaterialCategory newCategory = activeCategory(20L, 31L);
        when(materialMapper.selectOne(any())).thenReturn(material);
        when(categoryMapper.selectForUpdate(31L, 20L)).thenReturn(newCategory);
        when(categoryMapper.selectForUpdate(31L, 30L)).thenReturn(oldCategory);
        when(materialMapper.selectForUpdate(31L, 1L)).thenReturn(material);
        when(materialMapper.update(
                org.mockito.ArgumentMatchers.<ProcMaterial>isNull(),
                org.mockito.ArgumentMatchers.<Wrapper<ProcMaterial>>any())).thenReturn(1);
        when(categoryMapper.selectOne(any())).thenReturn(newCategory);
        MaterialServiceImpl service = new MaterialServiceImpl(tenantInitializer, categoryMapper, materialMapper);

        service.update(1L, updateMaterialRequest(20L, 4));

        InOrder order = inOrder(categoryMapper, materialMapper);
        order.verify(categoryMapper).selectForUpdate(31L, 20L);
        order.verify(categoryMapper).selectForUpdate(31L, 30L);
        order.verify(materialMapper).selectForUpdate(31L, 1L);
    }

    private MaterialRequests.CreateMaterialRequest createMaterialRequest() {
        MaterialRequests.CreateMaterialRequest request = new MaterialRequests.CreateMaterialRequest();
        request.setCategoryId(100L);
        request.setMaterialCode("it-nb-001");
        request.setMaterialName("商务笔记本");
        request.setUnit("ea");
        request.setAssetManaged(true);
        request.setStatus(MaterialDomainPolicy.ACTIVE);
        return request;
    }

    private MaterialRequests.UpdateCategoryRequest updateCategoryRequest(Long parentId, int version) {
        MaterialRequests.UpdateCategoryRequest request = new MaterialRequests.UpdateCategoryRequest();
        request.setVersion(version);
        request.setParentId(parentId);
        request.setCategoryName("终端设备");
        request.setSort(20);
        request.setStatus(1);
        return request;
    }

    private MaterialRequests.UpdateMaterialRequest updateMaterialRequest(Long categoryId, int version) {
        MaterialRequests.UpdateMaterialRequest request = new MaterialRequests.UpdateMaterialRequest();
        request.setVersion(version);
        request.setCategoryId(categoryId);
        request.setMaterialName("商务笔记本");
        request.setUnit("EA");
        request.setAssetManaged(true);
        request.setStatus(MaterialDomainPolicy.ACTIVE);
        return request;
    }

    private ProcMaterial activeMaterial(Long id, Long tenantId, Long categoryId) {
        ProcMaterial material = new ProcMaterial();
        material.setId(id);
        material.setTenantId(tenantId);
        material.setCategoryId(categoryId);
        material.setStatus(MaterialDomainPolicy.ACTIVE);
        material.setVersion(4);
        return material;
    }

    private ProcMaterialCategory activeCategory(Long id, Long tenantId) {
        ProcMaterialCategory category = new ProcMaterialCategory();
        category.setId(id);
        category.setTenantId(tenantId);
        category.setParentId(0L);
        category.setCategoryCode("IT_DEVICE");
        category.setStatus(1);
        return category;
    }

    private static void initialize(Class<?> entityType, String mapperName) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "material-service-" + mapperName);
        assistant.setCurrentNamespace("com.omni.procurement.mapper." + mapperName);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
