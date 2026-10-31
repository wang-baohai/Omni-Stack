# 5. Data scope: who sees which records inside a company?

Tenancy separates companies, but each company also divides work. Li maintains his customers; Wang manages the department. Some roles also need child-department records.

Data scope expresses this. Operation eligibility and visible records must both be satisfied. Multiple roles do not let a person arbitrarily use the broadest role for every operation.

## Understand the six scope values

| Value | Initial meaning |
|---|---|
| SELF | Records assigned to this person |
| DEPT | Records belonging to the primary organization |
| DEPT_AND_BELOW | Primary organization and eligible descendants |
| CUSTOM | Organizations configured for the role |
| TENANT | No further self/organization narrowing within the tenant |
| ALL | No extra row condition at the scope layer; cross-tenant access depends on the interface and tenant rules |

SELF's actual column depends on the business: owner, requester, or a fixed current asset user. It does not mean any column named user_id.

Some handlers add no further conditions for either ALL or TENANT, but the values are not identical across every interface.

## Combine permission and scope

A common-service method annotated with @ServiceDataScope asks Auth to resolve scope for the current user, tenant, and specific permissionCode. It validates the returned identity and binds the result during the method.

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java" -->
```java
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        try {
            InternalDataScopeDTO scope = dataScopeResolver.resolve(identity, annotation.permissionCode());
            validate(scope, identity, annotation.permissionCode());
            ServiceDataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            ServiceDataScopeContext.clear();
        }
```

It obtains trusted identity, resolves and validates scope, executes the original business method, then clears scope. This is the continuous core, not a claim that every method and service uses the annotation.

The permissionCode-aware resolver considers only enabled roles granting that exact permission. Ordinary Auth request resolution merges the relevant role scopes differently; distinguish the two entries.

For example, a broad user-management role does not automatically widen customer access. First confirm that it grants the customer operation.

<!-- omni:modules:crm -->
## Read one customer-record branch

CRM first limits processing to its registered business tables, then reads this operation's scope. The continuous condition-selection branch is:

<!-- omni:source path="omni-backend/omni-crm/src/main/java/com/omni/crm/security/CrmDataPermissionHandler.java" -->
```java
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        ServiceDataScopeContext.ScopeInfo info = ServiceDataScopeContext.get();
        if (info == null || info.effectiveScope() == null) {
            return deny(alias);
        }
        return switch (info.effectiveScope()) {
            case "ALL", "TENANT" -> null;
            case "SELF" -> equals(alias, "owner_user_id", info.userId());
            case "DEPT" -> equals(alias, "owner_unit_id", info.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> in(alias, "owner_unit_id", info.accessibleUnitIds());
            default -> deny(alias);
        };
```

Missing valid scope creates a nonmatching condition. SELF uses owner_user_id; DEPT uses owner_unit_id; descendants/custom scope use the visible organization set. ALL/TENANT add no owner condition here, while the separate tenant plugin retains the company restriction.

A salesperson therefore sees customers both owned by that person and belonging to Company A. The manager sees customers in the department scope and Company A. Widening ownership must not remove the tenant condition.

This explains the source boundary, without claiming every combination has been rerun in the current environment.
<!-- omni:modules:end -->

## Why child tables need parent ownership

A detail row may have no owner_user_id. Supplier contacts, evaluation details, or purchase lines that inherit visibility through their parent must be checked through that relationship.

Think of the parent as a folder and details as pages. Whether you may read a page depends on folder access; do not invent an owner field that the page does not have.

Domains in the full repository have different mappings:

| Object | Scope basis |
|---|---|
| Registered CRM customer and related tables | Their owner and organization columns |
| SRM supplier/evaluation child resources | Inherit through supplier/evaluation relationships; do not append nonexistent owner columns |
| Purchase requisition | Requester and requesting organization |
| RFQ, purchase order, goods receipt, and details | Owner/organization, with details inheriting from their parent |
| Asset management, my assets, acceptance/return | Management uses owner; personal actions use fixed current_user_id; children inherit through the asset |

This describes full-repository responsibilities. Generated projects contain only the selected preset's capabilities; an absent module has no corresponding menus or data.

## Why organization trees also need tenants

Child organizations are not simply every department with a larger ID. The project expands relationships and paths inside the current tenant.

DEPT uses the enabled primary organization; DEPT_AND_BELOW expands eligible descendants; CUSTOM expands configured role roots. Auth does not apply identical enabled-state checks to CUSTOM roots and descendants. A descendant query with status=1 does not prove the same root check.

SAME_UNIT and UNIT_AND_BELOW describe whether a role assignment applies at the same organization or also descendants, and participate in finding approval handlers. They are configured separately from self/department/tenant query scope.

## Explicit exceptions and failure limits

Online-user management reads Redis and checks company and organization through the user database. ALL currently permits cross-tenant access; other scopes first restrict the tenant. Domain business lists do not thereby gain the same cross-tenant capability.

Missing-scope behavior also differs. Registered CRM and similar domain tables deny matching. Auth's own handler adds no condition for missing context, unknown scope, or unregistered tables and relies on other API/query checks. Installing a plugin does not make every project path automatically fail closed.

Scope-resolution failure, absent operation permission, and invalid business state are different problems. Locate the condition instead of forging headers, sharing administrator accounts, or removing restrictions.

Continue with [caches and background work](tenancy-background.en.md) to complete the paths beyond HTTP.
