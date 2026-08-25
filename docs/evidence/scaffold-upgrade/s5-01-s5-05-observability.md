# S5-01～S5-05 可观测性验收证据

验收日期：2026-08-25
工作分支：`codex/scaffold-upgrade`

## 1. 实现范围

- 8 个可运行应用统一接入 Actuator、Prometheus registry、Micrometer Tracing 与 OTLP。
- W3C `traceparent` 作为同步传播标准，`X-Trace-Id` 映射为实际 traceId；Gateway 删除客户端伪造的兼容头。
- Feign 接入 Observation；Outbox 保存 `producer_trace_id`，Broker 消息携带消息 ID 和生产 trace，消费者日志同时记录生产与消费 trace。
- 提供 Outbox、Inbox、XXL-JOB、Workflow、采购审批重试和数据库迁移自定义指标。
- 增加独立 observability Compose profile、7 个 Dashboard、告警规则、SLO 模板和运维说明。
- CLI `dev up` 增加 `--observability`；关闭时 OTLP 导出关闭且采样率为 0。

组件使用固定版本：OpenTelemetry Collector 0.159.0、Prometheus 3.14.0、Pushgateway 1.11.3、Node Exporter 1.12.1、cAdvisor 0.60.5、Alertmanager 0.30.1、Tempo 2.10.5、Loki 3.7.4、Alloy 1.18.0、Grafana 13.1.0。

## 2. 自动化与配置验证

| 检查 | 结果 |
|---|---|
| `omni-common` reactor | 10 tests，0 failure；包含 OTLP 开启/关闭和 TraceId 延迟解析测试 |
| `omni-common-service` reactor | 26 tests，0 failure；验证 Feign Micrometer Observation classpath |
| `omni-common-mqlog` 定向测试 | 9 tests，0 failure；验证 relay tracer 延迟解析和消息关联头 |
| Gateway 定向测试 | 7 tests，0 failure；验证 Observation traceId 和安全响应头 |
| Dashboard JSON | 7/7 可解析并由 Grafana provision |
| Dashboard PromQL | 19 条全部查询成功，0 条语法或执行错误；验收时 16 条已有时间序列 |
| Prometheus 配置和规则 | `promtool check config`、`promtool check rules` 均通过 |
| Prometheus targets | 13/13 UP，包含 8 个应用、Collector、Prometheus、Pushgateway、Node Exporter、cAdvisor |

根依赖管理固定 `okio-jvm` 3.16.1，消除了 RocketMQ 传递的 3.4.0 与 OpenTelemetry OkHttp exporter 之间的 `NoSuchMethodError`。Collector 自身指标通过 8888 pull reader 暴露并被 Prometheus 抓取。

## 3. 同步 Trace 验收

在隔离项目 `omni-wp07-observe` 中，经真实 CAPTCHA 登录后调用：

`Gateway → Procurement /approval-route/workflow-options → Feign → Workflow`

结果：

- 业务 HTTP：200。
- 最终镜像响应 `X-Trace-Id`：`908e3b3f2298a2a8bc4c4d876d909230`。
- Tempo 按该 ID 精确查询：HTTP 200。
- Trace 服务：`omni-auth`、`omni-gateway`、`omni-procurement`、`omni-workflow`。
- Span 数：25。

这同时证明 Gateway 响应兼容头与实际 Observation trace 一致，Feign Observation 能跨进程传播。

## 4. 异步因果关联验收

在隔离采购库写入一条明确标识的合法验收夹具，由已启用的 XXL-JOB `mqRelayHandler` 真实执行：

`Procurement sys_mq_message → RocketMQ → Asset Consumer → ast_inbox_event / ast_asset`

结果：

- 夹具消息 ID：`092c2cda-9141-450c-abeb-625ebfcf9f17`。
- 生产 traceId：`7c427d1ae1041de009e35b964457172c`。
- 最终状态：Outbox `SENT`、Asset Inbox `PROCESSED`、新建资产 1 张。
- Loki 精确检索返回 2 条关联日志、2 个服务流：`omni-procurement`、`omni-asset`。
- 日志同时命中 relay 投递成功、Asset Inbox 收到事件和相同 `producerTraceId`。

