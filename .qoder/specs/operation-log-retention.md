# 操作日志留存功能实现计划

## Context

项目已有基于 Spring ApplicationEvent 的安全审计日志系统（`sys_audit_log`），覆盖登录/登出/账户锁定/用户CRUD/角色变更等 11 种安全事件。但缺少对**业务实体 CRUD 操作**的通用日志记录能力（如"管理员 X 修改了字典类型 Y"）。

本功能的目标是：为 omni-base 及未来业务微服务提供通用的操作日志留存能力，支持 CRUD 全记录、变更快照（old/new diff）、异步 MQ 传输、冷热分离归档、以及前端可视化查询，满足后续审计需求。

---

## 设计决策总览

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 日志范围 | 业务实体 CRUD（不含 auth 服务） | auth 已有安全审计日志覆盖 |
| 采集方式 | `@OperLog` 注解 + AOP 切面 | 侵入性低，Controller 层标注 |
| 变更快照 | AOP 自动 diff（实体类 + Mapper 查找） | 通过 ApplicationContext 查找 BaseMapper，selectById 前后对比 |
| 传输机制 | Spring Cloud Stream + RocketMQ | `spring-cloud-starter-stream-rocketmq:2025.1.0.0`（SCA BOM 管理，兼容 Spring Boot 4.x） |
| 存储/查询 | omni-base 服务统一收集和查询 | 集中存储，简化架构 |
| 保留策略 | 冷热分离（热表 180 天 + 归档表） | 平衡查询性能和审计需求 |
| 注解位置 | Controller 层 + SpEL 表达式 | 清晰分层，SpEL 提取实体 ID |
| RocketMQ 部署 | Docker 单机模式，持久化到 `D:\docker-vm\rocketmq` | 开发环境足够 |
| 前端位置 | "基础数据"目录下新增"操作日志"菜单 | 与字典管理同级 |

---

## 架构总览

```
Browser → Gateway(:8102) → omni-base(:8101) Controller
                                   │
                           ┌───────▼────────┐
                           │  @OperLog AOP  │  (omni-common-operlog)
                           │  1. 捕获请求上下文
                           │  2. selectById (前: old snapshot)
                           │  3. proceed()  (执行目标方法)
                           │  4. selectById (后: new snapshot)
                           │  5. JSON diff
                           │  6. StreamBridge → RocketMQ
                           └───────┬────────┘
                                   │
                           ┌───────▼────────┐
                           │   RocketMQ     │
                           │  operlog-topic │
                           └───────┬────────┘
                                   │
                           ┌───────▼────────┐
                           │  omni-base     │  (OperLogConsumer)
                           │  → INSERT      │  sys_oper_log
                           └────────────────┘

定时任务: OperLogArchiver (每日 02:00)
  sys_oper_log (>180天) → sys_oper_log_archive → DELETE from hot
```

---

## Step 1: RocketMQ Docker 部署

用户手动拉取镜像和启动容器，提供 `docker run` 命令。

**镜像**: `apache/rocketmq:5.3.2`（与 SCA BOM 管理的 `rocketmq-client:5.3.1` 兼容）

**持久化目录**: `D:\docker-vm\rocketmq`（需预先创建子目录 `logs`、`store`、`conf`）

**启动命令**（NameServer + Broker 分容器）:

```bash
# NameServer (端口 9876)
docker run -d --name omni-rocketmq-namesrv \
  --restart unless-stopped \
  -p 9876:9876 \
  -v D:\docker-vm\rocketmq\logs:/home/rocketmq/logs \
  apache/rocketmq:5.3.2 sh mqnamesrv

# Broker (端口 10911, 依赖 NameServer)
docker run -d --name omni-rocketmq-broker \
  --restart unless-stopped \
  -p 10911:10911 -p 10909:10909 \
  -v D:\docker-vm\rocketmq\logs:/home/rocketmq/logs \
  -v D:\docker-vm\rocketmq\store:/home/rocketmq/store \
  -e NAMESRV_ADDR=host.docker.internal:9876 \
  -e JAVA_OPT_EXT="-Drocketmq.broker.diskSpaceWarningLevelRatio=0.98" \
  apache/rocketmq:5.3.2 sh mqbroker -n host.docker.internal:9876 --enable-proxy
```

