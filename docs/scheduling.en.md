# Scheduling constraints

This document defines scheduling facts and engineering constraints. Use the [scheduling tutorial](guides/scheduling.en.md) for UI steps, screenshots and source debugging, and [Docker deployment](docker-deployment.en.md) for runtime configuration.

## 1. Boundaries and data ownership

| Owner | Responsibility |
|---|---|
| `omni-common-core` | Spring-independent `UserJobHandler` SPI and `UserJobMessage` |
| `omni-common-job` | XXL-JOB executor, Admin client, system metadata registry and automatic registration |
| `omni-base` | System/type management, personal-job ownership checks, handler routing and execution logs |
| XXL-JOB Admin | Cron scheduling, executor discovery, trigger/callback records; it does not own personal-job authorization |

`sys_user_job_type` is a global catalog with a unique `type_code` and no tenant column. `sys_user_job` stores tenant and creator; `sys_user_job_log` stores the message tenant and `jobId`. The workbench verifies the parent job's tenant/creator before querying by `jobId`; the current log service does not add its `tenantId` argument to SQL. `xxl_job_id` links the scheduler row. A name is not a trusted correlation key.

## 2. System jobs

- Each system method requires both `@XxlJob("handlerName")` and `@SystemJobMeta(...)`. A method missing either annotation does not enter `SystemJobRegistry`.
- `SystemJobRegistry.scan()` collects metadata. With the executor enabled, `XxlJobAutoConfiguration.systemJobAutoRegistrar()` registers missing jobs and preserves existing Cron/parameters.
- Services need `omni-common-job`. Auto-configuration requires `XxlJobSpringExecutor` on the classpath; the executor bean and registration Runner also require `xxl.job.executor.enabled` not to be `false`.
- Services use separate AppNames. Identical handlers can exist in different executor groups. Registration, lookup and deletion must use the correct group.
- `/api/job/system-job/list` requires `job:system-job:list`; register/start/stop/trigger/remove require `job:system-job:manage`. List states are `UNREGISTERED`, `RUNNING` and `STOPPED`. A failed remote list query can also fall back to `UNREGISTERED`, so this label alone does not prove remote absence.
- Check system results in XXL-JOB logs, not personal-job logs. A successful trigger response only confirms scheduler acceptance.

## 3. Personal-job contracts and consistency

- Creation under `/api/base/my-job` obtains tenant and creator from the trusted header and authentication context. Update/delete/status/trigger/log operations call `MyJobController.verifyOwnership(id, tenantId, username)` and check both tenant and creator. Do not replace row ownership checks with `@PreAuthorize`.
- Lists and statistics are fixed to the current tenant and `createBy`. Enabled types share the global catalog. `/api/job/user-job-type` management uses `job:user-job-type:*` permissions.
- `UserJobMessage` contains exactly `jobId`, `tenantId`, `jobType`, `jobName` and `jobParams`. All personal jobs register `userJobExecuteHandler`; internal routing looks up the Bean by `jobType`.
- Creation inserts a local row, registers XXL-JOB and backfills its ID. Normal creation enables scheduling immediately. Local `status=1` is enabled and `0` is paused.
- Registration/backfill failure uses the executor group, handler and `executorParam` containing tenant/local ID for precise cleanup. Delete the local row only after confirming the remote job is absent. Retain the recovery association and fail when remote results are unknown; do not claim rollback or blindly create another job.
- When an XXL ID is associated, Cron/parameter updates, status changes and deletion operate remotely before local persistence. On remote failure retain old local values/associations. Failed deletion requires a complete paginated remote absence check. Renaming alone does not update the XXL-JOB description.
- Pausing stops future Cron scheduling but allows manual triggering. Acceptance and completion are separate; verify new log IDs/times and the XXL-JOB trigger origin.
- Execution-log `status=1` means success and `0` means failure. A missing handler or caught handler exception attempts to write a failed log; JSON deserialization failure returns before log persistence. Log insertion and last-time update do not share a declared transaction, and database errors can prevent final result reporting. A personal log is not guaranteed for every failure.
- `XxlJobAdminClient` keeps its session Cookie in memory. A response with non-200 code or `not login` text triggers re-login and one retry; this does not establish idempotent business writes. Never persist the Cookie or include it in public evidence.

## 4. Extension constraints

1. Implement `UserJobHandler`; the `@Component` Bean name must exactly match the catalog `type_code`. An enabled catalog row does not prove handler deployment.
2. Validate parameters and use the message's explicit `tenantId`, without HTTP thread context. Business effects must be idempotent; Cron and manual execution can overlap.
3. `param_template` accepts standard JSON Schema `object/properties` and the compatible flat format. Submit stable enum values; display labels are not routing codes.
4. Update `scripts/sql/seed/base.sql` for seed changes and refresh corresponding checksums/assertions in `database/seed/manifest.yaml`. Use forward-only schema migrations.
5. New system jobs use both annotations and declare default Cron, parameters and strategy. Do not classify the shared personal-job handler as a system job.
6. In local Docker verify create/edit/pause/manual trigger/resume/real Cron execution, success/failure logs, ownership denial and no remote residue after deletion.

## 5. Source entry points

- [Personal controller](../omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java): list filtering and `verifyOwnership()`.
- [Personal service](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobServiceImpl.java): remote-first changes, precise compensation and unknown-result retention.
- [Admin client](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/XxlJobAdminClient.java): registration, complete-field updates, full pagination and repeated-page protection.
- [Shared execution entry](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobExecuteHandler.java), [routing registry](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java) and [drink-water handler](../omni-backend/omni-base/src/main/java/com/omni/base/job/handler/DrinkWaterRemindHandler.java).
- [Auto-configuration](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/config/XxlJobAutoConfiguration.java), [system registry](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java) and [seed synchronizer](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobSeedSyncRunner.java). Seed synchronization scans enabled rows without XXL IDs; restarting does not replace reconciliation of unknown results.

- [Personal log query](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobLogServiceImpl.java): currently relies on the endpoint's parent ownership guard and fixed jobId.
- [System-job service](../omni-backend/omni-base/src/main/java/com/omni/base/service/SystemJobService.java): merges metadata with remote state and falls back on remote-query failure.

## 6. Runtime and verification boundaries

`compose.infra.yaml` and the migrator own infrastructure. XXL-JOB schema/seeds are in `database/changelog/xxl-job/` and `scripts/sql/seed/xxl-job.sql`. Credentials and access tokens come from private `.env`, not hardcoded tutorial values. Services connect to `xxl-job-admin:8080/xxl-job-admin`; consult actual Compose for host bindings.

Use `yyyy-MM-dd HH:mm:ss` for date/time. Applications, builds, tests, screenshots and evidence run in local Docker. Tutorial evidence identifies actors, images, source, actual trigger origins and limitations. A catalog screenshot, acceptance response or simulated failure alone cannot prove real execution.