夹具只存在于专用 WP07 数据卷，不属于种子数据或仓库脚本。

## 5. Grafana、日志和隔离性

- Grafana `/api/health`：数据库状态 `ok`。
- 自动预置 3 个数据源：Prometheus、Tempo、Loki。
- 自动预置 7 个 Dashboard：Platform Overview、Service RED、JVM and Pools、Feign Clients、MQ and Outbox、Workflow、Database Migrations。
- Loki 使用容器 allow-list 采集当前 Compose 项目；验收查询未混入并行的 `omni-g1` 项目日志。
- 管理端口全部绑定 127.0.0.1 或仅在 Compose 网络内访问，不经 Gateway 暴露。

## 6. 关闭模式与性能预算

使用同一 `omni-procurement` 镜像、同一隔离 MySQL/Redis/Nacos/Auth 依赖建立两个临时实例。二者均禁止注册自身、XXL-JOB、MQ relay 和消息消费者；区别仅为观测关闭与 OTLP 开启。业务夹具为物料品类查询，使用可信 Gateway 身份头，所有请求均为 HTTP 200。

本地验收预算：10% 采样相对关闭模式，吞吐下降不超过 10%，P95/P99 增幅不超过 15%，运行内存增幅不超过 10%，负载 CPU 增幅不超过 10%；启动时间不得出现超过 10% 的回退。该预算是本机回归门，不是生产 SLO。

| 指标 | 关闭 | 10% 采样 | 差异 | 结论 |
|---|---:|---:|---:|---|
| 启动至管理健康 UP | 15.70s | 13.53s | -13.8% | 通过 |
| 1000 请求、并发 20、错误数 | 0 | 0 | 0 | 通过 |
| 吞吐 | 260.01 req/s | 247.25 req/s | -4.9% | 通过 |
| P95 | 127.20ms | 123.70ms | -2.8% | 通过 |
| P99 | 157.33ms | 151.25ms | -3.9% | 通过 |
| 负载 CPU（40 并发持续负载） | 0.599 core | 0.614 core | +2.4% | 通过 |
| 基准后内存 | 579.3MiB | 618.6MiB | +6.8% | 通过 |

额外 100% 采样小样本（80 请求、并发 4）为 0 错误，P95 54.07ms；P99 由关闭模式的 63.68ms 增至 166.22ms。因此 100% 采样只用于本地链路验收，不作为生产默认值。

观测关闭实例单独验证：业务 HTTP 200，兼容 traceId 为 `4e8dd9ad2bb37477bf6c46fac6b5b53c`，等待导出窗口后 Tempo 精确查询返回 404，证明关闭配置不导出 Span 且不影响业务启动和请求。

曾有一轮使用错误业务路径导致全部非 200，另有一轮因禁用服务发现导致数据权限 Feign 等待超时；两轮均已废弃，不进入上述结论。最终基准允许发现依赖但设置 `register-enabled=false`，不会进入 Gateway 路由。

## 7. 限制与发布要求

- 本证据验证本地模板连通性和回归预算，不代替目标环境容量、存储成本、保留期和故障演练。
- 生产采样率、告警阈值、SLO、值班接收人、TLS、认证和外部 Secret 必须由部署方按容量重新校准。
- 生产不得公开 Prometheus、Grafana、Tempo、Loki、Collector、Pushgateway、Exporter 或管理端口。
- WP-09 需要把本页事实同步到四语言运维文档并更新正式截图。

## 8. 最终质量门

- JDK 25 全量 Maven `clean install`：20/20 reactor 项目 SUCCESS，所有已执行测试 0 failure；CRM 4 个数据库条件测试按既有设计跳过。
- Omni CLI：TypeScript build 通过，40/40 tests 通过。
- 前端：TypeScript + Vite production build 通过（2448 modules），ESLint 0 error、0 warning。
- 最终迁移器与 8 个应用镜像全部构建成功；隔离项目 8/8 应用 healthy。
- 最终应用容器中 `OTEL_SDK_ENABLED` 残留数为 0；该变量对应的 Spring Boot 4 无效配置已从应用、Compose、模板、CLI 和文档统一删除。
