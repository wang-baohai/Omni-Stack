# 消息可靠性发送（Transactional Outbox）

本文定义当前入队、投递、管理和消费边界。操作步骤与真实截图见 [可靠消息 Wiki](wiki/outbox-development.md)，断点与竞争测试见 [源码调试](wiki/source-debugging.md#outbox)。

## 1. 架构概览

`ReliableMessageRelay` 定义在 common-core；common-mqlog 提供本地 Outbox、XXL-JOB 中继和发送策略。Base 的管理聚合目前只覆盖 Base 与 CRM，不能据此断言其他服务的 Outbox 已显示在统一页面。

- 入队生成唯一 msgId、序列化 payload、校验正 tenantId，并写入本服务的 sys_mq_message。当前 broker_type 固定为 rocketmq。
- 四参数 send 使用 REQUIRED。业务与 Outbox 的原子性要求调用发生在同一数据源、同一有效本地事务内；Controller 的 @OperLog 后处理不能自动证明已结束的业务事务与日志入队原子提交。
- 当前模板把 topic 字段写为 bindingName；真正的 RocketMQ destination 由 Stream binding 配置决定。Base 的 operlog-out-0 绑定 operlog-topic。
- 可靠性以至少一次投递为目标。发送成功后、SENT 更新前崩溃仍可能重投；消费端必须幂等。退避、积压、停机和死信处理影响完成时间，不存在统一“最多10秒”的承诺。

## 2. 消息生命周期

| 状态 | 编码 | 允许行为 |
|---|---|---|
| PENDING | 0 | 到期后可领取；管理重发不接受此状态 |
| SENT | 1 | 发送策略返回成功；不等于消费业务已完成 |
| FAILED | 2 | 到期后直接重新领取；不先回写 PENDING |
| DEAD_LETTER | 3 | 自动重试停止；可修复后重发或忽略 |
| SKIPPED | 4 | 管理员确认不再投递；不能再次重发或忽略 |
| PROCESSING | 5 | 原子领取后的120秒租约态；租约过期可回收 |

每批最多100条，按创建时间扫描 PENDING/FAILED 到期记录和租约过期的 PROCESSING。领取、成功和失败写回均使用 CAS；未领取成功的执行器不能发送，终态更新未命中不能覆盖新状态。

失败次数在数据库原子递增。默认 max_retry=3 表示第3次失败进入死信：第1次失败设置20秒退避，第2次40秒，第3次清空 next_retry_time，不再等待80秒。未知 broker_type 同样进入此失败状态机。实际再次执行还需等调度轮询。

- `POST /api/base/mq-message/{msgId}/resend`：仅 FAILED/DEAD_LETTER；CAS 重置为 PENDING、retry_count=0 并清除退避/错误。要求 base:mqmessage:resend 且异步投递已启用。
- `POST /api/base/mq-message/{msgId}/skip`：仅 DEAD_LETTER → SKIPPED，要求 base:mqmessage:skip。
- 状态已变更、终态重复操作返回 HTTP 200 / 业务码400；没有功能权限返回 HTTP403 / 业务码403。界面确认框与“指令已提交”提示都不代表投递已完成。
- mqRelayHandler 使用 @XxlJob + @SystemJobMeta；注册器默认 cron 为 `0/10 * * * * ?`，就绪后注册并启动，注册失败每10秒重试，最多12次。各服务执行器 AppName 分离。

## 3. 租户隔离

send 必须显式传入正 Long tenantId，不能依赖 ThreadLocal 推导。外部管理接口读取受信任的 X-Tenant-Id；内部查询显式接受 tenantId，受内部调用认证保护。包括管理员在内的面向用户查询、重发与忽略都必须限定租户。

后台中继跨租户扫描是基础设施职责。SRM/采购/资产的 TenantLine 不得拦截 sys_mq_message，否则会漏投其他租户消息。

## 4. 约束与注意事项

- 唯一正式 DDL 为 [common-mq-0001-schema](../database/changelog/common/0001-mq-schema.yaml)。迁移历史与前置条件控制重复执行，不修改已执行 changeSet；旧 schema.sql 只供历史参考。
- MessageSender 按 broker_type 选择。新增策略还要扩展入队契约；现有 send 不能选择非 rocketmq broker。
- StreamBridge 不提供业务去重保证。操作日志按 eventId 唯一约束去重；OperLogConsumer 仅吞 DuplicateKeyException，其他异常重新抛出交给 Broker 重试。资产收货等 Inbox 使用各自领域契约。
- producer_trace_id 是入队时的 MDC traceId，可以为空；中继另建 Trace。发送头携带 omniMessageId 与可用的 omniProducerTraceId，不伪造同步父子 Span。
- 指标只用固定 destination/result/status 标签，不包含租户、用户名、业务键或 payload。管理端口、采样与导出配置见 [可观测性事实](observability.md)。
- Outbox 模式关闭异步投递时仍可入队；必须从 runtime 接口核对 deliveryEnabled，不能把未投递误判为服务丢失消息。

## 5. 源码与验证入口

| 符号 | 当前源码 |
|---|---|
| ReliableMessageTemplate.send | [ReliableMessageTemplate.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java) |
| relayPendingMessages / relayOne / handleSendFailure | [MqMessageRelayService.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java) |
| claim / CAS | [SysMqMessageMapper.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/mapper/SysMqMessageMapper.java) |
| RocketMqMessageSender.send | [RocketMqMessageSender.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/sender/RocketMqMessageSender.java) |
| MqRelayJobRegistrar | [MqRelayJobRegistrar.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqRelayJobRegistrar.java) |
| resend / skip | [MqMessageAggregationServiceImpl.java](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/MqMessageAggregationServiceImpl.java) |
| eventId | [OperLogConsumer.java](../omni-backend/omni-base/src/main/java/com/omni/base/consumer/OperLogConsumer.java), [OperLogServiceImpl.java](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/OperLogServiceImpl.java) |
| Trace / Metric | [MqLogMetrics.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/metrics/MqLogMetrics.java) |

[MqMessageRelayServiceTest](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceTest.java) · [CAS](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceCasTest.java) · [UnknownBroker](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceUnknownBrokerTest.java) · [AggregationCas](../omni-backend/omni-base/src/test/java/com/omni/base/service/impl/MqMessageAggregationCasTest.java)

真实教学生命周期由 [mq-teaching.flows.spec.ts](../omni-frontend/e2e-docs/flows/mq-teaching.flows.spec.ts) 与 [受控夹具](../scripts/fixtures/run-mq-teaching-fixture.ps1) 分阶段验证。合成 Outbox 数据不证明业务与入队事务的原子性；默认重试、正式管理动作、Broker 消费完成和去重必须分别核对。
