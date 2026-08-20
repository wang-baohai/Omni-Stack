package com.omni.workflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.dto.CreateModelRequest;
import com.omni.workflow.dto.SaveDraftRequest;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.entity.WfProcessModelVersion;
import com.omni.workflow.mapper.WfProcessModelMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import com.omni.workflow.service.WorkflowModelService;

/**
 * Workflow 租户模型初始化测试。
 */
class WorkflowTenantModuleProvisionerTest {

    /** 初始化 MyBatis-Plus Lambda 元数据。 */
    @BeforeAll
    static void initializeTableMetadata() {
        initialize(WfProcessModel.class, "WfProcessModelMapper");
        initialize(WfProcessModelVersion.class, "WfProcessModelVersionMapper");
    }

    @Test
    void shouldSnapshotTemplateThenPublishUnderTargetTenantContext() {
        WfProcessModelMapper modelMapper = mock(WfProcessModelMapper.class);
        WfProcessModelVersionMapper versionMapper = mock(WfProcessModelVersionMapper.class);
        WorkflowModelService modelService = mock(WorkflowModelService.class);
        WfProcessModel template = new WfProcessModel();
        template.setId(1L);
        template.setTenantId(1L);
        template.setModelKey("purchase-approval");
        template.setModelName("采购审批");
        template.setCategory("purchase");
        template.setCurrentPublishedVersionId(11L);
        WfProcessModelVersion templateVersion = new WfProcessModelVersion();
        templateVersion.setId(11L);
        templateVersion.setBpmnXml("<definitions />");
        templateVersion.setDesignerJson("{\"nodes\":[]}");
        WfProcessModel target = new WfProcessModel();
        target.setId(22L);
        target.setTenantId(9L);
        target.setModelKey("purchase-approval");
        when(modelMapper.selectList(any())).thenReturn(List.of(template));
        when(versionMapper.selectById(11L)).thenReturn(templateVersion);
        when(modelMapper.selectOne(any())).thenReturn(null);
        when(modelService.createModel(any(), eq(9L), eq("tenant-provisioning"))).thenReturn(target);
        doAnswer(invocation -> {
            assertThat(TenantInfoHolder.getTenantId()).isEqualTo("9");
            return null;
        }).when(modelService).publishModel(22L, "tenant-provisioning");

        new WorkflowTenantModuleProvisioner(modelMapper, versionMapper, modelService)
                .provision(request());

        ArgumentCaptor<CreateModelRequest> create = ArgumentCaptor.forClass(CreateModelRequest.class);
        verify(modelService).createModel(create.capture(), eq(9L), eq("tenant-provisioning"));
        assertThat(create.getValue().getModelKey()).isEqualTo("purchase-approval");
        ArgumentCaptor<SaveDraftRequest> save = ArgumentCaptor.forClass(SaveDraftRequest.class);
        verify(modelService).saveDraft(eq(22L), save.capture(), eq("tenant-provisioning"));
        assertThat(save.getValue().getBpmnXml()).isEqualTo("<definitions />");
        verify(modelService).publishModel(22L, "tenant-provisioning");
        assertThat(TenantInfoHolder.getTenantId()).isNull();
    }

    private static ProvisionRequestedEvent request() {
        return new ProvisionRequestedEvent(
                "event-1", "request-1", 9L, "tenant-9", "租户 9",
                List.of("workflow"), Instant.now());
    }

    private static void initialize(Class<?> entityType, String mapperName) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "workflow-provision-" + mapperName);
        assistant.setCurrentNamespace("com.omni.workflow.mapper." + mapperName);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
