# Omni-Stack 可観測性の運用とメンテナンス

本ドキュメントは WP-07 の運用事実源であり、アプリケーションメトリクス、分散トレーシング、構造化ログ、ローカル観測スタック、アラート、SLO テンプレートを説明します。観測機能はデフォルトで無効であり、アプリケーション起動の前提条件ではありません。

## 1. データフローと境界

| シグナル | アプリケーション出口 | ローカルバックエンド | クエリ入口 |
|---|---|---|---|
| メトリクス | `/actuator/prometheus`；マイグレーター終了時プッシュ | Prometheus / Pushgateway | Grafana / Prometheus |
| Trace | OTLP HTTP、W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| ログ | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| アラート | Prometheus rules | Alertmanager | Alertmanager / 外部 receiver |

`X-Trace-Id` は旧クライアントとログ検索のための互換レスポンスヘッダーにすぎません。トレーシング有効時は現在の Micrometer/OTel traceId に等しく；サービス間伝播は W3C `traceparent` を正とします。Gateway はクライアントが偽装した身分ヘッダーを引き続き上書きしますが、標準の Trace コンテキストは破壊しません。

管理エンドポイントは Gateway を経由しません。Compose ではビジネスサービスの Actuator はアプリケーションとコンテナ内 `8080` を共有し、Prometheus のみが内部 Compose ネットワーク経由でスクレイプします；Gateway のビジネスポートを除き、サービスのホストポートはすべて `127.0.0.1` にバインドされます。本番では独立した管理ポート、ネットワークポリシー、Actuator 認証を推奨します。

## 2. 起動と停止

まず `.env.example` から完全な `.env` を作成し、リポジトリルートから実行します：

```powershell
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability
npm --prefix tools/omni-cli run dev -- dev status
```

CLI は本プロセスに `OTLP_EXPORT_ENABLED=true`、ECS JSON ログ、ローカル 100% サンプリングを設定し、一回限りのマイグレーターが終了時に結果を Pushgateway にプッシュするようにします。`--observability` がない場合、OTLP とマイグレーションメトリクスのプッシュはいずれも無効で、サンプリング率は 0、アプリケーションは Span をエクスポートせず正常に起動します。

ローカル入口：

| コンポーネント | アドレス | デフォルト用途 |
|---|---|---|
| Grafana | `http://127.0.0.1:3001` | ダッシュボード、ログ、Trace |
| Prometheus | `http://127.0.0.1:9090` | Targets、PromQL、ルール |
| Pushgateway | `http://127.0.0.1:9091` | 短命のマイグレーションメトリクス |
| Node Exporter | `http://127.0.0.1:9100` | ローカルノードとファイルシステムのメトリクス |
| cAdvisor | `http://127.0.0.1:8088` | コンテナリソースとライフサイクルのメトリクス |
| Alertmanager | `http://127.0.0.1:9093` | アラート状態 |
| Tempo | `http://127.0.0.1:3200` | Trace API |
| Loki | `http://127.0.0.1:3100` | ログ API |
| Alloy | `http://127.0.0.1:12345` | ログ収集状態 |
| OTLP | `127.0.0.1:4317/4318` | gRPC/HTTP 受信 |

停止はデフォルトで観測データボリュームを保持します：

```powershell
npm --prefix tools/omni-cli run dev -- dev down
```

ローカルのメトリクス、Trace、ログ、データベースデータが不要であることを確認した時のみ、すべての名前付きボリュームを明示的に削除できます：

```powershell
npm --prefix tools/omni-cli run dev -- dev down --volumes --confirm-delete-volumes
```

## 3. ダッシュボードとメトリクス契約

Grafana は 7 つの読み取り専用ダッシュボードを自動ロードします：Platform Overview、Service RED、JVM and Pools、Feign Clients、MQ and Outbox、Workflow、Database Migrations。JSON は `observability/grafana/dashboards/` に、データソースとダッシュボード provider は `observability/grafana/provisioning/` にあります。

Alloy は自分と同じ `COMPOSE_PROJECT_NAME` ラベルのコンテナログのみを収集します。`docker compose -p <name>` を使うか `COMPOSE_PROJECT_NAME` を設定すると、このフィルタ値は同期的に変わり、ホスト上の他の Compose プロジェクトをスキャンしません。

許可されるメトリクスラベルは次のみです：`service.name`、`environment`、`instance`、HTTP method/route template/status、exception class、MQ destination/result、およびコード内の閉じた列挙 operation/status。データベースマイグレーション情報はリポジトリ管理下の schema version を追加で使用できます。tenantId、userId、username、businessKey、生の URL、リクエストボディ、メッセージボディ、動的 SQL、テーブル名、接続アドレスをラベルに入れてはいけません。新しいラベルはまず値集合が固定であることを証明し、カーディナリティ上限を評価しなければなりません。

現在のカスタム Outbox メトリクス：

- `omni_mq_outbox_messages{status}`：pending/sent/failed/dead_letter の数量。
- `omni_mq_outbox_oldest_age_seconds`：最も古い pending/failed メッセージの経過時間。
- `omni_mq_outbox_operations_total{destination,result}`：enqueued/sent/retry/dead_letter の結果。

その他のカスタムメトリクス：

