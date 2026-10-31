# core プリセット：認証と基盤プラットフォームのみ

core は「まずログイン、テナント、権限を管理でき、業務プロセスは後回しにしたい」場面向けです。価値は機能の多さではなく、閉包が小さく境界が明確なことです。本ページの構成、ポート、リソースは現在のプリセットファイルと catalog から取得し、実行結果は「検証済み/未検証」に分けて示します。構造解析を起動成功として書きません。

本ページの実行結果は、カバレッジ絞り込みの不具合修正前の記録です。メニュー数と審査待ち状態も当時の値です。旧 G7 と納品検査の通過では現行版の全要件を証明できません。現行版は全適用要件を保持した G6/G7 と必要な G11 で再検証します。ビルド、起動、役割分離の証拠は、入力のハッシュが有効な範囲で再利用できます。

## 誰に向くか

- ログイン、テナント、RBAC、組織、辞書、ログを先に検証したいチーム。
- 最小のバックオフィス骨格を持ち、業務ドメインを一つずつ追加する開発者。
- CRM、SRM、購買、資産、ワークフローを含まない独立工程を生成したい人。

承認フロー、販売パイプライン、サプライヤ/購買/資産の連携、スケジューラが必要な定期タスクには向きません。core の閉包にこれらの業務サービスはなく、`xxl-job` スケジューラもありません。

## 明示 5 モジュールから閉包 7 モジュールへ

`scaffold/presets/core.yaml` の明示モジュール：

```text
base, gateway, mysql, redis, nacos
```

`preset explain core` が解決する閉包：

```text
platform, auth, base, nacos, gateway, mysql, redis
```

増える `platform` と `auth` は依存由来です。`base` は `auth` に、`auth` と `nacos` は `platform` に依存します。`auth` を明示していないのにログインと権限サービスがある理由がこれです。

## 実際のサービスとポート

7 モジュールは次の 8 つの Compose サービスに対応します。

| サービス | 由来モジュール | 役割 |
|---|---|---|
| `mysql` | mysql | 業務・基盤データ |
| `redis` | redis | キャッシュ、セッション/トークン関連データ |
| `nacos` | nacos | サービス発見と設定センター |
| `omni-db-migrator` | platform | 一度だけのスキーマ移行とシード初期化 |
| `omni-auth` | auth | ログイン、テナント、RBAC、組織、辞書、監査など |
| `omni-base` | base | 基盤管理、監視、ログ、ワークスペース、タスク API |
| `omni-gateway` | gateway | 統一入口とルーティング |
| `omni-frontend` | platform | Vue フロントエンド |

catalog が宣言するポート（同一モジュールの複数ポートはそのまま列挙し、未宣言の用途は推測しません）：

| モジュール | 宣言ポート |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

フロントエンド入口は platform 宣言の 3000 です。ポートが使用中なら、プリセット閉包を変えて「避ける」のではなく、ポートを解放するか配置文書に従ってホストマッピングを調整します。Nacos 3.x の 9848 は gRPC ポートで標準ポートであり、ランダムな偏移ではありません。

## 前提と設定

core は「ゼロ設定」ではありません。起動前に少なくとも次を用意します。

