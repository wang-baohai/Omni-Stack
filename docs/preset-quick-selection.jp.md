# プロジェクトプリセット クイック選択

プリセットは Omni-Stack ソースプロジェクトから、境界の明確な新しいプロジェクトディレクトリを生成します。コマンドは現在のリポジトリの内容をその場で削除しません。モジュール、依存関係、ポート、リソース占有の唯一の情報源は `scaffold/catalog/modules.yaml` です。完全な結果は[プリセット依存関係マトリクス](preset-dependency-matrix.jp.md)を参照してください。

## 選択の推奨

| 要件 | 選択 | 含まれる業務境界 |
|---|---|---|
| ログイン、RBAC、組織、辞書、ログ、基本ジョブ | `core` | Workflow と業務ドメインを含まない |
| BPMN、承認、ToDo、プロセスインスタンスが必要 | `workflow` | `core` + Workflow |
| 営業、顧客、商談システムを構築 | `crm` | `core` + CRM、サプライチェーンなし |
| 仕入先、購買、資産のクローズドループを構築 | `supply-chain` | Workflow + SRM + Procurement + Asset |
| 現在のリポジトリの全機能が必要 | `full` | CRM、サプライチェーン、資産、完全なインフラ |

まだ不確かな場合は、直近の要件を満たす最小プリセットから始めてください。後でモジュールを追加する方が、完全なプロジェクトから手動でモジュールを削除するより安全です。

## 使用手順

~~~powershell
Set-Location tools/omni-cli
npm ci
npm run build
node dist/src/cli.js preset list
node dist/src/cli.js preset explain workflow
node dist/src/cli.js preset create workflow --output C:\WorkSpace\my-workflow --dry-run
node dist/src/cli.js preset create workflow --output C:\WorkSpace\my-workflow
node dist/src/cli.js preset validate workflow --output C:\WorkSpace\my-workflow
~~~

生成前に依存クロージャ、バックエンドモジュール、Compose サービス、ポート、データベース、権限ルート、メモリ見積もりを表示します。出力ディレクトリは存在しないか空でなければなりません。生成完了後、`scaffold.lock` にソースバージョン、プリセットバージョン、モジュールバージョン、テンプレートバージョンを記録します。

## 生成後に必ず検証

生成されたプロジェクトでバックエンドビルド、`npm ci`、フロントエンド lint/build、`docker compose config --quiet` を実行します。リポジトリ保守者は次も実行すべきです：

~~~powershell
npm run test:preset-structure
npm run test:preset-smoke
~~~

`test:preset-golden` は 5 種すべてのプリセットの完全なビルドマトリクスを実行し、夜間やリリース前の実行に適します。データベース fresh、実起動、ブラウザ smoke はランタイム受け入れであり、構造チェックで代替できません。

## 重要な境界

- `core`、`workflow`、`crm` は RocketMQ、XXL-JOB Admin を起動しません；関連機能は設定で無効化するか、必要に応じてカスタムプリセットに追加しなければなりません。
- `supply-chain` は依存クロージャにより Workflow、SRM、Procurement を自動的に含み、中間依存をスキップできません。
- カスタム構成は catalog 内の既存モジュールを参照しなければなりません。詳細は[カスタムプリセットチュートリアル](custom-preset-tutorial.jp.md)を参照。
- 現在のリポジトリのインプレース削減はサポートせず、生成プロジェクト内の管理対象ファイルを手動で削除しないでください。
