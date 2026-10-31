# 5. Project practice: initiators, repeat requests and finding approvers

Once a requisition passes business checks, workflow still needs to know its initiator, rule version and the actual person represented by department manager.

## Two start entry points

| Entry | Caller | Identity and repeat handling |
|---|---|---|
| Ordinary start | User through the gateway, process-instance/start | Current identity and workflow:instance:start; no automatic internal reservation deduplication |
| Internal start | Business service, internal/workflow/process-instance/start | Shared token and tenant consistency; caller supplies a trusted initiator and reserves request/business keys |

An internal endpoint is not permission to start for anyone whose ID is known. The shared token protects service calls; the business service still performs user authorization. startUserId/startUserName must not become arbitrary end-user choices.

Ordinary starts reject simulation as another user and remove reserved initiator keys from incoming variables. From ProcessInstanceServiceImpl:

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/ProcessInstanceServiceImpl.java" -->
```java
        safeVariables.remove("initiator");
        safeVariables.remove("startUserId");
        safeVariables.remove("authenticatedUserId");
        safeVariables.remove("START_USER_ID");
        // 启动会同步执行首个节点，候选人解析必须在此时已能读取可信发起人。
        safeVariables.put("initiator", String.valueOf(effectiveUserId));
```

This makes the trusted initiator available when the first node resolves people. Flowable's authentication context is cleared in finally so the next request on the thread does not inherit it. This startup cleanup does not imply reserved fields in later approval variables are filtered.

## Choose an explicit version, not only a name

With modelVersionId, the project requires a same-tenant, published version with an engine definition. Ordinary starts retain a processKey-and-tenant compatibility path. That path need not match the model catalog's current published pointer.

Internal business starts check published availability again; validation when saving an approval route is insufficient. Asset transfer, asset disposal and supplier onboarding also require an exact category/business-type match.

## Idempotency: recognize the same request after a network retry

Idempotency here means replaying the same business intent without starting another process. Internal reservation enforces tenant-local uniqueness for requestId and for businessType + businessKey. It attempts insertion and resolves conflicts:

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowProcessStartRequestServiceImpl.java" -->
```java
        if (requestMapper.insertIgnore(candidate) == 1) {
            return new Reservation(candidate, true, true);
        }
        return resolveConflict(candidate);
    }
```

A repeat does not silently succeed in every case. The project compares business identifiers, model version and initiator, rejecting mismatches. It does not compare title, display name or a full digest of all variables.

A STARTED record returns its original instance with replayed=true; an in-progress request returns 409. A FAILED record can be reserved again with the same requestId. Changing requestId while hitting the same failed business key does not automatically grant retry rights. After a timeout, use formal queries to establish state and retry the same intent with the same requestId rather than inventing new IDs.

## Turn a role into a person

A role describes eligibility. An organization narrows where to look. An anchor chooses the organization from which this request begins searching. BPMN's omni:assignment extension stores the rule, for example:

```json
{
  "assignmentType": "SCOPED_ROLE",
  "roleCode": "SUPER_ADMIN",
  "anchorType": "START_USER_PRIMARY_UNIT",
  "anchorParams": {},
  "scopeMode": "SAME_UNIT",
  "fallbackStrategy": "ERROR",
  "approvalMode": "ALL"
}
```

This illustrates the configuration shape. It does not recommend administrators for every approval or create accounts, roles or organizations. Configure actual roles and organizations in the target tenant.

| Anchor | Current starting point |
|---|---|
| START_USER_PRIMARY_UNIT | Initiator's enabled primary organization |
| PARENT_BY_TYPE, PARENT_CHILDREN | Search upward by targetType; use primary organization if type is absent |
| CHILD_BY_CODE, SIBLING_BY_CODE | Direct child or sibling organization by code |
| DEPT_BY_CODE | Enabled organization by code within the tenant |
| ABSOLUTE_UNIT | Ordinary resolution reads unitIds or unitId |
| PARENT | Parent of the primary organization |
| CHILD_UNIT, SIBLING_UNIT | Read configured unitId directly; kinship with the initiator is not automatically established |

SAME_UNIT searches the same organization; UNIT_AND_BELOW includes descendants; CHILDREN_ONLY uses immediate children. Unknown scopeMode currently falls back to the same organization. These are candidate search scopes, not the six dataScope permission modes.

## Resolve people before the collection is consumed

The designer installs a node start execution listener. It resolves candidates **before multi-instance creation reads the collection**. From ScopedRoleAssignmentListener:

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java" -->
```java
            // 3. 将候选人列表写入流程变量，供多实例 collection 使用
            delegateExecution.setVariable("candidateUserIds",
                    candidateUserIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()));

            candidateResolutionService.initApprovalCounters(
                    delegateExecution, approvalMode, candidateUserIds.size());
```

candidateUserIds is the collection, userId is the per-person element, and the assignee refers to userId. Counters are initialized on this activity's multi-instance root. A root-local marker prevents reinitialization or reusing a previous node's votes.

The legacy TaskListener entry only logs and cannot replace this start listener. A compatible collection expression shares the resolution service, but legacy delegates differ in error and fallback handling. Missing assignment or roleCode can also cause an early return. Do not promise identical failure behavior for all old models.

## No candidates must never become invented approval

| Strategy | Current behavior |
|---|---|
| ERROR, default and unrecognized strategies | Throw an error |
| ASSIGN_ADMIN | Find SUPER_ADMIN candidates in organization ID 1 of this tenant; not hardcoded user 1; fail if none |
| ESCALATE_PARENT | Search upward from the anchor's parent, at most 20 levels; use the first match, otherwise fail |

The upward ABSOLUTE_UNIT path reads a single unitId; it does not select the first unitIds item automatically. Missing organizations, disabled users, mismatched roles or scope, and lookup errors can all leave no candidates. Correct legitimate configuration and data, then recover under business retry rules. Do not bypass this with automatic approval.

Publish a new model version after configuration changes. There is no separate runtime candidate table that automatically changes every running instance. Continue to [task actions and multiple reviewers](workflow-tasks.en.md).
