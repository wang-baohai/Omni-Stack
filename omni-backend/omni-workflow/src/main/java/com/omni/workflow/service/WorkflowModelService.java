package com.omni.workflow.service;

import com.omni.common.core.result.PageResult;
import com.omni.workflow.dto.*;
import com.omni.workflow.entity.WfProcessModel;
import com.omni.workflow.entity.WfProcessModelVersion;

import java.util.List;

/**
 * 流程模型管理服务接口。
 * <p>
 * 提供模型的创建、草稿保存、校验、发布、版本查询等全生命周期管理。
 * 支持草稿/发布双轨版本制度。</p>
 *
 * @author Omni-Stack Team
 */
public interface WorkflowModelService {

    /**
     * 创建流程模型（同时创建初始草稿版本）。
     *
     * @param request  创建请求
     * @param tenantId 租户 ID
     * @param userName 操作人
     * @return 模型实体
     */
    WfProcessModel createModel(CreateModelRequest request, Long tenantId, String userName);

    /**
     * 保存草稿（更新草稿版本的 BPMN XML 和设计器 JSON）。
     *
     * @param modelId  模型 ID
     * @param request  保存草稿请求
     * @param userName 操作人
     * @return 草稿版本实体
     */
    WfProcessModelVersion saveDraft(Long modelId, SaveDraftRequest request, String userName);

    /**
     * 校验模型的 BPMN XML。
     *
     * @param modelId 模型 ID
     * @return 校验结果
     */
    ValidateResult validateModel(Long modelId);

    /**
     * 发布模型到 Flowable 引擎。
     * <p>使用 SELECT FOR UPDATE 锁定模型行，防止并发发布。</p>
     *
     * @param modelId  模型 ID
     * @param userName 发布人
     * @return 发布结果
     */
    PublishResult publishModel(Long modelId, String userName);

    /**
     * 分页查询模型列表。
     *
     * @param tenantId 租户 ID
     * @param keyword  关键字（模型名称/标识模糊查询，可选）
     * @param category 分类（可选）
     * @param page     页码
     * @param size     每页数量
     * @return 分页结果
     */
    PageResult<WfProcessModel> listModels(Long tenantId, String keyword, String category, int page, int size);

    /**
     * 获取单个模型详情。
     *
     * @param modelId 模型 ID
     * @return 模型实体
     */
    WfProcessModel getModel(Long modelId);

    /**
     * 获取模型的版本列表（按版本号倒序）。
     *
     * @param modelId 模型 ID
     * @return 版本 VO 列表
     */
    List<ModelVersionVO> listVersions(Long modelId);

    /**
     * 获取指定版本详情（含 BPMN XML 和设计器 JSON）。
     *
     * @param versionId 版本 ID
     * @return 版本实体
     */
    WfProcessModelVersion getVersion(Long versionId);

    /**
     * 删除模型及其所有版本（仅允许删除无已发布版本的模型）。
     *
     * @param modelId 模型 ID
     */
    void deleteModel(Long modelId);
}
