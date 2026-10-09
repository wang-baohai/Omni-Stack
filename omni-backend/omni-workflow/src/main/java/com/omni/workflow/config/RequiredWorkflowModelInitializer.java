package com.omni.workflow.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.entity.WfProcessModelVersion;
import com.omni.workflow.mapper.WfProcessModelMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import com.omni.workflow.service.WorkflowModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 必需业务流程模型启动初始化器。
 * <p>
 * 对默认 SQL 中的供应商准入、采购审批、资产调拨和资产处置模型执行幂等分类校正与发布，
 * 避免新环境需要人工进入设计器发布后业务入口才能使用。
 * </p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequiredWorkflowModelInitializer implements ApplicationRunner {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final Map<String, String> REQUIRED_MODELS = Map.of(
            "supplier-onboarding", "SRM_SUPPLIER_ONBOARDING",
            "procurement-approval", "purchase",
            "asset-transfer", "ASSET_TRANSFER",
            "asset-disposal", "ASSET_DISPOSAL");

    private final WfProcessModelMapper modelMapper;
    private final WfProcessModelVersionMapper versionMapper;
    private final WorkflowModelService workflowModelService;

    /**
     * 校正并发布必需模型；默认租户缺失模型或发布失败时终止启动。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        List<WfProcessModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<WfProcessModel>()
                        .eq(WfProcessModel::getStatus, 1)
                        .in(WfProcessModel::getModelKey, REQUIRED_MODELS.keySet()));
        assertDefaultModelsExist(models);
        for (WfProcessModel model : models) {
            ensureStartable(model);
        }
    }

    private void assertDefaultModelsExist(List<WfProcessModel> models) {
        List<String> existingKeys = models.stream()
                .filter(model -> DEFAULT_TENANT_ID.equals(model.getTenantId()))
                .map(WfProcessModel::getModelKey)
                .toList();
        List<String> missingKeys = REQUIRED_MODELS.keySet().stream()
                .filter(key -> !existingKeys.contains(key))
                .toList();
        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException("默认租户缺少必需流程模型: " + String.join(", ", missingKeys));
        }
    }

    private void ensureStartable(WfProcessModel model) {
        String expectedCategory = REQUIRED_MODELS.get(model.getModelKey());
        boolean categoryChanged = !expectedCategory.equals(model.getCategory());
        if (categoryChanged) {
            model.setCategory(expectedCategory);
            model.setUpdateBy("system");
            modelMapper.updateById(model);
        }

        WfProcessModelVersion published = model.getCurrentPublishedVersionId() == null
                ? null : versionMapper.selectById(model.getCurrentPublishedVersionId());
        boolean startable = !categoryChanged
                && published != null
                && "PUBLISHED".equals(published.getStatus())
                && published.getProcessDefinitionId() != null
                && !published.getProcessDefinitionId().isBlank();
        if (startable) {
            return;
        }

        try {
            workflowModelService.publishModel(model.getId(), "system");
            log.info("必需流程模型已自动发布: tenantId={}, modelKey={}, category={}",
                    model.getTenantId(), model.getModelKey(), expectedCategory);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("必需流程模型自动发布失败: tenantId="
                    + model.getTenantId() + ", modelKey=" + model.getModelKey(), exception);
        }
    }
}
