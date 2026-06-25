# XXL-JOB 分布式任务调度集成

## Context

Omni-Stack 当前使用 Spring `@Scheduled` 管理定时任务（如 `OperLogArchiver`），在多实例部署时无法防止重复执行。需要引入 XXL-JOB v3.3.1 作为分布式调度平台，同时支持两类场景：
- **A 场景**：管理员通过 XXL-JOB Web UI 管理系统级任务
- **B 场景**：终端用户从预定义任务类型中选择、填参数创建任务，XXL-JOB 扫描触发 + RocketMQ 扇出执行

本次为 Phase 1（基础设施层），不含前端和管理 API。

## Task 1: 新建 omni-common-job Starter 模块

**新建文件：**

| 文件 | 路径 |
|------|------|
| pom.xml | `omni-backend/omni-common-job/pom.xml` |
| XxlJobProperties | `omni-backend/omni-common-job/src/main/java/com/omni/common/job/XxlJobProperties.java` |
| XxlJobAutoConfiguration | `omni-backend/omni-common-job/src/main/java/com/omni/common/job/config/XxlJobAutoConfiguration.java` |
| AutoConfiguration.imports | `omni-backend/omni-common-job/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

**设计要点：**
- `@AutoConfiguration` + `@ConditionalOnClass(XxlJobSpringExecutor.class)` + `@ConditionalOnProperty(xx.job.executor.enabled, matchIfMissing=true)`
- `@ConditionalOnMissingBean` 允许服务覆盖
- `XxlJobProperties` 绑定 `xxl.job.*` 前缀，admin 默认地址 `http://127.0.0.1:18080/xxl-job-admin`
- appname 兜底取 `spring.application.name`，每个服务自动获得唯一 AppName
- 默认 executor port 9999

## Task 2: 父 POM 和依赖管理

**修改文件：** `omni-backend/pom.xml`

- `<modules>` 中 `omni-common-operlog` 之后新增 `omni-common-job`
- `<dependencyManagement>` 新增：
  ```xml
  <dependency>
      <groupId>com.omni</groupId>
      <artifactId>omni-common-job</artifactId>
      <version>${project.version}</version>
  </dependency>
  ```
- `<properties>` 新增 `<xxl-job.version>3.0.0</xxl-job.version>`（Maven Central 上 xxl-job-core 最新版）

> **兼容性验证**：xxl-job-core 3.0.0 使用 `jakarta.annotation-api 3.0.0`，与 Spring Boot 4 + JDK 25 兼容。
> 如 Maven Central 已发布 3.3.1 则使用 3.3.1。实现阶段需先验证。

## Task 3: Docker 部署 XXL-JOB Admin

**修改文件：** `docker-compose.yml`

在 `rocketmq-broker` 服务后、`networks` 前新增 `xxl-job-admin` 服务：
- 镜像：`xuxueli/xxl-job-admin:3.3.1`（如无此镜像，使用替代方案或自行构建）
- 端口映射：`18080:8080`（避免与 Nacos 8080 冲突）
- 通过 `PARAMS` 环境变量传入 MySQL 连接信息
- depends_on mysql healthy
- 默认账号 admin/123456

**新建文件：** `scripts/sql/init-xxl-job.sql`
- 内容：XXL-JOB 官方 `tables_xxl_job.sql`（11 张表）
- 挂载到 `/docker-entrypoint-initdb.d/03-init-xxl-job.sql`

**修改文件：** `docker-compose.yml` mysql volumes 新增挂载行

**修改文件：** `start.bat` 端口保护列表追加 `18080`

## Task 4: OperLogArchiver 迁移到 @XxlJob

**修改文件：** `omni-backend/omni-base/src/main/java/com/omni/base/service/OperLogArchiver.java`
- 移除 `@Scheduled(cron = "0 0 2 * * ?")`，替换为 `@XxlJob("operLogArchiveHandler")`
- 移除 `AtomicBoolean running` 字段和防重入逻辑（XXL-JOB DB 锁保证一致性）
- 新增 `XxlJobHelper.log()` 调用
- `doArchive()`、`selectExpiredIds()`、`archiveBatch()` 方法体不变

