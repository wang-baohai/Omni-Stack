# 消息可靠性发送（Transactional Outbox）

> 本文档描述 Omni-Stack 的 Transactional Outbox 模式实现，保证消息至少一次投递。  
> 架构概览详见 [architecture.md](architecture.md)。Docker 部署配置详见 [docker-deployment.md](docker-deployment.md)。

Omni-Stack 通过 **Transactional Outbox** 模式 + **XXL-JOB** 中继调度，保证消息至少一次投递。系统不直接向 MQ 发送消息（避免事务回滚时数据丢失），而是将 PENDING 记录写入本地 `sys_mq_message` 表（同一数据库事务），由后台中继任务异步投递到 MQ Broker。

## 1. 架构概览

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        业务服务（如 omni-auth）                             │
│                                                                            │
│  @OperLog 方法 ──> OperLogAspect ──> OperLogProducer                       │
│                                            │                               │
│                                            ▼                               │
│                                    ReliableMessageRelay（接口）             │
│                                            │                               │
│  ┌─────────────────────────────────────────┼─────────────────────────────┐ │
│  │            omni-common-mqlog            │                             │ │
│  │                                         ▼                             │ │
│  │                              ReliableMessageTemplate                   │ │
│  │                                  │                                     │ │
│  │                    INSERT sys_mq_message (PENDING)                      │ │
│  │                    [同一本地事务]                                        │ │
│  └─────────────────────────────────────────┼─────────────────────────────┘ │
└────────────────────────────────────────────┼───────────────────────────────┘
                                             │
┌────────────────────────────────────────────┼───────────────────────────────┐
│  XXL-JOB 调度器（每 10 秒）               ▼                               │
│                                                                            │
│  MqMessageRelayJob (@XxlJob + @SystemJobMeta)                              │
│       │                                                                    │
│       ▼                                                                    │
│  MqMessageRelayService                                                     │
│       │ fetchPendingMessages()  ──> PENDING / FAILED 且退避已过期           │
│       │                                                                    │
│       ▼ relayOne(msg)                                                      │
│  MessageSender 策略 ──> RocketMqMessageSender (StreamBridge)              │
│       │                                                                    │
│       ├── 成功  ──> status = SENT                                          │
│       └── 失败  ──> retry_count++，指数退避                                │
│                         └── 超过最大重试次数 ──> DEAD_LETTER               │
└────────────────────────────────────────────────────────────────────────────┘
```

**模块依赖**：

| 模块 | 职责 |
|------|------|
| `omni-common-core` | 定义 `ReliableMessageRelay` 接口（纯 POJO，零 Spring 依赖） |
| `omni-common-mqlog` | 实现 Outbox 模式：`ReliableMessageTemplate`、`MqMessageRelayService`、`MqMessageRelayJob`、`MessageSender`、自动配置 |
| `omni-common-operlog` | 可选调用方：`OperLogProducer` 优先使用 `ReliableMessageRelay`，不可用时回退到直接 `StreamBridge` |
| `omni-base` | 对外管理控制器 `MqMessageController`，供前端管理界面使用 |

**关键设计决策**：

- **为什么选择 Outbox 而非直接发送？** 在事务内直接发送 MQ 消息会产生分布式事务问题——如果 MQ 发送成功但数据库事务回滚，消费者会收到一条"幽灵消息"。Outbox 模式将所有操作保持在单个本地事务中。
- **为什么使用 XXL-JOB 作为中继引擎？** 项目已经使用 XXL-JOB 进行调度。复用它来中继消息可以避免引入独立的轮询守护进程。每个服务的 executor AppName 不同，因此同一个 `mqRelayHandler` 名称在各服务间天然隔离。
- **为什么使用显式 tenantId 参数？** 租户隔离必须在 API 层面保证——隐式的 ThreadLocal 解析方式脆弱且容易出错。每个调用方都必须显式传递 `tenantId`。

## 2. 消息生命周期

### 2.1 状态机

```
                         ┌──────────┐
                INSERT   │          │   中继成功
  ─────────────────────> │ PENDING  │ ──────────────────> SENT
                         │  (0)     │                      (1)
                         │          │
                         └────┬─────┘
                              │ 中继失败
                              ▼
                         ┌──────────┐
                         │          │   重试次数 < 最大重试次数
                         │ FAILED   │ ──────────────────> 回到 PENDING
                         │  (2)     │   （next_retry_time = now + 2^count × 10s）
                         │          │
                         └────┬─────┘
                              │ 重试次数 >= 最大重试次数
                              ▼
                         ┌──────────┐
                         │DEAD_LETTER│
                         │  (3)     │
                         └────┬─────┘
                              │
                    ┌─────────┴─────────┐
                    │ 重发               │ 跳过
                    ▼                    ▼
               PENDING (0)         SKIPPED (4)