1. **ローカル Docker**。3000、8100、8101、8102、3306、6379、8080、8848、9848 が空いていることを確認します。
2. **`.env` の非公開設定**：DB、Redis、Nacos 認証、ゲートウェイのアサーションなどの秘密値。実値をコミットせず、サンプル値を利用可能な秘密として扱わないでください。
3. **Nacos 設定**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-gateway.yml`。Nacos 認証有効時は全クライアントが同じ `NACOS_USERNAME` / `NACOS_PASSWORD` を使います。不一致は再起動ループや認証失敗として現れます。
4. **スキーマとシード**：`omni-db-migrator` が実行する一度きりの処理で、常駐サービスではありません。初期化済みの共有 DB を再インポートしたり消去したりしないでください。
5. **テナントと管理者身元**：認証シード由来です。初回ログインではガイドのテナントとシードアカウントを選び、今回表示された CAPTCHA を入力します。

ゲートウェイのアサーションキーは統制された設定からのみ注入します。欠落や下流との不一致があると API は認証/ゲートウェイエラーを返します。業務コードを変える前に設定の出所を確認してください。

## 経路 1：ソースから core を起動

### 初回インストール（クリーン DB）

共有データが未初期化の独立環境：

```bash
docker compose --parallel 1 --profile core up -d --build
docker compose ps
```

期待：`omni-db-migrator` が完了して終了し、mysql/redis/nacos/auth/base/gateway/frontend が起動、`docker compose ps` でアプリが healthy、フロントエンド 3000 に到達可能。この経路は移行とシードを実行するため、空 DB または初期化が明示的に許可された環境だけに使います。**本ページではこの経路を実測していません**：現在の検証環境は full で初期化済みの共有 DB を使うため、空 DB 初回インストールは「未検証」の表記を保ち、共有 DB の結果をその証拠として提示しません。

### 既存共有環境での原位サブセット（本ページで検証した経路）

DB が full で初期化済みなら migrator を再実行しません。今回のソースイメージをビルドし、core 閉包外の業務アプリを停止してから、core アプリだけを置換します。

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-gateway omni-frontend
docker compose -p omni-stack stop omni-workflow omni-crm omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

停止リストは現在の環境に実際に登録されたサービスから記入し、別プリセットを写さないでください。core に workflow、CRM、SRM、購買、資産、xxl-job、RocketMQ は含まれません。mysql/redis/nacos とデータは保持し、`down -v` やボリューム消去は行いません。検証後は登録済みの集合を `up -d` で全サービス復元します。

## 経路 2：独立 core 工程を生成

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create core --output generated/learning-core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate core --output generated/learning-core
```

生成物は独自の `scaffold.lock`、Compose、文書、AI 入口を持ちます。生成物に入ってから独自のビルドコマンドを使い、元リポジトリから生成物の Compose プロジェクトを起動しないでください。生成物の migrator を共有 DB に対して実行しないでください。

過去の記録：G6 構造（7 モジュール/8 サービス）、生成物のバックエンドビルドとフロントエンド build/lint は通過しました。生成物とキャッシュだけを参照し、ソースも Docker socket も持たない納品用実行環境で自己ビルドが通過しました。生成した 4 イメージを同じ環境で置換し、ログイン、メニュー、ワークスペースを確認した後、元のイメージに復元しました。当時の `verify-preset-delivery core` は `automated-delivery-pass`、`release_ready=false` でしたが、旧処理は検証要件を削る場合がありました。この結果で現行 G7 や公開受入れの完了を宣言したり、残件を翻訳審査だけと説明したりしてはいけません。

## 初回アクセスとログイン

1. `http://localhost:3000` を開き、Wiki ホームとログイン入口が読み込めることを確認します。
2. ガイドの開発テナントを選び、シードアカウントと**今回表示された CAPTCHA** を入力します。古い CAPTCHA の再利用や認証裏口は使いません。
3. ログイン後に確認：
   - ホームから「マイワークスペース」を別のブラウザータブで開き、元の Wiki タブは残す。
   - システム管理、ユーザ/ロール/権限、組織、テナント、ログの入口がロールに応じて出る。
   - 基盤管理、辞書、タスク API が `base` 権限に応じて出る。
4. メニューが読み込めない場合は、フロントエンドより先に auth/base/gateway の健全性、Nacos 設定、`/api/auth/menus` を確認します。DB のメニューテーブルを直接編集しないでください。

## 成功基準

- `docker compose ps` で core 閉包のアプリが healthy、migrator が期待どおり終了。
- フロントエンド 3000 が 200 を返し、静的資産が読み込める。
- 実際の CAPTCHA ログインが成功し、メニュー API が現在の身元に見えるノードを返す。
- 認証、RBAC、組織、辞書、ログの入口が使える。
- 新しいプロジェクト、ネットワーク、ボリューム、ポート偏移がない。共有データを消去せず、移行を再生していない。

