# 4. Project practice: from editable draft to a runnable version

Treat an editor diagram as an approval policy still under discussion. Saving keeps a copy; publishing permits new requests to use that edition. The project separates drafts, published versions and engine definitions.

## Separate drawing from publishing

modelKey must be unique within the current tenant, begin with a letter, and contain only letters, digits or hyphens afterward. The initial draft has business version 0. Saving accepts designer JSON and BPMN XML; the backend does not generate executable XML from JSON alone.

Editing after publication creates a separate draft and retains the old published content. Running instances keep their original definitions. The project increments the model's highest existing business version; Flowable maintains its own definition version. The document's concurrency version is a third value.

## First project excerpt: lock, then require a real draft

The start of WorkflowModelServiceImpl's publishing method:

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

The row lock queues publishing for the same model, preventing two callers from independently publishing the same draft. Missing XML or a status other than DRAFT is rejected; XML validation still follows. The method uses a local transaction. This is a lock on one model row, not a global cross-service lock.

The row is locked with raw JDBC. Acquiring the lock is not proof that tenant authorization was checked.

## Validation means more than XML parsing

BpmnXmlValidator disables DOCTYPE and external entities, and checks for one executable process, a process ID matching modelKey, start/end events, assignment extensions, relevant copy extensions, gateway default connections and some connection references.

It does not prove assignment JSON is meaningful, or supply a complete safety allowlist for scripts, listeners and expressions. An extension existing does not guarantee any approver will be found. Test valid structures with real roles, organizations and business scenarios.

## Second excerpt: send the diagram with an explicit tenant

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java" -->
```java
            deployment = repositoryService.createDeployment()
                    .name(model.getModelName())
                    .category(model.getCategory())
                    .addString(model.getModelKey() + ".bpmn20.xml", bpmnXml)
                    .tenantId(effectiveTenantId)
                    .deploy();
```

The project supplies the XML resource, name, category and effective tenant. If the model has a category, it also replaces the XML targetNamespace for the engine definition category.

It then records the definition ID, deployment ID and business version, marks the new version PUBLISHED, archives the previous published version, and updates the model's published pointer. New starts should use an explicit version after this local sequence succeeds.

## When publishing fails, inspect the response and saved state

The invalid-model branch attempts to write FAILED, then throws a business exception. Both actions belong to the same rollback transaction. **FAILED is not guaranteed to persist.** Do not assume a failed version was saved or repeatedly click publish without checking.

Use the error to inspect the XML and actual draft. Save a corrected draft when needed, validate it and publish. After a concurrency failure, refresh versions and pointers first to identify any successful publication. A timeout alone does not mean nothing was published.

## Raw definition management is a different entry point

Legacy definition management retains deployment, BPMN reads, suspension, activation and cascading deletion. It bypasses this business draft validation and version process. Operations by ID currently lack a unified tenant ownership check. A definition functional permission is not proof of data isolation; this entry point should not substitute for normal model publishing.

Next, learn [who starts and who is selected](workflow-assignment.en.md).
