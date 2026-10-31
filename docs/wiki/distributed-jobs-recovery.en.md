# 7. Recovery and extension: locate the failing layer first

“The job did not run” can mean the time is not due, dispatch failed, business execution failed or the result did not return. These require different recovery paths; repeated clicking is not diagnosis.

## Follow the evidence through each layer

| Observation | Check first | Avoid doing immediately |
|---|---|---|
| Missing system-catalog entry | Both annotations, deployed code, executor switch | Adding system metadata to every personal handler |
| Project shows unregistered | Whether the remote query succeeded, group and handler | Assuming absence from this label and registering again |
| Dispatch failed | Online address, network, authentication, handler name | Editing business state or retrying without evidence |
| Dispatch succeeded, execution failed | That execution log, inputs and business state | Calling dispatch success business success |
| Long-running record | Duration, blocking policy, timeout and callback | Assuming interruption reversed all side effects |
| No personal log | Dispatch, argument parsing and database writes | Inventing a successful timestamp |
| Unknown creation/deletion outcome | Local/remote IDs, trusted association and full lookup | Guessing by name and doing bulk cleanup |

Record time and time zone, tenant, local job ID, executor group, remote job ID and log ID. Keep passwords, cookies and complete sensitive arguments out of public reports.

## Why registration errors sometimes retain a local row

This is the actual branch when lookup or cleanup also fails. The remote outcome is unknown; deleting the local row would discard the recovery association.

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobServiceImpl.java" -->
```java
        } catch (Exception cleanupFailed) {
            // 查询/清理失败：无法确认远端，保留本地可恢复关联与停止责任，绝不删本地
            log.error("{}：调度中心反查/清理失败，保留本地待恢复记录 localId={}, executorParam={}, 需对账",
                    action, localId, executorParam, cleanupFailed);
            throw new BusinessException(500, "调度中心注册结果未知且清理失败，已保留本地待恢复记录(id="
                    + localId + ")，请联系运维对账: " + cleanupFailed.getMessage());
        }
```

Reconcile by a known exact remote ID, or by executor group, shared handler and arguments containing tenant and local ID. Names can repeat or change and are not deletion authority. Restarting seed synchronization does not resolve an unknown outcome.

## Add a personal job type

Define the user's purpose, inputs and duplicate protection. Implement the existing UserJobHandler interface, use the exact catalog type code as its Bean name, and maintain its parameter definition. The water-reminder handler actually declares:

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/job/handler/DrinkWaterRemindHandler.java" -->
```java
@Slf4j
@Component("Task-00001")
@RequiredArgsConstructor
public class DrinkWaterRemindHandler implements UserJobHandler {
```

The message carries tenantId explicitly; handlers must not depend on an HTTP thread's tenant context. Design authorization, state transitions, transactions and idempotency for business writes. A user-supplied identifier is not trusted authorization.

Update the type seed, parameter template and assertions together; use forward migrations for structural changes. Changing a dropdown alone is insufficient. These are extension instructions, not a new implemented type or scheduling feature.

## Add a system maintenance job

Use both annotations and provide a readable name, default schedule, arguments and routing policy. Assess volume, batching, concurrency, idempotency and post-failure state before scheduling periodic work. Services retain their distinct executor AppNames.

## Verify that an extension works

In the same local Docker environment, use dedicated test identities to verify creation, editing, pausing, manual execution, resuming, genuine scheduled execution, success/failure logs, other-user and cross-tenant rejection, and deletion without remote residue. Preserve real reconciliation failures and recovery evidence.

Source matching, unit tests, real Cron execution and image review are separate evidence. Registration and cleanup tests must include lost responses and unknown outcomes, not just the happy path. See the [scheduling facts](../scheduling.en.md) for engineering constraints, or restart at the [chapter introduction](distributed-jobs-principles.en.md).