- `omni_job_registrations_total{result}`、`omni_job_executions_total{result}`：XXL-JOB 登録とユーザータスク実行の結果。
- `omni_workflow_start_operations_total{result}`、`omni_workflow_approval_operations_total{result}`：プロセス起動と承認の結果。
- `omni_workflow_approval_backlog`、`omni_workflow_approval_duration_seconds`、`omni_workflow_process_duration_seconds`：未処理バックログ、単一承認処理、プロセスのエンドツーエンド所要時間。
- `omni_procurement_workflow_start_retries_total{result}`：購買申請の Workflow 起動リトライ結果。
- `omni_inbox_operations_total{destination,result}`：SRM、調達、資産の Inbox 成功または Broker リトライのトリガー。
- `omni_db_migration_operations_total{result}`、`omni_db_migration_duration_seconds`、`omni_db_schema_version_info{version}`：一回限りのマイグレーターが終了時にプッシュする結果、所要時間、管理下のマニフェストバージョン。

Outbox 作成時に実際の traceId を `producer_trace_id` として保存し、送信時に `omniProducerTraceId` と `omniMessageId` メッセージヘッダーでコンシューマーに渡します；履歴に生産 traceId が欠けていても正常に配信できます。リレーは配信ごとに新しい relay span を作成し、ログには `msgId`、`producerTraceId`、`relayTraceId` と固定の destination/result のみを出力します；Asset Consumer はメッセージヘッダーから関連フィールドを取り出し、`msgId`、`producerTraceId` と自身の `consumerTraceId` を記録します。どちら側も payload、テナント、ビジネスキーを出力しません。`msgId` により生産ログからリレーと消費ログへジャンプし、それぞれ対応する Trace を照会できます。

## 4. アラートと閾値キャリブレーション

`observability/prometheus/alerts.yml` にはサービス利用不可、5xx、P95/P99、コネクションプール、Outbox バックログ/デッドレター、Workflow/XXL-JOB/Inbox の失敗、ディスク、コンテナ再起動、メモリ、JVM デッドロックのルールが含まれます。ファイル内の 5%、1 秒、2 秒、85%、5 分などはすべてローカル例であり、本番の約束ではありません。

本番リリース前に必ず：

1. 少なくとも 7 日間の代表的トラフィックでベースラインと日次/週次サイクルを計算する。
2. サービスごとに異なるレイテンシとエラーバジェットを設定し、1 つのグローバル閾値が差異を隠すのを避ける。
3. 実際の Alertmanager receiver、オンコール、エスカレーションポリシーを設定する；認証情報は Secret 管理からのみ取得し、リポジトリにコミットしてはいけない。
4. service down、5xx、Outbox デッドレター、receiver 失敗を訓練する。
5. 閾値の責任者、キャリブレーション日、次回レビュー日を記録する。

## 5. SLO テンプレート

各本番サービスは以下のテンプレートをコピーし、ビジネスとプラットフォームが共同で確認します：

| フィールド | 例 | 必須説明 |
|---|---|---|
| サービス/ユーザージャーニー | `omni-procurement / 購買申請の提出` | ユーザー成果で命名 |
| SLI | 成功した非 5xx リクエスト / 有効リクエスト | ヘルスチェックとクライアントキャンセルを明示的に除外 |
| 目標 | 30 日 99.9% | 例をそのまま写してはいけない |
| レイテンシ目標 | P95 < 800ms、P99 < 2s | route template で統計 |
| エラーバジェット | 30 日 43m12s | 目標から自動計算 |
| 高速バーンアラート | 1h 窓、14.4x | 短窓/長窓を同時に要求 |
| 低速バーンアラート | 6h 窓、6x | オンコール応答レベルに紐づけ |
| 責任者 | チームとオンコール表 | 連絡可能でなければならない |
| キャリブレーション日 | YYYY-MM-DD | 四半期ごとまたは大型バージョン後にレビュー |

## 6. セキュリティと本番化

- ローカル Grafana のデフォルト認証情報は初期状態の確認専用；共有や本番環境では `.env`/Secret で上書きし、SSO/TLS を有効化しなければならない。
- Alloy はコンテナ stdout 収集のために Docker socket を読み取り専用マウントする。Docker socket は高権限制御面と同等；本番では制限付きログエージェント、最小権限、独立した収集ノードに切り替えるべき。
- ローカル保持期間はデフォルトで Prometheus 7 日、Tempo 24 時間、Loki 7 日。本番の容量、バックアップ、暗号化、保持ポリシーは再設計しなければならない。
- 管理ポート、OTLP、Grafana、Prometheus、Pushgateway、Exporter、Loki、Tempo、Alertmanager をインターネットに直接公開してはいけない。本番 Pushgateway は TLS/認証を有効化しプッシュ元を制限しなければならない。
- ECS ログにトークン、パスワード、Cookie、Authorization、リクエストボディ、メッセージボディ、生の例外中の Secret を記録してはいけない。

## 7. 検証とトラブルシューティング

構成レベルの検証：

```powershell
docker compose --profile observability config --quiet
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check rules /etc/prometheus/alerts.yml
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml
```

実行後、Prometheus `/targets` で起動済みアプリケーションが UP であることを確認します；現在のプリセットに含まれないサービスが DOWN と表示されるのは想定内です。Grafana にデータがない場合は、アプリケーションの `/actuator/prometheus`、Prometheus target、OTel Collector ログ、Tempo/Loki readiness、Alloy targets の順に確認します。

性能受け入れは同じ fixture で観測の無効時と有効時の起動時間、CPU、メモリ、スループット、P95/P99 をそれぞれ測定します。結果は `docs/evidence/scaffold-upgrade/` に書き込みます；ローカル 100% サンプリングは受け入れ専用で、本番のサンプリング率は容量と SLO でキャリブレーションします。
