# SRM Supplier Management Architecture and Implementation Baseline

> Engineering translation draft. Business terminology requires human review; stable codes and state values remain unchanged.

## 1. Scope and boundaries

`omni-srm` owns supplier master data, admission, lifecycle, evaluation, risk, invitations, and portal-company association. Auth owns users, roles, and tenant/DataScope resolution; Workflow owns approval runtime; Procurement owns RFQ and orders; Asset owns asset cards. SRM never reads another service database and never embeds Flowable.

The MVP covers supplier invitation, portal registration/enrollment, admission, activation/suspension/blacklist, performance evaluation, risk indicators, supplier 360, and quotation entry integration. Contract, settlement, category strategy, and advanced scoring remain outside the MVP.

## 2. Domain and lifecycle

Supplier, Invitation, Portal Association, Evaluation, Risk Indicator/Record, and Inbox Event are explicit aggregates or owned records. Every `srm_*` table is tenant-bound. Child resources inherit visibility through Supplier or Evaluation roots; DataScope must not append owner columns to child tables that do not contain them.

Supplier lifecycle transitions are server commands. Admission and activation require validated data and approval outcomes; suspension, reactivation, and blacklist preserve audit evidence. Evaluations use versioned criteria and deterministic weighted scoring. Risk level is calculated from active indicators and cannot be freely edited.

## 3. Portal enrollment and Saga

Default `USER` may enroll only. `srm:portal:profile` and `srm:portal:evaluation` require `SUPPLIER` plus an active `srm_supplier_portal_user` association. Enrollment requires both `inviteToken` and a client `requestId`; the request ID is an idempotency key.

SRM commits the local association request and Outbox event first. Auth consumes the role-assignment request, assigns `SUPPLIER` idempotently, and emits a result. SRM consumes the result through Inbox and marks the Saga successful or failed. Role-assignment failure never creates an unverified active association. Portal user IDs are never written to internal `owner_user_id` or `owner_unit_id` fields.

## 4. Security and DataScope

Gateway identity, tenant filter, functional permission, permission-aware DataScope, tenant/scope interceptors, and row AccessGuard form the trust chain. Missing context fails closed. TenantLine applies only to `srm_*`; Outbox relay tables remain outside tenant interception. Write commands check tenant, aggregate visibility, lifecycle, and optimistic version.

Internal management roles and portal roles are separate. Portal endpoints derive the associated supplier from the authenticated user and never accept an arbitrary supplier ID as authorization. PII is masked unless explicitly permitted. Operation logs and events contain minimal IDs/states, not full contact or banking data.

## 5. Cross-service consistency

Auth user/unit/role operations are tenant-restricted. Workflow starts are idempotent and completion is consumed as an event. Procurement queries eligible active suppliers and exposes RFQ invitations; SRM portal submits quotations to Procurement with request ID and version checks. Asset does not read SRM tables and uses explicit supplier snapshots/IDs from procurement events where needed.

Outbox uses an explicit tenant ID; every consumer validates tenant, writes an Inbox record plus business change atomically, and tolerates duplicate or out-of-order delivery. Network calls do not occur inside SRM database transactions.

## 6. API, UI, and persistence

Controllers use `R<T>` / `R<PageResult<T>>`; writes declare `@PreAuthorize`. Management pages live under `views/srm/`; portal pages under `views/supplier-portal/`. All buttons use matching `v-permission`, responsive states, and stable codes separate from localized labels.

Structure is maintained under `database/changelog/srm/`; idempotent defaults and RBAC live in `scripts/sql/seed/srm.sql` and `scripts/sql/seed/auth.sql`, with `database/seed/manifest.yaml` checksums and assertions refreshed in the same change.

## 7. Verification

Tests cover lifecycle legality, tenant isolation, every DataScope mode, child-root inheritance, portal enrollment replay, role Saga success/failure/duplicate messages, invite expiry/revocation, evaluation math, risk aggregation, quotation concurrency, PII/XSS/audit behavior, and management/portal browser flows. Acceptance proves portal users cannot access management data, internal owners are not polluted by portal IDs, and failures never widen access.

See [SRM module truth](../srm.en.md) and [SRM user flow](../guides/srm-flow.en.md).
