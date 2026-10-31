# 1. Centralized log collection: decide what you are looking for

A user says, “Saving failed a moment ago.” You need the time, the service, and the point where processing failed. Logs record events during processing. In a microservices system those events are spread across containers; centralized collection gives you one place to search instead of opening every container.

## Three records answer three questions

| Record | Main question | Where to look in this project |
|---|---|---|
| Runtime logs | Why did a service fail, and how far did a request get? | Docker logs; Grafana querying Loki when collection is enabled |
| Operation logs | Who performed a business action, and what was its outcome? | My Workspace → Monitoring → Operation Logs |
| Security audit | Who signed in, was rejected, or changed security settings? | Audit Logs, Login Records, and Auth Records in System Management |

For a failed supplier update, runtime logs help locate the exception, operation logs identify the action, and login records explain how the identity entered the system. These records do not replace one another. An empty operation log does not prove that no request occurred.

## A few useful terms

**Structured logs** separate time, level, service, and message into searchable fields. A **collector** moves records, a **store** keeps and retrieves them, and a **query interface** displays results. A **traceId** is a correlation clue, not a login credential or proof that a complete trace was stored.

The runtime path is application output → Docker logs → Alloy → Loki → Grafana. The application Compose configuration defaults to ECS JSON. When optional Alloy, Loki, and Grafana services are not running, container logs can still exist, but centralized search is unavailable.

## What you will learn

Read [tool choices](logging-design.en.md), then [collection and request correlation](logging-pipeline.en.md), [operation records and security](logging-audit.en.md), [page and troubleshooting practice](logging-practice.en.md), and [extension and maintenance](logging-extension.en.md). Necessary configuration and source excerpts are included in the text.

Remember: having a log does not prove business success, and finding no log does not prove that an action never ran. Choose the right record first, then check time, service, and filters.
