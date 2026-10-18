# Workflow Development and Modeling

Use the `workflow`, `full`, or another preset containing `omni-workflow`. Complete the [workflow tutorial](../guides/workflow-approval.en.md) and Docker setup first. [Workflow facts](../workflow.en.md) and source define current state machines, tenancy, publish locking, and failures.

The modeling account needs the permissions applicable to each operation from `workflow:model:list`, `workflow:model:create`, `workflow:model:update`, `workflow:model:validate`, `workflow:model:publish`. Login alone does not grant modeling access. Choose a unique tenant-local `modelKey` matching BPMN `<process id>`. Clean up only your own exercise data after confirming it has no references.

## 4. Extension Guide

### 4.1 Add an Approval Process Type

1. Design BPMN XML with `<omni:assignment>` on each UserTask, using supported roles, anchors, and scopes.
2. Create the model: `POST /api/workflow/model`.
3. Save XML: `PUT /api/workflow/model/{id}/draft`.
4. Validate the current draft: `POST /api/workflow/model/{id}/validate`; inspect errors and warnings.
5. Publish: `POST /api/workflow/model/{id}/publish`. Publishing validates again internally.

[BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java) checks XML, the process identifier, nodes, and required extension presence. It does not fully validate assignment JSON field semantics. After validation, start and approve a real exercise following the tutorial, checking candidates, failures, and results. Combining supported capabilities usually needs no code change; new anchors and strategies require the following source extensions.

### 4.2 Add an Anchor Type

1. Start in [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java), at `doResolveCandidates()/resolveAnchorUnitId()`; implement the anchor and tenant-filtered organization queries.
2. Trace [ScopedRoleAssignmentListener](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java), `CandidateResolverDelegate`, and `CandidateResolverBean`. Editing only the listener misses the shared resolution entry.
3. Update options, parameters, and XML save/readback in [UserTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/UserTaskPanel.vue); inspect [useBpmnExtension.ts](../../omni-frontend/src/composables/useBpmnExtension.ts).
4. Implement and test any additional field validation explicitly. Do not assume [BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java) already has a complete anchor whitelist or JSON Schema validation.

### 4.3 Add a Fallback Strategy

1. Inspect `handleFallback()` and `escalateToParentForCandidates()` in [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java), then integrate the engine entry points.
2. Existing `ASSIGN_ADMIN` resolves `SUPER_ADMIN` candidates in organization unit `1` of the current tenant and still fails if none exist. `ESCALATE_PARENT` resolves the same role/scope along parent organizations and must fail closed if empty.
3. Parent escalation branches exist in the ExecutionListener and `CandidateResolverBean`. The current `CandidateResolverDelegate` calls `handleFallback()` directly and its outer catch only logs exceptions. The three paths are not interchangeable; verify failure behavior at the entry point you use.
4. Update frontend strategy options and test matches, empty candidates, tenant boundaries, and dependency failures for all three entry points.

### 4.4 Customize Notifications

The current [WorkflowNotificationService](../../omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/notification/WorkflowNotificationService.java) pending-task method has four parameters, including `processInstanceId`:

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/notification/WorkflowNotificationService.java" -->
```java
void notifyPendingTask(String userId, String taskId, String processInstanceId, String title);
```

Place this implementation in `com.omni.workflow.notification` within `omni-workflow` so component scanning registers it. It only logs events and sends no external notification. Implement actual delivery, failure handling, and retry behavior for your channel.

```java
package com.omni.workflow.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.omni.common.workflow.notification.WorkflowNotificationService;

/** 演示通知 SPI 的完整签名；只记录事件，不发送外部通知。 */
@Slf4j
@Service
public class MyNotificationService implements WorkflowNotificationService {

    /** 记录待办通知事件，真实投递由业务实现。 */
    @Override
    public void notifyPendingTask(String userId, String taskId,
                                  String processInstanceId, String title) {
        log.debug("待办通知事件：taskId={}, processInstanceId={}", taskId, processInstanceId);
    }

    /** 记录任务通知清理事件。 */
    @Override
    public void clearPendingTask(String taskId) {
        log.debug("清理待办通知事件：taskId={}", taskId);
    }
}
```

[FlowableAutoConfiguration](../../omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/config/FlowableAutoConfiguration.java) supplies `NoOpNotificationService` with `@ConditionalOnMissingBean`. Check that condition after registering your own `WorkflowNotificationService` Bean; an empty implementation or log entry does not establish delivery.

### 4.5 CC Notifications

