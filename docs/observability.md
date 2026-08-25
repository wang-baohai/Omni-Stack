# Omni-Stack 可观测性运行与维护

本文是 WP-07 的运行事实源，说明应用指标、分布式追踪、结构化日志、本地观测栈、告警和 SLO 模板。观测能力默认关闭，不是应用启动前置条件。

## 1. 数据流与边界

| 信号 | 应用出口 | 本地后端 | 查询入口 |
|---|---|---|---|
| 指标 | `/actuator/prometheus`；迁移器结束推送 | Prometheus / Pushgateway | Grafana / Prometheus |
| Trace | OTLP HTTP，W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| 日志 | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| 告警 | Prometheus rules | Alertmanager | Alertmanager / 外部 receiver |

`X-Trace-Id` 仅作为旧客户端和日志检索的兼容响应头。开启追踪时它等于当前 Micrometer/OTel traceId；跨服务传播以 W3C `traceparent` 为准。网关仍会覆盖客户端伪造的身份头，但不会破坏标准 Trace 上下文。

管理端点不经过 Gateway。Compose 中业务服务的 Actuator 与应用共用容器内 `8080`，仅 Prometheus 通过内部 Compose 网络抓取；除 Gateway 业务端口外，服务宿主端口均绑定 `127.0.0.1`。生产环境建议使用独立管理端口、网络策略和 Actuator 认证。

## 2. 启动与停止

先按 `.env.example` 创建完整 `.env`，再从仓库根目录执行：

~~~powershell
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability
npm --prefix tools/omni-cli run dev -- dev status
~~~

CLI 会为本次进程设置 `OTLP_EXPORT_ENABLED=true`、ECS JSON 日志、本地 100% 采样，并让一次性迁移器在退出时向 Pushgateway 推送结果。没有 `--observability` 时 OTLP 和迁移指标推送均关闭，采样率为 0，应用仍可正常启动且不导出 Span。

本地入口：

| 组件 | 地址 | 默认用途 |
|---|---|---|
| Grafana | `http://127.0.0.1:3001` | Dashboard、日志和 Trace |
| Prometheus | `http://127.0.0.1:9090` | Targets、PromQL、规则 |
| Pushgateway | `http://127.0.0.1:9091` | 短生命周期迁移指标 |
| Node Exporter | `http://127.0.0.1:9100` | 本地节点与文件系统指标 |
| cAdvisor | `http://127.0.0.1:8088` | 容器资源与生命周期指标 |
| Alertmanager | `http://127.0.0.1:9093` | 告警状态 |
| Tempo | `http://127.0.0.1:3200` | Trace API |
| Loki | `http://127.0.0.1:3100` | 日志 API |
| Alloy | `http://127.0.0.1:12345` | 日志采集状态 |
| OTLP | `127.0.0.1:4317/4318` | gRPC/HTTP 接收 |

停止默认保留观测数据卷：

~~~powershell
npm --prefix tools/omni-cli run dev -- dev down
~~~

只有确认不再需要本地指标、Trace、日志和数据库数据时，才可显式删除所有命名卷：

~~~powershell
npm --prefix tools/omni-cli run dev -- dev down --volumes --confirm-delete-volumes
~~~

## 3. Dashboard 与指标契约

Grafana 自动加载 7 个只读 Dashboard：Platform Overview、Service RED、JVM and Pools、Feign Clients、MQ and Outbox、Workflow、Database Migrations。JSON 位于 `observability/grafana/dashboards/`，数据源和 Dashboard provider 位于 `observability/grafana/provisioning/`。

Alloy 只采集与自己相同 `COMPOSE_PROJECT_NAME` 标签的容器日志。使用 `docker compose -p <name>` 或设置 `COMPOSE_PROJECT_NAME` 时该过滤值会同步变化，不会扫描主机上其他 Compose 项目。

允许的指标标签只有：`service.name`、`environment`、`instance`、HTTP method/route template/status、exception class、MQ destination/result，以及代码中封闭枚举的 operation/status。数据库迁移信息可额外使用仓库受控的 schema version。禁止把 tenantId、userId、username、businessKey、原始 URL、请求体、消息体、动态 SQL、表名或连接地址放入标签。新增标签必须先证明取值集合固定，并评估基数上界。

当前自定义 Outbox 指标：

- `omni_mq_outbox_messages{status}`：pending/sent/failed/dead_letter 数量。
- `omni_mq_outbox_oldest_age_seconds`：最旧 pending/failed 消息年龄。
- `omni_mq_outbox_operations_total{destination,result}`：enqueued/sent/retry/dead_letter 结果。

其余自定义指标：

