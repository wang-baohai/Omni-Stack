# Asset Management Architecture and Implementation Baseline

> Engineering translation draft. Human review is required; stable codes, API values, and state names are preserved.

## 1. Scope and boundaries

`omni-asset` owns the asset ledger, procurement receipt ingestion, assignment/acceptance/return, transfer, disposal, and overview. Procurement owns purchase orders and receipts; Workflow owns approval runtime; Auth owns identity/RBAC/DataScope; SRM owns suppliers. Asset never reads another service database and never embeds Flowable or `omni-common-workflow`.

The MVP manages physical asset cards and custody. Inventory valuation, depreciation posting, accounting integration, maintenance work orders, and complex warehousing are later capabilities.

## 2. Domain and lifecycle

Asset, Transfer, Disposal, and Inbox Event are the main aggregates. Every `ast_*` table is tenant-bound. Management views scope by `owner_user_id/owner_unit_id`; “My Assets,” acceptance, and return use fixed `current_user_id`. Transfer/disposal child rows inherit access through `ast_asset`.

Asset state transitions are server commands for available, pending acceptance, in use, transfer/disposal processing, returned, and disposed states. Arbitrary status edits are forbidden. Each write validates tenant, visibility, current state, active operation, and optimistic version.

## 3. Procurement receipt ingestion

`ProcurementGoodsReceiptConsumer` creates cards only when `qualityStatus=PASS`, `assetManaged=true`, and `assetQuantity` is an exact positive integer. Decimal or fractional quantities that cannot map exactly to units are rejected and retried/dead-lettered according to the event policy; they are never silently rounded.

The Inbox event ID prevents duplicate event consumption. Source receipt-line plus unit sequence prevents duplicate card creation even if semantically equivalent events use different message IDs. Card monetary fields preserve source precision and JSON monetary fields (`purchaseAmount`, `residualValue`, event prices) are decimal strings, never JSON numbers.

Backfill uses an authenticated internal Procurement endpoint, explicit tenant, bounded pages, the same validation/idempotency keys, and observable progress. It cannot bypass normal ingestion rules.

## 4. Assignment, transfer, and disposal

Assignment changes custody to the target user/unit and creates an acceptance action. Only the fixed current user may accept or return their assigned asset. Row-level permissions cannot be inferred from broad management ownership for these self-service commands.

Transfer and disposal share atomic occupancy fields `active_operation_type` / `active_operation_id` on `ast_asset`; only one active operation may exist. Starting an operation stores `previous_asset_status`, reserves occupancy, and starts Workflow idempotently outside the local transaction. Approval completes the command. Cancellation or rejection restores `previous_asset_status` and clears occupancy in the same transaction. Duplicate/out-of-order completion events are harmless.

## 5. Tenant, RBAC, DataScope, and integration

TenantLine applies only to `ast_*`; `sys_mq_message` remains outside interception. Gateway identity → tenant filter → `@PreAuthorize` → aggregate-specific scope → MyBatis → AccessGuard fails closed. Invisible rows return 404. Write endpoints use resource/action permission codes and matching frontend `v-permission`.

Workflow starts and results use idempotent requests plus Inbox events; Flowable never updates Asset tables. Procurement events and backfill DTOs are versioned contracts. SRM references are IDs/minimal snapshots only. Outbox calls pass tenantId explicitly, and consumers establish/clear tenant context in `finally`.

## 6. API, UI, persistence, and observability

Controllers return `R<T>` / `R<PageResult<T>>`. Frontend pages under `views/asset/` cover overview, ledger, My Assets, transfers, and disposals with responsive list/detail/action/error states. Stable codes are separate from localized labels.

Structure migrations live under `database/changelog/asset/`; RBAC seed changes are in `scripts/sql/seed/auth.sql` with manifest checksum/assertion updates. Logs and metrics record tenant-safe IDs, state, event/message/trace IDs, latency, Inbox outcome, and backfill progress without PII or payloads.

## 7. Verification

Tests cover lifecycle legality, tenant/DataScope and child inheritance, fixed-current-user self-service, receipt eligibility and exact quantities, dual idempotency, monetary string transport, backfill replay, assignment acceptance/return, competing transfer/disposal occupancy, cancellation/rejection restoration, Workflow retry/duplicate/order, Outbox/Inbox, permissions, XSS/audit, and the full browser flow.

See [Asset user flow](../guides/asset-flow.en.md).
