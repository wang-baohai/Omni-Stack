package com.omni.workflow.service.impl;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.tenant.TenantModuleProvisioner;
import com.omni.common.core.tenant.TenantProvisionContracts.ProvisionRequestedEvent;
import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.dto.CreateModelRequest;
import com.omni.workflow.dto.SaveDraftRequest;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.entity.WfProcessModelVersion;
import com.omni.workflow.mapper.WfProcessModelMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import com.omni.workflow.service.WorkflowModelService;
import lombok.RequiredArgsConstructor;

/**
 * Workflow 租户模型目录初始化器。
 *
 * <p>从默认租户复制每个正式模型的 BPMN 草稿，并通过既有校验与发布服务部署到目标租户。</p>
 */
@Component
@RequiredArgsConstructor
public class WorkflowTenantModuleProvisioner implements TenantModuleProvisioner {

    private static final Long TEMPLATE_TENANT_ID = 1L;
    private static final String SYSTEM_OPERATOR = "tenant-provisioning";

    private final WfProcessModelMapper modelMapper;
    private final WfProcessModelVersionMapper versionMapper;
    private final WorkflowModelService workflowModelService;

    /** {@inheritDoc} */
    @Override
    public String moduleId() {
        return "workflow";
    }

    /** {@inheritDoc} */
    @Override
    public void provision(ProvisionRequestedEvent event) {
        if (TEMPLATE_TENANT_ID.equals(event.tenantId())) {
            return;
        }
        List<WorkflowModelSeed> seeds = loadTemplateSeeds();
        TenantInfoHolder.setTenantId(String.valueOf(event.tenantId()));
        try {
            for (WorkflowModelSeed seed : seeds) {
                provisionModel(event.tenantId(), seed);
            }
        } finally {
            TenantInfoHolder.clear();
        }
    }

    private List<WorkflowModelSeed> loadTemplateSeeds() {
        TenantInfoHolder.setTenantId(String.valueOf(TEMPLATE_TENANT_ID));
        try {
            List<WfProcessModel> templates = modelMapper.selectList(
                    new LambdaQueryWrapper<WfProcessModel>()
                            .eq(WfProcessModel::getTenantId, TEMPLATE_TENANT_ID)
                            .eq(WfProcessModel::getStatus, 1)
                            .orderByAsc(WfProcessModel::getId));
            if (templates.isEmpty()) {
                throw new IllegalStateException("默认租户没有可复制的 Workflow 模型");
            }
            return templates.stream().map(template -> {
                WfProcessModelVersion version = resolveTemplateVersion(template);
                return new WorkflowModelSeed(
                        template.getModelKey(),
                        template.getModelName(),
                        template.getCategory(),
                        designerJson(version),
                        version.getBpmnXml());
            }).toList();
        } finally {
            TenantInfoHolder.clear();
        }
    }

    private void provisionModel(Long tenantId, WorkflowModelSeed seed) {
        WfProcessModel target = findModel(tenantId, seed.modelKey());
        if (target == null) {
            CreateModelRequest createRequest = new CreateModelRequest();
            createRequest.setModelKey(seed.modelKey());
            createRequest.setModelName(seed.modelName());
            createRequest.setCategory(seed.category());
            createRequest.setDesignerJson(seed.designerJson());
            target = workflowModelService.createModel(createRequest, tenantId, SYSTEM_OPERATOR);
        }
        if (isPublishedAndStartable(target)) {
            return;
        }
        SaveDraftRequest saveRequest = new SaveDraftRequest();
        saveRequest.setDesignerJson(seed.designerJson());
        saveRequest.setBpmnXml(seed.bpmnXml());
        saveRequest.setModelName(seed.modelName());
        saveRequest.setCategory(seed.category());
        workflowModelService.saveDraft(target.getId(), saveRequest, SYSTEM_OPERATOR);
        workflowModelService.publishModel(target.getId(), SYSTEM_OPERATOR);
    }

    private WfProcessModelVersion resolveTemplateVersion(WfProcessModel template) {
        Long versionId = template.getCurrentPublishedVersionId() != null
                ? template.getCurrentPublishedVersionId() : template.getCurrentDraftVersionId();
        WfProcessModelVersion version = versionId == null ? null : versionMapper.selectById(versionId);
        if (version == null || !StringUtils.hasText(version.getBpmnXml())) {
            throw new IllegalStateException("默认 Workflow 模型缺少可发布 BPMN: " + template.getModelKey());
        }
        return version;
    }

    private WfProcessModel findModel(Long tenantId, String modelKey) {
        return modelMapper.selectOne(new LambdaQueryWrapper<WfProcessModel>()
                .eq(WfProcessModel::getTenantId, tenantId)
                .eq(WfProcessModel::getModelKey, modelKey));
    }

    private boolean isPublishedAndStartable(WfProcessModel model) {
        if (model.getCurrentPublishedVersionId() == null) {
            return false;
        }
        WfProcessModelVersion version = versionMapper.selectById(model.getCurrentPublishedVersionId());
        return version != null
                && "PUBLISHED".equals(version.getStatus())
                && StringUtils.hasText(version.getProcessDefinitionId());
    }

    private static String designerJson(WfProcessModelVersion version) {
        return StringUtils.hasText(version.getDesignerJson()) ? version.getDesignerJson() : "{}";
    }

    /** Workflow 默认模型不可变快照。 */
    private record WorkflowModelSeed(
            String modelKey,
            String modelName,
            String category,
            String designerJson,
            String bpmnXml) {
    }
}
