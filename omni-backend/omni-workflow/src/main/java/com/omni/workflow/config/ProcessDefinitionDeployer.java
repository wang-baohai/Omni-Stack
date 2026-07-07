package com.omni.workflow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 流程定义启动部署器。
 * <p>
 * 应用启动时从 classpath 读取 BPMN XML，以正确的租户 ID 部署到 Flowable 引擎，
 * 确保 {@code startProcessInstanceByKeyAndTenantId} 能匹配到最新版本的流程定义。</p>
 * <p>
 * 若已存在相同资源名称的部署且内容未变更，Flowable 不会创建重复部署。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessDefinitionDeployer implements ApplicationRunner {

    private static final String BPMN_RESOURCE = "bpmn/leave-approval.bpmn20.xml";
    private static final String DEPLOYMENT_NAME = "OmniLeaveApproval";
    private static final String DEFAULT_TENANT_ID = "1";
    private static final String DEFAULT_CATEGORY = "leave";

    private final RepositoryService repositoryService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ClassPathResource resource = new ClassPathResource(BPMN_RESOURCE);
        if (!resource.exists()) {
            log.debug("未找到 BPMN 资源文件，跳过自动部署: {}", BPMN_RESOURCE);
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            String bpmnXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            Deployment deployment = repositoryService.createDeployment()
                    .name(DEPLOYMENT_NAME)
                    .category(DEFAULT_CATEGORY)
                    .addString(BPMN_RESOURCE, bpmnXml)
                    .tenantId(DEFAULT_TENANT_ID)
                    .deploy();

            log.info("流程定义自动部署完成: deploymentId={}, tenantId={}, resource={}",
                    deployment.getId(), DEFAULT_TENANT_ID, BPMN_RESOURCE);
        } catch (Exception e) {
            log.error("流程定义自动部署失败: resource={}", BPMN_RESOURCE, e);
        }
    }
}
