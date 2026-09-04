# Omni-Stack Observability Operations and Maintenance

This document is the operational source of truth for WP-07, covering application metrics, distributed tracing, structured logs, the local observability stack, alerts, and the SLO template. Observability is disabled by default and is not a prerequisite for application startup.

## 1. Data Flow and Boundaries

| Signal | Application output | Local backend | Query entry |
|---|---|---|---|
| Metrics | `/actuator/prometheus`; migrator exit push | Prometheus / Pushgateway | Grafana / Prometheus |
| Traces | OTLP HTTP, W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| Logs | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| Alerts | Prometheus rules | Alertmanager | Alertmanager / external receiver |

`X-Trace-Id` is only a compatibility response header for legacy clients and log retrieval. When tracing is enabled it equals the current Micrometer/OTel traceId; cross-service propagation follows the W3C `traceparent`. The Gateway still overrides client-forged identity headers but does not break the standard Trace context.

Management endpoints do not go through the Gateway. In Compose, a business service's Actuator shares the in-container `8080` with the application, and only Prometheus scrapes it via the internal Compose network; except for the Gateway business port, all service host ports bind to `127.0.0.1`. Production should use a separate management port, network policy, and Actuator authentication.

## 2. Start and Stop

First create a complete `.env` from `.env.example`, then run from the repository root:

```powershell
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability
npm --prefix tools/omni-cli run dev -- dev status
```

The CLI sets `OTLP_EXPORT_ENABLED=true`, ECS JSON logs, and local 100% sampling for this process, and makes the one-shot migrator push results to Pushgateway on exit. Without `--observability`, both OTLP and migration metric pushes are disabled, the sampling rate is 0, and applications still start normally without exporting spans.

Local entries:

| Component | Address | Default purpose |
|---|---|---|
| Grafana | `http://127.0.0.1:3001` | Dashboards, logs and traces |
| Prometheus | `http://127.0.0.1:9090` | Targets, PromQL, rules |
| Pushgateway | `http://127.0.0.1:9091` | Short-lived migration metrics |
| Node Exporter | `http://127.0.0.1:9100` | Local node and filesystem metrics |
| cAdvisor | `http://127.0.0.1:8088` | Container resource and lifecycle metrics |
| Alertmanager | `http://127.0.0.1:9093` | Alert state |
| Tempo | `http://127.0.0.1:3200` | Trace API |
| Loki | `http://127.0.0.1:3100` | Log API |
| Alloy | `http://127.0.0.1:12345` | Log collection state |
| OTLP | `127.0.0.1:4317/4318` | gRPC/HTTP receiver |

Stopping preserves the observability data volumes by default:

```powershell
npm --prefix tools/omni-cli run dev -- dev down
```

Only when you confirm that local metrics, traces, logs and database data are no longer needed may you explicitly delete all named volumes:

```powershell
npm --prefix tools/omni-cli run dev -- dev down --volumes --confirm-delete-volumes
```

## 3. Dashboards and Metric Contract

Grafana auto-loads 7 read-only dashboards: Platform Overview, Service RED, JVM and Pools, Feign Clients, MQ and Outbox, Workflow, Database Migrations. The JSON lives in `observability/grafana/dashboards/`, and the datasource and dashboard providers live in `observability/grafana/provisioning/`.

Alloy only collects container logs whose `COMPOSE_PROJECT_NAME` label matches its own. When using `docker compose -p <name>` or setting `COMPOSE_PROJECT_NAME`, this filter value changes accordingly and will not scan other Compose projects on the host.

The only allowed metric labels are: `service.name`, `environment`, `instance`, HTTP method/route template/status, exception class, MQ destination/result, and the closed operation/status enums in code. Database migration info may additionally use the repository-controlled schema version. Never put tenantId, userId, username, businessKey, raw URL, request body, message body, dynamic SQL, table name or connection address into labels. A new label must first prove its value set is fixed and assess its cardinality upper bound.

Current custom Outbox metrics:

- `omni_mq_outbox_messages{status}`: pending/sent/failed/dead_letter counts.
- `omni_mq_outbox_oldest_age_seconds`: age of the oldest pending/failed message.
- `omni_mq_outbox_operations_total{destination,result}`: enqueued/sent/retry/dead_letter results.

Other custom metrics:

