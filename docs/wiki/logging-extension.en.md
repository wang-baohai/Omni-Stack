# 6. Extension and maintenance: leave useful clues

When adding logging to a module, first decide which event needs explaining, who will inspect it, and how long it must remain searchable. Those answers determine whether it belongs in runtime logs, business operation records, or security audit. Printing complete objects everywhere is not a starting point.

## Integrate a feature in order

1. Record necessary success, failure, and timing events with parameterized logging and stable meanings.
2. Reuse traceId; carry necessary correlation fields explicitly across background tasks and messages, and clean up context afterwards.
3. For a Servlet module needing operation records, use existing operlog integration points and add snapshot/sensitive-field rules. Authentication services keep their own security-audit path.
4. Check structured output and current-project filtering, then verify queries, access control, and recovery.

Unification means common entry points and conventions. It does not require one shared business database or permission to read another user's logs.

## Keep four boundaries clear

| Boundary | Rule |
|---|---|
| Content | Keep credentials and business secrets out; sanitize sensitive exception text too |
| Capacity | Assess level, frequency, and retention; do not leave plain SQL output enabled indefinitely by default |
| Identity | Enforce tenant and permissions in the application; configure Grafana/Loki access separately |
| Control plane | A read-only Docker socket mount is still highly privileged; the local template is not a production least-privilege design |

## Possible improvements

Add controlled event fields, regression tests for sanitization, collection-health checks, and environment-specific retention/capacity policies incrementally. Record the triggering problem, expected benefit, and verification method before implementation. These are improvement directions, not already-delivered features.

Finally verify output and search using an actual request containing no secrets, and error handling using a legitimate failing request. Confirm cross-tenant denial and protection of sensitive fields. If only configuration was inspected, record “configuration checked” rather than “end-to-end collection passed.”

To understand reliable background delivery of operation records, continue with [unified MQ management](mq-principles.en.md).
