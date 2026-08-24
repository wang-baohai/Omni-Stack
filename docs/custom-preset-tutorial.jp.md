# カスタムプリセット チュートリアル

カスタムプリセットは、catalog に登録済みのモジュールを公式 5 種以外の形で組み合わせます。公式と同じ Schema、依存クロージャ、競合検査、原子生成、残留検査を使用します。

~~~yaml
id: supplier-workspace
version: "1.0.0"
displayName: Supplier Workspace
description: Core platform, workflow, and supplier management.
modules: [srm, gateway, mysql, redis, nacos]
~~~

入口モジュールだけを指定します。`srm` は `workflow → base → auth → platform` を自動解決し、無関係な Procurement/Asset は追加しません。

~~~powershell
Set-Location tools/omni-cli
npm run build
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset explain C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app --dry-run
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
~~~

Schema エラー、未知 ID、競合は書き込み前に失敗します。出力には正規化済み YAML と `scaffold.lock` が保存され、この例では Workflow の仕入先承認だけを残して購買/資産モデルを除去します。

生成後に Maven `clean install`、frontend `npm ci`・lint・build、Compose config を実行し、隔離した project/volume で fresh・起動・browser smoke を確認します。artifactId を module ID として使う、推移依存を列挙する、非空ディレクトリへ出力する、生成後に手動削除する、任意インフラが自動停止すると仮定する、という誤りを避けてください。
