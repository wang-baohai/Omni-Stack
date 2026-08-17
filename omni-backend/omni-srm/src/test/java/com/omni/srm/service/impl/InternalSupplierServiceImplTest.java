package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.InternalSupplierSummary;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.security.SrmDataScopeContext;
import com.omni.srm.security.SrmTenantContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 内部供应商查询的租户隔离与批量约束测试。 */
@ExtendWith(MockitoExtension.class)
class InternalSupplierServiceImplTest {

    @Mock private SrmSupplierMapper supplierMapper;
    @InjectMocks private InternalSupplierServiceImpl service;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "internal-supplier-test");
        assistant.setCurrentNamespace("com.omni.srm.mapper.SrmSupplierMapper");
        TableInfoHelper.initTableInfo(assistant, SrmSupplier.class);
    }

    /** 清理测试线程上下文。 */
    @AfterEach
    void clearContext() {
        SrmDataScopeContext.clear();
        SrmTenantContext.clear();
    }

    /** 批量查询必须去重、保持输入顺序并过滤其他租户数据。 */
    @Test
    void shouldDeduplicatePreserveOrderAndIsolateTenant() {
        SrmSupplier first = supplier(1L, 7L, "S-001");
        SrmSupplier third = supplier(3L, 7L, "S-003");
        SrmSupplier foreign = supplier(3L, 8L, "FOREIGN");
        when(supplierMapper.selectList(ArgumentMatchers.any())).thenAnswer(invocation -> {
            assertThat(SrmTenantContext.requireTenantId()).isEqualTo(7L);
            assertThat(SrmDataScopeContext.require().tenantId()).isEqualTo(7L);
            return List.of(first, foreign, third);
        });

        List<InternalSupplierSummary> result = service.batch(7L, List.of(3L, 1L, 3L));

        assertThat(result).extracting(InternalSupplierSummary::getId).containsExactly(3L, 1L);
        assertThat(result).extracting(InternalSupplierSummary::getSupplierNo)
                .containsExactly("S-003", "S-001");
        assertContextsCleared();
    }

    /** 查询异常时仍必须清理租户与数据范围上下文。 */
    @Test
    void shouldClearContextsWhenMapperFails() {
        when(supplierMapper.selectList(ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.batch(7L, List.of(1L)))
                .isInstanceOf(IllegalStateException.class);
        assertContextsCleared();
    }

    /** 单次批量查询去重后不得超过一百个 ID。 */
    @Test
    void shouldRejectMoreThanOneHundredDistinctIds() {
        List<Long> supplierIds = LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatThrownBy(() -> service.batch(7L, supplierIds))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        verifyNoInteractions(supplierMapper);
    }

    /** 空列表和非法 ID 必须在访问数据库前被拒绝。 */
    @Test
    void shouldRejectInvalidSupplierIds() {
        assertThatThrownBy(() -> service.batch(7L, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        assertThatThrownBy(() -> service.batch(7L, java.util.Arrays.asList(1L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        verifyNoInteractions(supplierMapper);
    }

    private SrmSupplier supplier(Long id, Long tenantId, String supplierNo) {
        SrmSupplier supplier = new SrmSupplier();
        supplier.setId(id);
        supplier.setTenantId(tenantId);
        supplier.setSupplierNo(supplierNo);
        supplier.setName(supplierNo);
        supplier.setDeleted(0);
        return supplier;
    }

    private void assertContextsCleared() {
        assertThat(SrmDataScopeContext.get()).isNull();
        assertThatThrownBy(SrmTenantContext::require).isInstanceOf(BusinessException.class);
    }
}
