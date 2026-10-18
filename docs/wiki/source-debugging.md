# 源码调试：从症状走到边界

适用基线：主干 fc165e3 及本次文档分支；以链接源码为准。先读 [源码索引](../reference/source-map.md)，再在独立教学环境执行，不在共享业务数据上注入故障。

<!-- omni:modules:auth -->
## captcha-jwt

**问题与调用链**：验证码错了，为什么再次提交会失效？

登录请求先执行 validate；Redis GETDEL 原子取走验证码，不论比较成功还是失败都已消费。再到 generateToken，观察 tenantId、roles、permissions、jti 与过期时间。不要记录图片答案、密码或完整 token。

- [CaptchaServiceImpl.java](../../omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/CaptchaServiceImpl.java) — `validate`
- [JwtTokenServiceImpl.java](../../omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/JwtTokenServiceImpl.java) — `generateToken`

**断点、失败与预期结果**：断点停在 getAndDelete 前后：第一次 stored 非空，第二次为空。一次失败必须刷新验证码。真实页面验证码由人输入；本页不提供绕过入口。

**对应测试**：本单元尚无独立验证码单元测试；真实登录和一次性消费需要容器集成取证，不能写成测试已通过。

关键请求是 POST /api/auth/login 的 captchaKey/captchaCode。redisKey 定位一次性记录，stored=null 表示已过期、不存在或已被消费；进入密码校验之前已经消费。下面是 validate 的实际片段，用于定位断点；不记录 stored 的答案值。

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

**问题与调用链**：为什么伪造 X-User-Id 不能变成管理员？

AuthFilter 清除外来身份头，再从已验 JWT 生成身份与 HMAC 断言。下游重新验证签名和时效。签名密钥缺失时返回 503，不能继续放行。

- [AuthFilter.java](../../omni-backend/omni-gateway/src/main/java/com/omni/gateway/filter/AuthFilter.java) — `filter`

**断点、失败与预期结果**：断点 filter 与下游验签入口；只看请求头名称、验证布尔值与状态码。改变用户头或断言内容，测试应拒绝；不要输出签名密钥。

