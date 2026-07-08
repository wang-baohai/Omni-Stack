# MQ 消息发送记录与补偿管理模块

## 架构决策汇总

| 维度 | 决策 |
|------|------|
| 整体方案 | 方案C：本地发件箱 + Feign 聚合查询 |
| Starter | `omni-common-mqlog`，引入即用，零业务代码 |
| 自动建表 | `schema.sql` + `CREATE TABLE IF NOT EXISTS` |
| 异步投递 | XXL-JOB 系统任务，handler 内置于 starter，按执行器 AppName 隔离 |
| 多 MQ 预留 | `broker_type` 字段 + binding name 抽象，策略模式路由 |
| 业务键 | `msg_key` 字段保留，可选（nullable） |
| 死信处理 | 手动重发（PENDING/FAILED）+ 标记忽略（DEAD_LETTER -> SKIPPED） |
| 前端菜单 | 新建"运维监控"一级菜单，含"消息记录"和"操作日志"（从系统管理迁移） |
| 数据迁移 | 同步更新 init-all.sql 种子数据 + 运行中 Docker 数据库实际数据 |

---

## Task 1: 创建 `omni-common-mqlog` Maven 模块

在 `omni-backend/` 下新建模块，POM 依赖：
- `omni-common-core`（R, PageResult, BaseEntity）
- `omni-common-mybatis`（Mapper 扫描，可选依赖）
- `spring-cloud-starter-stream-rocketmq`（optional，MQ 发送能力）
- `omni-common-job`（optional，XXL-JOB 系统任务注册）
- `spring-boot-starter-web`（optional）

文件结构：
```
omni-common-mqlog/
  pom.xml
  src/main/java/com/omni/common/mqlog/
    config/
      MqLogAutoConfiguration.java       -- 自动装配
    entity/
      SysMqMessage.java                 -- 实体类
    mapper/
      SysMqMessageMapper.java           -- MyBatis-Plus Mapper
    template/
      ReliableMessageTemplate.java      -- 可靠消息发送模板
    relay/
      MqMessageRelayService.java        -- 核心投递逻辑
      MqMessageRelayJob.java            -- XXL-JOB handler（@XxlJob + @SystemJobMeta）
    sender/
      MessageSender.java                -- 策略接口
      RocketMqMessageSender.java        -- RocketMQ 实现（基于 StreamBridge）
    controller/
      MqMessageInternalController.java  -- Feign 内部查询 API
  src/main/resources/
    schema.sql                          -- CREATE TABLE IF NOT EXISTS sys_mq_message
    META-INF/spring/
      org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

在父 `pom.xml` 的 `<modules>` 中注册新模块。

---

## Task 2: 数据模型 — `sys_mq_message` 表

`schema.sql` 内容（`CREATE TABLE IF NOT EXISTS`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT PK | 主键 |
| msg_id | VARCHAR(36) UNIQUE NOT NULL | 业务消息ID（UUID），防重投递 |
| topic | VARCHAR(128) NOT NULL | MQ Topic（对应 binding destination） |
| binding_name | VARCHAR(128) NOT NULL | Spring Cloud Stream binding name |
| tag | VARCHAR(64) | MQ Tag（可选） |
| msg_key | VARCHAR(128) | 业务键（可选，如 order:12345） |
| payload | TEXT NOT NULL | 消息体 JSON |
| broker_type | VARCHAR(32) DEFAULT 'rocketmq' | 消息中间件类型（预留多MQ） |
| status | TINYINT NOT NULL DEFAULT 0 | 0=PENDING, 1=SENT, 2=FAILED, 3=DEAD_LETTER, 4=SKIPPED |
| retry_count | INT DEFAULT 0 | 已重试次数 |
| max_retry | INT DEFAULT 3 | 最大重试次数 |
| next_retry_time | DATETIME | 下次重试时间（指数退避） |
| error_msg | VARCHAR(512) | 最后一次失败错误信息 |
| service_name | VARCHAR(64) NOT NULL | 来源服务名（spring.application.name） |
| tenant_id | BIGINT | 租户ID |
| create_time | DATETIME NOT NULL | 创建时间 |
| update_time | DATETIME | 更新时间 |

索引：
- `UNIQUE INDEX uk_msg_id (msg_id)`
- `INDEX idx_relay (status, next_retry_time)` -- relay 轮询复合索引
- `INDEX idx_tenant_time (tenant_id, create_time)` -- 管理查询

同步将 DDL 追加到 `scripts/sql/init-all.sql` 的各服务数据库段落中（`omni_auth`、`omni_base`、`omni_workflow` 各建一份）。

---

## Task 3: 核心组件实现

### 3a. `ReliableMessageTemplate`

提供两个重载方法：
```java
void send(String bindingName, Object payload);
void send(String bindingName, Object payload, String msgKey);
```

核心逻辑：
1. 生成 UUID 作为 `msg_id`
2. 构建 `SysMqMessage` 实体，status=PENDING
3. 在**当前事务**中 INSERT 到 `sys_mq_message` 表（通过 `@Transactional(propagation = REQUIRED)` 加入调用方事务）
4. 事务提交后由 XXL-JOB relay 异步投递

### 3b. `MqMessageRelayJob`（XXL-JOB 系统任务）

```java
@XxlJob("mqRelayHandler")
@SystemJobMeta(
    name = "MQ消息投递",
    description = "轮询 PENDING/待重试 状态的消息并投递到 MQ",
    defaultCron = "0/10 * * * * ?",
    routeStrategy = "FIRST"
)
```

各服务执行器 AppName 不同，handler name 天然隔离，不冲突。

### 3c. `MqMessageRelayService`（核心投递逻辑）

轮询条件：`status IN (0=PENDING, 2=FAILED) AND (next_retry_time IS NULL OR next_retry_time <= NOW())`

投递流程：
1. 批量查询待投递消息（LIMIT 100）
2. 调用对应 `MessageSender` 策略实现发送
3. 成功 -> status=SENT
4. 失败 -> retry_count++，计算 next_retry_time（指数退避：`2^retry_count * 10s`），超过 max_retry -> status=DEAD_LETTER，记录 error_msg

退避策略：第1次 20s，第2次 40s，第3次 80s（基数 10s，2^n 倍增）。

### 3d. `MessageSender` 策略接口

```java
public interface MessageSender {
    String brokerType();  // "rocketmq"
    void send(SysMqMessage message);
}
```

`RocketMqMessageSender`：基于 `StreamBridge` 发送。后续新增 Kafka 时实现 `KafkaMessageSender` 即可。

### 3e. `MqMessageInternalController`（Feign 内部 API）

```java
@GetMapping("/api/internal/mq-message/list")
public R<PageResult<SysMqMessage>> list(...)  // 供 base 服务 Feign 聚合调用
```

此接口不走网关路由，仅供服务间内部调用。

---

## Task 4: 自动装配

`MqLogAutoConfiguration`：
- `@ConditionalOnClass(SysMqMessage.class)` 激活
- 注册 `ReliableMessageTemplate`、`MqMessageRelayService`、`MqMessageRelayJob`、`MessageSender` 实现
- `@MapperScan` 扫描 `com.omni.common.mqlog.mapper`
- 配置 `spring.sql.init.schema-locations` 指向 `classpath:schema.sql`

通过 `AutoConfiguration.imports` 注册，引入 starter 即自动生效。

---

## Task 5: omni-base 服务接入 + 聚合查询 API

### 5a. POM 依赖

`omni-base/pom.xml` 新增：
```xml
<dependency>
    <groupId>com.omni</groupId>
    <artifactId>omni-common-mqlog</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