> `--restart unless-stopped`：Docker 启动时自动拉起，手动 `docker stop` 后不会自动重启。
> `host.docker.internal`：Docker Desktop (Windows/Mac) 内置 DNS，指向宿主机。

同时更新 `docker-compose.yml` 添加 RocketMQ 服务定义（可选，方便一键启动）。

**修改文件**:
- `docker-compose.yml` — 添加 rocketmq-namesrv 和 rocketmq-broker 服务

---

## Step 2: SQL Schema

在 `scripts/sql/init-all.sql` 中添加：

### 2.1 操作日志表（omni_base 库）

```sql
-- ============================================================
-- 5.3 操作日志表（热表，保留最近 180 天）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
    oper_username    VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
    oper_time       DATETIME     NOT NULL COMMENT '操作时间',
    module          VARCHAR(100) DEFAULT NULL COMMENT '业务模块名称',
    oper_type       VARCHAR(20)  NOT NULL COMMENT '操作类型: CREATE/UPDATE/DELETE/QUERY/EXPORT/IMPORT',
    request_method  VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP方法',
    request_url     VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
    request_params  TEXT         DEFAULT NULL COMMENT '请求参数JSON',
    response_status INT          DEFAULT 200 COMMENT '响应状态码',
    ip_address      VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    user_agent      VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    execution_time  BIGINT       DEFAULT NULL COMMENT '执行耗时（毫秒）',
    old_value       JSON         DEFAULT NULL COMMENT '变更前值快照',
    new_value       JSON         DEFAULT NULL COMMENT '变更后值快照',
    error_msg       VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_operlog_tenant (tenant_id),
    INDEX idx_operlog_time (oper_time),
    INDEX idx_operlog_module (module),
    INDEX idx_operlog_type (oper_type),
    INDEX idx_operlog_username (oper_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志热表';

-- ============================================================
-- 5.4 操作日志归档表（冷表）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_oper_log_archive (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
    oper_username    VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
    oper_time       DATETIME     NOT NULL COMMENT '操作时间',
    module          VARCHAR(100) DEFAULT NULL COMMENT '业务模块名称',
    oper_type       VARCHAR(20)  NOT NULL COMMENT '操作类型',
    request_method  VARCHAR(10)  DEFAULT NULL COMMENT 'HTTP方法',
    request_url     VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
    request_params  TEXT         DEFAULT NULL COMMENT '请求参数JSON',
    response_status INT          DEFAULT 200 COMMENT '响应状态码',
    ip_address      VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    user_agent      VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    execution_time  BIGINT       DEFAULT NULL COMMENT '执行耗时（毫秒）',
    old_value       JSON         DEFAULT NULL COMMENT '变更前值快照',
    new_value       JSON         DEFAULT NULL COMMENT '变更后值快照',
    error_msg       VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    create_time     DATETIME     DEFAULT NULL COMMENT '原始记录创建时间',
    archived_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
    INDEX idx_archive_tenant (tenant_id),
    INDEX idx_archive_time (oper_time),
    INDEX idx_archive_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志归档冷表';
```

### 2.2 权限种子数据（omni_auth 库）

```sql
-- 操作日志权限节点（1 个菜单 + 1 个 API = 2 条）
INSERT INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (61, 1, 50, 'base:operlog',       '操作日志',     'MENU', '/50/61/',    2, 2, 1, 'system'),
    (62, 1, 61, 'base:operlog:list',  '查看操作日志', 'API',  '/50/61/62/', 3, 1, 1, 'system');

-- SUPER_ADMIN 角色追加操作日志权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 61), (1, 62);
```

**修改文件**: `scripts/sql/init-all.sql`

---

## Step 3: 新建 `omni-common-operlog` 模块

### 3.1 模块定位