Configure a ServiceTask with `flowable:delegateExpression="${ccNotifyDelegate}"` and `<omni:cc>`. [CcNotifyDelegate](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/delegate/CcNotifyDelegate.java) resolves direct user IDs or role/organization recipients and writes `wf_cc_record`. CC does not enter the pending-task list or call the pending-task notification SPI.

This JavaDelegate runs synchronously. Its current implementation catches and logs exceptions. If CC records are absent, inspect configuration, SQL logs, and transaction results; continued process execution does not prove CC success or asynchronous delivery.

## 5. Current Engine and Design Boundaries

The [Maven POM](../../omni-backend/pom.xml) `flowable.version` and [shared Starter POM](../../omni-backend/omni-common-workflow/pom.xml) define the engine dependency. [Workflow facts](../workflow.en.md) identify auto-configuration, tenant bridging, and approval SPIs. See the [frontend package.json](../../omni-frontend/package.json) for designer dependencies. Upgrades or engine replacements must account for these integration points, published models, and multi-instance regressions; record architectural tradeoffs in ADRs.

## 6. BPMN Modeling Conventions

### Naming

| Element | Rule | Example |
|---|---|---|
| Process ID | Match `model_key` | `leave-request`, `expense-approval` |
| UserTask ID | kebab-case role and action | `dept-leader-approve`, `hr-review` |
| SequenceFlow ID | `flow-{source}-{target}` | `flow-start-submit` |
| Gateway ID | `{type}-gw-{purpose}` | `exclusive-gw-amount`, `parallel-gw-notify` |

### Modeling Rules

1. Configure `<omni:assignment>` on each UserTask and resolve candidates dynamically; avoid hardcoded personal accounts.
2. Give each ExclusiveGateway an unconditional default flow for when other conditions do not match.
3. Use the following multi-instance completion expression. Copy `||` directly, without Markdown escaping: `${rejectedCount > 0 || approvedCount >= requiredApprovals}`
4. Use the ServiceTask delegate described above for CC and check actual records.
5. Inspect `BpmnXmlValidator` results before publishing, then exercise approval, rejection, and empty-candidate paths.

### Designer Source Entry Points

| File | Responsibility |
|---|---|
| [useBpmnModeler.ts](../../omni-frontend/src/composables/useBpmnModeler.ts) | Create and destroy the Modeler |
| [useBpmnExtension.ts](../../omni-frontend/src/composables/useBpmnExtension.ts) | Read and write BPMN extensions |
| [bpmnContextPadI18n.ts](../../omni-frontend/src/composables/workflow/bpmnContextPadI18n.ts) | Localize the context menu |
| [bpmnContextPadProvider.ts](../../omni-frontend/src/composables/workflow/bpmnContextPadProvider.ts) | Customize the context menu |
| [UserTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/UserTaskPanel.vue) | Role, anchor, scope, and fallback parameters |
| [ServiceTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/ServiceTaskPanel.vue) | CC parameters |
| [GatewayPanel.vue](../../omni-frontend/src/components/workflow/panels/GatewayPanel.vue) | Gateway conditions |

## 7. Troubleshooting

| Symptom | Entry point and expected check |
|---|---|
| Publishing fails | Run validate and inspect errors, [BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java), and the current draft. |
| No candidates | Trace roleCode, anchor, scopeMode, tenant, and enabled status in [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java); inspect the actual fallback branch. |
| An instance cannot start | Check published version, permissions, and request results using the tutorial; verify `wf_process_model_version.status=PUBLISHED`. |
| Countersign does not finish | Inspect `approvedCount/rejectedCount/requiredApprovals` on this MI activity root and its completionCondition, rather than only process-level variables. |
| Unexpected deleteReason | See [workflow facts](../workflow.en.md) §3.1: `MI_END` automatic ending differs from termination with `deleted`. |
| Tenant isolation fails | Inspect authenticated requests through the trusted gateway and `TenantInfoFilter/TenantInfoHolder` setup/cleanup. Client-supplied headers alone are not trusted identity. |
| CC records are absent | Inspect configuration parsing, SQL, and exceptions in [CcNotifyDelegate](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/delegate/CcNotifyDelegate.java); a continuing process does not prove CC success. |
| The designer fails to load | Install against package.json and its lockfile; inspect the container frontend and browser console. |

After source extensions, verify the backend with Maven Wrapper in local Docker. If the designer changed, also run frontend build/lint. Follow the [workflow tutorial](../guides/workflow-approval.en.md) for full operations, expected results, and exercise cleanup.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
```
