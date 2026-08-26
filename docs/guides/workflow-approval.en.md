# Workflow Modeling, Publishing, Approval, and Candidate Rules

The independent `omni-workflow` service owns process runtime. Business services own domain state and use internal contracts to start, withdraw, or query processes; they do not depend directly on Flowable.

## 1. Model Lifecycle

```text
Create → Edit Draft → Server Validation → Publish Version → Business Start → Approval Complete
```

A model key is tenant-unique and matches BPMN `<process id>`. `BpmnXmlValidator` runs before publishing, and a row lock prevents concurrent duplicate versions. A published version is immutable; later draft edits do not change running instances.

## 2. Modeling

1. Open Workflow → Process Models.
2. Create a stable key, name, and category.
3. In Design, add events, approval/service tasks, gateways, and flows.
4. Select an element and edit properties on the right.
5. Save the draft.
6. Validate and resolve every error before publishing.

Do not edit BPMN XML in the database or expose model version IDs to business users.

## 3. Approval Candidates

Approval tasks use the `omni:assignment` JSON extension as the sole configuration source:

- `roleCode`: approval role.
- `anchorType` and `anchorParams`: organizational anchor.
- `scopeMode`: same unit, parent, descendants, and related scopes.
- `fallbackStrategy`: behavior when no candidate is found.
- `approvalMode`: any approver or all approvers.

`ScopedRoleAssignmentListener` resolves candidates at task start. A new anchor or scope requires resolver, validator, preview API, and tests described in the [Workflow Guide](../workflow.en.md).

## 4. Countersign

Multi-instance approval obtains candidates from `candidateResolver.resolve(execution)`. Completion uses `approvedCount`, `rejectedCount`, and `requiredApprovals`. Tasks ended early by the completion condition have delete reason `MI_END`; read `HistoricTaskInstance.getDeleteReason()` when determining results.

## 5. Publishing and Business Binding

Business configuration selects a current published version by category. The procurement approval-rule UI presents business name, scope, amount range, match simulation, and path preview without requiring a version ID.

Publishing a new version never migrates running instances. Existing instances require an explicit compensation or migration design.

## 6. Start and Approval

Starts include stable business type/ID, title, starter, tenant, and variables. A persistent request reservation makes retries idempotent. The workspace lists tasks to do, initiated instances, completed tasks, progress, and approval records.

Completion validates task ownership, comment, and required domain state or optimistic lock. Domain coordinators own cancellation, withdrawal, and retry; the frontend never manipulates Flowable tables.

## 7. Acceptance Checklist

- Save, validation, and publishing provide clear results.
- Candidate preview matches runtime resolution.
- Any, countersign, rejection, and empty-candidate paths are tested.
- New versions do not change running instances.
- Users process only their candidate tasks.
- Process and business records are mutually traceable.

