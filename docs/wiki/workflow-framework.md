# 3. 框架原理：按下“完成”，Flowable 怎样继续往下走

本页只研究项目使用的 Flowable 8.0.0。下面是官方源码包中的连续短片段，保持原文；先读每段前后的解释，不需要理解每一种 Java 类型。

## 第一站：把一个操作交给统一执行入口

TaskServiceImpl 的第 234–237 行：

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/task-command.java.txt" -->
```java
    @Override
    public void complete(String taskId) {
        commandExecutor.execute(new CompleteTaskCmd(taskId, (Map<String, Object>) null));
    }
```

调用方传入任务编号，框架把“完成这件任务”包装成 CompleteTaskCmd，再交给 commandExecutor。可以把它理解为把一张操作单交到统一受理处；这还不是项目的“同意采购”规则。

CompleteTaskCmd 随后调用 TaskHelper.completeTask。框架会处理变量、完成监听器、历史及事件等工作；这些不等于项目已经检查了当前用户是否能审批。下一页以后会看到，项目必须先做自己的守卫。

## 第二站：完成当前任务，再安排后续执行

TaskHelper 的第 178–184 行：

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/task-advance.java.txt" -->
```java
        completeTask(taskEntity, userId);

        // Continue process (if not a standalone task)
        if (taskEntity.getExecutionId() != null && !bpmnErrorPropagated) {
            ExecutionEntity executionEntity = processEngineConfiguration.getExecutionEntityManager().findById(taskEntity.getExecutionId());
            CommandContextUtil.getAgenda(commandContext).planTriggerExecutionOperation(executionEntity);
        }
```

第一句处理任务完成。后面判断这件任务是否属于流程执行，且当前没有传播相应 BPMN 错误；条件满足时，安排继续推进执行。

所以，“完成任务”不只是把一行状态改为完成。它可能马上进入网关、产生下一件任务，甚至结束流程。项目先写本次意见和计数，再调用 complete，正是为了让后续节点读到这次结果。安排推进也不意味着每个模型中的所有工作都会在同一次请求里结束。

## 第三站：多人会签怎样知道可以离开

同一个审批节点给多个人各发一件任务，叫多实例。ParallelMultiInstanceBehavior 第 231–234 行中的判断如下，片段止于离开调用：

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/parallel-completion.java.txt" -->
```java
            boolean isCompletionConditionSatisfied = completionConditionSatisfied(execution.getParent());
            if (nrOfCompletedInstances >= nrOfInstances || isCompletionConditionSatisfied) {
                leave(executionEntity, nrOfInstances, nrOfCompletedInstances, isCompletionConditionSatisfied);
```

框架检查两个条件：所有实例是否完成，或者模型定义的完成条件是否成立。这里是“或者”。项目配置“有拒绝票，或同意票达到阈值”时，可能在所有人都处理之前离开这个节点。

完成条件要能计算成布尔值，也就是成立或不成立。框架不会自行猜出“我们公司要求几票通过”。ALL、ANY 和项目计数器如何配合，要继续看任务篇。

## 第四站：剩下的待办为什么会消失

MultiInstanceActivityBehavior 第 266–269 行：

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/multi-instance-cleanup.java.txt" -->
```java
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager();
        Collection<String> executionIdsNotToSendCancelledEventsFor = execution.isMultiInstanceRoot() ? null : Collections.singletonList(execution.getId());
        executionEntityManager.deleteChildExecutions(multiInstanceRootExecution, null, executionIdsNotToSendCancelledEventsFor, DELETE_REASON_END, true, flowElement);
        executionEntityManager.deleteRelatedDataForExecution(multiInstanceRootExecution, DELETE_REASON_END, false);
```

这里清理多实例下的执行与关联数据，并传递 DELETE_REASON_END。这个类把该常量定义为 MI_END。达到提前结束条件后，尚未投票的任务可能因此结束。

**这表示系统按规则结束了剩余等待，不表示这些人投了赞成票。** 项目旧展示标签 auto-approved 也不能改变这个事实。

## 发布模型也经过命令入口

RepositoryServiceImpl 第 98–100 行：

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/deployment-command.java.txt" -->
```java
    public Deployment deploy(DeploymentBuilderImpl deploymentBuilder) {
        return commandExecutor.execute(new DeployCmd<Deployment>(deploymentBuilder));
    }
```

部署把模型资源交给引擎处理。它不等于“项目发布已完成”：项目还要锁定模型、校验草稿、记录业务版本和更新发布指针。更不等于在其他业务服务中已经创建了单据。

## 本页能证明什么

这些片段说明框架如何接收命令、继续执行和处理多人节点。它们没有提供本项目尚未暴露的认领、退回指定节点或运行实例迁移界面，也不证明某条本地审批已经跑通。

源码包已经与官方 SHA-256 校验文件核对。摘录的原始路径、行号、摘要及版权头保存在[来源登记](../reference/upstream/flowable-8.0.0/provenance.json)，完整许可见 [Apache 2.0](../reference/upstream/flowable-8.0.0/LICENSE.txt)。如需继续阅读概念，可自行打开 [Flowable 官方 BPMN 说明](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs/)；理解本页不需要跳出去。

下一页看[工程如何发布模型](workflow-models.md)。
