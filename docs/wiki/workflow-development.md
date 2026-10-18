# 工作流开发与建模

适用 `workflow`、`full` 或已包含 `omni-workflow` 的预设。先完成[工作流操作教程](../guides/workflow-approval.md)与 Docker 环境准备；当前状态机、租户、发布锁和失败语义以[工作流事实](../workflow.md)及源码为准。

模型操作账号需要 `workflow:model:list`, `workflow:model:create`, `workflow:model:update`, `workflow:model:validate`, `workflow:model:publish` 中对应操作的权限；不是只要能登录就能建模。为练习选择本租户内唯一的 `modelKey`，让 BPMN `<process id>` 与它一致；只清理自己本次创建且已确认无引用的练习数据。

## 4. 扩展指南

### 4.1 添加新的审批流程类型

1. 设计 BPMN XML，为每个 UserTask 配置 `<omni:assignment>`，使用已有的角色、锚点和作用域。
2. 创建模型：`POST /api/workflow/model`。
3. 保存 XML：`PUT /api/workflow/model/{id}/draft`。
4. 校验当前草稿：`POST /api/workflow/model/{id}/validate`，查看返回的错误和警告。
5. 发布：`POST /api/workflow/model/{id}/publish`；发布内部仍会重新校验。

[BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java) 检查 XML、流程标识、节点与必需扩展的存在性；当前不会完整校验 assignment JSON 的字段语义。校验通过后仍需按操作教程实际启动和审批，核对候选人、失败分支及结果。只组合已有能力通常不改代码；新锚点或策略属于下面的源码扩展。

### 4.2 添加新的锚点类型

1. 从 [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java) 的 `doResolveCandidates()/resolveAnchorUnitId()` 开始，实现新锚点及带租户条件的组织查询。
2. 沿 [ScopedRoleAssignmentListener](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java)、`CandidateResolverDelegate` 和 `CandidateResolverBean` 检查调用边界；只改监听器会遗漏共享解析入口。
3. 更新 [UserTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/UserTaskPanel.vue) 的选项、参数和 XML 保存/回读；核对 [useBpmnExtension.ts](../../omni-frontend/src/composables/useBpmnExtension.ts)。
4. 若新增字段校验，明确实现并测试；不能假设 [BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java) 已有锚点值或 JSON Schema 的完整白名单。

### 4.3 添加新的降级策略

1. 核对 [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java) 的 `handleFallback()` 与 `escalateToParentForCandidates()`，再处理各引擎入口的分支。
2. 现有 `ASSIGN_ADMIN` 查找当前租户、组织单元 `1` 的 `SUPER_ADMIN` 角色候选人，找不到仍失败；`ESCALATE_PARENT` 沿父组织重解角色与作用域，空结果必须失败关闭。
3. 上溯分支位于 ExecutionListener 和 `CandidateResolverBean`；当前 `CandidateResolverDelegate` 直接调用 `handleFallback()`，外层捕获异常后只记录日志；不能假设三条路径完全相同，应验证所用入口的真实失败结果。
4. 同步前端策略选项，并分别测试三入口的命中、无候选人、租户隔离和依赖失败。

### 4.4 自定义通知服务

当前 [WorkflowNotificationService](../../omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/notification/WorkflowNotificationService.java) 的待办方法有四个参数，包含 `processInstanceId`：

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/notification/WorkflowNotificationService.java" -->
```java
void notifyPendingTask(String userId, String taskId, String processInstanceId, String title);
```

下面的实现放在 `omni-workflow` 的 `com.omni.workflow.notification` 包内，由组件扫描注册。它只记录事件，不发送外部通知；真实渠道、失败和重试需由业务实现。

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

[FlowableAutoConfiguration](../../omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/config/FlowableAutoConfiguration.java) 通过 `@ConditionalOnMissingBean` 提供 `NoOpNotificationService`。注册自己的 `WorkflowNotificationService` Bean 后核对该条件，不要把空实现或日志当作通知已送达。

### 4.5 抄送（CC）通知

在 ServiceTask 设置 `flowable:delegateExpression="${ccNotifyDelegate}"`，并配置 `<omni:cc>`。[CcNotifyDelegate](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/delegate/CcNotifyDelegate.java) 支持直接用户 ID 或角色/组织解析，写入 `wf_cc_record`；抄送不进入待办，也不调用待办通知 SPI。

