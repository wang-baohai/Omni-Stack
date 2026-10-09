# プリセット、軽量モード、サービス作成、CRUD 生成

新規プロジェクトでは CLI とモジュールカタログを使用し、既存サービスのコピーと一括置換を行いません。

## 1. プリセット選択

```bash
npm --prefix tools/omni-cli run dev -- preset list
npm --prefix tools/omni-cli run dev -- preset explain crm
```

プリセットはバックエンド、フロントエンド、seed、migration、Compose profile、外部依存を選びます。`supply-chain` は SRM、Procurement、Asset を含みます。詳細は [プリセット選択](../preset-quick-selection.jp.md)と[依存マトリクス](../preset-dependency-matrix.jp.md)を参照してください。

## 2. 軽量開発

`dev` のサブコマンドは `up`、`down`、`status`、`doctor`、`logs` の 5 つのみです。`dev plan` は存在せず、計画は `dev up` が Docker 起動前に出力します。

```bash
npm --prefix tools/omni-cli run dev -- dev doctor
npm --prefix tools/omni-cli run dev -- dev up --preset crm
npm --prefix tools/omni-cli run dev -- dev status
npm --prefix tools/omni-cli run dev -- dev logs --module crm --tail 200
npm --prefix tools/omni-cli run dev -- dev down
```

`dev up` は `development preset|module: <id>`、`mode: lite|full`、`modules:`、`services:`、`observability:` と縮退情報をこの順で出力し、これが実行の事実源です。モードは preset またはモジュールから推定（`core`、`workflow`、`crm` は lite）され、`--mode lite|full` で同一 preset を切り替えられるため両モードを同一契約で比較できます。SRM/Procurement/Asset を含む閉包は、リアルタイム消費者を黙って省略せず lite を拒否します。lite は Nacos クライアント、XXL-JOB、MQ relay と自動検出を明示的に無効化し（Outbox 書き込みは継続）、full はこれらのスイッチを CLI で上書きしないため `.env` が権威です。

並列行の分離は `--project-name`（コンテナ・ネットワーク・ボリュームを命名する Compose project）と `--set NAME=VALUE`（Compose が宣言済みのホストポートとボリューム名のみ、例 `OMNI_MYSQL_HOST_PORT=23306`、`OMNI_MYSQL_VOLUME_NAME=wp011-row1-mysql`）で行います。Secret は拒否され、値はログに出ません。

命名ボリュームは既定で保持されます。`dev down` は `--volumes`、`--confirm-delete-volumes`、`--project-name` が揃った場合のみ削除し、先に各ボリュームの `com.docker.compose.project` ラベルを照合します（`omni-stack-mysql-data` のような固定名は project をまたいで共有されるため）。project 未指定では Compose がディレクトリ名から導出し、削除経路は使えません。

## 3. サービス作成

`create-service` は**デフォルトではプレビュー専用**で何も書き込みません。まず計画を確認します：

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory
```

プレビューされたリソース/統合一覧を確認後、**明示的に `--apply` を付けて**初めてアトミック書き込みとモジュール作成が行われます：

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory --apply
```

`--apply` でのみ Maven モジュール、アプリ、設定、ヘルス、Docker、フロント/権限/ドキュメント統合とロックが生成されます（既定は現ワークスペースへ書込；`--output` を指定した場合は後続ビルド/統合コマンドの cwd を調整）。`--apply` 前はモジュールが存在しないため先にコンパイルすると失敗します。生成後 JDK 25 で検証：

```bash
cd omni-backend
./mvnw clean install -pl omni-inventory -am
```

`@RequestBody` がある新サービスは `XssConfigProvider` を実装します。Servlet は `omni-common-service` を使い、Gateway の reactive セキュリティをコピーしません。

## 4. CRUD 生成

```bash
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --check
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --dry-run
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --apply
```

コマンドは `generate crud --spec <file>` で、`crud plan`/`crud generate` は存在しません。`--check` は宣言の検証のみ、`--dry-run` は変更計画のレンダリング、`--apply` で初めてクロスファイルの原子的書き込みを行い、`--apply` と `--dry-run` は排他です。

型、API、サービス、永続化、権限、migration、テスト、i18n タスクを生成します。状態機械、データ範囲、冪等性、業務検証は開発者が追加します。

## 5. DB と権限

forward-only Liquibase changeSet、安定 seed、`scripts/sql/seed/auth.sql` 権限、`database/seed/manifest.yaml`、fresh/upgrade の両経路を更新します。`migrate-*.sql` や一時修復スクリプトを正式成果物にしません。

## 6. カスタムプリセット

依存閉包、競合、前後端選択、migration、seed を宣言し、5 プリセット黄金マトリクスとロックを更新します。[カスタムプリセット](../custom-preset-tutorial.jp.md)と[保守説明](../preset-maintenance.jp.md)を参照してください。

## 7. 完了条件

JDK 25 Reactor、lint 0 warning、production build、権限負向きテスト、fresh/upgrade/対象プリセット、四言語文書と画像、臨時成果物ゼロを満たします。加えて `omni dev doctor` に FAIL が無いこと（未起動時の SKIP は失敗ではないが証跡でもない）。

