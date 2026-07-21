package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.srm.entity.SrmEvaluationDimension;
import com.omni.srm.entity.SrmEvaluationTemplate;
import com.omni.srm.mapper.SrmEvaluationDimensionMapper;
import com.omni.srm.mapper.SrmEvaluationTemplateMapper;
import com.omni.srm.security.SrmTenantContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SRM 租户默认模板初始化测试。 */
@ExtendWith(MockitoExtension.class)
class SrmTenantInitializerImplTest {

    @Mock private SrmEvaluationTemplateMapper templateMapper;
    @Mock private SrmEvaluationDimensionMapper dimensionMapper;

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant templateAssistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "srm-template-test");
        templateAssistant.setCurrentNamespace("com.omni.srm.mapper.SrmEvaluationTemplateMapper");
        TableInfoHelper.initTableInfo(templateAssistant, SrmEvaluationTemplate.class);
        MapperBuilderAssistant dimensionAssistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "srm-dimension-test");
        dimensionAssistant.setCurrentNamespace("com.omni.srm.mapper.SrmEvaluationDimensionMapper");
        TableInfoHelper.initTableInfo(dimensionAssistant, SrmEvaluationDimension.class);
    }

    /** 清理租户上下文。 */
    @AfterEach
    void clearContext() {
        SrmTenantContext.clear();
    }

    /** 新租户必须自动获得四个总权重为一百的默认评估维度。 */
    @Test
    void shouldCreateDefaultTemplateAndDimensionsForNewTenant() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        when(templateMapper.insert(any(SrmEvaluationTemplate.class))).thenAnswer(invocation -> {
            SrmEvaluationTemplate template = invocation.getArgument(0);
            template.setId(99L);
            return 1;
        });
        SrmTenantInitializerImpl initializer = new SrmTenantInitializerImpl(templateMapper, dimensionMapper);

        Long templateId = initializer.ensureInitialized();

        assertThat(templateId).isEqualTo(99L);
        ArgumentCaptor<SrmEvaluationTemplate> templateCaptor =
                ArgumentCaptor.forClass(SrmEvaluationTemplate.class);
        verify(templateMapper).insert(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getTenantId()).isEqualTo(3L);
        assertThat(templateCaptor.getValue().getDefaultFlag()).isTrue();
        assertThat(templateCaptor.getValue().getStatus()).isEqualTo(1);

        ArgumentCaptor<SrmEvaluationDimension> dimensionCaptor =
                ArgumentCaptor.forClass(SrmEvaluationDimension.class);
        verify(dimensionMapper, times(4)).insert(dimensionCaptor.capture());
        assertThat(dimensionCaptor.getAllValues())
                .extracting(SrmEvaluationDimension::getIndicatorName)
                .containsExactly("质量", "交期", "价格", "服务");
        assertThat(dimensionCaptor.getAllValues().stream()
                .map(SrmEvaluationDimension::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
        assertThat(dimensionCaptor.getAllValues())
                .allSatisfy(dimension -> {
                    assertThat(dimension.getTenantId()).isEqualTo(3L);
                    assertThat(dimension.getTemplateId()).isEqualTo(99L);
                    assertThat(dimension.getStatus()).isEqualTo(1);
                    assertThat(dimension.getDeleted()).isZero();
                });
        verify(templateMapper).update(isNull(), any());
        verify(dimensionMapper).update(isNull(), any());
    }

    /** 已初始化租户仍需收敛为唯一默认模板和固定四个启用维度。 */
    @Test
    void shouldReconcileExistingTenantToSingleDefaultAndFourDimensions() {
        SrmTenantContext.set(new SrmTenantContext.RequestIdentity(7L, 3L, "buyer"));
        SrmEvaluationTemplate template = new SrmEvaluationTemplate();
        template.setId(99L);
        template.setTenantId(3L);
        template.setName("默认供应商评估模板");
        template.setStatus(1);
        template.setDefaultFlag(true);
        template.setVersion(0);
        template.setDeleted(0);
        when(templateMapper.selectOne(any())).thenReturn(template);
        when(dimensionMapper.selectOne(any())).thenReturn(
                dimension("质量", "30.00", 10),
                dimension("交期", "30.00", 20),
                dimension("价格", "20.00", 30),
                dimension("服务", "20.00", 40));
        SrmTenantInitializerImpl initializer = new SrmTenantInitializerImpl(templateMapper, dimensionMapper);

        assertThat(initializer.ensureInitialized()).isEqualTo(99L);

        verify(templateMapper, never()).insert(any(SrmEvaluationTemplate.class));
        verify(dimensionMapper, never()).insert(any(SrmEvaluationDimension.class));
        verify(templateMapper).update(isNull(), any());
        verify(dimensionMapper).update(isNull(), any());
    }

    private SrmEvaluationDimension dimension(String name, String weight, int sort) {
        SrmEvaluationDimension dimension = new SrmEvaluationDimension();
        dimension.setId(Long.valueOf(sort));
        dimension.setTenantId(3L);
        dimension.setTemplateId(99L);
        dimension.setIndicatorName(name);
        dimension.setWeight(new BigDecimal(weight));
        dimension.setSort(sort);
        dimension.setStatus(1);
        dimension.setDeleted(0);
        return dimension;
    }
}
