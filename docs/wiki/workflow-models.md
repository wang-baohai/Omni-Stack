# 4. 工程实践：草稿怎样变成可以启动的版本

把编辑器里的图想成一份还在讨论的审批制度。保存只是留档；发布才表示允许新的申请使用这一版。项目为此分开保存草稿、发布版本和引擎定义。

## 先把画图和发布分开

创建模型时，modelKey 在当前租户唯一，要以字母开头，后续可以包含字母、数字和连字符。初始草稿的业务版本是 0。保存时接收设计器 JSON 和 BPMN XML；后端不会只凭 JSON 自动补出可执行 XML。

发布后再修改，会形成独立草稿，保留旧发布内容。在途实例仍使用原定义。业务模型版本按该模型已有最大版本加一，引擎定义版本则由 Flowable 自己维护；单据自己的版本又是第三回事。

## 第一段工程源码：先锁定，再确认真有草稿

WorkflowModelServiceImpl 的发布方法开头：

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java" -->
```java
    public PublishResult publishModel(Long modelId, String userName) {
        // 1. SELECT FOR UPDATE 锁定模型行
        WfProcessModel model = lockModelForUpdate(modelId);

        // 2. 获取草稿版本
        WfProcessModelVersion draft = versionMapper.selectById(model.getCurrentDraftVersionId());
        if (draft == null || draft.getBpmnXml() == null || draft.getBpmnXml().isBlank()) {
            throw new BusinessException("BPMN XML 为空，无法发布");
        }
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new BusinessException("当前草稿指针指向非 DRAFT 版本(" + draft.getStatus()
                    + ")，请先通过 saveDraft 创建独立草稿");
        }
```

行锁好比让同一张图的发布操作排队，避免两人同时读到同一个草稿、各自生成发布记录。草稿为空或不是 DRAFT 就拒绝，之后还必须做 XML 校验。方法使用本地事务；锁只作用于这张模型行，不是跨业务服务的全局锁。

模型行锁由原始 JDBC 查询取得，不能把“拿到锁”理解为已经完成租户授权。

## 校验不是只看 XML 能不能打开

BpmnXmlValidator 禁用 DOCTYPE 和外部实体等解析入口，并检查一个可执行流程、与 modelKey 相同的 process id、开始/结束节点、审批分配扩展、相关抄送扩展、网关默认连线和部分连线引用。

它不会自动证明 assignment JSON 业务含义正确，也没有提供完整的脚本、监听器和表达式安全白名单。检查扩展存在，不等于一定找得到审批人；结构通过后仍要用实际角色、组织和业务场景验证。

## 第二段工程源码：把明确的租户和图交给引擎

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java" -->
```java
            deployment = repositoryService.createDeployment()
                    .name(model.getModelName())
                    .category(model.getCategory())
                    .addString(model.getModelKey() + ".bpmn20.xml", bpmnXml)
                    .tenantId(effectiveTenantId)
                    .deploy();
```

项目把 XML 作为资源，给出名称、分类和有效租户，再部署。配置了模型分类时，还会替换 XML 的 targetNamespace，供引擎定义分类使用。

随后项目保存引擎定义编号、部署编号和业务版本，把新版本设为 PUBLISHED，归档原发布版本，并更新模型的发布指针。这一串本地操作完成后，新的申请才应使用明确的版本。

## 发布失败时，先查返回和实际保存结果

校验失败分支会尝试写 FAILED，然后抛业务异常。因为它与发布处在同一个会回滚的事务中，**不能保证 FAILED 一定保存下来**。因此不要根据文章假设已经留下失败版本，也不要不查结果就连续点发布。

先根据错误检查 XML 和当前草稿；需要修改就保存正确草稿，再校验发布。并发失败时先刷新版本和指针，确认已有哪一版成功，避免把超时直接当作没有发布。

## 原始定义管理是另一条入口

旧的流程定义管理保留部署、读取 BPMN、挂起、激活和级联删除。它不经过上面的业务模型草稿校验和版本流程。当前按 ID 的读写没有统一租户归属检查，不能把拥有 definition 功能权限当成数据隔离证明，也不建议用这条入口替代正常模型发布。

进入业务前，还要知道谁发起、用哪版图和找谁审批，继续读[启动与候选人](workflow-assignment.md)。
