# Operations, Observability, Backup, Recovery, and Upgrade

This guide supports local template validation and production planning. Reconfigure the template for capacity, compliance, and ownership; never expose local Compose ports unchanged.

## 1. Startup Order

```text
MySQL / Redis / RocketMQ → Nacos / XXL-JOB → Liquibase migrator
→ Auth / Base / Workflow / Domain Services → Gateway / Frontend
```

After `docker compose --profile full up -d`, rely on health checks, not container start state. The migrator must exit successfully before serving traffic.

## 2. Observability Profile

```bash
docker compose --profile full --profile observability up -d
```

The stack includes OpenTelemetry Collector, Prometheus, Alertmanager, Tempo, Loki, Alloy, Grafana, Pushgateway, Node Exporter, and cAdvisor. Dashboards cover platform overview, RED, JVM, Feign, MQ, Workflow, and migrations.

Synchronous requests use W3C `traceparent`; `X-Trace-Id` reflects the actual trace. Messages preserve producer trace and consumer logs record both sides. Production sampling, retention, alert thresholds, and recipients require capacity-specific calibration. Disabling observability must not affect business startup.

## 3. Health and Alerts

Monitor application health, Prometheus targets, Gateway/Feign error rate and latency, pools/JVM/GC, Outbox/Inbox backlog, dead letters, XXL-JOB failures, Workflow retries, and migration failures.

## 4. Backup

Back up MySQL, persistent Nacos configuration, required external Secret inventory, custom Grafana/alert configuration, deployment version, image digests, `database/seed/manifest.yaml`, and `omni-scaffold.lock`. Redis, MQ, Tempo, and Loki backup depends on explicit RPO/RTO.

## 5. Recovery Exercise

Restore into an isolated environment, disconnect production downstream systems, run migrations, start services, execute health/login/permission/business/async checks, reconcile key state and message records, and record actual recovery time. A backup without successful restore evidence is not considered usable.

## 6. Upgrade

Use expand → migrate/backfill → contract:

1. Read release and preset differences.
2. Back up and prove recovery.
3. Test upgrade against a production-like copy.
4. Deploy backward-compatible Schema.
5. Deploy applications compatible with old and new structures and finish backfill.
6. Observe metrics and error budget.
7. Contract only after old consumers and versions are gone.

Liquibase changeSets are forward-only. Recovery uses backup restoration or a compensating changeSet, never edits an executed changeSet.

## 7. Security

Bind management endpoints internally or to localhost. Use TLS, reverse proxy/WAF, least privilege, and external Secret management. Do not place secrets in Git, image layers, screenshots, command history, or logs. See [Docker Deployment](../docker-deployment.en.md) and [Observability](../observability.md).

