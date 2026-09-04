# プリセット、軽量モード、サービス作成、CRUD 生成

新規プロジェクトでは CLI とモジュールカタログを使用し、既存サービスのコピーと一括置換を行いません。

## 1. プリセット選択

```bash
npm --prefix tools/omni-cli run dev -- preset list
npm --prefix tools/omni-cli run dev -- preset explain crm
```

プリセットはバックエンド、フロントエンド、seed、migration、Compose profile、外部依存を選びます。`supply-chain` は SRM、Procurement、Asset を含みます。詳細は [プリセット選択](../preset-quick-selection.jp.md)と[依存マトリクス](../preset-dependency-matrix.jp.md)を参照してください。

## 2. 軽量開発

```bash
npm --prefix tools/omni-cli run dev -- doctor
npm --prefix tools/omni-cli run dev -- dev plan --preset crm
npm --prefix tools/omni-cli run dev -- dev up --preset crm
```

計画出力を実行の事実源とします。選択しない MQ、Workflow、ジョブ統合は明示的に無効化します。

## 3. サービス作成

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory
```

Maven モジュール、アプリ、設定、ヘルス、Docker、統合計画、ロックを生成します。計画をレビューし、JDK 25 で検証します。

```bash
cd omni-backend
./mvnw clean install -pl omni-inventory -am
```

`@RequestBody` がある新サービスは `XssConfigProvider` を実装します。Servlet は `omni-common-service` を使い、Gateway の reactive セキュリティをコピーしません。

## 4. CRUD 生成

```bash
npm --prefix tools/omni-cli run dev -- crud plan path/to/spec.yaml
npm --prefix tools/omni-cli run dev -- crud generate path/to/spec.yaml
```

型、API、サービス、永続化、権限、migration、テスト、i18n タスクを生成します。状態機械、データ範囲、冪等性、業務検証は開発者が追加します。

## 5. DB と権限

forward-only Liquibase changeSet、安定 seed、`scripts/sql/seed/auth.sql` 権限、`database/seed/manifest.yaml`、fresh/upgrade の両経路を更新します。`migrate-*.sql` や一時修復スクリプトを正式成果物にしません。

## 6. カスタムプリセット

依存閉包、競合、前後端選択、migration、seed を宣言し、5 プリセット黄金マトリクスとロックを更新します。[カスタムプリセット](../custom-preset-tutorial.jp.md)と[保守説明](../preset-maintenance.jp.md)を参照してください。

## 7. 完了条件

JDK 25 Reactor、lint 0 warning、production build、権限負向きテスト、fresh/upgrade/対象プリセット、四言語文書と画像、臨時成果物ゼロを満たします。