| 放置位置 | 内容 |
|----------|------|
| `omni-common-core`（纯 POJO） | `@OperLog` 注解、`OperType` 枚举、`OperLogMessage` MQ DTO |
| `omni-common-operlog`（Spring 自动装配） | AOP 切面、实体 Diff 器、MQ 生产者、自动配置 |

### 3.2 在 omni-common-core 中添加 POJO

**新建文件** (3个):

- `omni-backend/omni-common-core/src/main/java/com/omni/common/core/operlog/OperLog.java`
  - `@Target(METHOD)`, `@Retention(RUNTIME)`
  - 属性: `module` (String), `operType` (OperType), `entityClass` (Class<?>, default Object.class), `idExpr` (String, SpEL, default "")

- `omni-backend/omni-common-core/src/main/java/com/omni/common/core/operlog/OperType.java`
  - 枚举: `CREATE, UPDATE, DELETE, QUERY, EXPORT, IMPORT`

- `omni-backend/omni-common-core/src/main/java/com/omni/common/core/operlog/OperLogMessage.java`
  - Serializable POJO，用于 MQ 传输
  - 字段: `operUsername`, `tenantId`, `operTime` (LocalDateTime), `module`, `operType`, `requestMethod`, `requestUrl`, `requestParams`, `responseStatus`, `ipAddress`, `userAgent`, `executionTime`, `oldValue`, `newValue`, `errorMsg`
  - 必须声明 `serialVersionUID`

### 3.3 新建 omni-common-operlog 模块

**POM 依赖** (`omni-backend/omni-common-operlog/pom.xml`):
- `omni-common-core`（必需）
- `spring-boot-starter-aop`（optional）
- `spring-boot-starter-web`（optional，用于 RequestContextHolder）
- `mybatis-plus-spring-boot4-starter`（optional，用于 BaseMapper diff）
- `spring-cloud-starter-stream-rocketmq`（optional，用于 StreamBridge）
- `lombok`（provided）

**新建文件** (5个):

| 文件 | 职责 |
|------|------|
| `com/omni/common/operlog/aspect/OperLogAspect.java` | AOP 切面，核心逻辑 |
| `com/omni/common/operlog/diff/EntityDiffer.java` | 实体 JSON diff（只输出变更字段） |
| `com/omni/common/operlog/producer/OperLogProducer.java` | StreamBridge 封装，发送 MQ 消息 |
| `com/omni/common/operlog/config/OperLogAutoConfiguration.java` | 自动装配，条件守卫 |
| `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 注册自动配置 |

### 3.4 OperLogAspect 核心逻辑

```
@Around("@annotation(operLog)")
1. 从 RequestContextHolder 获取 HttpServletRequest
2. 捕获: URL, HTTP方法, IP(X-Forwarded-For), User-Agent, 请求参数(序列化)
3. 从 SecurityContextHolder 获取操作人用户名
4. 从 X-Tenant-Id header 获取租户 ID
5. 记录 startTime = System.currentTimeMillis()
6. 若 operType 为 UPDATE 或 DELETE:
   - SpEL 解析 idExpr 获取实体 ID
   - ApplicationContext.getBean(BaseMapper<entityClass>) 查找 Mapper
   - selectById(entityId) → 序列化为 oldValue
