# Omni-Stack learning and maintenance Wiki

Baseline: main `fc165e3` and its documentation remediation branch. Readers are expected to know basic Java/Vue and be new to this project. Chinese is the content baseline; [简体中文](index.md), [日本語](index.jp.md), and [한국어](index.kr.md) stay aligned. Actual translation review status, distinguishing HUMAN_REVIEW from owner-authorized AI_REVIEW, is recorded in the [documentation manifest](../docs-manifest.yaml).

## 1. Start from scratch

Complete the first four steps in order, then choose a business path for your role. Verify each expected result in local Docker.

| Step | Read and do | Expected result |
|---|---|---|
| 1 | [Get the code, understand the tree, and start](../guides/quick-start.en.md) | Migrator exits successfully, selected services are healthy, frontend is reachable |
| 2 | [Login and tenants](../guides/authentication.en.md) | Real CAPTCHA login with the intended tenant and identity |
| 3 | [Roles, menus, buttons, and data scope](../guides/permissions.en.md) | Admin and employee capabilities differ; unauthorized requests are denied |
| 4 | [Seeds, teaching data, and test fixtures](data-and-fixtures.en.md) | Distinguish an empty environment, base seeds, and business demonstration records |
| 5 | [Select a preset](../preset-quick-selection.en.md) and [generate a project](../guides/scaffold-development.en.md) | Explain running a subset versus generating a pruned project |

## 2. Learn by role

| Role or objective | Path | Prerequisites |
|---|---|---|
| Platform administrator | [System, security, and audit](../guides/system-security-audit.en.md) | core; tenant administrator role |
| Employee and approver | [Workflow](../guides/workflow-approval.en.md), [personal and system jobs](../guides/scheduling.en.md) | workflow; candidate assignment and task ownership |
| Sales | [CRM leads to opportunities](../guides/crm-flow.en.md) | crm; sales role and data scope |
| Supplier administrator and supplier | [SRM onboarding and portal](../guides/srm-flow.en.md) | supply-chain; invitation, SUPPLIER role, and active association |
| Requester, buyer, supplier | [Requisition to receipt](../guides/procurement-flow.en.md) | supply-chain; approval models, active suppliers, procurement permissions |
| Asset administrator, user, approver | [Asset creation, allocation, transfer, disposal](../guides/asset-flow.en.md) | supply-chain; accepted asset-managed receipts and approval models |
| Operator | [Operations, backups, upgrades](../guides/operations-upgrade.en.md), [troubleshooting](../guides/troubleshooting.en.md) | Confirmed local environment and backup boundaries |

Base seeds do not guarantee orders or approvals shown in historical screenshots. Business numbers in images are examples; use IDs returned by your own operations. Do not change SQL state to bypass approvals, quotes, or receipt processing.

## 3. Read source and extend the project

- [Source reading and breakpoints](source-debugging.en.md): follow a request through permissions, transactions, events, tests, and failures.
- [Service and CRUD generation](../guides/scaffold-development.en.md): dry-run before generating, integrating, and verifying.
- [Dependency matrix](../preset-dependency-matrix.en.md), [maintenance](../preset-maintenance.en.md), [custom presets](../custom-preset-tutorial.en.md), [upgrades](../preset-upgrade-guide.en.md): fact tables derive from the catalog.
- [Architecture](../architecture.en.md), [API contract](../api-contract.en.md), [core flows](../core-flows.en.md): current engineering reference for maintainers.
- [Backend](../backend-patterns.en.md) and [frontend conventions](../frontend-patterns.en.md): read the relevant rules before changing code.

## 4. Glossary

| Term | Meaning in this project |
|---|---|
| preset | Module selection; catalog computes its full dependency closure |
| migrator | One-shot process for schema migration, idempotent seeds, and bootstrap configuration before applications start |
| tenant | Isolation boundary; ALL scope still cannot cross tenants |
| RBAC / DataScope | Functional permission and row visibility, both enforced |
| Gateway assertion | Signed gateway identity; downstream services distrust client identity headers |
| Outbox / Inbox | Transactional pending events / consumer deduplication records |
| Saga | Distributed steps and compensation, not cross-service ACID transactions |
| fixture | Test or teaching setup with explicit tenant, business keys, and cleanup boundaries |
| ADR | Historical decision with rationale, consequences, and supersession |
| Harness | Execution loop formed by facts, constraints, tools, and verification |

## 5. Website and maintenance

[Independent documentation site](site-maintenance.en.md) covers Docker builds, port 4173, local search, static output, and repository links across hosts. Repository Markdown is the sole content source; locale directories are generated from the manifest.

Current facts, designs, tutorials, ADRs, and acceptance history have separate responsibilities; see [repository governance](../repository-governance.md). Historical acceptance only proves its original scope. A site preview does not mean translation review or release is complete.
