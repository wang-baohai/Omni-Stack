# 5 分で始めるクイックスタート

対象バージョン：Omni-Stack 0.6.x

ローカル開発環境を起動し、初回ログインを確認するためのガイドです。本番環境では [Docker デプロイガイド](../docker-deployment.jp.md) と [運用・バックアップ・復旧・アップグレード](operations-upgrade.jp.md)を参照してください。

## 1. 前提条件

- JDK 25。
- Node.js LTS 22.12.0 以上。
- Docker Desktop と Docker Compose。
- 利用可能メモリ 12 GB 以上を推奨。単一ドメインの評価にはプロジェクトプリセットを使用します。

## 2. 起動方法

フル構成：

```bash
docker compose --profile full up -d
docker compose ps
```

CRM のみ：

```bash
npm --prefix tools/omni-cli run dev -- dev up --preset crm
```

`core`、`crm`、`srm`、`procurement`、`asset`、`supply-chain` を選択できます。詳細は [プリセット早見表](../preset-quick-selection.jp.md)を参照してください。初回起動では Liquibase が先に移行を実行します。旧 `init-all.sql` や `migrate-*.sql` は実行しません。

## 3. 稼働確認

`docker compose ps` で選択したアプリケーションが `healthy` になるまで待ちます。

| 入口 | URL | 用途 |
|---|---|---|
| フロントエンド | `http://localhost:3000` | ログイン、ワークスペース、管理画面 |
| Nacos | `http://localhost:8080` | ローカルのサービス検出と設定 |
| Gateway | `http://localhost:8102` | フロントエンド API の統一入口 |

ポート競合時は `docker compose ps` と `docker compose logs <service>` で失敗サービスを特定し、データベースボリュームを安易に作り直さないでください。

## 4. 初回ログイン

1. ログイン画面を開きます。
2. 開発テナントを選択します。
3. `scripts/sql/seed/auth.sql` で開発用と明示された管理者を使用します。
4. 画面の一回限りの CAPTCHA を入力します。
5. ログイン後、開発用認証情報を直ちに変更します。共有環境や本番では使用しません。

CAPTCHA は 1 リクエスト 1 回限りです。更新または失敗後は、新しい画像と `captchaKey` を使用します。

## 5. 最初の機能確認

管理者にはシステム管理、基本データ、ジョブ管理、運用監視、ワークフロー、およびプリセットの業務モジュールが表示されます。ユーザー一覧、プロセスモデル、対象ドメイン概要、個人タスク作成画面を順に確認してください。

メニューがない場合は権限またはプリセットを確認し、フロントエンドへ静的ルートを追加して回避しません。

## 6. ソース開発

```bash
cd omni-backend
./mvnw clean install
```

Windows では先に `JAVA_HOME` を JDK 25 に設定します。

```bash
cd omni-frontend
npm install
npm run lint
npm run build
npm run dev
```

## 7. 停止

```bash
docker compose --profile full down
```

通常は `--volumes` を付けません。完全な新規 DB が必要な場合に限り、Compose project と専用ボリューム名を確認して削除します。

## 8. 次のガイド

- [認証とテナント選択](authentication.jp.md)
- [メニュー・ロール・データ権限](permissions.jp.md)
- [スキャフォールド開発](scaffold-development.jp.md)
- [トラブルシューティング](troubleshooting.jp.md)