### 5b. Feign 客户端

定义 `MqMessageFeignClient` 接口，调用各服务的 `/api/internal/mq-message/list`。

### 5c. 聚合查询 Controller

`MqMessageController`（路径 `/api/base/mq-message`）：
- `GET /list` — 聚合各服务消息记录，合并排序后返回分页结果
- `GET /{msgId}` — 根据 msgId 定位服务并获取详情
- `POST /{msgId}/resend` — 手动重发（将 PENDING/FAILED/DEAD_LETTER 状态改回 PENDING）
- `POST /{msgId}/skip` — 标记忽略（DEAD_LETTER -> SKIPPED）

权限码：`base:mqmessage:list`、`base:mqmessage:resend`、`base:mqmessage:skip`

---

## Task 6: 数据库种子数据 + 运行数据迁移

### 6a. 修改 `scripts/sql/init-all.sql`

**当前状态**（Section 4.13，`scripts/sql/init-all.sql` L451-455）：
- ID 61 "操作日志" MENU，parent_id=50（挂在"基础数据"下）
- ID 62 "查看操作日志" API，parent_id=61

**变更内容**：

1. **新增"运维监控"权限节点**（替换原 Section 4.13）：

```sql
-- 4.13 运维监控权限节点（1 个目录 + 2 个菜单 + 4 个 API 权限 = 7 条）
INSERT INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (63, 1, 0,  'monitor',                  '运维监控',     'DIRECTORY', '/63/',          1, 5, 1, 'system'),
    (61, 1, 63, 'base:operlog',             '操作日志',     'MENU',      '/63/61/',       2, 1, 1, 'system'),
    (62, 1, 61, 'base:operlog:list',        '查看操作日志', 'API',       '/63/61/62/',    3, 1, 1, 'system'),
    (64, 1, 63, 'base:mqmessage',           '消息记录',     'MENU',      '/63/64/',       2, 2, 1, 'system'),
    (65, 1, 64, 'base:mqmessage:list',      '查看消息记录', 'API',       '/63/64/65/',    3, 1, 1, 'system'),
    (66, 1, 64, 'base:mqmessage:resend',    '重发消息',     'API',       '/63/64/66/',    3, 2, 1, 'system'),
    (67, 1, 64, 'base:mqmessage:skip',      '忽略消息',     'API',       '/63/64/67/',    3, 3, 1, 'system');
```