**删除文件：** `omni-backend/omni-base/src/main/java/com/omni/base/config/SchedulingConfig.java`

## Task 5: B 场景 — 数据库表设计

**修改文件：** `scripts/sql/init-all.sql`（在 `omni_base` section 末尾追加）

新建两张表：

**sys_user_job**（用户自定义任务）：
- `id` BIGINT PK AUTO_INCREMENT
- `tenant_id` BIGINT NOT NULL（租户隔离）
- `job_name` VARCHAR(100) NOT NULL
- `job_type` VARCHAR(50) NOT NULL（关联 type_code）
- `cron_expression` VARCHAR(100) NOT NULL
- `job_params` JSON DEFAULT NULL（参数 JSON）
- `status` TINYINT DEFAULT 1（0-禁用, 1-启用）
- `next_fire_time` DATETIME DEFAULT NULL
- `last_fire_time` DATETIME DEFAULT NULL
- `create_time` / `update_time` / `create_by` / `update_by`
- 索引：`idx_user_job_status_fire(status, next_fire_time)`

**sys_user_job_type**（任务类型注册表）：
- `id` BIGINT PK
- `type_code` VARCHAR(50) UNIQUE（对应 UserJobHandler Bean 名称）
- `type_name` VARCHAR(100)
- `description` VARCHAR(500)
- `param_template` JSON（参数模板，前端据此渲染表单）
- `status` TINYINT
- 预置示例数据：`log_cleanup_remind`（日志清理提醒）

## Task 6: B 场景 — SPI 接口与消息 DTO

**新建文件（omni-common-core 模块）：**

| 文件 | 路径 |
|------|------|
| UserJobHandler | `omni-backend/omni-common-core/src/main/java/com/omni/common/core/job/UserJobHandler.java` |
| UserJobMessage | `omni-backend/omni-common-core/src/main/java/com/omni/common/core/job/UserJobMessage.java` |

**UserJobHandler 接口**：
```java
public interface UserJobHandler {
    void execute(UserJobMessage message) throws Exception;
}
```
开发者实现此接口 + `@Component("type_code")` 即可注册新任务类型。

**UserJobMessage**：jobId, tenantId, jobType, jobName, jobParams（Serializable + serialVersionUID）

## Task 7: B 场景 — 扫描 Job + MQ 扇出 + 消费者

**新建文件（omni-base 模块）：**

| 文件 | 路径 |
|------|------|
| SysUserJob | `omni-backend/omni-base/src/main/java/com/omni/base/entity/SysUserJob.java` |
| SysUserJobType | `omni-backend/omni-base/src/main/java/com/omni/base/entity/SysUserJobType.java` |
| SysUserJobMapper | `omni-backend/omni-base/src/main/java/com/omni/base/mapper/SysUserJobMapper.java` |
| SysUserJobTypeMapper | `omni-backend/omni-base/src/main/java/com/omni/base/mapper/SysUserJobTypeMapper.java` |
| UserJobScanHandler | `omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobScanHandler.java` |
| UserJobHandlerRegistry | `omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java` |
| UserJobConsumer | `omni-backend/omni-base/src/main/java/com/omni/base/consumer/UserJobConsumer.java` |
| LogCleanupRemindHandler | `omni-backend/omni-base/src/main/java/com/omni/base/job/handler/LogCleanupRemindHandler.java` |

**UserJobScanHandler 核心逻辑**：
- `@XxlJob("userJobScanHandler")`，XXL-JOB 配置分片广播路由
- `shardIndex = XxlJobHelper.getShardIndex()`, `shardTotal = XxlJobHelper.getShardTotal()`
- SQL: `WHERE status = 1 AND next_fire_time <= NOW() AND MOD(id, #{shardTotal}) = #{shardIndex}`
- 对每条到期任务：更新 last_fire_time → 构建 UserJobMessage → StreamBridge 发送到 `userJob-out-0`
- MQ topic: `user-job-topic`，tag 为 jobType

