# Source debugging: from symptoms to boundaries

Baseline: main fc165e3 and this documentation branch; linked source defines current behavior. Read the [source index](../reference/source-map.en.md) first, then use an isolated teaching environment. Do not inject faults into shared business data.

<!-- omni:modules:auth -->
## captcha-jwt

**Scenario and call chain**：Why does a second submission fail after a wrong CAPTCHA?

The login request calls validate first. Redis GETDEL atomically consumes the value, whether comparison succeeds or fails. Then inspect tenantId, roles, permissions, jti and expiry at generateToken. Never log the answer, password or complete token.

- [CaptchaServiceImpl.java](../../omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/CaptchaServiceImpl.java) — `validate`
- [JwtTokenServiceImpl.java](../../omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/JwtTokenServiceImpl.java) — `generateToken`

**Breakpoints, failures and expected results**：Break before and after getAndDelete: stored is present once, then absent. Refresh after failure. A person enters the real page CAPTCHA; this guide supplies no bypass.

**Related tests**：There is no standalone CAPTCHA unit test in this unit. Real login and one-time consumption need container integration evidence; do not report them as passed.

The request is POST /api/auth/login with captchaKey/captchaCode. redisKey locates a single-use record; stored=null means expired, absent or consumed. Consumption happens before password validation. This exact validate excerpt identifies breakpoints; never record the answer in stored.

