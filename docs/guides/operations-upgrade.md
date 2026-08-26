# 运维、可观测性、备份、恢复与升级指南

本指南面向本地模板验证和生产落地评审。生产环境需要根据容量、合规和组织职责重新配置，不应原样暴露本地 Compose 端口。

## 1. 启动与依赖顺序

```text
MySQL / Redis / RocketMQ
→ Nacos / XXL-JOB
→ Liquibase migrator
→ Auth / Base / Workflow / 领域服务
→ Gateway / Frontend
```

使用 `docker compose --profile full up -d` 后以健康检查为准，不以容器“已启动”代替应用可用。迁移器必须成功退出，应用才可接受流量。

## 2. 可观测性 profile

```bash
docker compose --profile full --profile observability up -d
```

观测栈包含 OpenTelemetry Collector、Prometheus、Alertmanager、Tempo、Loki、Alloy、Grafana、Pushgateway、Node Exporter 和 cAdvisor。默认预置平台概览、服务 RED、JVM、Feign、MQ、Workflow 和数据库迁移 Dashboard。

同步请求使用 W3C `traceparent`，响应 `X-Trace-Id` 映射实际 traceId。异步消息保存生产 Trace，并在消费者日志中同时记录生产和消费 Trace。

生产采样率、保留期、告警阈值和接收人必须按容量校准。关闭 observability 时业务仍应正常启动，且不导出 Span。

## 3. 健康与告警

重点检查：

- Compose 服务和应用 Actuator 健康。
- Prometheus targets 全部 UP。
- Gateway、Feign 和业务接口错误率、P95/P99。
- MySQL 连接池、JVM、线程和 GC。
- Outbox/Inbox 堆积、死信和重试。
- XXL-JOB 执行失败。
- Workflow 启动失败和审批重试。
- Liquibase 迁移失败或耗时异常。

## 4. 备份

备份至少包含：

- MySQL 全库或按恢复单元的逻辑/物理备份。
- Nacos 持久化配置和服务所需外部 Secret 清单。
- Grafana 自定义配置、告警接收配置和必要的对象存储数据。
- 部署版本、镜像摘要、`database/seed/manifest.yaml` 和 `omni-scaffold.lock`。

Redis、MQ、Tempo、Loki 是否备份取决于恢复目标和保留策略，必须形成书面 RPO/RTO。

## 5. 恢复演练

1. 建立隔离恢复环境。
2. 恢复数据库和配置，不连接生产下游。
3. 运行 migrator，确认 changeSet 状态一致。
4. 启动应用并执行健康、登录、权限、核心业务和异步消息检查。
5. 核对数据总量、关键业务状态、Outbox/Inbox 和 Workflow 实例。
6. 记录实际恢复时间和缺口。

没有成功恢复证据的备份不能算可用。

## 6. 升级

升级遵循 expand → migrate/backfill → contract：

1. 阅读版本升级说明和预设差异。
2. 备份并验证可恢复。
3. 在生产样本副本执行 upgrade 测试。
4. 先发布向后兼容 Schema。
5. 发布兼容新旧结构的应用并完成回填。
6. 观察指标和错误预算。
7. 只在旧消费者和旧版本全部退出后收缩契约。

Liquibase changeSet forward-only。数据库回滚优先恢复备份或发布补偿 changeSet，不修改已经执行的历史 changeSet。

## 7. 安全

管理端口绑定内网或 localhost。外部入口通过 TLS、WAF/反向代理和最小权限访问。Secret 不进入 Git、镜像层、截图、命令行历史或日志。详细 Compose 配置见 [Docker 部署指南](../docker-deployment.md)，指标与 Trace 见 [可观测性文档](../observability.md)。