**UserJobHandlerRegistry**：
- 通过 `Map<String, UserJobHandler>` 自动注入收集所有实现
- `getHandler(typeCode)` 按 Bean 名称路由

**UserJobConsumer**：
- Spring Cloud Stream Consumer Bean `userJobConsumer`
- 从 Registry 获取 Handler → 执行 `handler.execute(message)`

**LogCleanupRemindHandler**：
- `@Component("log_cleanup_remind")` 实现 UserJobHandler
- 仅记录日志，演示扩展模式

## Task 8: omni-base 配置更新

**修改文件：** `omni-backend/omni-base/pom.xml`
- 新增 `omni-common-job` 依赖

**修改文件：** `omni-backend/omni-base/src/main/resources/application.yml`
- stream bindings 新增 `userJob-out-0` 和 `userJobConsumer-in-0`（topic: user-job-topic）
- 新增 `xxl.job.admin.addresses` 和 `xxl.job.executor.appname: omni-base` / `port: 9999`

## Task 9: 编译验证

- `cd omni-backend && ./mvnw clean install`（需设置 JAVA_HOME=C:\APP\JDK25\jdk-25.0.2）
- 确认无编译错误、无新增警告

## 文件清单汇总

### 新建文件（8 个）
1. `omni-backend/omni-common-job/pom.xml`
2. `omni-backend/omni-common-job/src/main/java/com/omni/common/job/XxlJobProperties.java`
3. `omni-backend/omni-common-job/src/main/java/com/omni/common/job/config/XxlJobAutoConfiguration.java`
4. `omni-backend/omni-common-job/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
5. `scripts/sql/init-xxl-job.sql`
6. `omni-backend/omni-common-core/src/main/java/com/omni/common/core/job/UserJobHandler.java`
7. `omni-backend/omni-common-core/src/main/java/com/omni/common/core/job/UserJobMessage.java`
8. `omni-backend/omni-base/src/main/java/com/omni/base/entity/SysUserJob.java`
9. `omni-backend/omni-base/src/main/java/com/omni/base/entity/SysUserJobType.java`
10. `omni-backend/omni-base/src/main/java/com/omni/base/mapper/SysUserJobMapper.java`
11. `omni-backend/omni-base/src/main/java/com/omni/base/mapper/SysUserJobTypeMapper.java`
12. `omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobScanHandler.java`
13. `omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java`
14. `omni-backend/omni-base/src/main/java/com/omni/base/consumer/UserJobConsumer.java`
15. `omni-backend/omni-base/src/main/java/com/omni/base/job/handler/LogCleanupRemindHandler.java`

### 修改文件（5 个）
1. `omni-backend/pom.xml` — modules + dependencyManagement + xxl-job.version
2. `docker-compose.yml` — 新增 xxl-job-admin 服务 + mysql volumes
3. `scripts/sql/init-all.sql` — omni_base section 追加 sys_user_job + sys_user_job_type
4. `omni-backend/omni-base/src/main/java/com/omni/base/service/OperLogArchiver.java` — @Scheduled → @XxlJob
5. `omni-backend/omni-base/pom.xml` — 新增 omni-common-job 依赖
6. `omni-backend/omni-base/src/main/resources/application.yml` — stream bindings + xxl.job 配置
7. `start.bat` — 端口保护追加 18080

### 删除文件（1 个）
1. `omni-backend/omni-base/src/main/java/com/omni/base/config/SchedulingConfig.java`

## 验证步骤

1. 编译：`cd omni-backend && set JAVA_HOME=C:\APP\JDK25\jdk-25.0.2 && .\mvnw.cmd clean install`
2. Docker：`docker compose up -d` → 确认 xxl-job-admin 容器健康
3. 访问 `http://localhost:18080/xxl-job-admin`（admin/123456）
4. 启动 omni-base → XXL-JOB 调度中心确认执行器自动注册
5. 在调度中心配置 `operLogArchiveHandler` 任务，手动触发验证
6. 在调度中心配置 `userJobScanHandler` 任务（分片广播），手动触发验证日志输出