**对应测试**：[AuthFilterHmacTest.java](../../omni-backend/omni-gateway/src/test/java/com/omni/gateway/filter/AuthFilterHmacTest.java) · [GatewayPreAuthFilterHmacTest.java](../../omni-backend/omni-base/src/test/java/com/omni/base/security/GatewayPreAuthFilterHmacTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## data-scope

**问题与调用链**：菜单可见却读不到其他部门的数据，在哪里排查？

功能权限决定能否调用；DataScope 决定能读写哪些行。bindScope 在业务方法期间绑定权威范围，finally 清理。SQL 链固定 TenantLine → DataPermission → OptimisticLocker → Pagination。

- [ServiceDataScopeAspect.java](../../omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java) — `bindScope`

**断点、失败与预期结果**：观察当前租户、scope 类型、允许组织/用户集合和最终 WHERE，不打印个人资料。SELF 用户不能读写他人行；抛异常后上下文也必须清空。子表通过聚合根继承范围，不猜测不存在的 owner 列。

**对应测试**：[ServiceDataScopeAspectTest.java](../../omni-backend/omni-common-service/src/test/java/com/omni/common/service/datascope/ServiceDataScopeAspectTest.java) · [DataScopeResolveFilterTest.java](../../omni-backend/omni-auth/src/test/java/com/omni/auth/security/DataScopeResolveFilterTest.java)

identity 来自已验证网关断言，scope 必须与 userId、tenantId 和当前 permissionCode 一致。joinPoint.proceed 进入被注解的业务方法；无论正常返回还是异常，finally 都清理 ThreadLocal。调试时记录范围类型与允许 ID 集合，不记录个人资料。

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

**问题与调用链**：两人同时发布，或会签提前结束时，结果为何容易误判？

publishModel 先 SELECT FOR UPDATE 锁定模型并验证 BPMN，成功才部署和更新版本。会签 completionCondition 跳过的任务用 HistoricTaskInstance.deleteReason=MI_END 判定，不能按活动父级猜结果。

- [WorkflowModelServiceImpl.java](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java) — `publishModel`
- [ProcessInstanceServiceImpl.java](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/ProcessInstanceServiceImpl.java) — `MI_END`

**断点、失败与预期结果**：在发布锁、validate 调用和部署前断点；观察 modelKey、tenantId、版本。非法 BPMN 不应部署。会签观察任务 deleteReason 与实际审批记录；MI_END 在历史投影中显示为 auto-approved，表示引擎自动跳过，不能当作人工同意票或驳回。

**对应测试**：[WorkflowModelServiceImplTest.java](../../omni-backend/omni-workflow/src/test/java/com/omni/workflow/service/impl/WorkflowModelServiceImplTest.java) · [WorkflowApprovalServiceImplTest.java](../../omni-backend/omni-workflow/src/test/java/com/omni/workflow/service/impl/WorkflowApprovalServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## outbox

**问题与调用链**：消息已入库却没送到，为什么重试会等待？

业务事务写 PENDING；relayPendingMessages 扫描到期消息并用 claimForRelay CAS 取得 PROCESSING 租约。broker 策略发送后用 CAS 更新；失败 newRetryCount 递增，退避为 2^newRetryCount × 10s，超限进入 DEAD_LETTER。未知 broker 也计失败。

- [MqMessageRelayService.java](../../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java) — `relayPendingMessages`

**断点、失败与预期结果**：观察 id、status、retryCount、nextRetryTime 和 claim 行数。两个 worker 只有一个能领取。后台扫描跨租户是设计行为，外部查询仍必须 tenantId。故障注入只在隔离容器，恢复 broker 后确认可重送且消费者幂等。

**对应测试**：[MqMessageRelayServiceTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceTest.java) · [MqMessageRelayServiceCasTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceCasTest.java) · [MqMessageRelayServiceUnknownBrokerTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceUnknownBrokerTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## portal-saga

**问题与调用链**：入驻完成为何还不能看供应商资料？

enroll 用 inviteToken 与 requestId 验证邀请和幂等，在本地事务保存关联并发出角色分配事件；Auth 赋 SUPPLIER 后返回结果，SRM 才完成关联。USER 只能入驻，资料/评估要求角色与活跃关联同时满足。

- [SupplierPortalServiceImpl.java](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/SupplierPortalServiceImpl.java) — `enroll`

**断点、失败与预期结果**：断点 enroll、角色结果应用；观察 requestId、关联状态和结果关联键。失败用 retryEnrollment 恢复，重复结果不得重复赋权。门户用户 ID 不得成为内部 owner_user_id/owner_unit_id。

**对应测试**：[SupplierPortalServiceImplTest.java](../../omni-backend/omni-srm/src/test/java/com/omni/srm/service/impl/SupplierPortalServiceImplTest.java) · [PortalRoleResultServiceImplTest.java](../../omni-backend/omni-srm/src/test/java/com/omni/srm/service/impl/PortalRoleResultServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:asset -->
## receipt-inbox

**问题与调用链**：同一收货事件或补录为什么不会建两批资产？

importEvent 校验版本/租户；只有 qualityStatus=PASS 且 assetManaged=true 的行、正整数 assetQuantity 才建卡。Inbox eventId 与来源收货行/单位序号形成双重幂等；金额 JSON 使用十进制字符串。

- [ProcurementAssetImportServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/ProcurementAssetImportServiceImpl.java) — `importEvent`

**断点、失败与预期结果**：观察 eventId、tenantId、sourceGrLineId、单位序号和 ImportResult。已处理或忽略的同事件重放返回 `replayed=true`、`createdCount=0`；不同事件补同来源也不新增。跨租户碰撞应拒绝，不得把小数数量向下取整。测试覆盖并发补录。

**对应测试**：[ProcurementAssetImportServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/ProcurementAssetImportServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:asset -->
## asset-occupancy

**问题与调用链**：一项资产为何不能同时调拨和报废？

prepareTransfer/prepareDisposal 在同一事务占用 active_operation_type/active_operation_id 并保存 previous_asset_status。完成事件按当前操作校验；拒绝或取消恢复原状态并清占用。审批引擎仍在 Workflow，不在 Asset 内复制。

- [AssetOperationWorkflowStateServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/AssetOperationWorkflowStateServiceImpl.java) — `prepareTransfer`
- [WorkflowCompletionServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/WorkflowCompletionServiceImpl.java) — `restoreAsset`

**断点、失败与预期结果**：观察 assetId、version、占用字段与 previousAssetStatus。第二个操作必须冲突。拒绝/取消后两项占用都为空且状态恢复；旧完成事件不得释放新操作的占用。

**对应测试**：[AssetOperationWorkflowStateServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/AssetOperationWorkflowStateServiceImplTest.java) · [WorkflowCompletionServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/WorkflowCompletionServiceImplTest.java)

restoreAsset 同时比较租户、资产 ID、版本、当前状态与当前操作占用，再在一条 UPDATE 恢复状态并清空占用。affected 必须等于 1；否则抛可重试异常，不能让旧完成事件释放新操作。previousAssetStatus 是业务保存的值，不是此类的方法名。

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

**问题与调用链**：为什么不能只复制服务目录或裁剪一个 Maven 模块？

applyRenderedIntegration 对计划预检、临时文件和备份执行可回滚替换；applyPresetGeneration 在 staging 按 catalog 闭包裁剪，并验证 lock 后 rename。Gateway、Compose、迁移、种子、前端与文档都属于交付边界。

- [service-integration-transaction.ts](../../tools/omni-cli/src/service-integration-transaction.ts) — `applyRenderedIntegration`
- [preset-generator.ts](../../tools/omni-cli/src/preset-generator.ts) — `applyPresetGeneration`

**断点、失败与预期结果**：观察 changes、selected modules、staging 与最终目录；在隔离副本中制造路径冲突应在正式写入前拒绝。中途失败保留原目标且清自身 staging。生成物移动后还要验证自己的 Wiki、AI 和锁，不依赖源 checkout。

**对应测试**：[service-integration-renderer.test.ts](../../tools/omni-cli/test/service-integration-renderer.test.ts) · [preset-generator.test.ts](../../tools/omni-cli/test/preset-generator.test.ts)

所有变更先准备，再执行 rename 和写后校验。下方内层 catch 尝试恢复当前文件备份并重新抛出；外层 catch 才调用 rollback，并区分完整与不完整回滚。当前测试共有 39 个变更，在第 38 个写入后用 failAfter 注入失败，检查新增文件删除、原文件还原和事务临时文件清理。failAfter 仅供测试，不是公开 CLI 参数。

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

## 容器里的测试与调试

先安装工具依赖，再运行下面命令。后端 runner 自带 JDK 25/JAVA_HOME；全量 clean install 为正式后端修改门。IDE 仅作客户端连接容器 JDWP，端口 5005 仅绑定本机且不要放到公网。断点会暂停线程，只在教学环境启用。实际断点截图必须来自真实容器进程，源码图片不能代替运行证据。
执行这些命令前先安装工具依赖。backend runner 提供 JDK 25/JAVA_HOME；后端变更使用完整 clean install 门禁。IDE 可以作为客户端连接容器 JDWP，宿主端口 5005 只绑定本机。断点会暂停线程，只在教学环境操作。下方选定测试通过 Surefire 参数配置调试，不要同时设置全局 JAVA_TOOL_OPTIONS，以免 Maven 与测试 JVM 争用 5005。调试截图必须来自实际容器进程，源码图片不算运行证据。
```bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm test
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
# JDWP: only in an isolated teaching environment
# Set JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
# Then connect the IDE to localhost:5005 using the runner's declared port.
```

测试断言说明行为；本页源码链接不是已运行证明。执行后记录命令、镜像 ID、源摘要、退出码和断言结果，不写 token、cookie 或原始个人数据。

## 在容器内复现一个可调试的测试

先选源码和对应测试，避免在真实业务链注入故障。以下只执行 Outbox CAS 测试；它不代表全量 Reactor 或端到端业务验收。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw install -pl omni-common-mqlog -am -DskipTests
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm --service-ports backend ./mvnw test -pl omni-common-mqlog -am -Dtest=MqMessageRelayServiceCasTest -Dsurefire.failIfNoSpecifiedTests=false "-Dmaven.surefire.debug=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
```

第二条命令让 Docker 内的测试 JVM 暂停并监听容器接口；compose.tools.yaml 仍只向宿主 127.0.0.1:5005 发布端口。IDE 连接这个固定地址，将当前 checkout 映射为源码。在 claimForRelay、casMarkSent/casApplyFailure 设置断点，观察抢占次数、状态和影响行数；两个竞争执行流只能有一个抢占成功，旧状态更新 0 行不能当成功。该单元测试用 Mock 和 AtomicBoolean 模拟抢占，另检查 SQL 注解，不等于真实数据库/Broker 投递验收。继续执行后查看所选测试结果；上游模块没有同名测试，不代表所选测试被跳过。结束自己的调试容器，不改业务端口。

若 5005 被占用，先确认所有者和自己的调试容器，只停止自己的进程后重试，不自动换端口。docs/scripts/source-excerpts.test.mjs 验证引文与当前源码一致；源码变更后应同步四语解释并完成实际 AI_REVIEW，人工文档复核不是必需条件。断点与变量截图只在真正连接和检查后登记，静态代码图片不是调试证据。

命令依据：[Surefire 调试说明](https://maven.apache.org/surefire/maven-surefire-plugin/examples/debugging.html)、[JDK 25 JDWP 地址说明](https://docs.oracle.com/en/java/javase/25/docs/specs/jpda/conninv.html)。[本次源码核对记录](../reference/evidence/source-debugging-wiki-review-2026-10-17.json)保留实际阅读范围。上述测试链接各自仅证明其实际断言；本次文档复核没有新执行测试、IDE 连接或业务写入。
