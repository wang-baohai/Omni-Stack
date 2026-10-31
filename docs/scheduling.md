# 定时任务系统约束

本文是调度模块的事实与工程约束。从零开始的概念、框架原文、工程实现与真实网页见 [XXL-JOB 独立章节](wiki/distributed-jobs-principles.md)；既有细化操作见 [调度教程](guides/scheduling.md)，运行配置见 [Docker 部署](docker-deployment.md)。

## 1. 边界与数据所有权

| 所有者 | 职责 |
|---|---|
| `omni-common-core` | `UserJobHandler` SPI 与 `UserJobMessage`，不依赖 Spring |
| `omni-common-job` | XXL-JOB 执行器、Admin 客户端、系统任务元数据注册与自动注册 |
| `omni-base` | 系统任务/类型管理、个人任务归属校验、处理器路由与执行日志 |
| XXL-JOB Admin | Cron 调度、执行器发现、触发与回调记录；不是个人任务归属的权威 |

`sys_user_job_type` 是全局目录，`type_code` 唯一，没有租户字段。`sys_user_job` 保存租户及创建人，`sys_user_job_log` 保存消息中的租户与 `jobId`。工作台日志入口先校验父任务的租户及创建人，再按 `jobId` 查询；当前日志服务未使用 `tenantId` 入参追加 SQL 条件。`xxl_job_id` 关联调度中心记录，不能用名称代替可信关联。

## 2. 系统任务约束

- 系统任务方法必须同时声明 `@XxlJob("handlerName")` 和 `@SystemJobMeta(...)`；缺少任一注解不会进入 `SystemJobRegistry`。
- `SystemJobRegistry.scan()` 收集元数据；`XxlJobAutoConfiguration.systemJobAutoRegistrar()` 在启用执行器时注册尚不存在的任务，保留已有任务的 Cron 与参数。
- 执行器需要 `omni-common-job`。自动配置以 `XxlJobSpringExecutor` 在 classpath 为条件；执行器 Bean 和自动注册 Runner 还要求 `xxl.job.executor.enabled` 未设为 `false`。
- 不同服务使用各自 AppName。同名 handler 跨执行器组可共存；注册、查找和删除必须带正确执行器组。
- `/api/job/system-job/list` 需要 `job:system-job:list`；注册、启停、触发、注销需要 `job:system-job:manage`。列表状态为 `UNREGISTERED`、`RUNNING`、`STOPPED`；远端列表查询失败时也可能回退为 `UNREGISTERED`，该标签不能单独证明远端无任务。
- 系统任务结果在 XXL-JOB 日志中核对，不写成个人任务日志。立即触发返回成功只代表调度中心受理。

## 3. 个人任务契约与一致性

- `/api/base/my-job` 创建从可信租户头与认证上下文取得租户和创建人；更新、删除、启停、触发与日志调用 `MyJobController.verifyOwnership(id, tenantId, username)`，同时核对租户及创建人。不得给这些接口添加 `@PreAuthorize` 代替逐行归属校验。
- 列表与统计固定当前租户、当前 `createBy`。启用类型列表共享全局目录。任务类型管理接口 `/api/job/user-job-type` 使用 `job:user-job-type:*` 权限。
- `UserJobMessage` 字段固定为 `jobId`、`tenantId`、`jobType`、`jobName`、`jobParams`。所有个人任务注册为同一 `userJobExecuteHandler`；内部用 `jobType` 查找 Bean。
- 创建先插入本地行，再注册 XXL-JOB 并回填 ID。正常创建立即启用；本地 `status=1` 为启用，`0` 为暂停。
- 注册失败或回填失败时，按执行器组、handler、含租户与本地 ID 的 `executorParam` 精确清理。只有确认远端已无该任务后才能删除本地行；远端结果未知时保留恢复关联并报错，禁止声称已回滚或盲目再次创建。
- 已关联 XXL ID 时，更新 Cron/参数、启停和删除先操作远端，再提交本地变更。远端失败时保留本地旧值/关联；删除异常须通过完整分页确认远端已不存在。只改名称不更新 XXL-JOB 描述。
- 暂停停止后续 Cron 调度，不禁止手动触发。触发响应与执行完成是两个阶段；通过新日志 ID、时间和 XXL-JOB 触发来源核对结果。
- 执行日志 `status=1` 表示成功，`0` 表示失败。缺失处理器或捕获的处理器异常会尝试写失败日志；JSON 反序列化失败时在日志写入前返回。日志插入、最后时间更新不在同一声明式事务内，数据库异常可阻止后续执行结果设置，不能承诺每次失败都有个人日志。
- `XxlJobAdminClient` 的会话 Cookie 只驻留内存。响应 code 非 200 或消息含 `not login` 时会重新登录并重试一次；这不构成业务写入幂等保证。Cookie 不写入文件或公共证据。

## 4. 扩展约束

1. 实现 `UserJobHandler`，`@Component` Bean 名必须与目录的 `type_code` 完全一致；目录启用不证明处理器已部署。
2. 校验任务参数，显式使用消息中的 `tenantId`，避免依赖 HTTP 线程上下文。业务副作用必须幂等，不能假设 Cron 与手动触发互斥。
3. `param_template` 支持标准 JSON Schema `object/properties` 与兼容扁平格式；枚举提交稳定值，显示文本不作为路由编码。
4. 种子变更修改 `scripts/sql/seed/base.sql`，刷新 `database/seed/manifest.yaml` 对应校验与断言；数据库结构使用前向迁移。
5. 新系统任务使用双注解，声明默认 Cron、参数和策略。不得把所有个人任务的共享 handler 标成系统任务。
6. 在本机 Docker 验证创建、编辑、暂停、手动执行、恢复、真实 Cron 执行、成功/失败日志、归属拒绝及删除后的远端无残留。

## 5. 源码入口

- [个人任务控制器](../omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java)：列表过滤、`verifyOwnership()`。
- [个人任务服务](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobServiceImpl.java)：外部先行、精确补偿、未知结果保留。
- [Admin 客户端](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/XxlJobAdminClient.java)：注册、全字段更新、完整分页与重复页保护。
- [共享执行入口](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobExecuteHandler.java)、[路由注册表](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java)、[喝水处理器](../omni-backend/omni-base/src/main/java/com/omni/base/job/handler/DrinkWaterRemindHandler.java)。
- [自动配置](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/config/XxlJobAutoConfiguration.java)、[系统元数据注册表](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java)、[种子同步器](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobSeedSyncRunner.java)。种子同步扫描启用且无 XXL ID 的行，重启不能代替未知结果对账。

- [个人日志查询](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobLogServiceImpl.java)：当前依赖入口的父任务归属校验和固定 jobId。
- [系统任务服务](../omni-backend/omni-base/src/main/java/com/omni/base/service/SystemJobService.java)：元数据与远端状态合并，以及远端查询失败的展示回退。

## 6. 运行与验收边界

正式基础设施由 `compose.infra.yaml` 和 migrator 管理；XXL-JOB 数据库/种子位于 `database/changelog/xxl-job/`、`scripts/sql/seed/xxl-job.sql`。账号、密码和 access token 来自私有 `.env`，不使用教程硬编码凭据。服务间连接 `xxl-job-admin:8080/xxl-job-admin`；宿主端口以实际 Compose 为准。

日期时间使用 `yyyy-MM-dd HH:mm:ss`。应用、构建、测试、截图与取证在本机 Docker 执行。教程证据说明具体身份、镜像、源码、真实触发来源与限制；类型目录截图、受理响应或模拟故障不能单独证明真实执行闭环。
