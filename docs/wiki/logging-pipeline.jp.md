# 3. 収集経路：コンテナからリクエストをたどる

アプリが標準出力に書き、Dockerがコンテナログを保持し、Alloyが対象を発見・絞り込み、Lokiへ送ります。Grafanaは各業務コンテナへ直接接続するのではなく、Lokiから結果を読みます。

## 手順1：現在のプロジェクトに絞る

Alloyのラベル変換設定の冒頭です。抜粋は最初の絞り込みルールの末尾までです。

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

`keep`はComposeのプロジェクトラベルが一致する対象だけを残します。OMNI_COMPOSE_PROJECTは現在のComposeプロジェクト名です。メタデータを発見しただけでは全コンテナのログを収集したことにはなりません。ログソースは絞り込み後の対象を使います。

<!-- omni:source path="observability/alloy/config.alloy" -->
```hcl
loki.source.docker "local" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.relabel.containers.output
  forward_to = [loki.write.local.receiver]
}
```

後続のローカル書き込み先からLokiへ送ります。対象名が正しくてもログがなければ、実際の出力、Dockerへのアクセス、Lokiへの書き込みを確認します。Runningという状態だけでは経路の成功を証明できません。

## 手順2：リクエストの手掛かりを残す

共通のServletフィルターは現在のスレッドのログコンテキストにtraceIdを入れ、終了時に消します。

<!-- omni:source path="omni-backend/omni-common/src/main/java/com/omni/common/web/TraceIdFilter.java" -->
```java
        MDC.put(MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
```

MDCは「この処理が持つ小さなメモ」と考えると分かりやすくなります。finallyで消すのは、再利用されたスレッドに前のリクエストの番号を残さないためです。実際の追跡Spanがあれば優先し、追跡が無効でも互換番号が生成される場合があります。番号だけでTempoへの保存を断定できません。

## 手順3：サービスとメッセージをまたぐ

同期呼び出しではW3C traceparentで追跡コンテキストを伝えます。メッセージには待機、再試行、消費があるため、まずmsgIdで同じメッセージを見つけ、生成側、中継、消費側の追跡番号を別々に確認します。一つの連続したTraceとは限りません。

サービスと時間帯を絞ってから番号を検索します。見つからなければ、出力、プロジェクト絞り込み、収集と送信、保存、検索条件の順に調べます。検索エラーを「記録ゼロ」と扱わないでください。

次は[操作記録と安全監査](logging-audit.jp.md)を区別します。
