package com.omni.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.dto.*;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.entity.WfProcessModelVersion;
import com.omni.workflow.engine.BpmnXmlValidator;
import com.omni.workflow.mapper.WfProcessModelMapper;
import com.omni.workflow.mapper.WfProcessModelVersionMapper;
import com.omni.workflow.service.WorkflowModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程模型管理服务实现。
 * <p>
 * 实现模型创建、草稿保存、BPMN 校验、发布部署、版本管理等全流程。
 * 发布时使用 SELECT FOR UPDATE 锁定模型行防止并发冲突。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowModelServiceImpl implements WorkflowModelService {

    private final WfProcessModelMapper modelMapper;
    private final WfProcessModelVersionMapper versionMapper;
    private final RepositoryService repositoryService;
    private final JdbcTemplate jdbcTemplate;
    private final BpmnXmlValidator bpmnXmlValidator;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessModel createModel(CreateModelRequest request, Long tenantId, String userName) {
        // 校验 modelKey 唯一性
        LambdaQueryWrapper<WfProcessModel> keyCheck = new LambdaQueryWrapper<WfProcessModel>()
                .eq(WfProcessModel::getTenantId, tenantId)
                .eq(WfProcessModel::getModelKey, request.getModelKey());
        if (modelMapper.selectCount(keyCheck) > 0) {
            throw new BusinessException("模型标识已存在: " + request.getModelKey());
        }

        // 创建模型
        WfProcessModel model = new WfProcessModel();
        model.setTenantId(tenantId);
        model.setModelKey(request.getModelKey());
        model.setModelName(request.getModelName());
        model.setCategory(request.getCategory());
        model.setStatus(1);
        model.setCreateBy(userName);
        model.setUpdateBy(userName);
        model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(LocalDateTime.now());
        modelMapper.insert(model);

        // 创建初始草稿版本（version = 0）
        WfProcessModelVersion draft = new WfProcessModelVersion();
        draft.setTenantId(tenantId);
        draft.setModelId(model.getId());
        draft.setVersion(0);
        draft.setStatus("DRAFT");
        draft.setDesignerJson(request.getDesignerJson());
        draft.setCreateTime(LocalDateTime.now());
        draft.setUpdateTime(LocalDateTime.now());
        versionMapper.insert(draft);

        // 回写草稿版本 ID
        model.setCurrentDraftVersionId(draft.getId());
        modelMapper.updateById(model);

        log.info("流程模型创建: modelId={}, modelKey={}", model.getId(), model.getModelKey());
        return model;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessModelVersion saveDraft(Long modelId, SaveDraftRequest request, String userName) {
        WfProcessModel model = getModelOrThrow(modelId);

        WfProcessModelVersion draft = versionMapper.selectById(model.getCurrentDraftVersionId());
        if (draft == null) {
            throw new BusinessException("草稿版本不存在");
        }

        // 计算 SHA-256
        String xmlSha256 = null;
        if (request.getBpmnXml() != null && !request.getBpmnXml().isBlank()) {
            xmlSha256 = sha256(request.getBpmnXml());
        }

        // 更新草稿版本
        draft.setDesignerJson(request.getDesignerJson());
        draft.setBpmnXml(request.getBpmnXml());
        draft.setXmlSha256(xmlSha256);
        draft.setUpdateTime(LocalDateTime.now());
        versionMapper.updateById(draft);

        // 同步更新模型名称和分类
        boolean modelUpdated = false;
        if (request.getModelName() != null && !request.getModelName().isBlank()
                && !request.getModelName().equals(model.getModelName())) {
            model.setModelName(request.getModelName());
            modelUpdated = true;
        }
        if (request.getCategory() != null && !request.getCategory().equals(model.getCategory())) {
            model.setCategory(request.getCategory());
            modelUpdated = true;
        }
        if (modelUpdated) {
            model.setUpdateBy(userName);
            model.setUpdateTime(LocalDateTime.now());
            modelMapper.updateById(model);
        }

        log.info("草稿保存: modelId={}, versionId={}", modelId, draft.getId());
        return draft;
    }

    /** {@inheritDoc} */
    @Override
    public ValidateResult validateModel(Long modelId) {
        WfProcessModel model = getModelOrThrow(modelId);
        WfProcessModelVersion draft = versionMapper.selectById(model.getCurrentDraftVersionId());

        if (draft == null || draft.getBpmnXml() == null || draft.getBpmnXml().isBlank()) {
            return ValidateResult.builder()
                    .valid(false)
                    .errors(List.of("BPMN XML 为空，请先保存设计器内容"))
                    .warnings(Collections.emptyList())
                    .build();
        }

        return bpmnXmlValidator.validate(draft.getBpmnXml(), model.getModelKey());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishResult publishModel(Long modelId, String userName) {
        // 1. SELECT FOR UPDATE 锁定模型行
        WfProcessModel model = lockModelForUpdate(modelId);

        // 2. 获取草稿版本
        WfProcessModelVersion draft = versionMapper.selectById(model.getCurrentDraftVersionId());
        if (draft == null || draft.getBpmnXml() == null || draft.getBpmnXml().isBlank()) {
            throw new BusinessException("BPMN XML 为空，无法发布");
        }

        // 3. 校验
        ValidateResult validationResult = validateModel(modelId);
        if (!validationResult.isValid()) {
            // 标记为失败状态
            draft.setStatus("FAILED");
            draft.setUpdateTime(LocalDateTime.now());
            versionMapper.updateById(draft);
            throw new BusinessException("模型校验未通过: " + String.join("; ", validationResult.getErrors()));
        }

        // 4. 部署到 Flowable
        String tenantIdStr = TenantInfoHolder.getTenantId();
        String effectiveTenantId = tenantIdStr != null ? tenantIdStr : String.valueOf(model.getTenantId());

        // 部署前将 BPMN XML 的 targetNamespace 替换为模型分类（Flowable 用它作为流程定义 category）
        String bpmnXml = draft.getBpmnXml();
        if (model.getCategory() != null && !model.getCategory().isBlank()) {
            bpmnXml = bpmnXml.replaceAll("targetNamespace=\"[^\"]*\"",
                    "targetNamespace=\"" + model.getCategory() + "\"");
        }

        Deployment deployment;
        try {
            deployment = repositoryService.createDeployment()
                    .name(model.getModelName())
                    .category(model.getCategory())
                    .addString(model.getModelKey() + ".bpmn20.xml", bpmnXml)
                    .tenantId(effectiveTenantId)
                    .deploy();
        } catch (Exception e) {
            log.error("Flowable 部署失败: modelId={}, modelKey={}, tenantId={}, xmlLength={}",
                    modelId, model.getModelKey(), effectiveTenantId,
                    draft.getBpmnXml() != null ? draft.getBpmnXml().length() : 0, e);
            throw new BusinessException("流程部署失败: " + e.getMessage());
        }

        // 5. 获取流程定义
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();

        // 6. 计算业务版本号（取该模型所有版本中的最大值 + 1）
        Integer maxVersion = versionMapper.selectObjs(
                new LambdaQueryWrapper<WfProcessModelVersion>()
                        .select(WfProcessModelVersion::getVersion)
                        .eq(WfProcessModelVersion::getModelId, modelId)
                        .orderByDesc(WfProcessModelVersion::getVersion)
                        .last("LIMIT 1")
        ).stream().map(o -> (Integer) o).findFirst().orElse(0);
        draft.setVersion(maxVersion + 1);

        // 7. 更新版本记录
        draft.setStatus("PUBLISHED");
        draft.setDeploymentId(deployment.getId());
        draft.setProcessDefinitionId(definition.getId());
        draft.setEngineProcessKey(definition.getKey());
        draft.setEngineVersion(definition.getVersion());
        draft.setPublishTime(LocalDateTime.now());
        draft.setPublishBy(userName);
        draft.setUpdateTime(LocalDateTime.now());
        versionMapper.updateById(draft);

        // 8. 归档旧已发布版本
        if (model.getCurrentPublishedVersionId() != null
                && !model.getCurrentPublishedVersionId().equals(draft.getId())) {
            WfProcessModelVersion oldPublished = versionMapper.selectById(model.getCurrentPublishedVersionId());
            if (oldPublished != null) {
                oldPublished.setStatus("ARCHIVED");
                oldPublished.setUpdateTime(LocalDateTime.now());
                versionMapper.updateById(oldPublished);
            }
        }

        // 9. 更新模型主表
        model.setCurrentPublishedVersionId(draft.getId());
        model.setUpdateBy(userName);
        model.setUpdateTime(LocalDateTime.now());
        modelMapper.updateById(model);

        PublishResult result = PublishResult.builder()
                .versionId(draft.getId())
                .businessVersion(draft.getVersion())
                .deploymentId(deployment.getId())
                .processDefinitionId(definition.getId())
                .engineVersion(definition.getVersion())
                .build();

        log.info("模型发布: modelId={}, versionId={}, deploymentId={}, engineVersion={}",
                modelId, draft.getId(), deployment.getId(), definition.getVersion());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<WfProcessModel> listModels(Long tenantId, String keyword, String category,
                                                  int page, int size) {
        LambdaQueryWrapper<WfProcessModel> wrapper = new LambdaQueryWrapper<WfProcessModel>()
                .eq(WfProcessModel::getTenantId, tenantId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(WfProcessModel::getModelName, keyword)
                        .or()
                        .like(WfProcessModel::getModelKey, keyword))
                .eq(category != null && !category.isBlank(), WfProcessModel::getCategory, category)
                .ne(WfProcessModel::getStatus, 0)
                .orderByDesc(WfProcessModel::getUpdateTime);

        Page<WfProcessModel> pageResult = modelMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public WfProcessModel getModel(Long modelId) {
        return getModelOrThrow(modelId);
    }

    /** {@inheritDoc} */
    @Override
    public List<ModelVersionVO> listVersions(Long modelId) {
        getModelOrThrow(modelId);
        LambdaQueryWrapper<WfProcessModelVersion> wrapper = new LambdaQueryWrapper<WfProcessModelVersion>()
                .eq(WfProcessModelVersion::getModelId, modelId)
                .orderByDesc(WfProcessModelVersion::getVersion);

        return versionMapper.selectList(wrapper).stream()
                .map(v -> ModelVersionVO.builder()
                        .id(v.getId())
                        .version(v.getVersion())
                        .status(v.getStatus())
                        .xmlSha256(v.getXmlSha256())
                        .deploymentId(v.getDeploymentId())
                        .processDefinitionId(v.getProcessDefinitionId())
                        .engineVersion(v.getEngineVersion())
                        .publishBy(v.getPublishBy())
                        .publishTime(v.getPublishTime())
                        .createTime(v.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public WfProcessModelVersion getVersion(Long versionId) {
        WfProcessModelVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }
        return version;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long modelId) {
        WfProcessModel model = getModelOrThrow(modelId);

        // 检查是否有已发布版本
        LambdaQueryWrapper<WfProcessModelVersion> publishedCheck = new LambdaQueryWrapper<WfProcessModelVersion>()
                .eq(WfProcessModelVersion::getModelId, modelId)
                .eq(WfProcessModelVersion::getStatus, "PUBLISHED");
        if (versionMapper.selectCount(publishedCheck) > 0) {
            throw new BusinessException("存在已发布版本，不允许删除模型。请先归档。");
        }

        // 删除所有版本
        LambdaQueryWrapper<WfProcessModelVersion> versionWrapper = new LambdaQueryWrapper<WfProcessModelVersion>()
                .eq(WfProcessModelVersion::getModelId, modelId);
        versionMapper.delete(versionWrapper);

        // 删除模型
        modelMapper.deleteById(modelId);
        log.info("模型删除: modelId={}, modelKey={}", modelId, model.getModelKey());
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 获取模型实体，不存在则抛出异常。
     */
    private WfProcessModel getModelOrThrow(Long modelId) {
        WfProcessModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        return model;
    }

    /**
     * 使用 SELECT FOR UPDATE 锁定模型行（悲观锁，防止并发发布）。
     */
    private WfProcessModel lockModelForUpdate(Long modelId) {
        List<WfProcessModel> rows = jdbcTemplate.query(
                "SELECT * FROM wf_process_model WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> {
                    WfProcessModel m = new WfProcessModel();
                    m.setId(rs.getLong("id"));
                    m.setTenantId(rs.getLong("tenant_id"));
                    m.setModelKey(rs.getString("model_key"));
                    m.setModelName(rs.getString("model_name"));
                    m.setCategory(rs.getString("category"));
                    m.setStatus(rs.getInt("status"));
                    m.setCurrentDraftVersionId(rs.getObject("current_draft_version_id") != null
                            ? rs.getLong("current_draft_version_id") : null);
                    m.setCurrentPublishedVersionId(rs.getObject("current_published_version_id") != null
                            ? rs.getLong("current_published_version_id") : null);
                    m.setCreateBy(rs.getString("create_by"));
                    m.setUpdateBy(rs.getString("update_by"));
                    m.setCreateTime(rs.getTimestamp("create_time") != null
                            ? rs.getTimestamp("create_time").toLocalDateTime() : null);
                    m.setUpdateTime(rs.getTimestamp("update_time") != null
                            ? rs.getTimestamp("update_time").toLocalDateTime() : null);
                    return m;
                },
                modelId);
        if (rows.isEmpty()) {
            throw new BusinessException("模型不存在");
        }
        return rows.get(0);
    }

    /**
     * 计算字符串的 SHA-256 摘要。
     */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }
}

