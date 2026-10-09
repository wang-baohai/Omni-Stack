package com.omni.workflow.service.impl;

import com.omni.common.core.result.BusinessException;
import com.omni.workflow.dto.SaveDraftRequest;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.entity.WfProcessModelVersion;
import com.omni.workflow.engine.BpmnXmlValidator;
import com.omni.workflow.mapper.WfProcessModelMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程模型版本保护测试。
 * <p>验证 saveDraft 不覆盖已发布版本、publish 拒绝非 DRAFT 指针。</p>
 *
 * @author Omni-Stack Team
 */
@ExtendWith(MockitoExtension.class)
class WorkflowModelServiceImplTest {

    @Mock private WfProcessModelMapper modelMapper;
    @Mock private WfProcessModelVersionMapper versionMapper;
    @Mock private RepositoryService repositoryService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private BpmnXmlValidator bpmnXmlValidator;

    private WorkflowModelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowModelServiceImpl(
                modelMapper, versionMapper, repositoryService, jdbcTemplate, bpmnXmlValidator);
    }

    /** 草稿指针指向 PUBLISHED 版本时必须创建独立新草稿，不能原地覆盖已发布内容。 */
    @Test
    void shouldCreateNewDraftWhenCurrentDraftPointsToPublishedVersion() {
        WfProcessModel model = model(5L, 5L);
        when(modelMapper.selectById(5L)).thenReturn(model);

        WfProcessModelVersion publishedDraft = version(5L, "PUBLISHED");
        publishedDraft.setBpmnXml("<old-published-xml/>");
        when(versionMapper.selectById(5L)).thenReturn(publishedDraft);
        when(versionMapper.insert(any(WfProcessModelVersion.class))).thenAnswer(invocation -> {
            WfProcessModelVersion v = invocation.getArgument(0);
            v.setId(99L);
            return 1;
        });
        when(versionMapper.updateById(any(WfProcessModelVersion.class))).thenReturn(1);
        when(modelMapper.updateById(any(WfProcessModel.class))).thenReturn(1);

        SaveDraftRequest request = new SaveDraftRequest();
        request.setBpmnXml("<new-draft-xml/>");

        WfProcessModelVersion result = service.saveDraft(5L, request, "admin");

        // 必须插入新草稿行并更新模型指针
        ArgumentCaptor<WfProcessModelVersion> insertCaptor = ArgumentCaptor.forClass(WfProcessModelVersion.class);
        verify(versionMapper).insert(insertCaptor.capture());
        assertEquals("DRAFT", insertCaptor.getValue().getStatus());
        ArgumentCaptor<WfProcessModel> modelCaptor = ArgumentCaptor.forClass(WfProcessModel.class);
        verify(modelMapper).updateById(modelCaptor.capture());
        assertEquals(99L, modelCaptor.getValue().getCurrentDraftVersionId());
        assertEquals(99L, result.getId());
        assertEquals("DRAFT", result.getStatus());
    }

    /** 发布时草稿指针指向非 DRAFT 版本必须拒绝，防止重复部署。 */
    @Test
    void shouldRejectPublishWhenDraftPointerIsNotInDraftStatus() {
        WfProcessModel model = model(5L, 5L);
        WfProcessModelVersion publishedVersion = version(5L, "PUBLISHED");
        publishedVersion.setBpmnXml("<xml/>");
        // lockModelForUpdate 通过 jdbcTemplate 查询，返回模型行（三参数重载：sql, rowMapper, args）
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(java.util.List.of(model));
        // selectById 在草稿校验时返回 PUBLISHED 版本
        when(versionMapper.selectById(5L)).thenReturn(publishedVersion);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.publishModel(5L, "admin"));

        assertEquals("当前草稿指针指向非 DRAFT 版本(PUBLISHED)，请先通过 saveDraft 创建独立草稿",
                exception.getMessage());
        verify(repositoryService, never()).createDeployment();
    }

    private WfProcessModel model(Long id, Long currentDraftVersionId) {
        WfProcessModel model = new WfProcessModel();
        model.setId(id);
        model.setTenantId(1L);
        model.setModelKey("supplier-onboarding");
        model.setModelName("供应商准入审批");
        model.setCategory("SRM_SUPPLIER_ONBOARDING");
        model.setStatus(1);
        model.setCurrentDraftVersionId(currentDraftVersionId);
        model.setCurrentPublishedVersionId(currentDraftVersionId);
        return model;
    }

    private WfProcessModelVersion version(Long id, String status) {
        WfProcessModelVersion version = new WfProcessModelVersion();
        version.setId(id);
        version.setTenantId(1L);
        version.setModelId(5L);
        version.setVersion(2);
        version.setStatus(status);
        return version;
    }
}
