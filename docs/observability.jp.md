# Omni-Stack 可観測性の運用とメンテナンス

本書はメトリクス、追跡、構造化ログ、アラートと SLO の現在の工程境界を定義します。観測機能は既定で無効で、アプリ起動の前提条件ではありません。

概念、七つのダッシュボード、収集対象、ログ、実トレース、アラート画面の図解は [Wiki の可観測性章](wiki/observability-principles.jp.md)にまとめています。

## 1. データフローと境界

| シグナル | アプリケーション出口 | ローカルバックエンド | クエリ入口 |
|---|---|---|---|
| メトリクス | `/actuator/prometheus`；マイグレーター終了時プッシュ | Prometheus / Pushgateway | Grafana / Prometheus |
| Trace | OTLP HTTP、W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| ログ | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| アラート | Prometheus rules | Alertmanager | Alertmanager / 外部 receiver |

`X-Trace-Id` は旧クライアントとログ検索のための互換レスポンスヘッダーにすぎません。トレーシング有効時は現在の Micrometer/OTel traceId に等しく；サービス間伝播は W3C `traceparent` を正とします。Gateway はクライアントが偽装した身分ヘッダーを引き続き上書きしますが、標準の Trace コンテキストは破壊しません。

管理エンドポイントは Gateway を経由しません。Compose ではアプリケーションポートはコンテナ内で `8080` に統一されますが、Actuator は独立した管理ポート `MANAGEMENT_SERVER_PORT`（サービスごとに `19900`〜`19907`、auth/base/gateway/workflow/crm/srm/procurement/asset に対応）を使用し、両者は共有しません。管理ポートはホストにマップされず、内部 Compose ネットワーク経由で Prometheus のみがスクレイプします。Gateway のビジネスポートを除き、サービスのホストポートはすべて `127.0.0.1` にバインドされます。本番ではネットワーク分離を引き続き適用し、Actuator 認証を有効化してください。

## 2. 起動と停止

observability profile と明示的な出力設定が観測機能を制御し、既定は無効です。有効化しても業務ポートを変更しません。管理入口を公開してはなりません。メトリクスは独立した management ポートを使い、業務ポートの 404 はメトリクス欠如の証拠ではありません。起動・ポート・正常性確認と安全な停止は[運用](guides/operations-upgrade.jp.md)を参照してください。

## 3. ダッシュボードとメトリクス契約

Grafana は 7 つの読み取り専用ダッシュボードを自動ロードします：Platform Overview、Service RED、JVM and Pools、Feign Clients、MQ and Outbox、Workflow、Database Migrations。JSON は `observability/grafana/dashboards/` に、データソースとダッシュボード provider は `observability/grafana/provisioning/` にあります。

Alloy は Docker ラベル `com.docker.compose.project` でログ対象を絞り込み、現在の Compose プロジェクトのログだけを収集します。フィルタ値は `COMPOSE_PROJECT_NAME` に由来する `OMNI_COMPOSE_PROJECT` から取得し、`docker compose -p <name>` または明示的な `COMPOSE_PROJECT_NAME` に従います。Docker discovery はコンテナのメタデータを検出しますが、ログ収集は絞り込み後に行い、他の Compose プロジェクトのログを収集しません。

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

Outbox 作成時に実際の traceId を `producer_trace_id` として保存し、送信時に `omniProducerTraceId` と `omniMessageId` メッセージヘッダーでコンシューマーに渡します；履歴に送信側 traceId が欠けていても正常に配信できます。リレーは配信ごとに新しい relay span を作成し、ログには `msgId`、`producerTraceId`、`relayTraceId` と固定の destination/result のみを出力します；Asset Consumer はメッセージヘッダーから関連フィールドを取り出し、`msgId`、`producerTraceId` と自身の `consumerTraceId` を記録します。どちら側も payload、テナント、ビジネスキーを出力しません。`msgId` により送信側ログからリレーと受信側ログへジャンプし、それぞれ対応する Trace を照会できます。

### Alertmanager UI の境界

`alertmanager-ui` は公式 v0.30.1 の Elm ソースから4言語の UI を構築し、同じ `alertmanager:9093` バックエンドへプロキシします。ホスト入口は `127.0.0.1:9093` のままで、バックエンドから同じポートを重複公開しません。UI パスは `/zh-CN/`、`/en-US/`、`/ja-JP/`、`/ko-KR/` で、言語を切り替えても hash ルートとフィルターを維持します。API フィールド、ラベル、状態の列挙値、レシーバー名は原値を保持し、曜日設定も上流の英語プロトコル値で保存します。ビルドは上流アーカイブの SHA-256 を固定し、Apache-2.0 の LICENSE/NOTICE を保持します。文案の欠落や構造パッチの不一致があれば失敗します。出典と保守判断は [ADR-0004](adr/0004-native-alertmanager-localization.md)、操作と画像は[運用ガイド](guides/operations-upgrade.jp.md)を参照してください。業務サービスや公開管理入口の追加ではありません。

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
| エラーバジェット | 30 日間の有効リクエスト数 × 0.1% | この表は要求数で計算する。時間で可用性を測る場合にだけ30日99.9%が43m12sに相当し、直接置き換えられない |
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

設定、実行正常性、Trace・ログ関連、通知の到達と性能を個別に検証します。構文成功だけでは targets UP や通知到達は証明できません。未起動サービスは対象 preset と照合し、すべての DOWN を無視しません。現在のソース、イメージ、preset、時刻、実際の失敗と範囲を記録します。過去の検証は当時の基準だけを証明します。手順と復元範囲は[運用](guides/operations-upgrade.jp.md)を参照してください。
