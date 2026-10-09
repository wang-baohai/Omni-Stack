# カスタムプリセット チュートリアル

カスタムプリセットは、5 つの公式プリセット以外に catalog 既知のモジュールを組み合わせるのに適します。公式プリセットと同じ Schema、依存クロージャ、競合検査、アトミック生成、残留検査を使用します。

## 1. YAML を書く

例えばサプライヤーとワークフローの機能だけが必要な場合：

~~~yaml
id: supplier-workspace
version: "1.0.0"
displayName: サプライヤーワークスペース
description: コアプラットフォーム、ワークフロー、サプライヤー管理。
modules: [srm, gateway, mysql, redis, nacos]
~~~

`modules` には入口モジュールだけを書きます。`srm` は `workflow → base → auth → platform` を自動的に取り込みます；ジェネレーターは依存と無関係な Procurement や Asset を自動追加しません。

## 2. 検証と説明

~~~powershell
Set-Location tools/omni-cli
npm run build
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset explain C:\WorkSpace\supplier-workspace.yaml
~~~

未知のモジュール、Schema エラー、競合は、いかなるファイルの書き込み前にも失敗します。

## 3. プレビューと生成

~~~powershell
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app --dry-run
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
~~~

生成プロジェクトは正規化された `scaffold/presets/supplier-workspace.yaml` と `scaffold.lock` を保存します。共有 Workflow サービス内の調達、資産のデフォルトプロセスは構成により裁断され、サプライヤー参入プロセスのみを残します。

## 4. 生成プロジェクトの検証

`docker compose config`/起動はプロジェクト直下の `.env` を読む；これが無いと "required variable is missing" で失敗する（exit 1）。まず生成プロジェクトに同梱の `.env.example` をコピーし、**自前で生成したランダムな秘密情報**を填入する（≥ 32 バイト、`JWK_ENCRYPT_KEY` は必ず 32 文字；リポジトリのプレースホルダ既定値や実際の共有秘密の再利用は禁止）。並行隔離時は独立した project/ポート/ボリューム/イメージ名前空間を設定する：

~~~powershell
Set-Location C:\WorkSpace\supplier-app
Copy-Item .env.example .env   # 必須: OMNI_INTERNAL_API_TOKEN / GATEWAY_ASSERTION_SECRET / MYSQL_ROOT_PASSWORD / OMNI_DB_PASSWORD / REDIS_PASSWORD / XXL_JOB_ACCESS_TOKEN|USERNAME|PASSWORD / NACOS_AUTH_TOKEN|IDENTITY_KEY|IDENTITY_VALUE / OAUTH2_STATE_SECRET / JWK_ENCRYPT_KEY; 隔離: COMPOSE_PROJECT_NAME(または -p)、OMNI_MYSQL_VOLUME_NAME、OMNI_*_HOST_PORT、OMNI_IMAGE_NAMESPACE
~~~

続いてバックエンド/フロントをビルドし Compose 設定を検証する：

~~~powershell
Set-Location C:\WorkSpace\supplier-app\omni-backend
$env:JAVA_HOME='C:\APP\JDK25\jdk-25.0.2'
.\mvnw.cmd clean install

Set-Location ..\omni-frontend
npm ci
npm run lint
npm run build

Set-Location ..
docker compose config --quiet
~~~

その後、隔離された Compose project/volume で fresh データベース、起動、ブラウザスモークを実行します。既存の開発スタックとデータベースボリュームを共有しないでください。

## よくある誤り

- `omni-*` の artifact 名を `modules` に書く：ここでは catalog のモジュール ID を使わなければならない。
- すべての推移的依存を明示的にコピーする：不要で、メンテナンスノイズを増やす。
- 現在のリポジトリや非空ディレクトリに出力する：ジェネレーターが拒否する。
- 生成後にモジュールを手動削除する：`scaffold.lock`、シードダイジェスト、ルートを歪めるため、YAML を修正して再生成すべき。
- 任意インフラを自動無効化と理解する：生成後に対応するランタイムスイッチの確認が必要。
- RocketMQ を含めるが `srm` を省く：`auth` のポータルロールコンシューマーは `srm-domain-event` を購読するため、ジェネレーターはダングリングコンシューマーで書き込みを拒否する。MQ を有効にする組み合わせは必ず `srm` も含めること。RocketMQ を含まない場合はこの制約は適用されない（例：`auth`、`workflow`、`crm` のみの組み合わせは正常に生成される）。
