# Omni-Stack

Spring Boot 4 + Vue 3 を基盤とし、テナント分離、権限制御、業務拡張に対応する企業向けマイクロサービスのひな形です。ソースコード、技術的な事実、画像付きチュートリアル、AI 協働のルールを同じリポジトリで管理します。

[简体中文](README.md) · [English](README.en.md) · [한국어](README.kr.md)

## 主な特長

- 公開ホームから Wiki を閲覧できます。ワークスペースには権限のあるアプリを集約し、業務モジュールは新しいタブで開きます。
- マルチテナント、機能権限、データ範囲。CAPTCHA ログイン、ソーシャルログイン、OAuth2 認可に対応します。
<!-- omni:modules:workflow -->
- 独立した Workflow サービスで BPMN のモデリング、公開、承認、合議を扱います。
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB がシステムタスクと個人タスクを管理します。
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM はリード、顧客、商談、フォロー活動を扱います。
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset が仕入先、調達、資産の業務をつなぎます。
<!-- omni:modules:end -->
- Transactional Outbox、冪等な消費、デッドレター管理。メトリクス、トレース、統合ログ収集は任意で利用できます。
- `tools/omni-cli` がプリセット別の独立プロジェクト、サービス、CRUD を生成します。
- Harness の実践により、簡潔な技術的事実、共有 AI ワークフロー、検証証拠を継続開発に生かします。

技術基盤：Spring Boot 4.0.6、JDK 25、Spring Cloud、Vue 3、TypeScript、Vite、Element Plus。

## プロジェクト構成

| パス | 役割と参照先 |
|---|---|
| `omni-backend/` | Maven マルチモジュールのバックエンド。`omni-backend/pom.xml` がサービスと共通モジュールをまとめます。 |
| `omni-frontend/` | 公開ホーム、個人用ワークスペース、業務画面を含む Vue 3 アプリケーション。 |
| `database/` · `scripts/sql/seed/` | Liquibase のマイグレーションは `database/changelog/` にあり、シードデータと検証ルールは `database/seed/manifest.yaml` に登録します。 |
| `scripts/fixtures/` | 管理されたテスト用フィクスチャとスクリーンショット収集ツール。 |
| `scaffold/` · `tools/omni-cli/` | `scaffold/catalog/modules.yaml` はモジュールを、`scaffold/presets/` はプリセットを定義します。CLI はプロジェクト、サービス、CRUD を生成します。 |
| `docker/` · `compose.yaml` | Dockerfile は `docker/` にあります。`compose.yaml` はローカルサービスを構成し、`compose.tools.yaml` はビルドと検証のツールを提供します。 |
| `observability/` | オプションのメトリクス、トレース、ログ、ダッシュボード、アラートの設定。 |
| `docs/` | 技術資料、4 言語の Wiki、[ソースコード索引](docs/reference/source-map.jp.md)。 |
| `AGENTS.md` · `ai/` | プロジェクトの実行ルールと共有 AI 資産の参照先。 |
| `.agents/skills/` | Codex 用のプロジェクト Skill の参照先。変更とレビューを扱う `omni-change` と、質問を重ねて計画を詰める `grill-me` を `ai/skills/` から生成します。 |
| `.qoder/skills/` | Qoder 用のプロジェクト Skill の参照先。同じ `omni-change` と `grill-me` をアダプターで同期します。 |
| `.github/workflows/` | 元のリポジトリの GitHub Actions 設定。`quality.yml` は push、Pull Request、定期実行、手動実行に対応し、バックエンド、フロントエンド、ドキュメント、AI Skill の参照先、データベース移行、スキャフォールディング、セキュリティを検証します。 |

### バックエンドモジュール

- `omni-gateway` はリクエストの受付とルーティング、`omni-auth` は認証と認可、`omni-base` は基礎データとプラットフォーム管理を担当します。
<!-- omni:modules:workflow -->
- `omni-workflow` は BPMN のモデリング、承認、合議を担当します。
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- `omni-crm` はリード、顧客、商談を管理します。
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- `omni-srm`、`omni-procurement`、`omni-asset` はそれぞれ仕入先、調達、資産を担当します。
<!-- omni:modules:end -->
- `omni-common` と `omni-common-*` は共通基盤を提供し、`omni-db-migrator` がデータベースのマイグレーションを実行します。

### フロントエンドの参照先

- `omni-frontend/src/views/` は画面、`omni-frontend/src/components/` は再利用コンポーネント、`omni-frontend/src/api/` はバックエンド API の呼び出しを管理します。
- `omni-frontend/src/router/` はルーティング、`omni-frontend/src/stores/` は Pinia の状態、`omni-frontend/src/locales/` は画面の翻訳を扱います。
- `omni-frontend/tests/` は単体テストです。`omni-frontend/e2e/` と `omni-frontend/e2e-docs/` は E2E テストとドキュメント用スクリーンショットを扱います。

### AI ツールのディレクトリ

`ai/` はプロジェクトで使う AI 資産の管理元です。`ai/manifest.yaml` に出典、バージョン、ライセンス、対応ツールを記録します。

- `ai/skills/` には、プロジェクト変更の手順を定める `omni-change` や、質問を重ねて計画を詰める `grill-me` などの Skill の原本があります。
- `ai/prompts/` には、変更レビューとドキュメント同期のテンプレートがあります。
- `ai/knowledge/` には、Docker での検証など、プロジェクトで得た知見を記録します。
- `ai/adapters/bootstrap.mjs` は共有の原本から Codex と Qoder 向けのプロジェクト Skill ファイルを生成し、同期します。

Skill を変更するときは、まず `ai/skills/` を更新し、アダプターで各ツール向けのファイルを同期します。インストールと確認の手順は [AI ツールガイド](ai/README.jp.md) を参照してください。

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[Wiki 全体：紹介、画像付き操作、起動、開発](docs/wiki/index.jp.md)
<!-- omni:preset-table:end -->

[Apache License 2.0](LICENSE) · © Wang Baohai