```

| 状态 | 编码 | 描述 |
|------|------|------|
| PENDING | 0 | 等待投递或就绪待重试 |
| SENT | 1 | 已成功投递到 MQ（终态） |
| FAILED | 2 | 投递失败，等待下次重试（退避中） |
| DEAD_LETTER | 3 | 超过最大重试次数（终态，需人工处理） |
| SKIPPED | 4 | 管理员手动标记为忽略（终态） |

### 2.2 写入路径

`ReliableMessageTemplate` 实现了 `ReliableMessageRelay` 接口：

```java
// 由 OperLogProducer 或任意业务代码调用
reliableMessageRelay.send("oper-log-out-0", operLogMessage, tenantId);
reliableMessageRelay.send("order-out-0", orderPayload, tenantId, "order:12345");
```

内部执行流程：
1. 生成 UUID 作为 `msgId`（去重键）
2. 通过 `ObjectMapper` 将 payload 序列化为 JSON
3. 构建 `SysMqMessage` 记录，设置 `status = PENDING`、`brokerType = "rocketmq"`、`tenantId`、`serviceName`
4. 在 `@Transactional(REQUIRED)` 事务内通过 MyBatis-Plus INSERT 到 `sys_mq_message` 表

### 2.3 中继路径

`MqMessageRelayJob` 由 XXL-JOB 调度（默认：每 10 秒，FIRST 路由策略）：

1. `fetchPendingMessages()` — 查询 `status IN (PENDING, FAILED)` 且 (`next_retry_time IS NULL` 或 `next_retry_time <= NOW()`) 的记录，按 `create_time ASC` 排序，限制 100 条
2. 对每条消息，根据 `broker_type` 从 sender 映射表中查找对应的 `MessageSender`
3. 调用 `sender.send(msg)` — 成功则标记 SENT；失败则递增重试次数并应用指数退避
4. 如果 `retryCount >= maxRetry`，标记为 DEAD_LETTER

### 2.4 重试策略

指数退避公式：**2^retryCount × 10 秒**

| 重试次数 | 退避时间 | 下次重试间隔 |
|----------|----------|------------|
| 1 | 2^1 × 10 = 20s | ~20 秒 |
| 2 | 2^2 × 10 = 40s | ~40 秒 |
| 3 | 2^3 × 10 = 80s | ~80 秒 |

默认 `max_retry = 3`。3 次失败后消息进入 DEAD_LETTER 状态。

### 2.5 死信处理

死信需要管理员通过前端管理界面（`运维监控 → 消息记录`）手动介入处理：

- **重发**（`POST /api/base/mq-message/{msgId}/resend`）：将状态重置为 PENDING，清除重试计数和退避计时。中继任务会在下次轮询时重新拾取该消息。
- **跳过**（`POST /api/base/mq-message/{msgId}/skip`）：将状态从 DEAD_LETTER 转为 SKIPPED，确认该消息不再投递。

## 3. 租户隔离

### 3.1 设计：显式参数传递（非 ThreadLocal）

`ReliableMessageRelay.send()` 方法要求显式传入 `Long tenantId` 参数。这是一个刻意的设计选择：

- **不依赖 ThreadLocal 魔法**：基于 ThreadLocal 的租户解析方式很脆弱——在异步边界、线程池交接或定时任务中可能丢失。
- **编译期安全**：缺少参数会导致编译错误，而不是运行时的静默 Bug。
- **调用方责任**：每个调用方从自己的上下文中提取 tenantId（例如 `OperLogMessage.getTenantId()`、`@RequestHeader("X-Tenant-Id")`）。

```java
// 正确写法：显式传递 tenantId
reliableMessageRelay.send("oper-log-out-0", message, message.getTenantId());

