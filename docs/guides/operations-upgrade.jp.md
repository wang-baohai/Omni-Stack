# 運用、可観測性、バックアップ、復旧、アップグレード

ローカル検証と本番計画のガイドです。本番では容量、法令、責任分界に合わせ、ローカル Compose ポートをそのまま公開しません。

## 1. 起動順序

```text
MySQL / Redis / RocketMQ → Nacos / XXL-JOB → Liquibase migrator
→ Auth / Base / Workflow / 業務サービス → Gateway / Frontend
```

`docker compose --profile full up -d` 後はコンテナ起動ではなく health を確認し、migrator 成功後にトラフィックを受けます。

## 2. 可観測性 profile

```bash
docker compose --profile full --profile observability up -d
```

OpenTelemetry Collector、Prometheus、Alertmanager、Tempo、Loki、Alloy、Grafana、Pushgateway、Node Exporter、cAdvisor と各種 Dashboard を起動します。同期は W3C `traceparent`、`X-Trace-Id` は実 trace、非同期は producer/consumer trace を関連付けます。

本番のサンプリング、保持、閾値、通知先は容量に合わせます。observability 無効時も業務は正常起動し、Span を送信しません。

## 3. ヘルスとアラート

アプリ health、Prometheus targets、Gateway/Feign のエラーと遅延、JVM/接続プール、Outbox/Inbox、デッドレター、XXL-JOB、Workflow、migration を監視します。

## 4. バックアップ

MySQL、Nacos 永続設定、外部 Secret 一覧、Grafana/通知設定、デプロイ版、イメージ digest、`database/seed/manifest.yaml`、`omni-scaffold.lock` を保護します。Redis、MQ、Tempo、Loki は RPO/RTO に基づき決定します。

## 5. 復旧演習

隔離環境へ復元し、本番下流を切断し、migration、起動、health、ログイン、権限、業務、非同期、件数照合を行い、実時間を記録します。復元成功証拠のないバックアップは利用可能とみなしません。

## 6. アップグレード

expand → migrate/backfill → contract を使用します。説明確認、バックアップ復元、実データ相当での upgrade、互換 Schema、両構造対応アプリ、観測、旧利用者終了後の contract の順です。実行済み changeSet は変更せず、復旧または補償 changeSet を使います。

## 7. セキュリティ

管理ポートは内部または localhost に限定し、TLS、リバースプロキシ/WAF、最小権限、外部 Secret 管理を使用します。Secret を Git、イメージ層、画像、履歴、ログへ残しません。[Docker デプロイ](../docker-deployment.jp.md)と[可観測性](../observability.md)を参照してください。