该 JavaDelegate 同步执行。当前异常会被捕获并记录日志；缺少抄送记录时要核对配置、SQL 日志和事务结果，不能把流程继续运行当作抄送成功或异步投递保证。

## 5. 当前引擎与设计边界

引擎依赖由 [Maven POM](../../omni-backend/pom.xml) 的 `flowable.version` 和 [共享 Starter POM](../../omni-backend/omni-common-workflow/pom.xml) 定义；自动配置、租户桥接和审批 SPI 的入口见[工作流事实](../workflow.md)。设计器依赖见[前端 package.json](../../omni-frontend/package.json)。升级或替换引擎时核对这些集成点、已发布模型和多实例回归；架构取舍记录在 ADR。

## 6. BPMN 建模约定

### 命名约定

| 元素 | 命名规则 | 示例 |
|---|---|---|
| Process ID | 与 `model_key` 一致 | `leave-request`, `expense-approval` |
| UserTask ID | kebab-case，角色与动作 | `dept-leader-approve`, `hr-review` |
| SequenceFlow ID | `flow-{source}-{target}` | `flow-start-submit` |
| Gateway ID | `{type}-gw-{purpose}` | `exclusive-gw-amount`, `parallel-gw-notify` |

### 建模原则

1. 每个 UserTask 配置 `<omni:assignment>`，动态解析候选人；不要硬编码个人账号。
2. ExclusiveGateway 设置无条件的 default flow，供其他分支条件不满足时使用。
3. 多实例会签使用以下表达式；这里的 `||` 可以直接复制，不含 Markdown 转义： `${rejectedCount > 0 || approvedCount >= requiredApprovals}`
4. CC 使用上一节的 ServiceTask 委托；按真实记录确认结果。
5. 发布前检查 `BpmnXmlValidator` 返回值，并实际验证审批通过、拒绝和空候选人分支。

### 设计器源码入口

| 文件 | 职责 |
|---|---|
| [useBpmnModeler.ts](../../omni-frontend/src/composables/useBpmnModeler.ts) | 创建和销毁 Modeler |
| [useBpmnExtension.ts](../../omni-frontend/src/composables/useBpmnExtension.ts) | 读写 BPMN 扩展元素 |
| [bpmnContextPadI18n.ts](../../omni-frontend/src/composables/workflow/bpmnContextPadI18n.ts) | 上下文菜单国际化 |
| [bpmnContextPadProvider.ts](../../omni-frontend/src/composables/workflow/bpmnContextPadProvider.ts) | 自定义上下文菜单 |
| [UserTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/UserTaskPanel.vue) | 角色、锚点、作用域与策略参数 |
| [ServiceTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/ServiceTaskPanel.vue) | 抄送参数 |
| [GatewayPanel.vue](../../omni-frontend/src/components/workflow/panels/GatewayPanel.vue) | 网关条件 |

## 7. 故障排查

| 现象 | 检查入口与预期 |
|---|---|
| 模型发布失败 | 调用 validate 查看错误；同时核对 [BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java) 与当前草稿。 |
| 候选人为空 | 从 [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java) 逐步检查 roleCode、锚点、scopeMode、租户和启用状态；按真实 fallback 分支核对异常。 |
| 实例无法启动 | 按操作教程核对发布版本、权限和请求结果；确认 `wf_process_model_version.status=PUBLISHED`。 |
| 会签不结束 | 核对本 MI 活动根的 `approvedCount/rejectedCount/requiredApprovals` 与 completionCondition，不能只看流程级变量。 |
| deleteReason 显示不符 | 读取[工作流事实](../workflow.md) §3.1；`MI_END` 的自动结束含义不同于 `deleted` 的终止。 |
| 租户隔离异常 | 检查经可信网关认证的请求，以及 `TenantInfoFilter/TenantInfoHolder` 的设置与清理；不把客户端自填头当作可信身份。 |
| CC 无记录 | 查看 [CcNotifyDelegate](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/delegate/CcNotifyDelegate.java) 的配置解析、SQL 与异常日志；流程继续不证明抄送成功。 |
| 设计器无法加载 | 对照 package.json 与锁文件安装依赖，检查容器前端及浏览器控制台。 |

源码扩展后，在本机 Docker 执行后端 Maven Wrapper 验证；若改设计器，再执行前端 build/lint。完整操作、预期结果和练习清理沿用[工作流操作教程](../guides/workflow-approval.md)。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
```
