# プロジェクトプリセット クイック選択

プリセットは、現在のリポジトリを削除せず、境界の明確な新しい Omni-Stack プロジェクトを生成します。モジュール、依存関係、ポート、リソースの唯一の情報源は `scaffold/catalog/modules.yaml` です。解決済みの内容は[依存関係マトリクス](preset-dependency-matrix.jp.md)を参照してください。

## 選択基準

| 要件 | プリセット | 境界 |
|---|---|---|
| ログイン、RBAC、組織、辞書、ログ、基本ジョブ | `core` | Workflow と業務ドメインを含まない |
| BPMN、承認、ToDo、プロセスインスタンス | `workflow` | `core` + Workflow |
| 営業、顧客、商談 | `crm` | `core` + CRM、サプライチェーンなし |
| 仕入先、購買、資産の一連の処理 | `supply-chain` | Workflow + SRM + Procurement + Asset |
| リポジトリの全機能 | `full` | CRM、サプライチェーン、資産、全インフラ |

直近の要件を満たす最小プリセットから始めてください。`full` から手動削除するより、後で catalog モジュールを追加する方が安全です。

## コマンド

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

プレビューには依存クロージャ、バックエンド、Compose、ポート、DB、権限ルート、メモリ見積もりが表示されます。出力先は未作成または空である必要があります。`scaffold.lock` に各バージョンが記録されます。

生成後は Maven、`npm ci`、lint/build、`docker compose config --quiet` を実行します。保守者は `test:preset-structure` と `test:preset-smoke`、リリース前は全 5 種の `test:preset-golden` を実行します。fresh migration、実起動、ログイン・メニュー・health・ブラウザ smoke は別途必須です。

`core`、`workflow`、`crm` は RocketMQ と XXL-JOB Admin を起動しません。`supply-chain` の中間依存は自動解決されます。独自構成は[カスタムプリセット](custom-preset-tutorial.jp.md)を参照し、管理対象ファイルを手動削除しないでください。
