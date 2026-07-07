package com.omni.workflow.service.impl;

import com.omni.common.core.result.PageResult;
import com.omni.common.workflow.tenant.TenantInfoHolder;
import com.omni.workflow.dto.DeployProcessRequest;
import com.omni.workflow.dto.ProcessDefinitionVO;
import com.omni.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * 流程定义服务实现。
 * <p>
 * 封装 Flowable {@link RepositoryService} 实现流程定义的部署、查询、
 * 挂起、激活、删除等操作。所有查询自动注入租户 ID 实现数据隔离。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionServiceImpl implements ProcessDefinitionService {

    private final RepositoryService repositoryService;

    /**
     * 分页查询流程定义列表。
     *
     * @param name     流程名称（模糊查询，可选）
     * @param category 流程分类（可选）
     * @param page     页码（从 1 开始）
     * @param size     每页数量
     * @return 流程定义分页结果
     */
    @Override
    public PageResult<ProcessDefinitionVO> list(String name, String category, int page, int size) {
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc();

        // 租户过滤
        String tenantId = TenantInfoHolder.getTenantId();
        if (tenantId != null) {
            query.processDefinitionTenantId(tenantId);
        }

        if (name != null && !name.isBlank()) {
            query.processDefinitionNameLike("%" + name + "%");
        }
        if (category != null && !category.isBlank()) {
            query.processDefinitionCategory(category);
        }

        long total = query.count();
        List<ProcessDefinition> entities = query
                .listPage((page - 1) * size, size);

        List<ProcessDefinitionVO> records = entities.stream()
                .map(this::toVO)
                .toList();

        return new PageResult<>(records, total, size, page);
    }

    /**
     * 将 Flowable 实体转为 VO，避免序列化时触发懒加载属性。
     *
     * @param def Flowable 流程定义实体
     * @return 流程定义视图对象
     */
    private ProcessDefinitionVO toVO(ProcessDefinition def) {
        return ProcessDefinitionVO.builder()
                .id(def.getId())
                .key(def.getKey())
                .name(def.getName())
                .category(def.getCategory())
                .version(def.getVersion())
                .deploymentId(def.getDeploymentId())
                .resourceName(def.getResourceName())
                .suspended(def.isSuspended())
                .tenantId(def.getTenantId())
                .description(def.getDescription())
                .build();
    }

    /**
     * 获取流程定义的 BPMN XML 内容。
     *
     * @param processDefinitionId 流程定义 ID
     * @return BPMN XML 字符串
     */
    @Override
    public String getBpmnXml(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
        if (definition == null) {
            throw new IllegalArgumentException("流程定义不存在: " + processDefinitionId);
        }

        try (InputStream is = repositoryService.getResourceAsStream(
                definition.getDeploymentId(), definition.getResourceName())) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("读取 BPMN XML 失败: " + processDefinitionId, e);
        }
    }

    /**
     * 部署流程定义。
     * <p>
     * 将 BPMN XML 部署到 Flowable 引擎，自动设置租户 ID。</p>
     *
     * @param request 部署请求
     * @return 部署 ID
     */
    @Override
    public String deploy(DeployProcessRequest request) {
        String tenantId = TenantInfoHolder.getTenantId();

        Deployment deployment = repositoryService.createDeployment()
                .name(request.getName())
                .category(request.getCategory())
                .addString(request.getName() + ".bpmn20.xml", request.getBpmnXml())
                .tenantId(tenantId != null ? tenantId : "default")
                .deploy();

        log.info("流程定义部署成功: deploymentId={}, name={}, tenantId={}",
                deployment.getId(), request.getName(), tenantId);
        return deployment.getId();
    }

    /**
     * 挂起流程定义。
     *
     * @param processDefinitionId 流程定义 ID
     */
    @Override
    public void suspend(String processDefinitionId) {
        repositoryService.suspendProcessDefinitionById(processDefinitionId, true, null);
        log.info("流程定义已挂起: processDefinitionId={}", processDefinitionId);
    }

    /**
     * 激活流程定义。
     *
     * @param processDefinitionId 流程定义 ID
     */
    @Override
    public void activate(String processDefinitionId) {
        repositoryService.activateProcessDefinitionById(processDefinitionId, true, null);
        log.info("流程定义已激活: processDefinitionId={}", processDefinitionId);
    }

    /**
     * 删除部署（级联删除流程实例）。
     *
     * @param deploymentId 部署 ID
     */
    @Override
    public void delete(String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
        log.info("部署已删除: deploymentId={}", deploymentId);
    }
}

