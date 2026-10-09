package com.omni.srm.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.srm.entity.SrmRiskCriterion;
import com.omni.srm.entity.SrmRiskIndicatorType;
import com.omni.srm.entity.SrmRiskScoreThreshold;
import com.omni.srm.mapper.SrmRiskCriterionMapper;
import com.omni.srm.mapper.SrmRiskIndicatorTypeMapper;
import com.omni.srm.mapper.SrmRiskScoreThresholdMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.srm.service.SrmTenantInitializer;

/**
 * SRM 租户风险目录初始化测试。
 */
class SrmTenantModuleProvisionerTest {

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(SrmRiskIndicatorType.class, "SrmRiskIndicatorTypeMapper");
        initialize(SrmRiskCriterion.class, "SrmRiskCriterionMapper");
        initialize(SrmRiskScoreThreshold.class, "SrmRiskScoreThresholdMapper");
    }

    @Test
    void shouldReadTemplateThenCloneRiskCatalogForTargetTenant() {
        SrmTenantInitializer initializer = mock(SrmTenantInitializer.class);
        SrmRiskIndicatorTypeMapper typeMapper = mock(SrmRiskIndicatorTypeMapper.class);
        SrmRiskCriterionMapper criterionMapper = mock(SrmRiskCriterionMapper.class);
        SrmRiskScoreThresholdMapper thresholdMapper = mock(SrmRiskScoreThresholdMapper.class);
        SrmRiskIndicatorType type = type(1L, "FINANCIAL");
        SrmRiskCriterion criterion = criterion(2L, 1L, "资金充裕");
        SrmRiskScoreThreshold threshold = threshold(3L, "GREEN");
        when(typeMapper.selectList(any())).thenReturn(List.of(type));
        when(criterionMapper.selectList(any())).thenReturn(List.of(criterion));
        when(thresholdMapper.selectList(any())).thenReturn(List.of(threshold));
        when(typeMapper.selectOne(any())).thenReturn(null);
        when(criterionMapper.selectCount(any())).thenReturn(0L);
        when(thresholdMapper.selectCount(any())).thenReturn(0L);
        when(typeMapper.insert(any(SrmRiskIndicatorType.class))).thenAnswer(invocation -> {
            SrmRiskIndicatorType target = invocation.getArgument(0);
            target.setId(101L);
            return 1;
        });
        doAnswer(invocation -> {
            assertThat(ServiceIdentityContext.requireTenantId()).isEqualTo(9L);
            return 99L;
        }).when(initializer).ensureInitialized();

        new SrmTenantModuleProvisioner(
                initializer, typeMapper, criterionMapper, thresholdMapper).provision(request());

        ArgumentCaptor<SrmRiskIndicatorType> typeTarget =
                ArgumentCaptor.forClass(SrmRiskIndicatorType.class);
        verify(typeMapper).insert(typeTarget.capture());
        assertThat(typeTarget.getValue().getTenantId()).isEqualTo(9L);
        ArgumentCaptor<SrmRiskCriterion> criterionTarget =
                ArgumentCaptor.forClass(SrmRiskCriterion.class);
        verify(criterionMapper).insert(criterionTarget.capture());
        assertThat(criterionTarget.getValue().getIndicatorTypeId()).isEqualTo(101L);
        ArgumentCaptor<SrmRiskScoreThreshold> thresholdTarget =
                ArgumentCaptor.forClass(SrmRiskScoreThreshold.class);
        verify(thresholdMapper).insert(thresholdTarget.capture());
        assertThat(thresholdTarget.getValue().getTenantId()).isEqualTo(9L);
        assertThatThrownBy(ServiceIdentityContext::requireTenantId)
                .hasMessageContaining("缺少服务请求身份上下文");
    }

    private static SrmRiskIndicatorType type(Long id, String code) {
        SrmRiskIndicatorType type = new SrmRiskIndicatorType();
        type.setId(id);
        type.setTenantId(1L);
        type.setTypeCode(code);
        type.setTypeName("财务风险");
        type.setSort(1);
        type.setAutoCalc(0);
        type.setStatus(1);
        type.setDeleted(0);
        return type;
    }

    private static SrmRiskCriterion criterion(Long id, Long typeId, String label) {
        SrmRiskCriterion criterion = new SrmRiskCriterion();
        criterion.setId(id);
        criterion.setTenantId(1L);
        criterion.setIndicatorTypeId(typeId);
        criterion.setCriterionLabel(label);
        criterion.setScore(1);
        criterion.setRiskLevel("GREEN");
        criterion.setSort(1);
        criterion.setStatus(1);
        criterion.setDeleted(0);
        return criterion;
    }

    private static SrmRiskScoreThreshold threshold(Long id, String level) {
        SrmRiskScoreThreshold threshold = new SrmRiskScoreThreshold();
        threshold.setId(id);
        threshold.setTenantId(1L);
        threshold.setRiskLevel(level);
        threshold.setMinScore(5);
        threshold.setMaxScore(8);
        threshold.setDeleted(0);
        return threshold;
    }

    private static ProvisionRequestedEvent request() {
        return new ProvisionRequestedEvent(
                "event-1", "request-1", 9L, "tenant-9", "租户 9", List.of("srm"), Instant.now());
    }

    private static void initialize(Class<?> entityType, String mapperName) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "srm-provision-" + mapperName);
        assistant.setCurrentNamespace("com.omni.srm.mapper." + mapperName);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
