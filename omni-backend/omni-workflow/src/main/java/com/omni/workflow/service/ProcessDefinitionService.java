package com.omni.workflow.service;

import com.omni.common.core.result.PageResult;
import com.omni.workflow.dto.DeployProcessRequest;
import com.omni.workflow.dto.ProcessDefinitionVO;

/**
 * 流程定义服务接口。
 * <p>
 * 封装 Flowable {@link org.flowable.engine.RepositoryService}，提供流程定义的
 * 部署、查询、挂起、激活、删除等管理能力。</p>
 *
 * @author Omni-Stack Team
 */
public interface ProcessDefinitionService {

    /**
     * 分页查询流程定义列表。
     *
     * @param name     流程名称（模糊查询，可选）
     * @param category 流程分类（可选）
     * @param page     页码
     * @param size     每页数量
     * @return 流程定义分页结果
     */
    PageResult<ProcessDefinitionVO> list(String name, String category, int page, int size);

    /**
     * 获取流程定义的 BPMN XML 内容。
     *
     * @param processDefinitionId 流程定义 ID
     * @return BPMN XML 字符串
     */
    String getBpmnXml(String processDefinitionId);

    /**
     * 部署流程定义。
     *
     * @param request 部署请求（名称 + 分类 + BPMN XML）
     * @return 部署 ID
     */
    String deploy(DeployProcessRequest request);

    /**
     * 挂起流程定义（暂停新实例发起）。
     *
     * @param processDefinitionId 流程定义 ID
     */
    void suspend(String processDefinitionId);

    /**
     * 激活流程定义。
     *
     * @param processDefinitionId 流程定义 ID
     */
    void activate(String processDefinitionId);

    /**
     * 删除部署（级联删除关联的流程实例）。
     *
     * @param deploymentId 部署 ID
     */
    void delete(String deploymentId);
}