2. **更新角色权限映射**（Section 4.14）：追加 (1,63), (1,64), (1,65), (1,66), (1,67)

3. **各服务库追加建表**（omni_auth、omni_base、omni_workflow 各一份 sys_mq_message DDL）

### 6b. 运行中的 Docker 数据库同步迁移

编写并执行迁移 SQL（`USE omni_auth`）：

```sql
-- 1. 新增"运维监控"目录
INSERT INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES (63, 1, 0, 'monitor', '运维监控', 'DIRECTORY', '/63/', 1, 5, 1, 'system');

-- 2. 迁移操作日志：从基础数据(50)移到运维监控(63)下，更新 path
UPDATE sys_permission SET parent_id = 63, path = '/63/61/' WHERE id = 61;
UPDATE sys_permission SET path = '/63/61/62/' WHERE id = 62;

-- 3. 新增消息记录权限节点
INSERT INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (64, 1, 63, 'base:mqmessage',        '消息记录',     'MENU',      '/63/64/',    2, 2, 1, 'system'),
    (65, 1, 64, 'base:mqmessage:list',   '查看消息记录', 'API',       '/63/64/65/', 3, 1, 1, 'system'),
    (66, 1, 64, 'base:mqmessage:resend', '重发消息',     'API',       '/63/64/66/', 3, 2, 1, 'system'),
    (67, 1, 64, 'base:mqmessage:skip',   '忽略消息',     'API',       '/63/64/67/', 3, 3, 1, 'system');

-- 4. SUPER_ADMIN 角色追加新权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 63), (1, 64), (1, 65), (1, 66), (1, 67);
```

各服务库（omni_auth、omni_base、omni_workflow）分别执行 sys_mq_message 建表 DDL。

---

## Task 7: 前端页面

### 7a. API 模块

新建 `omni-frontend/src/api/mqMessage.ts`：
- `listMqMessages(params)` — 分页查询
- `getMqMessageDetail(msgId)` — 详情
- `resendMessage(msgId)` — 重发
- `skipMessage(msgId)` — 忽略

### 7b. 消息记录页面

新建 `omni-frontend/src/views/monitor/mq-message/index.vue`：

查询条件：
- 状态下拉（全部/PENDING/SENT/FAILED/DEAD_LETTER/SKIPPED）
- Topic 输入框
- 业务键（msg_key）输入框
- 来源服务下拉
- 时间范围选择器

表格列：消息ID、Topic、业务键、状态（Tag标签色彩区分）、来源服务、重试次数、创建时间、操作

操作按钮：
- 查看详情（弹窗展示 payload JSON + error_msg）
- 重发（PENDING/FAILED/DEAD_LETTER 状态可用，v-permission="base:mqmessage:resend"）
- 忽略（DEAD_LETTER 状态可用，v-permission="base:mqmessage:skip"）

### 7c. 操作日志页面迁移

将 `omni-frontend/src/views/system/operlog/index.vue` 移动到 `omni-frontend/src/views/monitor/oper-log/index.vue`。

**删除**系统管理下的操作日志老路由和老菜单入口。

### 7d. 路由更新

`omni-frontend/src/router/index.ts` 新增"运维监控"路由组：
```
/monitor
  /monitor/mq-message   -- 消息记录
  /monitor/oper-log      -- 操作日志
```

移除原 `/system/operlog` 路由。

---

## Task 8: 文档更新

- `docs/architecture.md` — Module Map 新增 `omni-common-mqlog`，Data Flow 新增消息投递链路
- `docs/backend-patterns.md` — Common Starter 接入规范新增 mqlog 说明
- `AGENTS.md` — Entry Points 新增 mqlog 相关路径，Hard Constraints 补充 relay 规则

---

## Task 9: 编译验证

- 后端：`cd omni-backend && ./mvnw clean install` 通过
- 前端：`cd omni-frontend && npm run build && npm run lint` 通过
