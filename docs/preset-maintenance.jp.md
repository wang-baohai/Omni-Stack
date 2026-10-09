# プロジェクトプリセット保守ガイド

本ドキュメントはスキャフォールド保守者向けです。`scaffold/catalog/modules.yaml` がモジュール構成の唯一の事実源で、`scaffold/presets/*.yaml` は入口モジュールのみを宣言します。依存マトリクスと README プリセット表は `npm run docs:preset` で生成されます。

## 管理対象ファイル

- `scaffold/catalog/modules.yaml`：モジュール、依存、リソース、権限、データベース、MQ、XXL-JOB、ドキュメント、互換性。
- `scaffold/schemas/module.schema.json`：モジュールマニフェスト Schema。
- `scaffold/schemas/preset.schema.json`：プリセット Schema。
- `scaffold/presets/*.yaml`：5 つの公式プリセット。
- `tools/omni-cli/src/preset-generator.ts`：アトミックコピーと構造化裁断。
- `tools/omni-cli/scripts/preset-golden.mjs`：生成物マトリクスと残留ゲート。
- `scaffold.lock`：生成プロジェクトのバージョンとモジュールスナップショット。

## モジュール追加

1. まずモジュールのコード、マイグレーション、冪等シード、権限、Compose、Gateway、ドキュメントを完成させる。
2. catalog 末尾に実際の依存順でモジュールを登録；`dependencies` は宣言済みモジュールのみを指せる。
3. バックエンドモジュール、フロントエンド view/component/API/i18n、Gateway route、Compose service、changelog、seed、provisioning、Nacos、ポート、MQ、XXL-JOB、ドキュメント、リソース見積もりを漏れなく記述。
4. `optionalModules` は任意の統合を示すだけで、実行時無効化スイッチの代わりにはならない；`conflicts` は双方向または検証ルールで明示的に処理しなければならない。
5. `omni catalog validate` を実行し、存在しないパス、孤立した Compose 依存、重複ポート、未管理の公式モジュールを修正。
6. モジュールを適切なプリセット入口に追加；推移的依存を複製しない。
7. 裁断ユニットテスト、生成プロジェクトのビルド、ランタイムスモークの証跡を追加。

ドメインステートマシン、DataScope テーブルマッピング、子リソース継承、Saga、冪等ルールはビジネスモジュールに属し、汎用プリセットジェネレーターに下ろしてはいけない。

## 公式プリセットの変更

`scaffold/presets/<id>.yaml` の入口モジュールと説明のみを編集する。その後実行：

~~~powershell
Set-Location tools/omni-cli
npm test
npm run test:preset-structure
npm run docs:preset
npm run docs:preset:check
~~~

モジュール集合、デフォルト設定、生成結果に互換性の変化が生じたら `version` を増分：修正は patch、後方互換な機能追加は minor、破壊的な境界変化は major。catalog モジュールバージョンとプリセットバージョンはそれぞれ維持する。

## ゴールデンサンプルとリリースゲート

- PR：`npm run test:preset-smoke`、`core` と 1 つのビジネスプリセットを検証、バックエンドは `clean verify` を使い共有 `.m2` の書き込み衝突を避ける。
- 夜間/リリース：`npm run test:preset-golden`、5 プリセットで `clean install`、フロントエンド ci/lint/build、Compose 構成、残留スキャンを実行。
- ランタイム：各プリセットで db-migrator fresh、実起動、ログイン、メニュー、health、コアフローを実行；`full` は全量 E2E を実行。

残留スキャンは少なくとも Maven、Dockerfile、Compose、Gateway、ページ、コンポーネント、API、i18n、権限、データベース、MQ、モジュール専用ドキュメントをカバーする。

## 失敗、ロールバックと特定

生成は同階層の staging ディレクトリを使い、失敗時は staging を削除するため、目標ディレクトリに未完成品は現れない。既存の非空目標はコピー前に拒否される。

特定の順序：Schema エラー → 依存クロージャ/競合 → catalog パス → 構造化裁断 → Maven/npm → Compose → fresh データベース → ランタイム/E2E。生成後の失敗時はログと `scaffold.lock` を保持し、管理対象ファイルを直接パッチしない；まず源 catalog、テンプレート、裁断器を修正し、新しいディレクトリへ再生成する。

実行済み Liquibase changeSet は決して書き換えない。アプリバージョンをロールバックする際は互換なデータベース構造を保持し、必要に応じて前向き修正 changeSet を追加する；安易な down SQL を使ってはいけない。
