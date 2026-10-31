# 6. 工程实践：通过、拒绝和多人会签怎样记账

候选人是规则找出的人员集合，处理人是具体任务上的 assignee，审批记录中的人是实际处理过任务的人。项目设计器生成的多实例任务直接分配给处理人；没有独立的待认领任务池。

## 先证明这件任务现在归你

完成、加签、减签、委派分别要求 workflow:approval:complete、add-signer、remove-signer、delegate 功能权限。服务还用当前租户和任务编号查任务，再要求当前用户恰好是处理人：

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowApprovalServiceImpl.java" -->
```java
    private Task requireCurrentAssigneeTask(String taskId, Long userId, Long tenantId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .taskTenantId(String.valueOf(tenantId))
                .singleResult();
        if (task == null) {
            throw new BusinessException(409, "审批任务已被处理或不存在，请刷新待办列表");
        }
        if (!String.valueOf(userId).equals(task.getAssignee())) {
            throw new BusinessException(403, "无权操作该审批任务");
        }
        return task;
    }
```

不存在或其他租户查不到，使用业务码 409，先刷新待办；同租户查到但不是本人，使用 403，重复点击不会取得权限。拥有管理员角色也不能自动替别人完成这一任务。

## 通过和拒绝，先是“一票”

请求中的 approved 表示本次同意或拒绝，comment 是意见。工程先记意见与任务本地结果，同时把 approved 写入流程根作用域；后一票会覆盖根上的这个值。因此多人汇总不能只看根 approved。

驳回使用同一个完成入口，只是 approved=false。它不会自动跳回上一个节点；接下来结束还是走其他路径，要由模型中的条件和连线决定。

## 多人会签，需要一个共同的记票位置

把多实例根想成这次会签会议的记票本，每个人的任务是自己手里的票。工程最多向上找 32 层，以根本地的 nrOfInstances 识别它；找不到时保留旧定义兼容行为，就地更新。写票数的原文是：

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/approval/ApprovalServiceImpl.java" -->
```java
        if (miRoot != null) {
            runtimeService.setVariableLocal(miRoot, counterKey, currentCount + 1);
        } else {
            runtimeService.setVariable(execId, counterKey, currentCount + 1);
        }
```

工程把本次同意计入 approvedCount，拒绝计入 rejectedCount，**然后**才调用 taskService.complete。设计器生成的完成条件是：

```text
${rejectedCount > 0 || approvedCount >= requiredApprovals}
```

ALL 默认要求全部候选通过，初始阈值是人数；ANY 要求一票通过，阈值是 1。上面的表达式也使一票拒绝达到离开条件。其他手写 BPMN 必须自己提供匹配的完成条件和拒绝路由，不能只写 ALL/ANY 就假设引擎知道整套业务规则。

当前附加 variables 会在这些写入之后应用到任务和流程，尚未过滤保留计数、路由等键。不能据此承诺客户端永远不能覆盖关键变量；二开时应把变量白名单和反例验证列入设计。

## 加签、减签和委派分别改变什么

| 动作 | 当前实现 | 必须记住的限制 |
|---|---|---|
| 加签 | 给当前多实例活动增加执行，注入 userId | 由当前处理人调用；要求模型对应变量匹配；不等于普通单人节点也能使用 |
| 减签 | 找同一实例同一节点、目标人的仍待办任务，移除其多实例执行 | 不能撤销已投的票；同人多条匹配时 singleResult 会报错 |
| 委派 | 修改任务的 assignee | 是直接改派，没有“办完回到原处理人”的过程 |

加减签后，ALL 阈值按“已经同意的票数 + 当前仍待办的任务数”重算；ANY 不重算，找不到多实例根时也不重算。加签本身不额外写一条审批意见，不能把新增待办当成已经投票。

减签调用 deleteMultiInstanceExecution(..., false)。**false 的参数名是 executionIsCompleted，表示移除的执行不记为父多实例的完成；它不是级联删除开关。** 嵌套多实例的实际行为仍需要单独验证，不能由这一个值推断。

委派的核心工程原文只有这一句：

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/approval/ApprovalServiceImpl.java" -->
```java
        taskService.setAssignee(taskId, targetUserId);
```

它没有使用 Flowable 的 delegateTask/resolveTask 回还模式。加签、减签和委派目标目前只解析成数字，没有完整检查目标用户存在、启用及所属租户。不要把有按钮和有功能权限等同于目标身份已全面校验。

## 为什么别人还没点，待办就结束了

ANY 已有同意票，或模型的其他提前结束条件成立后，剩余任务可能以 MI_END 结束。审批记录对以 MI 开头的删除原因保留 auto-approved 旧标签，其他删除归 cancelled；**前者是系统跳过，不能冒充该人赞成**。

正常完成按任务本地 approved 判定，缺该变量时兼容默认通过；意见取第一条，不保证是最新一条。这些读取规则要和真人投票事实分开理解。

## 终止与尚未提供的动作

普通终止要求 workflow:instance:terminate，并检查同租户、发起人本人、实例仍在进行中；它删除运行实例，不是暂停后可以恢复。内部业务终止是另一条可信服务入口，跨服务场景按适用路径写 CANCELLED 事件。

当前没有独立的认领/取消认领、工作流撤回、另一套转办、委派后回还、驳回到指定节点、运行实例换版本、任务挂起/激活、管理员代审批/代终止入口。原始定义挂起是定义管理，不能当作“挂起这一件任务”。业务页面的撤回可能通过内部终止实现，也不是通用工作流撤回 API。

最后读[结果与排障](workflow-events.md)，把任务结果、待办展示和业务结果串起来。