<!-- omni:source path="omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/CaptchaServiceImpl.java" -->
```java
        String stored = stringRedisTemplate.opsForValue().getAndDelete(redisKey);

        if (stored == null) {
            // 键不存在，说明验证码已过期、不存在或已被并发消费
            throw new BusinessException(400, "验证码已过期");
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:gateway -->
## gateway-assertion

**Scenario and call chain**：Why cannot a forged X-User-Id grant admin access?

AuthFilter removes incoming identity headers, then derives identity and an HMAC assertion from verified JWT claims. Downstream services verify signature and freshness again. A missing signing secret produces 503.

- [AuthFilter.java](../../omni-backend/omni-gateway/src/main/java/com/omni/gateway/filter/AuthFilter.java) — `filter`

**Breakpoints, failures and expected results**：Break at filter and downstream verification; inspect header names, validation booleans and status. Changing identity or assertion must be rejected by the tests. Do not print the secret.

**Related tests**：[AuthFilterHmacTest.java](../../omni-backend/omni-gateway/src/test/java/com/omni/gateway/filter/AuthFilterHmacTest.java) · [GatewayPreAuthFilterHmacTest.java](../../omni-backend/omni-base/src/test/java/com/omni/base/security/GatewayPreAuthFilterHmacTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## data-scope

**Scenario and call chain**：Where to debug a visible menu with inaccessible department records?

Endpoint permissions decide whether a call is allowed; DataScope decides which rows are accessible. bindScope binds an authoritative scope for the method and clears it in finally. SQL interceptors run TenantLine → DataPermission → OptimisticLocker → Pagination.

- [ServiceDataScopeAspect.java](../../omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java) — `bindScope`

**Breakpoints, failures and expected results**：Inspect tenant, scope type, allowed unit/user IDs and final WHERE without personal data. SELF must not read or write other users' rows. Context must clear after exceptions. Child rows inherit scope from their aggregate root.

**Related tests**：[ServiceDataScopeAspectTest.java](../../omni-backend/omni-common-service/src/test/java/com/omni/common/service/datascope/ServiceDataScopeAspectTest.java) · [DataScopeResolveFilterTest.java](../../omni-backend/omni-auth/src/test/java/com/omni/auth/security/DataScopeResolveFilterTest.java)

identity comes from a verified gateway assertion. scope must match userId, tenantId and the current permissionCode. joinPoint.proceed calls the annotated business method; finally clears ThreadLocal on success or exception. Inspect scope type and allowed ID sets without personal data.

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java" -->
```java
        try {
            InternalDataScopeDTO scope = dataScopeResolver.resolve(identity, annotation.permissionCode());
            validate(scope, identity, annotation.permissionCode());
            ServiceDataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            ServiceDataScopeContext.clear();
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:workflow -->
## workflow-publish

**Scenario and call chain**：Why do concurrent publishing and early countersign completion need care?

publishModel locks the model using SELECT FOR UPDATE, validates BPMN, then deploys and updates the version. Tasks skipped by completionCondition use HistoricTaskInstance.deleteReason=MI_END; do not infer results from activity parents.

- [WorkflowModelServiceImpl.java](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java) — `publishModel`
- [ProcessInstanceServiceImpl.java](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/ProcessInstanceServiceImpl.java) — `MI_END`

**Breakpoints, failures and expected results**：Break at the publish lock, validate call and before deployment; inspect modelKey, tenantId and version. Invalid BPMN must not deploy. Inspect task deleteReason and actual approval records: MI_END appears as auto-approved in history because the engine skipped a task; it is neither a manual approval vote nor rejection.

**Related tests**：[WorkflowModelServiceImplTest.java](../../omni-backend/omni-workflow/src/test/java/com/omni/workflow/service/impl/WorkflowModelServiceImplTest.java) · [WorkflowApprovalServiceImplTest.java](../../omni-backend/omni-workflow/src/test/java/com/omni/workflow/service/impl/WorkflowApprovalServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## outbox

**Scenario and call chain**：Why does a persisted message wait before retry?

The business transaction writes PENDING. relayPendingMessages scans due messages and claimForRelay acquires a PROCESSING lease using CAS. Sending and final state updates are guarded. Failure increments newRetryCount, backs off by 2^newRetryCount × 10s and eventually enters DEAD_LETTER. Unknown brokers also count as failure.

- [MqMessageRelayService.java](../../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java) — `relayPendingMessages`

**Breakpoints, failures and expected results**：Inspect id, status, retryCount, nextRetryTime and claim row count. Only one worker may claim. Background scanning spans tenants intentionally; external queries still require tenantId. Inject faults only in an isolated container and verify resend plus consumer idempotency.

**Related tests**：[MqMessageRelayServiceTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceTest.java) · [MqMessageRelayServiceCasTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceCasTest.java) · [MqMessageRelayServiceUnknownBrokerTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceUnknownBrokerTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## portal-saga

**Scenario and call chain**：Why is supplier data unavailable immediately after enrollment?

enroll validates inviteToken and requestId, saves the association and emits role assignment in its local transaction. Auth grants SUPPLIER and returns a result before SRM completes the association. USER may enroll; profile/evaluation need both the role and an active association.

- [SupplierPortalServiceImpl.java](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/SupplierPortalServiceImpl.java) — `enroll`

**Breakpoints, failures and expected results**：Break at enroll and result application; inspect requestId, association state and correlation key. Recover failure using retryEnrollment. Duplicate results must not duplicate grants. Portal user IDs must never become internal owner_user_id/owner_unit_id.

**Related tests**：[SupplierPortalServiceImplTest.java](../../omni-backend/omni-srm/src/test/java/com/omni/srm/service/impl/SupplierPortalServiceImplTest.java) · [PortalRoleResultServiceImplTest.java](../../omni-backend/omni-srm/src/test/java/com/omni/srm/service/impl/PortalRoleResultServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:asset -->
## receipt-inbox

**Scenario and call chain**：Why do replay and backfill not create two batches of assets?

importEvent validates version and tenant. Only qualityStatus=PASS and assetManaged=true with a positive integer assetQuantity create cards. Inbox eventId plus receipt-line/unit sequence provide two idempotency boundaries. Monetary JSON values are decimal strings.

- [ProcurementAssetImportServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/ProcurementAssetImportServiceImpl.java) — `importEvent`

**Breakpoints, failures and expected results**：Inspect eventId, tenantId, sourceGrLineId, unit sequence and ImportResult. Replaying an already processed or ignored event returns `replayed=true` and `createdCount=0`; different-event backfill of the same source must not add cards either. Reject cross-tenant collisions and fractional quantities. Tests cover concurrent backfill.

**Related tests**：[ProcurementAssetImportServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/ProcurementAssetImportServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:asset -->
## asset-occupancy

**Scenario and call chain**：Why cannot one asset be transferred and disposed concurrently?

prepareTransfer/prepareDisposal reserve active_operation_type/active_operation_id and save previous_asset_status in one transaction. Completion is matched to the current operation. Rejection or cancellation restores status and clears occupancy. Approval runs in Workflow.

- [AssetOperationWorkflowStateServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/AssetOperationWorkflowStateServiceImpl.java) — `prepareTransfer`
- [WorkflowCompletionServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/WorkflowCompletionServiceImpl.java) — `restoreAsset`

**Breakpoints, failures and expected results**：Inspect assetId, version, occupancy and previousAssetStatus. A second operation must conflict. After rejection/cancellation both occupancy fields clear and status is restored. A stale completion must not release a newer operation.

**Related tests**：[AssetOperationWorkflowStateServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/AssetOperationWorkflowStateServiceImplTest.java) · [WorkflowCompletionServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/WorkflowCompletionServiceImplTest.java)

restoreAsset compares tenant, asset ID, version, current status and current occupancy, then restores status and clears occupancy in one UPDATE. affected must be 1; otherwise a retryable exception prevents stale completion releasing a newer operation. previousAssetStatus is a saved business value, not this class method name.

<!-- omni:source path="omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/WorkflowCompletionServiceImpl.java" -->
```java
        int affected = assetMapper.update(null, new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, asset.getTenantId())
                .eq(AstAsset::getId, asset.getId())
                .eq(AstAsset::getVersion, asset.getVersion())
                .eq(AstAsset::getStatus, intent.currentStatus())
                .eq(AstAsset::getActiveOperationType, intent.operationType())
                .eq(AstAsset::getActiveOperationId, intent.operationId())
                .eq(AstAsset::getDeleted, 0)
                .set(AstAsset::getStatus, intent.previousStatus())
                .set(AstAsset::getActiveOperationType, null)
                .set(AstAsset::getActiveOperationId, null)
                .set(AstAsset::getUpdateTime, LocalDateTime.now())
                .set(AstAsset::getUpdateBy, "workflow-event")
                .setSql("version = version + 1"));
        if (affected != 1) {
            throw new RetryableWorkflowEventException("恢复资产 Workflow 操作占位失败");
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## cli-atomic

**Scenario and call chain**：Why is copying a service folder or removing one Maven module insufficient?

applyRenderedIntegration preflights changes and uses temporary files plus backups for rollback. applyPresetGeneration prunes a catalog closure in staging, validates the lock, then renames. Gateway, Compose, migrations, seeds, frontend and documentation share the delivery boundary.

- [service-integration-transaction.ts](../../tools/omni-cli/src/service-integration-transaction.ts) — `applyRenderedIntegration`
- [preset-generator.ts](../../tools/omni-cli/src/preset-generator.ts) — `applyPresetGeneration`

**Breakpoints, failures and expected results**：Inspect changes, selected modules, staging and final target. A path conflict in an isolated copy must fail before writes. Failure preserves the original target and cleans its own staging. Move the output and verify its own Wiki, AI and lock without the source checkout.

**Related tests**：[service-integration-renderer.test.ts](../../tools/omni-cli/test/service-integration-renderer.test.ts) · [preset-generator.test.ts](../../tools/omni-cli/test/preset-generator.test.ts)

Prepare all changes before rename/application and post-write validation. The inner catch below attempts to restore the current file's backup and rethrows; the outer catch invokes rollback and distinguishes complete from incomplete rollback. The current test has 39 changes and uses failAfter to inject failure after write 38, then checks removal of created files, restoration of originals and cleanup of transaction artifacts. failAfter is a test hook, not a public CLI option.

<!-- omni:source path="tools/omni-cli/src/service-integration-transaction.ts" -->
```typescript
  } catch (error) {
        if (item.backup && existsSync(item.backup) && !existsSync(item.target)) {
          renameSync(item.backup, item.target);
        }
        throw error;
      }
      item.applied = true;
      appliedCount += 1;
      if (testOptions.failAfter !== undefined && appliedCount === testOptions.failAfter) {
        throw new Error(`测试注入失败：第 ${appliedCount} 个文件`);
      }
    }

    for (const item of prepared) {
      const actual = readFileSync(item.target, 'utf8');
      if (actual !== item.change.after) throw new Error(`磁盘写后内容不一致：${item.change.target}`);
    }
  } catch (error) {
    const rollbackErrors = rollback(prepared, createdDirectories);
    const detail = error instanceof Error ? error.message : String(error);
    if (rollbackErrors.length > 0) {
      throw new CliError(`接入事务失败且回滚不完整：${detail}；${rollbackErrors.join('; ')}`);
    }
    throw new CliError(`接入事务失败，已完整回滚：${detail}`);
  }
```

<!-- omni:modules:end -->

## Container tests and debugging

Install tool dependencies before these commands. The backend runner provides JDK 25/JAVA_HOME; full clean install is the backend change gate. An IDE may connect to container JDWP as a client. Bind port 5005 locally only. Breakpoints pause threads, so use a teaching environment. Debugger screenshots must show a real container process; source pictures are not runtime evidence.
Install tool dependencies before these commands. The backend runner provides JDK 25/JAVA_HOME; full clean install is the backend change gate. An IDE may connect to container JDWP as a client, with host port 5005 bound locally only. Breakpoints pause threads, so use a teaching environment. The selected test below configures debugging through Surefire; do not also set global JAVA_TOOL_OPTIONS, which could make Maven and the test JVM compete for 5005. Debugger screenshots must show a real container process; source pictures are not runtime evidence.
```bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm test
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
# JDWP: only in an isolated teaching environment
# Set JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
# Then connect the IDE to localhost:5005 using the runner's declared port.
```

Assertions define behavior; source links do not prove execution. Record commands, image IDs, source digests, exit codes and assertions. Exclude tokens, cookies and raw personal data.

## Reproduce one debuggable test inside Docker

Choose a source unit and its test rather than injecting faults into live business data. These commands select the Outbox CAS test; they do not prove a complete reactor or E2E business acceptance.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw install -pl omni-common-mqlog -am -DskipTests
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm --service-ports backend ./mvnw test -pl omni-common-mqlog -am -Dtest=MqMessageRelayServiceCasTest -Dsurefire.failIfNoSpecifiedTests=false "-Dmaven.surefire.debug=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
```

The second command suspends the test JVM and listens on container interfaces; compose.tools.yaml still publishes the port only at host 127.0.0.1:5005. Attach the IDE to this fixed address and map this checkout as source. Break at claimForRelay and casMarkSent/casApplyFailure; inspect claim count, state and affected rows. Only one competitor may claim, and a stale update affecting zero rows must not be treated as success. This unit test simulates claims using mocks and AtomicBoolean and separately inspects SQL annotations; it does not prove delivery through a real database or Broker. Continue and inspect the selected test result. Upstream modules without that named test do not imply the selected test was skipped. Stop your debugging container without changing business ports.

If 5005 is occupied, identify the owner and your own debugging container; stop only your process before retrying. Keep the fixed port. docs/scripts/source-excerpts.test.mjs verifies quoted text against current source. Source changes require synchronized explanations in all four languages and actual AI_REVIEW; human document review is optional. Register breakpoint/variable screenshots only after an actual attach and inspection; a static code image is not debugging evidence.

Command references: [Surefire debugging](https://maven.apache.org/surefire/maven-surefire-plugin/examples/debugging.html) and [JDK 25 JDWP addresses](https://docs.oracle.com/en/java/javase/25/docs/specs/jpda/conninv.html). The [current source audit](../reference/evidence/source-debugging-wiki-review-2026-10-17.json) records the actual reading scope. Each linked test supports only its actual assertions; this document review ran no new tests, IDE attachment or business writes.