- `omni_job_registrations_total{result}`、`omni_job_executions_total{result}`：XXL-JOB 注册和用户任务执行结果。
- `omni_workflow_start_operations_total{result}`、`omni_workflow_approval_operations_total{result}`：流程启动与审批结果。
- `omni_workflow_approval_backlog`、`omni_workflow_approval_duration_seconds`、`omni_workflow_process_duration_seconds`：待办积压、单次审批处理和流程端到端耗时。
- `omni_procurement_workflow_start_retries_total{result}`：请购 Workflow 启动重试结果。
- `omni_inbox_operations_total{destination,result}`：SRM、采购和资产 Inbox 成功或触发 Broker 重试。
- `omni_db_migration_operations_total{result}`、`omni_db_migration_duration_seconds`、`omni_db_schema_version_info{version}`：一次性迁移器退出时推送的结果、耗时和受控清单版本。

Outbox 创建时把真实 traceId 保存为 `producer_trace_id`，发送时通过 `omniProducerTraceId` 和 `omniMessageId` 消息头传给消费者；历史记录缺少生产 traceId 时仍可正常投递。中继为每次投递建立新的 relay span，日志只输出 `msgId`、`producerTraceId`、`relayTraceId` 与固定 destination/result；Asset Consumer 从消息头取出关联字段，并记录 `msgId`、`producerTraceId` 和自己的 `consumerTraceId`。任何一侧都不输出 payload、租户或业务键。通过 `msgId` 可从生产日志跳转到中继和消费日志，再分别查询对应 Trace。

## 4. 告警与阈值校准

`observability/prometheus/alerts.yml` 包含服务不可用、5xx、P95/P99、连接池、Outbox 积压/死信、Workflow/XXL-JOB/Inbox 失败、磁盘、容器重启、内存和 JVM 死锁规则。文件中的 5%、1 秒、2 秒、85%、5 分钟等均是本地示例阈值，不是生产承诺。

生产发布前必须：

1. 用至少 7 天代表性流量计算基线和日/周周期。
2. 按服务设置不同延迟和错误预算，避免一个全局阈值掩盖差异。
3. 配置真实 Alertmanager receiver、值班人和升级策略；凭据只能来自 Secret 管理，不得提交仓库。
4. 演练 service down、5xx、Outbox 死信和 receiver 失败。
5. 记录阈值负责人、校准日期和下一次复核日期。

## 5. SLO 模板

每个正式服务复制以下模板并由业务与平台共同确认：

| 字段 | 示例 | 必填说明 |
|---|---|---|
| 服务/用户旅程 | `omni-procurement / 提交请购` | 以用户结果命名 |
| SLI | 成功非 5xx 请求 / 有效请求 | 明确排除健康检查和客户端取消 |
| 目标 | 30 天 99.9% | 不得直接照抄示例 |
| 延迟目标 | P95 < 800ms，P99 < 2s | 按 route template 统计 |
| 错误预算 | 30 天 43m12s | 由目标自动计算 |
| 快速燃烧告警 | 1h 窗口，14.4x | 同时要求短/长窗口 |
| 慢速燃烧告警 | 6h 窗口，6x | 与值班响应级别绑定 |
| 负责人 | 团队与值班表 | 必须可联系 |
| 校准日期 | YYYY-MM-DD | 每季度或大版本后复核 |

## 6. 安全与生产化

- 本地 Grafana 默认凭据仅为开箱验证；共享或生产环境必须在 `.env`/Secret 中覆盖，并启用 SSO/TLS。
- Alloy 为采集容器 stdout 只读挂载 Docker socket。Docker socket 等价于高权限控制面；生产应改用受限日志代理、最小权限和独立采集节点。
- 本地保留期默认 Prometheus 7 天、Tempo 24 小时、Loki 7 天。生产容量、备份、加密和保留策略必须重新设计。
- 管理端口、OTLP、Grafana、Prometheus、Pushgateway、Exporter、Loki、Tempo 和 Alertmanager 不得直接暴露公网。生产 Pushgateway 必须启用 TLS/认证并限制推送来源。
- ECS 日志不得记录令牌、密码、Cookie、Authorization、请求体、消息体或原始异常中的 Secret。

## 7. 验证与排障

配置级校验：

~~~powershell
docker compose --profile observability config --quiet
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check rules /etc/prometheus/alerts.yml
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml
~~~

运行后检查 Prometheus `/targets` 的已启动应用为 UP；未被当前预设包含的服务显示 DOWN 是预期现象。Grafana 无数据时依次检查应用 `/actuator/prometheus`、Prometheus target、OTel Collector 日志、Tempo/Loki readiness 和 Alloy targets。

性能验收使用相同 fixture 分别测量观测关闭与开启后的启动时间、CPU、内存、吞吐和 P95/P99。结果写入 `docs/evidence/scaffold-upgrade/`；本地 100% 采样只用于验收，生产采样率按容量和 SLO 校准。
