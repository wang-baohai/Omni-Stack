# Omni-Stack 可観測性の運用と保守

本文書は WP-07 の運用事実源です。メトリクス、分散トレース、構造化ログ、任意のローカル観測スタック、アラート、SLO テンプレートを説明します。可観測性は既定で無効であり、アプリケーション起動の前提ではありません。

## 1. データフローと境界

| シグナル | アプリケーション出力 | ローカルバックエンド | 問い合わせ入口 |
|---|---|---|---|
| メトリクス | `/actuator/prometheus`、マイグレーター終了時 push | Prometheus / Pushgateway | Grafana / Prometheus |
| Trace | OTLP HTTP、W3C `traceparent` | OTel Collector → Tempo | Grafana Explore |
| ログ | ECS JSON stdout | Alloy → Loki | Grafana Explore |
| アラート | Prometheus rules | Alertmanager | Alertmanager / 外部 receiver |

`X-Trace-Id` は互換レスポンスヘッダーです。サービス間伝播は W3C `traceparent` を使用します。管理エンドポイントは Gateway を経由しません。本番では管理ポート、ネットワークポリシー、認証を分離してください。

## 2. 起動と停止

`.env.example` から完全な `.env` を作成し、リポジトリルートで実行します。

```powershell
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability
npm --prefix tools/omni-cli run dev -- dev status
```

このフラグは OTLP、ECS JSON、ローカル 100% サンプリング、マイグレーション結果 push を有効にします。フラグがない場合もアプリケーションは通常どおり動作します。

Grafana `:3001`、Prometheus `:9090`、Pushgateway `:9091`、Node Exporter `:9100`、cAdvisor `:8088`、Alertmanager `:9093`、Tempo `:3200`、Loki `:3100`、Alloy `:12345`、OTLP `:4317/4318` はすべて `127.0.0.1` または内部ネットワーク向けです。

`dev down` はボリュームを保持します。データが不要であることを確認した場合だけ `--volumes --confirm-delete-volumes` を使用します。

## 3. Dashboard とメトリクス契約

Grafana は Platform Overview、Service RED、JVM and Pools、Feign Clients、MQ and Outbox、Workflow、Database Migrations の7つの読み取り専用 Dashboard を自動登録します。

Alloy は同じ `COMPOSE_PROJECT_NAME` のコンテナだけを収集します。ラベルは service、environment、instance、HTTP method/route template/status、exception class、固定 MQ destination/result、閉じた列挙値に限定します。tenant、user、business key、生 URL、payload、SQL、テーブル名、接続先をラベルにしてはいけません。

独自メトリクスは Outbox、XXL-JOB、Workflow、Procurement retry、Inbox、DB migration を対象にします。`msgId` で producer・relay・consumer Trace を関連付け、payload やテナント情報はログに出力しません。

## 4. アラートとしきい値調整

`observability/prometheus/alerts.yml` の可用性、5xx、遅延、接続プール、Outbox、Workflow、ジョブ、ディスク、再起動、メモリ、JVM deadlock の値はローカル例です。本番前に7日以上の代表トラフィックでサービス別に調整し、Secret 経由で receiver とエスカレーションを設定し、障害訓練と担当者・見直し日を記録します。

## 5. SLO テンプレート

各本番サービスはユーザージャーニー、SLI、対象期間、P95/P99、計算済みエラーバジェット、短期/長期 burn-rate アラート、連絡可能な担当者、校正日を定義します。例示値を測定なしで採用してはいけません。

## 6. セキュリティと本番化

- Grafana のローカル認証情報を置き換え、SSO/TLS を有効にします。
- Docker socket は高権限です。本番では制限されたログエージェントを使用します。
- 保持期間、容量、暗号化、バックアップを本番向けに再設計します。
- 観測管理ポートをインターネットへ直接公開しません。
- token、password、Cookie、Authorization、本文、Secret をログに記録しません。

## 7. 検証とトラブルシューティング

```powershell
docker compose --profile observability config --quiet
docker run --rm --entrypoint /bin/promtool --mount type=bind,source=${PWD}/observability/prometheus,target=/etc/prometheus,readonly prom/prometheus:v3.14.0 check rules /etc/prometheus/alerts.yml
```

起動後は Prometheus target、アプリケーションメトリクス、Collector ログ、Tempo/Loki readiness、Alloy target の順に確認します。同じ fixture で観測無効/有効の起動時間、CPU、メモリ、スループット、遅延を比較し、`docs/evidence/scaffold-upgrade/` に記録します。
