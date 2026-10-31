# 6. Extension and verification: integrate without copying the state machine

Before sending from a new feature, define which business fact occurred and what the recipient must do. Then define payload, tenant, idempotency key, and recovery. Messages carry facts; they do not replace authorization or business-state validation.

## Integration order

1. Reuse common-mqlog and ReliableMessageRelay rather than copying scanners and retry tables.
2. Enqueue within an effective business transaction on the same data source, passing tenantId explicitly; verify rollback leaves no pending message.
3. Configure binding and destination in the service's Spring Cloud Stream settings and match the sender strategy to the actual Broker.
4. Use a unique constraint or domain Inbox for business idempotency. Acknowledge according to framework semantics after completion; preserve retryable errors for real failures.
5. Verify scheduling, sending, duplicate consumption, tenant rejection, dead letters, and formal recovery separately.

## Boundaries that must remain

External management queries enforce trusted tenant and functional permissions. Internal queries also need explicit tenantId and internal-call authentication. The relay intentionally scans all tenants; applying user-facing tenant interception to that scan can omit messages.

Outbox does not provide atomic commits across service databases. Cross-service coordination needs domain compensation and idempotency. A consumer deduplication key must identify the same business fact, rather than a new key for every retry. msgKey, tracing headers, and a true sending return are not substitutes.

## What proves the implementation?

| Scenario | Required observation |
|---|---|
| Local business rollback | Neither business change nor Outbox commit remains |
| Unreachable Broker | Actual failure enters backoff/dead letter, never false SENT |
| Process interruption after sending | Redelivery is allowed; business processing does not happen twice |
| Two execution flows claim one record | Only the successful claimant proceeds; stale results do not overwrite newer state |
| Another tenant reads or operates | Denied without leaking message contents |
| Formal resend/skip | Permission, state, delivery switch, and concurrency preconditions hold |

Fault checks must target registered test objects and controlled fixtures. Do not stop the whole stack, clear volumes, or change business states to fabricate success. Distinguish test data, real business, protocol fixtures, and actual delivery.

Management aggregation or Broker strategies can be extended later with a concrete requirement, contract, security boundary, and tests. This chapter explains current behavior and extension methods; it adds none of those product features.
