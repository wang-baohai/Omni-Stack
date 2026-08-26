# Omni-Stack Observability Operations and Maintenance

This is the operational source for WP-07. It covers metrics, distributed tracing, structured logs, the optional local stack, alerts, and the SLO template. Observability is disabled by default and is never a prerequisite for application startup.

## 1. Data flow and boundaries

| Signal | Application output | Local backend | Query entry |
|---|---|---|---|
| Metrics | `/actuator/prometheus`; migrator exit push | Prometheus / Pushgateway | Grafana / Prometheus |
| Traces | OTLP HTTP and W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| Logs | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| Alerts | Prometheus rules | Alertmanager | Alertmanager / external receiver |

`X-Trace-Id` is a compatibility response header. Cross-service propagation uses W3C `traceparent`. Management endpoints do not pass through Gateway. Compose keeps application Actuator endpoints on the internal network; production should use a separate management port, network policy, and authentication.

## 2. Start and stop

Create a complete `.env` from `.env.example`, then run from the repository root:

```powershell
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability
npm --prefix tools/omni-cli run dev -- dev status
```

The flag enables OTLP export, ECS JSON logs, local 100% sampling, and migrator result pushes. Without it, exports and migration pushes are disabled, sampling is zero, and applications continue normally.

| Component | Local address | Purpose |
|---|---|---|
| Grafana | `http://127.0.0.1:3001` | Dashboards, logs, traces |
| Prometheus / Pushgateway | `:9090` / `:9091` | Metrics and short-lived migration results |
| Node Exporter / cAdvisor | `:9100` / `:8088` | Host and container metrics |
| Alertmanager | `:9093` | Alert state |
| Tempo / Loki / Alloy | `:3200` / `:3100` / `:12345` | Trace, log storage, collection |
| OTLP | `127.0.0.1:4317/4318` | gRPC/HTTP receiver |

`dev down` preserves volumes. Delete volumes only with `--volumes --confirm-delete-volumes` after confirming that database and telemetry data are no longer needed.

## 3. Dashboards and metric contract

Grafana provisions seven read-only dashboards: Platform Overview, Service RED, JVM and Pools, Feign Clients, MQ and Outbox, Workflow, and Database Migrations. Definitions live under `observability/grafana/`.

Alloy collects only containers with the same `COMPOSE_PROJECT_NAME`. Metric labels must be bounded: service, environment, instance, HTTP method/route template/status, exception class, fixed MQ destination/result, and closed operation/status enums. Never label by tenant, user, business key, raw URL, payload, SQL, table name, or connection address.

Custom metrics cover Outbox counts/age/results, XXL-JOB registration/execution, Workflow starts/approvals/backlog/duration, Procurement workflow retries, Inbox outcomes, and database migration result/duration/version. Outbox delivery correlates producer, relay, and consumer traces by `msgId` without logging payload, tenant, or business keys.

## 4. Alerts and calibration

`observability/prometheus/alerts.yml` includes service availability, 5xx, latency, pool, Outbox, Workflow, XXL-JOB, Inbox, disk, restart, memory, and JVM deadlock examples. Its percentages and time thresholds are local examples, not production promises.

Before production: collect at least seven days of representative traffic, calibrate per service, configure a real receiver and escalation policy through Secrets, rehearse failure cases, and record the owner and review date.

## 5. SLO template

Every production service defines a user journey, SLI, target window, P95/P99 targets, calculated error budget, fast/slow multi-window burn alerts, reachable owner, and calibration date. Example targets must not be copied without measurement.

## 6. Security and production readiness

- Replace local Grafana credentials and enable SSO/TLS.
- Docker socket access is privileged; use a restricted production log agent.
- Redesign production retention, capacity, encryption, and backup.
- Never expose telemetry management endpoints directly to the public Internet.
- Logs must not contain tokens, passwords, cookies, authorization headers, request/message bodies, or Secrets.

## 7. Verification and troubleshooting

```powershell
docker compose --profile observability config --quiet
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check rules /etc/prometheus/alerts.yml
```

After startup, verify expected Prometheus targets, then check application metrics, Collector logs, Tempo/Loki readiness, and Alloy targets in that order. Compare disabled/enabled startup time, CPU, memory, throughput, and latency with the same fixture; store results in `docs/evidence/scaffold-upgrade/`.