- `omni_job_registrations_total{result}`, `omni_job_executions_total{result}`: XXL-JOB registration and user-task execution results.
- `omni_workflow_start_operations_total{result}`, `omni_workflow_approval_operations_total{result}`: process start and approval results.
- `omni_workflow_approval_backlog`, `omni_workflow_approval_duration_seconds`, `omni_workflow_process_duration_seconds`: approval backlog, single-approval handling and end-to-end process duration.
- `omni_procurement_workflow_start_retries_total{result}`: requisition Workflow start retry results.
- `omni_inbox_operations_total{destination,result}`: SRM, procurement and asset Inbox success or triggered Broker retry.
- `omni_db_migration_operations_total{result}`, `omni_db_migration_duration_seconds`, `omni_db_schema_version_info{version}`: results, duration and controlled manifest version pushed by the one-shot migrator on exit.

On Outbox creation the real traceId is saved as `producer_trace_id`; on send it is passed to consumers via the `omniProducerTraceId` and `omniMessageId` message headers; delivery still works when history lacks a producer traceId. The relay creates a new relay span per delivery, and logs only `msgId`, `producerTraceId`, `relayTraceId` and the fixed destination/result; the Asset Consumer extracts the correlation fields from the headers and logs `msgId`, `producerTraceId` and its own `consumerTraceId`. Neither side logs payload, tenant or business key. Via `msgId` you can jump from producer logs to relay and consumer logs, then query the corresponding Traces separately.

## 4. Alerts and Threshold Calibration

`observability/prometheus/alerts.yml` contains rules for service unavailability, 5xx, P95/P99, connection pools, Outbox backlog/dead-letter, Workflow/XXL-JOB/Inbox failures, disk, container restarts, memory and JVM deadlocks. The 5%, 1s, 2s, 85%, 5min etc. in the file are all local example thresholds, not production promises.

Before a production release you must:

1. Compute baselines and daily/weekly cycles from at least 7 days of representative traffic.
2. Set different latency and error budgets per service, avoiding one global threshold masking differences.
3. Configure a real Alertmanager receiver, on-call and escalation policy; credentials may only come from Secret management, never committed to the repository.
4. Rehearse service down, 5xx, Outbox dead-letter and receiver failure.
5. Record the threshold owner, calibration date and next review date.

## 5. SLO Template

Each production service copies the following template and confirms it jointly between business and platform:

| Field | Example | Required notes |
|---|---|---|
| Service / user journey | `omni-procurement / submit requisition` | Named by user outcome |
| SLI | Successful non-5xx requests / valid requests | Explicitly exclude health checks and client cancellations |
| Target | 99.9% over 30 days | Do not copy the example directly |
| Latency target | P95 < 800ms, P99 < 2s | Measured by route template |
| Error budget | 43m12s over 30 days | Calculated automatically from the target |
| Fast-burn alert | 1h window, 14.4x | Requires both short and long windows |
| Slow-burn alert | 6h window, 6x | Bound to on-call response levels |
| Owner | Team and on-call schedule | Must be reachable |
| Calibration date | YYYY-MM-DD | Review quarterly or after major releases |

## 6. Security and Production Readiness

- Local Grafana default credentials are only for out-of-the-box verification; shared or production environments must override them in `.env`/Secret and enable SSO/TLS.
- Alloy read-only mounts the Docker socket to collect container stdout. The Docker socket is equivalent to a high-privilege control plane; production should use a restricted log agent, least privilege and a separate collection node.
- Local retention defaults to Prometheus 7 days, Tempo 24 hours, Loki 7 days. Production capacity, backup, encryption and retention policy must be redesigned.
- Management ports, OTLP, Grafana, Prometheus, Pushgateway, Exporters, Loki, Tempo and Alertmanager must not be exposed directly to the public Internet. Production Pushgateway must enable TLS/auth and restrict push sources.
- ECS logs must not record tokens, passwords, cookies, Authorization, request bodies, message bodies or Secrets in raw exceptions.

## 7. Verification and Troubleshooting

Configuration-level validation:

```powershell
docker compose --profile observability config --quiet
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check rules /etc/prometheus/alerts.yml
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml
```

After running, check that the started applications in Prometheus `/targets` are UP; services not included in the current preset showing DOWN is expected. When Grafana has no data, check in order the application `/actuator/prometheus`, the Prometheus target, the OTel Collector logs, Tempo/Loki readiness and Alloy targets.

Performance acceptance uses the same fixture to separately measure startup time, CPU, memory, throughput and P95/P99 with observability disabled and enabled. Results are written to `docs/evidence/scaffold-upgrade/`; local 100% sampling is only for acceptance, and the production sampling rate is calibrated by capacity and SLO.
