# Base seeds, teaching data, and test fixtures

Baseline: main `fc165e3`. Complete [first start](../guides/quick-start.en.md) and confirm the Compose project, tenant, and records. This page does not authorize resetting existing data.

## 1. Identify the source

| Source | Responsibility | When to use |
|---|---|---|
| `database/changelog/` | Immutable schema and required data migration | Fresh and upgrades through migrator |
| `scripts/sql/seed/` | Idempotent base seeds | Referenced by migrator; do not replay manually |
| `database/seed/manifest.yaml` | Digests, natural keys, assertions | Verify base data without requiring user data to be empty |
| `database/seed/history/` | Frozen historical seed versions | Replay existing changeSets; do not edit freely |
| `scripts/fixtures/`, test classpath | Bounded identities and test data | Confirmed local validation; not production bootstrap |
| Tutorial records | Real business demonstrations | Create through UI or public APIs and use returned IDs |

Admin accounts, development tenants, menus, and approval models are base capabilities. Approved requisitions, quotes, orders, and assets are business records. Seeds do not guarantee historical `PR-1-1` or `RFQ-1-1` records.

## 2. Fresh, replay, adoption

Fresh means an isolated empty database environment. Compose starts dependent applications only after migrator exits successfully. Repeated startup is upgrade replay; do not delete volumes to hide failures.

Existing-data adoption requires the frozen baseline in `database/adoption/` and real backup evidence. Do not mark an unchecked database as migrated. Keep logs and versions on failure, apply forward repairs, and never rewrite executed changeSets.

Assertions sample natural keys in official seeds; user additions are excluded from fixed counts. Permission or seed changes require source, SHA-256, and assertion updates. Do not alter expectations to hide missing base records.

## 3. Create teaching records

1. Choose the preset: supply-chain for purchasing/assets, crm for sales.
2. Record tenant, admin, requester, approver, and supplier. A supplier needs SUPPLIER and an active portal association; a username alone proves nothing.
3. Prefix demonstration names with `WIKI-<unique-run-id>` and avoid real personal, banking, or company data.
4. Follow [SRM](../guides/srm-flow.en.md), [procurement](../guides/procurement-flow.en.md), and [assets](../guides/asset-flow.en.md), using official operations and recording returned IDs, states, and versions.
5. Reuse requestId for retries of one request; use a new requestId for a new operation. Check previous side effects before recreating a chain after an uncertain response.
6. Check invisibility, denial, cancellation and duplicate requests before confirming main-chain success. Cleanup is limited to this run's keys through supported withdrawal, close, or delete commands.

Keep terminal/cancelled records when physical deletion is unsupported. Demonstration tooling is not a general reset. Do not overwrite shared tenants or bulk-delete databases and volumes.

<!-- omni:modules:workflow -->
### Organization scopes for teaching approvers

Assigning a role and primary organization does not by itself make a user an approval candidate. The current user-role API writes `sys_user_role`, while candidate resolution also queries `sys_user_role_scope`. The administration UI has no maintenance entry for those bindings. Check candidate preview first; selecting a role is not proof that approval is ready.

Only in an independent teaching project with no host MySQL port mapping, use the [organization-scope fixture](../../scripts/fixtures/run-workflow-scope-fixture.ps1) to bind existing roles to the primary organization of explicitly named `wiki{run}_name` accounts using `SAME_UNIT`. It creates no users, grants no roles, never overwrites conflicts, and rejects the standard `omni-stack` project. Prepare enabled users, roles and primary organizations first. The host PowerShell wrapper runs the database client and SQL inside the target Docker container:

```powershell
$env:E2E_MUTATIONS = 'true'
$evidence = Join-Path ([IO.Path]::GetTempPath()) ('omni-scope-' + [guid]::NewGuid())
./scripts/fixtures/run-workflow-scope-fixture.ps1 -ProjectName 'omni-wiki-acceptance-yourrun' -Usernames 'wikiyourrun_manager','wikiyourrun_compliance' -OutputDirectory $evidence
```

Replace project and account names with actual values. The receipt records the SQL digest, bindings before and after, and container identity. Recheck with a new output directory and `-VerifyOnly`. Preserve conflicting receipts and correct the teaching configuration; never reset the database.

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
Candidate eligibility and permission to complete approval are separate requirements. The current seed for the three SRM approval roles does not grant `workflow:approval:complete`. Through role management, add a minimal teaching approval role containing `workflow`, `workflow:instance`, `workflow:instance:list`, `workflow:task:todo`, and `workflow:approval:complete`, while retaining the original business roles. Log in again or regenerate short-lived fixture tokens, then complete only tasks assigned to that identity. Do not conceal missing permissions by approving as super administrator.
<!-- omni:modules:end -->

## 4. Controlled identities and CAPTCHA

Real login uses a human-entered CAPTCHA generated by the service. Validation atomically consumes captchaKey on both success and failure; refresh before retrying.

Post-login E2E can use short-lived identities from `omni-auth/src/test/java/.../E2eTokenFixture.java`. It reads confirmed database roles and current Redis keys; it does not add a production login backdoor, read passwords, or solve CAPTCHA. Use `scripts/fixtures/run-token-fixture.ps1`, write outside the repository, and clean up afterward.

Preserve historical fixture tenant, UUID, and resource guards. Missing authorized inputs must fail closed; substituting the standard stack does not prove isolation. See [fixture instructions](../../scripts/fixtures/README.md).

## 5. Interpret results

| Symptom | Next check |
|---|---|
| Missing catalogs, permissions, default models | Migrator and seed assertions; do not patch business status |
| Empty overview | Expected for an empty business environment; create tutorial records |
| No asset after receipt | PASS, assetManaged, exact positive quantity, MQ and Inbox |
| Demonstration record invisible | Tenant, role, owner/current-user scope |
| Duplicates after rerun | requestId, event ID, source-unit sequence, previous side effects |
| Screenshot differs | Use this run's IDs and states; historical images are not current evidence |

See [operations](../guides/operations-upgrade.en.md) for backup and recovery. Commit only redacted conclusions and necessary images, never tokens, auth state, dumps, or raw logs.