**メニュー境界**：メニュー API は「テナント権限ツリー ∩ 現在身元の権限コード」だけを返します（`MenuController` が JWT 権限で DIRECTORY/MENU ノードをフィルタ）。どのサービスが稼働しているかとは無関係です。full で初期化した共有 DB では、業務サービスを停止しても業務権限コードが管理者などのロールに付与されたまま残るため、メニューに業務入口が出続けます。実測では core ウィンドウの管理者メニュー 57 ノード中 36 が業務 DIRECTORY/MENU コードで、共有 DB の付与クエリと一致しました。これは共有データの状態であり、core 閉包に業務モジュールが含まれる証拠ではありません。core の裁剪シードは業務権限行を一切含まないため（ソースシードは業務権限 141 行、生成 core は 0）、**core シードで初期化した空 DB の初回インストールには業務入口が出ません**。この結論はメニュー派生機構とシード裁剪で証明され、実行時実測は独立空 DB/独立テナント環境で行います。既存共有 DB で権限を削除したりロールを変えたりして模擬しないでください。

## 停止と復元

- 今回起動したアプリだけを停止：コンテナ名で `docker stop`、または明示した Compose サービスを停止。mysql/redis/nacos とデータボリュームは保持。
- 復元：登録済みの元サービス集合を `docker start`、または明示サービスに `up -d --no-build --no-deps`。`down -v`、ボリューム消去、グローバル prune は禁止。
- 置換失敗時は、置換前に記録した旧イメージを同じサービス位置で復元し、側路サービスを起動しない。

## よくある失敗

| 現象 | 最初に確認 | してはいけないこと |
|---|---|---|
| ポート使用中 | 3000/8100/8101/8102/3306/6379/8080/8848/9848 | プリセット閉包の変更やランダムなポート偏移 |
| Nacos 認証失敗 | `.env` と Nacos の資格情報が一致するか | 認証を無効化、サンプル秘密を書く |
| ゲートウェイ 503/認証失敗 | アサーションキー、下流登録、ルート設定 | フロントエンドの URL だけ変える |
| メニュー読み込み失敗 | auth/base/gateway の健全性、`/api/auth/menus`、Nacos 設定 | DB メニューを直接編集 |
| タスク入口はあるが動かない | core に `xxl-job` が含まれるか（含まれない） | タスクコードの存在をスケジューラ稼働と書く |
| 移行が二重実行 | 共有 DB が初期化済みか | DB 消去やシード再インポート |
| Maven ダウンロード `Remote host terminated the handshake` | 企業ネットワークの TLS 変動。同じコマンドを後で再行 | 依存バージョンやリポジトリ座標の変更 |
| `npm ci` ECONNRESET | ネットワーク変動。再行しオフラインキャッシュを保持 | lockfile 削除や lint スキップ |
| コンテナ内のダウンロードが全滅（DNS/握手） | ホストプロキシへ `HTTP_PROXY/HTTPS_PROXY` を設定（コンテナ内は `host.docker.internal:<混合ポート>`）、イメージビルドには同名 `--build-arg` | 製品やプリセットのせいにすること |
| 検証コンテナに `docker`/BuildKit がない | docker CLI と buildx プラグインを入れ `DOCKER_BUILDKIT=1` | 生成物をホスト一時ディレクトリへコピーしてビルド |
| コンテナ再構築のたび Maven ローカル倉が再ダウンロード | `-Dmaven.repo.local` を固定検証ボリュームのキャッシュに向ける | イメージに私有リポジトリ設定を焼く |

## 検証済みと未検証

- **検証済み**：core 閉包の解決、8 サービス一覧、ポートとリソースヒント、ソースアプリイメージのビルド、同一 `omni-stack` プロジェクトでの auth/base/gateway/frontend 原位置換、サービス健全性、フロントエンド 200、実 CAPTCHA ログイン 200、core ウィンドウのメニューとワークスペース画面（業務残存は共有 DB 授予ドリブンとの結論を含む）、full サービス集合復元後の健全性、生成物 G6/G7、交付役割の自己充足（ソースなし・socket なし）、生成物 4 イメージの原位置換と実入口検証とその後のリポジトリイメージ復元、交付ゲート `automated-delivery-pass`。
- **未検証**：クリーン空 DB の初回インストール、core シードでの「業務メニューなし」の実行時実測（機構とシード裁剪は証明済み、実行時は独立空 DB 環境待ち）、公開準備完了（`release_ready=false`、訳文は指定審校待ち）、定期タスク実行（core にスケジューラなし）。

[プリセット総覧](presets-overview.jp.md)または[初回起動ガイド](../guides/quick-start.jp.md)へ。
