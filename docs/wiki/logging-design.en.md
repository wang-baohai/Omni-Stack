# 2. Design choices: applications record, tools collect

An application can send logs directly to a remote endpoint, or write to standard output for a collector to read. This project uses the second approach. Business methods do not each need a Loki client, and Docker metadata allows collection targets to be discovered again after container replacement.

## Four roles with separate responsibilities

| Role | Tool in this project | What it does not do |
|---|---|---|
| Produce events | Spring Boot logging framework | Centralized storage |
| Discover and collect | Alloy | Business audit or access decisions |
| Store and retrieve | Loki | Replace the business database |
| Query and correlate | Grafana | Create data that was never collected |

Loki uses labels to narrow log streams before filtering their content. Avoid usernames, order numbers, or a unique request ID as labels: more distinct values increase management cost. Necessary correlation IDs can remain in log fields and be found by content queries.

## How structured output is configured

This line comes from the formal application Compose environment:

<!-- omni:source path="compose.apps.yaml" -->
```yaml
      LOGGING_STRUCTURED_FORMAT_CONSOLE: ${STRUCTURED_LOGGING_FORMAT:-ecs}
```

Spring Boot reads the corresponding logging property and defaults to ECS (Elastic Common Schema) JSON. ECS is a field convention, not another message queue or an automatic redaction switch. Other launch methods use their own effective configuration.

## Why not write everything into one database?

Runtime logs have different write volumes, retention needs, and query patterns from business tables. Operation audit also requires explicit tenant and access boundaries. Mixing them into one table obscures performance and ownership. This project maintains runtime logs separately from operation records and security events in the database.

The approach still has costs: collection, storage, retention, and query access require maintenance. Collector failures can cause delays or gaps. Production deployments must assess access control, capacity, and backups. The local template does not prove those production requirements are already met.

Next, follow [the actual collection configuration](logging-pipeline.en.md).
