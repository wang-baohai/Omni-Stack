# 3. Framework internals: what Flowable does after complete

This page studies the project's Flowable 8.0.0. These are unchanged, continuous short excerpts from the official source archive. Read the explanations first; you need not understand every Java type.

## First stop: submit an operation through a common executor

TaskServiceImpl, lines 234–237:

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/task-command.java.txt" -->
```java
    @Override
    public void complete(String taskId) {
        commandExecutor.execute(new CompleteTaskCmd(taskId, (Map<String, Object>) null));
    }
```

The caller supplies a task ID. The framework wraps completion in CompleteTaskCmd and passes it to commandExecutor. Think of submitting an action ticket to a shared service desk. This is not yet the project's purchasing approval rule.

CompleteTaskCmd then calls TaskHelper.completeTask. The framework handles variables, completion listeners, history and events, among other work. That does not mean the project has checked whether this user may approve. Project guards are still required.

## Second stop: complete the task, then schedule execution to continue

TaskHelper, lines 178–184:

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/task-advance.java.txt" -->
```java
        completeTask(taskEntity, userId);

        // Continue process (if not a standalone task)
        if (taskEntity.getExecutionId() != null && !bpmnErrorPropagated) {
            ExecutionEntity executionEntity = processEngineConfiguration.getExecutionEntityManager().findById(taskEntity.getExecutionId());
            CommandContextUtil.getAgenda(commandContext).planTriggerExecutionOperation(executionEntity);
        }
```

The first call handles completion. The condition then checks that the task belongs to a process execution and that the relevant BPMN error was not propagated. If so, the engine schedules execution to advance.

Completion therefore does more than change one status row: it can reach a gateway, create a next task or finish a process. The project writes the vote and counters before complete so subsequent execution can read them. Scheduling advancement does not mean every model's remaining work finishes in the same request.

## Third stop: decide when a group of reviewers can move on

A multi-instance activity creates separate tasks for several people at the same node. This condition is from ParallelMultiInstanceBehavior, lines 231–234; the excerpt stops at the leave call:

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/parallel-completion.java.txt" -->
```java
            boolean isCompletionConditionSatisfied = completionConditionSatisfied(execution.getParent());
            if (nrOfCompletedInstances >= nrOfInstances || isCompletionConditionSatisfied) {
                leave(executionEntity, nrOfInstances, nrOfCompletedInstances, isCompletionConditionSatisfied);
```

The engine checks whether all instances completed **or** the model's completion condition is satisfied. With the project's condition—any rejection, or enough approval votes—the node may finish before everyone acts.

The expression must produce a boolean: true or false. The framework does not invent the company's required vote count. The task page connects ALL, ANY and the project counters.

## Fourth stop: explain disappearing remaining tasks

MultiInstanceActivityBehavior, lines 266–269:

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/multi-instance-cleanup.java.txt" -->
```java
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager();
        Collection<String> executionIdsNotToSendCancelledEventsFor = execution.isMultiInstanceRoot() ? null : Collections.singletonList(execution.getId());
        executionEntityManager.deleteChildExecutions(multiInstanceRootExecution, null, executionIdsNotToSendCancelledEventsFor, DELETE_REASON_END, true, flowElement);
        executionEntityManager.deleteRelatedDataForExecution(multiInstanceRootExecution, DELETE_REASON_END, false);
```

This removes multi-instance executions and related data using DELETE_REASON_END. The same class defines that constant as MI_END. A task whose reviewer has not voted can end this way when an early completion condition is met.

**The system ended the remaining wait according to a rule; those people did not cast approval votes.** The project's legacy auto-approved display label does not change that fact.

## Deployment also uses a command

RepositoryServiceImpl, lines 98–100:

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/deployment-command.java.txt" -->
```java
    public Deployment deploy(DeploymentBuilderImpl deploymentBuilder) {
        return commandExecutor.execute(new DeployCmd<Deployment>(deploymentBuilder));
    }
```

Deployment hands model resources to the engine. Project publishing also needs a model lock, draft validation, business version records and pointer updates. Deployment alone does not mean that publishing is complete, or that another business service has created a document.

## What these excerpts establish

They explain command submission, execution advancement and multi-instance handling. They do not expose claim, return-to-node or instance migration features that this project has not implemented, and they do not prove a local approval ran successfully.

The source archive was checked against the official SHA-256 checksum. Original paths, line ranges, digests and copyright headers are in the [provenance record](../reference/upstream/flowable-8.0.0/provenance.json); the full license is [Apache 2.0](../reference/upstream/flowable-8.0.0/LICENSE.txt). The [official Flowable BPMN guide](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs/) is optional further reading; this page does not depend on leaving the Wiki.

Continue to [project model publishing](workflow-models.en.md).