// 错误写法：省略 tenantId 将无法编译
reliableMessageRelay.send("oper-log-out-0", message);
```

### 3.2 写入：Outbox 记录中的 tenantId

`ReliableMessageTemplate.send()` 在插入 `sys_mq_message` 之前设置 `message.setTenantId(tenantId)`。`tenant_id` 列上有索引（`idx_tenant_time`），用于高效的租户范围查询。

### 3.3 读取：所有查询控制器均按 tenantId 过滤

- **对外控制器**（`omni-base` 中的 `MqMessageController`）：使用 `@RequestHeader("X-Tenant-Id")` 获取租户 ID。所有查询都包含 `.eq(SysMqMessage::getTenantId, tenantId)`。
- **内部控制器**（`omni-common-mqlog` 中的 `MqMessageInternalController`）：使用 `@RequestParam Long tenantId`，用于基于 Feign 的跨服务聚合查询。

### 3.4 中继：不过滤租户（刻意设计）

`MqMessageRelayService` 扫描所有 PENDING/FAILED 消息，不区分 `tenant_id`。这是刻意设计——中继是后台基础设施进程，必须投递所有消息。租户隔离仅适用于面向用户的读写操作。

## 4. 约束与注意事项

### 4.1 tenantId 必须显式传递

绝不要为 `ReliableMessageRelay.send()` 引入基于 ThreadLocal 或 SecurityContext 的租户解析。显式参数就是契约——它能防止静默的 NULL tenantId Bug。

### 4.2 所有查询接口必须过滤

每个查询 `sys_mq_message` 的新端点都必须包含 `tenantId` 过滤。没有例外——即使是"管理员"或"内部"端点也必须遵守租户边界。

### 4.3 幂等 DDL

`schema.sql` 使用 `CREATE TABLE IF NOT EXISTS` 实现安全、幂等的建表。当 `omni-common-mqlog` 在 classpath 中时，表会在服务启动时自动创建。无需手动执行 DDL。

### 4.4 MessageSender 策略模式

添加新的 MQ Broker（如 Kafka）需要：
1. 实现 `MessageSender` 接口：`brokerType()` 返回 `"kafka"`，`send(SysMqMessage)` 处理实际投递。
2. 注册为 Spring Bean——`MqLogAutoConfiguration` 会将所有 `MessageSender` Bean 收集到以 `brokerType` 为键的映射表中。
3. 调用 `ReliableMessageTemplate.send()` 时设置 `broker_type = "kafka"`（或使用对应的 binding）。

无需修改 `MqMessageRelayService` 或中继逻辑。

## 5. 新服务接入指南

为新的服务（如 `omni-order`）添加可靠的 MQ 消息发送功能：

### 步骤 1：添加依赖

在 `omni-order/pom.xml` 中：

```xml
<dependency>
    <groupId>com.omni</groupId>
    <artifactId>omni-common-mqlog</artifactId>
    <version>${project.version}</version>
</dependency>
```

这会自动引入 `omni-common-core`（提供 `ReliableMessageRelay` 接口）和 `omni-common-mybatis`（提供 `SysMqMessageMapper`）。

### 步骤 2：自动建表

`schema.sql` 在启动时自动执行（`CREATE TABLE IF NOT EXISTS sys_mq_message`）。验证方式：

```sql
SELECT COUNT(*) FROM sys_mq_message;
```

### 步骤 3：注入并使用

在业务服务中：

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ReliableMessageRelay reliableMessageRelay;

    @Transactional
    public void createOrder(OrderDTO dto, Long tenantId) {
        // ... 业务逻辑 ...
        Order order = orderMapper.insert(entity);

        // 写入 Outbox — 同一事务，保证原子性
        reliableMessageRelay.send("order-out-0", order, tenantId, "order:" + order.getId());
    }
}
```

### 步骤 4：验证中继任务注册

启动服务并检查 XXL-JOB 管理控制台（`http://localhost:18080`）：
- 执行器：你的服务的 AppName 应出现在执行器列表中
- 任务：`mqRelayHandler` 应已注册，cron 表达式为 `0/10 * * * * ?`
- 如果任务未运行，请手动启动

### 步骤 5：检查前端管理界面

在前端导航到 `运维监控 → 消息记录`：
- 新消息应以 `status = PENDING`（0）出现，中继后转为 `SENT`（1）
- 可按 `tenantId`、`status`、`topic`、`serviceName` 或时间范围过滤
- 可重发失败消息或跳过死信

## 6. 扩展指南

### 6.1 添加新的 MQ Broker

实现 `MessageSender` 接口：

```java
@Component
public class KafkaMessageSender implements MessageSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public String brokerType() {
        return "kafka";
    }

    @Override
    public void send(SysMqMessage msg) {
        kafkaTemplate.send(msg.getTopic(), msg.getMsgKey(), msg.getPayload()).get();
    }
}
```

`MqLogAutoConfiguration` 会自动收集所有 `MessageSender` Bean。中继服务根据 `broker_type` 列进行路由——无需修改中继代码。

### 6.2 自定义重试策略

当前重试参数硬编码在 `MqMessageRelayService` 中：

| 参数 | 位置 | 默认值 |
|------|------|--------|
| `BATCH_SIZE` | `MqMessageRelayService` | 100 |
| `BACKOFF_BASE_SECONDS` | `MqMessageRelayService` | 10 |
| `max_retry` | `SysMqMessage.maxRetry` | 3 |

按消息自定义：在调用 `ReliableMessageTemplate.send()` 时设置 `maxRetry`（需要扩展接口）。全局自定义：使用自定义参数覆盖 `MqMessageRelayService` Bean。

### 6.3 自定义中继调度频率

中继任务的 cron 表达式可在 XXL-JOB 管理控制台中配置。默认为 `0/10 * * * * ?`（每 10 秒）。可根据吞吐量和延迟需求调整。