7. proceed() 执行目标方法
8. 若 operType 为 CREATE:
   - SpEL 从返回值提取新实体 ID (如 #result.data.id)
   - selectById → 序列化为 newValue
9. 若 operType 为 UPDATE:
   - selectById → 序列化为 newValue
10. 若 operType 为 DELETE: newValue = null (已在步骤6获取 oldValue)
11. 若 operType 为 QUERY/EXPORT/IMPORT: 不做实体 diff
12. 构建 OperLogMessage，通过 OperLogProducer 发送 MQ
13. 所有异常 catch → log.warn，不阻断主请求
```

**SpEL 求值**: 使用 `SpelExpressionParser`（线程安全，缓存为字段）+ `MethodBasedEvaluationContext`（支持参数名解析）。

**错误处理**:
- Mapper 未找到 → `log.warn("操作日志：未找到实体 {} 的 Mapper，跳过变更快照")`
- SpEL 执行失败 → `log.warn("操作日志：SpEL 表达式 {} 执行失败: {}")`
- MQ 发送失败 → `log.warn("操作日志：MQ 消息发送失败: {}")`
- JSON 快照超过 4000 字符 → 截断并 warn

### 3.5 自动配置条件

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(name = {
    "org.aspectj.lang.ProceedingJoinPoint",
    "org.springframework.cloud.stream.function.StreamBridge"
})
```

- `OperLogAspect`: `@ConditionalOnClass(BaseMapper.class)` — 仅在有 MyBatis-Plus 时启用 diff
- `OperLogProducer`: `@ConditionalOnClass(StreamBridge.class)` — 仅在有 Stream 时启用 MQ
- `EntityDiffer`: 无条件注册（仅依赖 Jackson ObjectMapper）

---

## Step 4: omni-base 服务端实现

### 4.1 POM 变更

在 `omni-backend/omni-base/pom.xml` 添加:
```xml
<dependency>
    <groupId>com.omni</groupId>
    <artifactId>omni-common-operlog</artifactId>
    <version>${project.version}</version>
</dependency>
```

> `spring-cloud-starter-stream-rocketmq` 不需要在 omni-base POM 中单独声明，因为 `omni-common-operlog` 已将其作为传递依赖。

### 4.2 新建文件 (11个)

| 文件 | 职责 |
|------|------|
| `com/omni/base/entity/SysOperLog.java` | 热表实体，`@TableName("sys_oper_log")`，不继承 BaseEntity |
| `com/omni/base/entity/SysOperLogArchive.java` | 冷表实体，`@TableName("sys_oper_log_archive")`，多 `archivedTime` 字段 |
| `com/omni/base/mapper/SysOperLogMapper.java` | `extends BaseMapper<SysOperLog>` |
| `com/omni/base/mapper/SysOperLogArchiveMapper.java` | `extends BaseMapper<SysOperLogArchive>` |
| `com/omni/base/service/OperLogService.java` | 接口: `save(OperLogMessage)`, `listOperLogs(Long, OperLogQuery)` |
| `com/omni/base/service/impl/OperLogServiceImpl.java` | 实现: 插入 + 分页查询（租户隔离） |
| `com/omni/base/service/OperLogVO.java` | 视图对象，`@Builder` + `Serializable` |
| `com/omni/base/dto/OperLogQuery.java` | 查询 DTO: module, operType, operUsername, startTime, endTime, page, size |
| `com/omni/base/controller/OperLogController.java` | `GET /api/base/oper-log/list`，`@PreAuthorize("hasAuthority('base:operlog:list')")` |
| `com/omni/base/consumer/OperLogConsumer.java` | Spring Cloud Stream Consumer bean |
| `com/omni/base/service/OperLogArchiver.java` | 定时归档任务 |
| `com/omni/base/config/SchedulingConfig.java` | `@EnableScheduling` |

### 4.3 OperLogConsumer（Spring Cloud Stream 消费者）

```java
@Configuration
public class OperLogConsumer {
    @Bean
    public Consumer<OperLogMessage> operlogConsumer(OperLogService operLogService) {
        return message -> {
            try {
                operLogService.save(message);
            } catch (Exception e) {
                log.warn("操作日志消费失败: {}", e.getMessage());
            }
        };
    }
}
```

Bean 名称 `operlogConsumer` 对应 binding `operlogConsumer-in-0`。

### 4.4 OperLogArchiver（定时归档）

- **触发**: `@Scheduled(cron = "0 0 2 * * ?")` 每日 02:00
- **逻辑**: 分批（每批 1000 条）将 `oper_time < NOW() - 180天` 的记录从 `sys_oper_log` 迁移到 `sys_oper_log_archive`
- **事务**: 每批独立 `@Transactional`，避免长事务
- **防重入**: `AtomicBoolean running` 标志
- **错误处理**: 单批失败 log.error 继续下一批

### 4.5 application.yml 变更

在 `omni-backend/omni-base/src/main/resources/application.yml` 的 `spring.cloud` 下添加:

```yaml
spring:
  cloud:
    stream:
      rocketmq:
        binder:
          name-server: 127.0.0.1:9876
      bindings:
        operlog-out-0:
          destination: operlog-topic
          content-type: application/json
        operlogConsumer-in-0:
          destination: operlog-topic
          content-type: application/json
          group: operlog-consumer-group
```

> `operlog-out-0`: StreamBridge 程序化发送 binding（AOP 切面中使用）
> `operlogConsumer-in-0`: Consumer bean 消费 binding

---

## Step 5: 现有 Controller 添加 @OperLog 注解

### 5.1 DictTypeController

```java
@OperLog(module = "字典类型管理", operType = OperType.QUERY)
@GetMapping("/list")
public R<PageResult<SysDictType>> list(...) { ... }

@OperLog(module = "字典类型管理", operType = OperType.QUERY, entityClass = SysDictType.class, idExpr = "#id")
@GetMapping("/{id}")
public R<SysDictType> getById(...) { ... }

@OperLog(module = "字典类型管理", operType = OperType.CREATE, entityClass = SysDictType.class, idExpr = "#result.data.id")
@PostMapping
public R<SysDictType> create(...) { ... }

@OperLog(module = "字典类型管理", operType = OperType.UPDATE, entityClass = SysDictType.class, idExpr = "#id")
@PutMapping("/{id}")
public R<SysDictType> update(...) { ... }

@OperLog(module = "字典类型管理", operType = OperType.DELETE, entityClass = SysDictType.class, idExpr = "#id")
@DeleteMapping("/{id}")
public R<Void> delete(...) { ... }

@OperLog(module = "字典类型管理", operType = OperType.UPDATE, entityClass = SysDictType.class, idExpr = "#id")
@PutMapping("/{id}/status")
public R<Void> toggleStatus(...) { ... }
```

### 5.2 DictDataController

```java
@OperLog(module = "字典数据管理", operType = OperType.QUERY)
@GetMapping("/list")
public R<PageResult<SysDictData>> list(...) { ... }

@OperLog(module = "字典数据管理", operType = OperType.CREATE, entityClass = SysDictData.class, idExpr = "#result.data.id")
@PostMapping
public R<SysDictData> create(...) { ... }

@OperLog(module = "字典数据管理", operType = OperType.UPDATE, entityClass = SysDictData.class, idExpr = "#id")
@PutMapping("/{id}")
public R<SysDictData> update(...) { ... }

@OperLog(module = "字典数据管理", operType = OperType.DELETE, entityClass = SysDictData.class, idExpr = "#id")
@DeleteMapping("/{id}")
public R<Void> delete(...) { ... }

@OperLog(module = "字典数据管理", operType = OperType.UPDATE)
@PostMapping("/refresh-cache")
public R<Void> refreshCache(...) { ... }
```

**修改文件**:
- `omni-backend/omni-base/src/main/java/com/omni/base/controller/DictTypeController.java`
- `omni-backend/omni-base/src/main/java/com/omni/base/controller/DictDataController.java`

---

## Step 6: 父 POM 变更

在 `omni-backend/pom.xml` 的 `<modules>` 中添加 `omni-common-operlog`（位于 `omni-common-redis-reactive` 之后、`omni-auth` 之前）:

```xml
<modules>
    <module>omni-common-core</module>
    <module>omni-common</module>
    <module>omni-common-mybatis</module>
    <module>omni-common-redis</module>
    <module>omni-common-redis-reactive</module>
    <module>omni-common-operlog</module>  <!-- 新增 -->
    <module>omni-auth</module>
    <module>omni-base</module>
    <module>omni-gateway</module>
</modules>
```

**修改文件**: `omni-backend/pom.xml`

---

## Step 7: 前端实现

### 7.1 新建文件 (2个)

- `omni-frontend/src/api/oper-log.ts` — API 模块
  - 接口 `OperLog`: id, tenantId, operUsername, operTime, module, operType, requestMethod, requestUrl, requestParams, responseStatus, ipAddress, userAgent, executionTime, oldValue, newValue, errorMsg, createTime
  - 接口 `OperLogQuery`: module?, operType?, operUsername?, startTime?, endTime?, page, size
  - 函数 `listOperLogs(params)`: `GET /base/oper-log/list`

- `omni-frontend/src/views/base/operlog/index.vue` — 操作日志页面
  - `<script setup>` → `<template>` → `<style scoped>`（项目强制顺序）
  - **筛选区**: 模块名称(input)、操作类型(select)、操作人(input)、时间范围(datetimerange)
  - **数据表格**: 操作时间、操作人、模块、操作类型(彩色tag)、请求(Method+URL)、IP、执行时间(ms)、状态(成功/失败tag)
  - **详情弹窗**: el-dialog，展示完整请求信息 + 变更快照(old/new JSON 并排 pre 块) + 错误信息
  - **行点击** 打开详情弹窗（只读页面不需要操作列）
  - `v-permission="'base:operlog:list'"` 保护表格
  - 操作类型 tag 颜色: CREATE=success, UPDATE=warning, DELETE=danger, QUERY=info, EXPORT/IMPORT=primary

### 7.2 修改文件 (4个)

- `omni-frontend/src/layout/index.vue`
  - `menuI18nMap` 添加: `'base:operlog': 'common.operLogs'`
  - `iconMap` 添加: `'base:operlog': 'Document'`

- `omni-frontend/src/router/index.ts`
  - `iconMap` 添加: `'base:operlog': 'Document'`
  - 路由自动通过 `import.meta.glob` 发现 `views/base/operlog/index.vue` 并注册 `/admin/operlog`

- `omni-frontend/src/locales/zh-CN.ts`
  - `common` 添加: `operLogs: '操作日志'`
  - 新增 `operLog` 节（约 30 个键：模块、操作类型、状态、详情弹窗字段、操作类型标签等）
  - `permission` 添加: `perm_base_operlog`, `perm_base_operlog_list`

- `omni-frontend/src/locales/en-US.ts`
  - 同步添加对应英文翻译

---

## Step 8: 文档更新

- `docs/architecture.md` — 添加操作日志模块说明和数据流
- `AGENTS.md` — Entry Points 添加 OperLog 相关路径，Hard Constraints 添加操作日志相关规则

---

## 完整文件清单

### 新建文件 (21个)

| # | 路径 | 职责 |
|---|------|------|
| 1 | `omni-backend/omni-common-operlog/pom.xml` | Maven 模块 |
| 2 | `omni-common-core/.../operlog/OperLog.java` | @OperLog 注解 |
| 3 | `omni-common-core/.../operlog/OperType.java` | 操作类型枚举 |
| 4 | `omni-common-core/.../operlog/OperLogMessage.java` | MQ 消息 DTO |
| 5 | `omni-common-operlog/.../aspect/OperLogAspect.java` | AOP 切面（核心） |
| 6 | `omni-common-operlog/.../diff/EntityDiffer.java` | 实体 JSON diff |
| 7 | `omni-common-operlog/.../producer/OperLogProducer.java` | StreamBridge 生产者 |
| 8 | `omni-common-operlog/.../config/OperLogAutoConfiguration.java` | 自动配置 |
| 9 | `omni-common-operlog/.../META-INF/spring/...AutoConfiguration.imports` | 注册文件 |
| 10 | `omni-base/.../entity/SysOperLog.java` | 热表实体 |
| 11 | `omni-base/.../entity/SysOperLogArchive.java` | 冷表实体 |
| 12 | `omni-base/.../mapper/SysOperLogMapper.java` | 热表 Mapper |
| 13 | `omni-base/.../mapper/SysOperLogArchiveMapper.java` | 冷表 Mapper |
| 14 | `omni-base/.../service/OperLogService.java` | Service 接口 |
| 15 | `omni-base/.../service/impl/OperLogServiceImpl.java` | Service 实现 |
| 16 | `omni-base/.../service/OperLogVO.java` | 视图对象 |
| 17 | `omni-base/.../dto/OperLogQuery.java` | 查询 DTO |
| 18 | `omni-base/.../controller/OperLogController.java` | 查询接口 |
| 19 | `omni-base/.../consumer/OperLogConsumer.java` | MQ 消费者 |
| 20 | `omni-base/.../service/OperLogArchiver.java` | 定时归档 |
| 21 | `omni-base/.../config/SchedulingConfig.java` | 启用调度 |
| 22 | `omni-frontend/src/api/oper-log.ts` | 前端 API 模块 |
| 23 | `omni-frontend/src/views/base/operlog/index.vue` | 操作日志页面 |

### 修改文件 (11个)

| # | 路径 | 变更 |
|---|------|------|
| 1 | `omni-backend/pom.xml` | `<modules>` 添加 `omni-common-operlog` |
| 2 | `omni-base/pom.xml` | 添加 `omni-common-operlog` 依赖 |
| 3 | `omni-base/.../controller/DictTypeController.java` | 添加 @OperLog 注解 |
| 4 | `omni-base/.../controller/DictDataController.java` | 添加 @OperLog 注解 |
| 5 | `omni-base/src/main/resources/application.yml` | 添加 Spring Cloud Stream 配置 |
| 6 | `scripts/sql/init-all.sql` | DDL + 权限种子数据 |
| 7 | `docker-compose.yml` | 添加 RocketMQ 服务 |
| 8 | `omni-frontend/src/layout/index.vue` | menuI18nMap + iconMap |
| 9 | `omni-frontend/src/router/index.ts` | iconMap |
| 10 | `omni-frontend/src/locales/zh-CN.ts` | operLog i18n 键 |
| 11 | `omni-frontend/src/locales/en-US.ts` | operLog i18n 键 |

---

## 实施顺序

```
1. SQL Schema (init-all.sql)
2. RocketMQ Docker 部署
3. omni-common-core POJO (注解 + 枚举 + DTO)
4. omni-common-operlog 模块 (AOP + diff + producer + auto-config)
5. 父 POM + omni-base POM 依赖变更
6. omni-base 服务端 (entity + mapper + service + consumer + archiver + controller)
7. omni-base application.yml Stream 配置
8. DictTypeController / DictDataController 添加 @OperLog
9. 前端 (API + 页面 + i18n + layout/router)
10. 后端编译验证 + 前端构建验证
```

---

## 验证方案

### 后端验证
1. 启动 RocketMQ 容器，确认 NameServer (:9876) 和 Broker (:10911) 正常
2. 执行 `scripts/sql/init-all.sql` 重建数据库（或手动执行新增 DDL + 权限数据）
3. `cd omni-backend && set JAVA_HOME=C:\APP\JDK25\jdk-25.0.2 && ./mvnw clean install` — 编译通过
4. 启动 omni-base 服务，观察日志中出现 Spring Cloud Stream 连接 RocketMQ 成功的日志
5. 调用字典管理 API（如 `POST /api/base/dict/type`），验证:
   - 请求正常返回（不被 AOP 阻断）
   - `sys_oper_log` 表中出现新记录
   - `old_value` / `new_value` JSON 字段包含正确的变更快照

### 前端验证
1. `cd omni-frontend && npm run build` — 构建通过
2. `npm run lint` — 零错误
3. 启动开发服务器，登录后侧边栏"基础数据"下出现"操作日志"菜单
4. 点击进入操作日志页面，验证:
   - 筛选表单正常渲染
   - 数据表格正常加载并展示分页
   - 操作类型和状态显示彩色 tag
   - 点击行打开详情弹窗，JSON 格式化显示
   - 中英文切换正常

### 归档验证
1. 手动触发 `OperLogArchiver.archive()` 方法（或等待 02:00 定时执行）
2. 确认超过 180 天的记录从 `sys_oper_log` 迁移到 `sys_oper_log_archive`
3. 确认归档表 `archived_time` 字段已填充
