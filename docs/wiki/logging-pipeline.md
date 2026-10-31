# 3. 采集链路：从一个容器到一次请求

现在顺着一条运行日志走。应用把事件写到标准输出，Docker 保存容器日志，Alloy 发现目标并筛选项目，最后把日志发给 Loki。Grafana 从 Loki 读取结果，而不是直接逐台连接业务容器。

## 第一步：只留下当前项目

下面是 Alloy 重标记配置的开头，片段结束于第一条筛选规则：

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

`keep` 表示只保留 Compose 项目标签匹配的目标。OMNI_COMPOSE_PROJECT 来自当前 Compose 项目名称。发现容器元数据不等于已经采集全部容器日志；真正的日志源读取筛选后的集合：

<!-- omni:source path="observability/alloy/config.alloy" -->
```hcl
loki.source.docker "local" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.relabel.containers.output
  forward_to = [loki.write.local.receiver]
}
```

后续本地写入端把记录送到 Loki。目标名正确但没有日志时，还要检查容器是否实际输出、采集器是否能访问 Docker、Loki 是否可写，不能只看一个容器是 Running。

## 第二步：给请求留下关联线索

Servlet 公共过滤器在当前线程的日志上下文放入 traceId，并在结束时清除：

<!-- omni:source path="omni-backend/omni-common/src/main/java/com/omni/common/web/TraceIdFilter.java" -->
```java
        MDC.put(MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
```

MDC 可以理解为“这一段处理随带的小纸条”。finally 清理，避免线程复用时把上次请求的编号带给下一位用户。有真实追踪 Span 时优先使用其 traceId；关闭追踪时也可能生成兼容编号。因此编号存在不等于 Tempo 里一定有链路。

## 第三步：跨服务和消息怎样查

同步服务调用使用标准 W3C traceparent 传播追踪上下文。后台消息另有等待、重试和消费过程，应先用 msgId 找同一条消息，再分别查生产、中继、消费的追踪编号，不能假定三个环节必然属于同一条 Trace。

排查时先缩小服务与时间窗口，再按编号查内容。没有结果时依次核对应用输出、项目筛选、采集投递、存储、查询条件。不要把“查询出错”解释成“记录数量为零”。

下一篇分清[操作记录与安全审计](logging-audit.md)。
