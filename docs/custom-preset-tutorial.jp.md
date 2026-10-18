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

上の YAML をソースリポジトリの scaffold/presets/supplier-workspace.yaml として保存します。以下はリポジトリ直下から実行し、CLI のビルドと実行にはローカル Docker のツールコンテナを使います。

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate scaffold/presets/supplier-workspace.yaml
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset explain scaffold/presets/supplier-workspace.yaml
~~~

未知のモジュール、Schema エラー、競合は、いかなるファイルの書き込み前にも失敗します。

## 3. プレビューと生成

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create scaffold/presets/supplier-workspace.yaml --output generated/supplier-app --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create scaffold/presets/supplier-workspace.yaml --output generated/supplier-app
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate scaffold/presets/supplier-workspace.yaml --output generated/supplier-app
~~~

生成プロジェクトは正規化された `scaffold/presets/supplier-workspace.yaml` と `scaffold.lock` を保存します。共有 Workflow サービス内の調達、資産のデフォルトプロセスは構成に従って除外され、仕入先の登録審査プロセスのみを残します。

## 4. 生成プロジェクトの検証

`docker compose config`/起動はプロジェクト直下の `.env` を読む；これが無いと "required variable is missing" で失敗する（exit 1）。まず生成プロジェクトに同梱の `.env.example` をコピーし、**自前で生成したランダムな秘密情報**を入力します（≥ 32 バイト、`JWK_ENCRYPT_KEY` は必ず 32 文字；リポジトリのプレースホルダ既定値や実際の共有秘密の再利用は禁止）。並行隔離時は独立した project/ポート/ボリューム/イメージ名前空間を設定する：

NACOS_AUTH_TOKEN は 32 バイト以上のランダム値を Base64 で符号化します。NACOS_USERNAME は nacos とし、NACOS_PASSWORD をサーバーと全クライアントで一致させます。

~~~powershell
Set-Location generated/supplier-app
Copy-Item .env.example .env   # 必須: OMNI_INTERNAL_API_TOKEN / GATEWAY_ASSERTION_SECRET / MYSQL_ROOT_PASSWORD / OMNI_DB_PASSWORD / REDIS_PASSWORD / XXL_JOB_ACCESS_TOKEN / XXL_JOB_ADMIN_USERNAME / XXL_JOB_ADMIN_PASSWORD / NACOS_PASSWORD / NACOS_AUTH_TOKEN / NACOS_AUTH_IDENTITY_KEY / NACOS_AUTH_IDENTITY_VALUE / OAUTH2_STATE_SECRET / JWK_ENCRYPT_KEY; 隔離: COMPOSE_PROJECT_NAME(または -p)、OMNI_MYSQL_VOLUME_NAME、OMNI_*_HOST_PORT、OMNI_IMAGE_NAMESPACE
~~~

続いてバックエンド/フロントをビルドし Compose 設定を検証する：

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose config --quiet
~~~

その後、隔離された Compose project/volume で fresh データベース、起動、ブラウザスモークを実行します。既存の開発スタックとデータベースボリュームを共有しないでください。

## よくある誤り

- `omni-*` の artifact 名を `modules` に書く：ここでは catalog のモジュール ID を使わなければならない。
- すべての推移的依存を明示的にコピーする：不要で、メンテナンスノイズを増やす。
- 元のリポジトリ直下そのものや、空でないディレクトリを上書きする：ジェネレーターが拒否する。
- 生成後にモジュールを手動削除する：`scaffold.lock`、シードダイジェスト、ルートを歪めるため、YAML を修正して再生成すべき。
- 任意インフラを自動無効化と理解する：生成後に対応するランタイムスイッチの確認が必要。
- RocketMQ を含めるが `srm` を省く：`auth` のポータルロールコンシューマーは `srm-domain-event` を購読するため、ジェネレーターはダングリングコンシューマーで書き込みを拒否する。MQ を有効にする組み合わせは必ず `srm` も含めること。RocketMQ を含まない場合はこの制約は適用されない（例：`auth`、`workflow`、`crm` のみの組み合わせは正常に生成される）。
