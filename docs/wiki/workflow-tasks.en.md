# 6. Project practice: votes, rejection and multiple reviewers

Candidates are the people found by a rule; an assignee is the person on a specific task; the person in an approval record actually acted. Designer-generated multi-instance tasks are assigned directly. There is no separate unclaimed task pool.

## First establish that the task currently belongs to you

Complete, add signer, remove signer and delegate require their respective workflow:approval:complete, add-signer, remove-signer and delegate permissions. The service also queries the task in the current tenant and requires the exact assignee:

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

A missing or cross-tenant task gives business code 409: refresh the list. A same-tenant task assigned to someone else gives 403; more clicks will not grant access. An administrator role does not automatically allow acting for that assignee.

## Approval or rejection is first one vote

approved is this decision; comment is the opinion. The project writes a comment and task-local result, and writes approved to the process root. Later votes overwrite that root value. A multi-person total therefore cannot rely on root approved alone.

Rejection uses the same completion endpoint with approved=false. It does not automatically jump to the previous node. The model's conditions and connections decide whether the process ends or follows another path.

## A group needs a shared tally

Think of the multi-instance root as the tally sheet for this meeting, and tasks as individual ballots. The project searches upward at most 32 levels, identifying a root-local nrOfInstances. If none is found, it retains compatibility updates along the existing execution scope. The write is:

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/approval/ApprovalServiceImpl.java" -->
```java
        if (miRoot != null) {
            runtimeService.setVariableLocal(miRoot, counterKey, currentCount + 1);
        } else {
            runtimeService.setVariable(execId, counterKey, currentCount + 1);
        }
```

Approval increments approvedCount; rejection increments rejectedCount. The project does this **before** taskService.complete. The designer's completion condition is:

```text
${rejectedCount > 0 || approvedCount >= requiredApprovals}
```

Default ALL initially requires all candidates; ANY sets the threshold to 1. The expression also allows a rejection to end this activity. Handwritten BPMN must provide appropriate completion conditions and rejection routing. Setting ALL or ANY alone does not teach the engine the whole business rule.

Additional variables are applied afterward to task and process scope, without filtering reserved counter or routing keys. Do not claim clients can never overwrite critical variables. An extension should design an allowlist and rejection tests.

## What signer changes and delegation actually change

| Action | Implementation | Limits |
|---|---|---|
| Add signer | Add an execution to this multi-instance activity with userId | Current assignee invokes it; model variables must match; not a generic single-person-node operation |
| Remove signer | Find the target's pending task in this instance/node and remove its multi-instance execution | Cannot retract an existing vote; several matching tasks make singleResult fail |
| Delegate | Change the task's assignee | Direct reassignment, not a return-to-original-assignee cycle |

After signer changes, ALL recalculates the threshold as approved votes plus tasks still pending. ANY does not recalculate; neither does a missing multi-instance root. Adding a signer creates no separate approval comment; a new task is not a vote.

Removal calls deleteMultiInstanceExecution(..., false). **The parameter is executionIsCompleted: false means the removed execution is not counted as completed on the parent multi-instance execution. It is not a cascade-delete switch.** Nested multi-instance behavior needs separate validation.

The core delegation statement is:

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/approval/ApprovalServiceImpl.java" -->
```java
        taskService.setAssignee(taskId, targetUserId);
```

This does not use Flowable delegateTask/resolveTask. Signer and delegation targets are currently only parsed as numeric IDs, without complete existence, enabled-state or tenant checks. A button and a functional permission do not prove target identity validation.

## Why can an untouched task already have ended?

After an ANY approval or another early-completion condition, remaining tasks may end with MI_END. Records retain the legacy auto-approved label for deletion reasons starting with MI; other deletions become cancelled. **The former is a system skip, not that person's approval vote.**

Normal completion reads task-local approved; if absent, compatibility handling defaults to approved. The displayed opinion is the first comment, not guaranteed to be the latest. Keep those read rules separate from actual human voting.

## Termination and actions not exposed

Ordinary termination requires workflow:instance:terminate, the same tenant, the actual initiator and an in-progress instance. It deletes the runtime instance rather than pausing for later recovery. Internal business termination is a separate trusted-service path; applicable cross-service paths write CANCELLED.

There are no separate claim/unclaim, workflow withdrawal, alternative transfer, delegate-and-return, return-to-node rejection, running-instance version migration, task suspend/activate or administrator override approval/termination endpoints. Suspending a definition is not suspending one task. A business withdrawal may use internal termination; that does not create a general workflow withdrawal API.

Finish with [results and troubleshooting](workflow-events.en.md).