---

## 7. 技术选型思考：Outbox 模式 vs 直接发送

| 考量 | Outbox 模式（Omni-Stack 采用） | 直接 MQ 发送 |
|------|------------------------------|------------|
| **事务一致性** | 业务数据和消息写入同一 DB 事务，保证原子性 | 分布式事务问题：DB 提交成功但 MQ 发送失败，或反过来 |
| **可靠性** | 消息持久化到 DB，服务宕机后重启可继续投递 | MQ 发送失败时消息丢失，无法重试 |
| **解耦性** | 业务代码仅写 Outbox 表，不依赖 MQ Broker 可用性 | 业务代码直接依赖 MQ 连接，Broker 不可用时阻塞 |
| **延迟** | 最大延迟 = 中继任务调度间隔（10s） + 投递时间 | 实时发送，延迟最低 |
| **复杂性** | 需要 Outbox 表 + 中继任务 + 状态机 | 简单，但需处理分布式事务 |
| **可观测性** | 消息状态可视化（PENDING/SENT/FAILED/DEAD_LETTER） | 仅 MQ Broker 日志 |

**结论**：Outbox 模式以少量延迟换取强一致性和可观测性，适合对消息可靠性要求高的业务场景。

### 直接发送的风险场景

```
场景 1：DB 提交前服务宕机
  直接发送：MQ 已发送，但 DB 事务未提交 → 消费者收到"幽灵消息"
  Outbox：消息未写入 Outbox → 不会发送

场景 2：DB 提交后 MQ 发送失败
  直接发送：DB 已提交，MQ 失败 → 消息丢失
  Outbox：消息已写入 Outbox（PENDING）→ 中继任务重试投递

场景 3：MQ Broker 宕机
  直接发送：业务代码阻塞或抛异常 → 影响用户体验
  Outbox：消息写入 Outbox 不受影响 → Broker 恢复后自动投递
```

## 8. RocketMQ Docker 部署配置详解

### 容器配置

```yaml
# docker-compose.yml
rocketmq-namesrv:
  image: apache/rocketmq:5.3.1
  container_name: omni-rocketmq-namesrv
  ports:
    - "9876:9876"
  command: sh mqnamesrv
  networks:
    - omni-network

rocketmq-broker:
  image: apache/rocketmq:5.3.1
  container_name: omni-rocketmq-broker
  ports:
    - "10911:10911"
    - "10909:10909"
  depends_on:
    - rocketmq-namesrv
  environment:
    NAMESRV_ADDR: rocketmq-namesrv:9876
  command: sh mqbroker -n rocketmq-namesrv:9876
  networks:
    - omni-network
```

### 关键配置说明

| 配置项 | 值 | 说明 |
|---------|-----|------|
| NameServer 端口 | 9876 | RocketMQ 服务发现端口 |
| Broker 端口 | 10911 | Broker 主端口 |
| Broker VIP 端口 | 10909 | Broker VIP 通道（快速响应） |
| 网络 | omni-network | 与其他服务同一 Bridge 网络 |

### Spring Cloud Stream 配置

```yaml
# application.yml
spring:
  cloud:
    stream:
      rocketmq:
        binder:
          name-server: ${ROCKETMQ_NAMESRV:rocketmq-namesrv:9876}
      bindings:
        oper-log-out-0:
          destination: omni-oper-log-topic
          content-type: application/json
```

**环境变量覆盖**：Docker 部署时通过 `SPRING_CLOUD_STREAM_ROCKETMQ_BINDER_NAME_SERVER` 环境变量覆盖 NameServer 地址。

## 9. 故障排查指南

| 问题 | 可能原因 | 排查方法 |
|------|---------|----------|
| **消息始终 PENDING** | 中继任务未启动 | XXL-JOB 控制台检查 `mqRelayHandler` 是否已注册并启动；检查 cron 配置 |
| **消息发送失败进入 FAILED** | RocketMQ Broker 未启动 | 检查 RocketMQ 容器状态；确认 `spring.cloud.stream.rocketmq.binder.name-server` 配置正确 |
| **消息进入 DEAD_LETTER** | 超过最大重试次数（3 次） | 前端管理页面查看消息详情和错误信息；修复问题后手动重发 |
| **消费者未收到消息** | Topic 未创建或消费者未订阅 | RocketMQ 控制台检查 Topic 和 Consumer Group；确认消费者服务已启动 |
| **租户隔离失效** | 查询未过滤 tenantId | 检查 Controller 中是否包含 `.eq(SysMqMessage::getTenantId, tenantId)` |
| **重复投递** | 中继任务多次扫描同一消息 | 检查 `msgId` 唯一性约束；确认 `StreamBridge.send()` 幂等性 |
