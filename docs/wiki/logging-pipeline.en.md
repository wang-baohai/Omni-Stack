# 3. The collection path: from a container to a request

Follow one runtime event: the application writes to standard output, Docker keeps container logs, Alloy discovers and filters targets, and Loki receives the records. Grafana reads from Loki rather than connecting to every business container.

## Step 1: keep the current project

This is the beginning of the Alloy relabel configuration. The excerpt ends after its first filtering rule:

<!-- omni:source path="observability/alloy/config.alloy" -->
```hcl
discovery.relabel "containers" {
  targets = discovery.docker.local.targets

  rule {
    source_labels = ["__meta_docker_container_label_com_docker_compose_project"]
    regex         = sys.env("OMNI_COMPOSE_PROJECT")
    action        = "keep"
  }
```

`keep` retains targets whose Compose project label matches. OMNI_COMPOSE_PROJECT comes from the current Compose project name. Discovering container metadata does not mean all discovered logs are collected. The log source uses the filtered targets:

<!-- omni:source path="observability/alloy/config.alloy" -->
```hcl
loki.source.docker "local" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.relabel.containers.output
  forward_to = [loki.write.local.receiver]
}
```

The local write component then sends records to Loki. If the target name is correct but logs are missing, check actual application output, Docker access, and Loki writes. A Running container alone does not prove this path works.

## Step 2: leave a request correlation clue

The shared Servlet filter places traceId in the current thread's logging context and removes it at the end:

<!-- omni:source path="omni-backend/omni-common/src/main/java/com/omni/common/web/TraceIdFilter.java" -->
```java
        MDC.put(MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
```

Think of MDC as a small note carried during this processing segment. The finally block prevents a reused thread from carrying the previous request's ID into the next request. An actual tracing Span takes priority when present; with tracing disabled a compatibility ID may still exist. An ID is therefore not proof of a trace in Tempo.

## Step 3: cross services and message boundaries

Synchronous calls propagate tracing context through standard W3C traceparent. Background messages also wait, retry, and get consumed. Find the message by msgId, then inspect producer, relay, and consumer trace IDs separately. They need not form one continuous Trace.

Narrow the service and time window before searching by ID. If nothing appears, check output, project filtering, collection delivery, storage, and query conditions in that order. A failed query is not a zero record count.

Next, distinguish [operation records from security audit](logging-audit.en.md).
