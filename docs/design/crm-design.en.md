# CRM Module Architecture and Implementation Baseline

> Translation status: engineering draft based on the Chinese source digest in `docs/docs-manifest.yaml`; business terminology requires human review. Stable identifiers, permission codes, and state values are intentionally not translated.

## 1. Design conclusions and scope

`omni-crm` is an independent tenant-aware service for leads, customers, contacts, opportunities, activities, and sales overview. Auth owns identity, tenant membership, RBAC, and permission-aware DataScope resolution. CRM never reads the Auth database directly and stores only referenced user/unit IDs. Flowable remains in `omni-workflow`; RocketMQ is at-least-once transport; XXL-JOB runs batched reminders rather than one job per record.

The MVP answers lead funnel, overdue follow-up, customer 360, opportunity pipeline, conversion/win rate, and audit questions. Public-pool, bulk import/export, tags, merge, contract, and advanced approval are later phases.

## 2. Domain and data model

Aggregate roots are Lead, Customer, Contact, Opportunity, and Activity. Every `crm_*` table contains `tenant_id`; authorizable roots also carry `owner_user_id`, `owner_unit_id`, `version`, and soft-delete/audit fields. Tenant-prefixed indexes support owner, status, next follow-up, and duplicate searches.

Money uses `DECIMAL(18,2)` and `BigDecimal`; currency is an ISO 4217 code. The MVP uses one configured currency per tenant and never sums mixed currencies. Business numbers are tenant-unique and never generated with `MAX + 1`.

Lead conversion locks the lead, validates visibility and state, creates or links Customer/Contact/Opportunity in one CRM transaction, inserts immutable conversion evidence, updates the lead, and writes domain events to the local Outbox. The unique lead conversion record makes retries idempotent.

## 3. State machines

- Lead: new/working/qualified then converted or disqualified; converted/disqualified records are terminal except for explicitly designed recovery commands.
- Customer: active/inactive/blacklisted with guarded status and blacklist commands.
- Opportunity: stages belong to one tenant pipeline; closed-won/closed-lost are explicit terminal outcomes and every move writes stage history.
- Activity: planned/completed/cancelled; completion and cancellation are commands, not arbitrary field edits.

All transitions are enforced on the server and covered by legal/illegal transition tests.

## 4. Tenant, RBAC, and DataScope

The trust chain is Gateway identity → CRM tenant filter → `@PreAuthorize` → permission-aware DataScope → MyBatis tenant/scope interceptors → row-level `CrmRecordAccessGuard`. Missing tenant, scope, internal token, timeout, or tenant mismatch fails closed with 403/503; it never degrades to unfiltered access.

Auth exposes a tenant- and permission-bound internal DataScope contract. `@PreAuthorize` and `@CrmDataScope` use the same complete permission code. ThreadLocals are always cleared in `finally`. TenantLine applies only to `crm_*`; `sys_mq_message` is excluded so the relay can scan all tenants. Invisible rows return 404 to prevent ID enumeration. Writes use tenant + id + scope + version.

Customer 360 resolves Customer, Contact, Opportunity, and Activity permissions independently; customer visibility never grants implicit access to all children. Default `USER` receives no CRM permissions. UI `v-permission` mirrors backend codes but is not a security boundary.

## 5. APIs and cross-service consistency

Controllers return `R<T>` and `R<PageResult<T>>`; list size is capped at 100. Write endpoints require resource/action permission codes. Owner assignment validates the target user and unit through tenant-restricted Auth APIs.

CRM stores stable dictionary codes. Lifecycle and permission semantics are code enums and cannot be changed by dictionaries. Workflow starts use a tenant/business-key idempotency key and complete asynchronously through Inbox events. Flowable never updates CRM tables directly, and Feign/MQ network calls never run inside the CRM transaction.

Domain events use `ReliableMessageRelay.send(bindingName, envelope, tenantId, eventId)`. Events contain IDs, states, and minimal snapshots, not phone, email, address, or notes. Consumers validate tenant, establish and clear system context, and atomically write `crm_inbox_event` plus the business change with `(consumer_name,event_id)` uniqueness and aggregate-version ordering.

## 6. Privacy, XSS, audit, and frontend

Operation logging masks PII, supports snapshot opt-out, preserves trace/event IDs, and applies the same tenant/scope to snapshot reads. CRM implements `XssConfigProvider` and reads tenant XSS keys from Redis DB 0. Logs, Outbox pages, backups, and dead letters are treated as PII-bearing administration surfaces.

Frontend routes live under `views/crm/**/index.vue`; all actions use matching `v-permission`. Stable API values are separate from localized labels. Pages cover overview, lead, customer 360, contacts, opportunity list/board, and activity timeline with responsive and permission-denied states.

## 7. Non-functional requirements and acceptance

All lists are paginated and indexed. Optimistic locking protects writes; Lead conversion, Outbox, Inbox, and scheduled claims are idempotent. RocketMQ outage preserves business plus Outbox and retries later. A horizontally scaled relay requires claim/lease or `SKIP LOCKED`.

Tests cover state machines, concurrent conversion, tenant isolation, each DataScope level, fail-closed context, optimistic conflicts, Outbox rollback, Inbox duplicates/order, PII masking, XSS, permissions, and the main browser flow. Acceptance proves sales representatives see self data, managers see department and descendants, CRM admins remain tenant-bound, PII is masked without permission, direct API bypass returns 403, and unauthenticated access returns 401.

## 8. Maintained implementation locations

- Service: `omni-backend/omni-crm/`
- Frontend: `omni-frontend/src/views/crm/`, `omni-frontend/src/api/crm.ts`
- Structure migrations: `database/changelog/crm/`
- Idempotent seeds: `scripts/sql/seed/crm.sql`, `scripts/sql/seed/auth.sql`
- Operational truth: [CRM module guide](../crm.en.md) and [user flow](../guides/crm-flow.en.md)
