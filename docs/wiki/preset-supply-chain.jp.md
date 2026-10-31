# supply-chain プリセット：サプライヤ、購買、資産、承認

supply-chain プリセットは、認証と基盤プラットフォームの上に SRM、購買、資産、ワークフロー、メッセージ、スケジューリングをまとめて追加します。「サプライヤ登録 → 購買申請と承認 → 見積/注文/入荷 → 資産カード作成 → 移動/廃棄」の協働チェーンを対象とし、複数ロールの業務フローを検証するチーム向けです。本リポジトリでも構成が多く、業務前提が重いプリセットでもあります。本ページのモジュール、サービス、ポート、リソースは現在の `scaffold/presets/supply-chain.yaml`、`scaffold/catalog/modules.yaml`、`preset explain supply-chain` の実際の解析結果です。実行結果は検証済み/未検証に分けて示します。

本ページの実行結果はカバレッジ絞り込みの不具合修正前の段階別記録で、現行版の結果ではありません。初期の失敗や未完了は残し、その後の成功で過去の段階に遡って合格を付けません。旧 G7 の通過は現行版の全要件を証明できず、公開時は全適用要件を保持した G6/G7 と必要な G11 で再検証します。

## 誰に向くか

- サプライヤ、購買、資産、承認を一本の実チェーンとしてつなぎたいチーム。
- 内部従業員、購買/承認者、サプライヤポータルの 3 つの身元とデータ範囲を検証したいプラットフォーム。
- MQ とスケジューリングを業務（Outbox 中継、テナント構成、ユーザータスク）に参加させたいチーム。

向かない場面：CRM だけが必要（CRM プリセット）、BPMN 承認だけが必要（workflow プリセット）、リソースが限られログインと RBAC だけを先に検証したい環境。supply-chain は「ページが数枚増える」ものではなく、複数サービス、複数身元域、複数段階の前提データを伴います。

## 明示 7 から閉包 13 へ

`scaffold/presets/supply-chain.yaml` の明示モジュール：

```text
asset, gateway, mysql, redis, nacos, rocketmq, xxl-job
```

`preset explain supply-chain` が現在解析する閉包：

```text
platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq
```

依存チェーンが、`asset` だけでサプライチェーン全体が入る理由を説明します。

```text
asset → procurement, srm
procurement → srm
srm → workflow
workflow → base
gateway → base
base → auth → platform
```

`platform` は移行、フロントエンド、共通機能を提供し、`auth` はログイン/テナント/RBAC、`base` は基盤管理とタスク API、`workflow` は承認ランタイム、`srm → procurement → asset` は業務の主軸です。`rocketmq` と `xxl-job` は明示的に選択されており、無視できる任意項目ではありません。Outbox 中継、テナント構成、ユーザータスクはスケジューリングに依存し、業務イベントはメッセージに依存します。

## 実際のサービスとポート

13 モジュールは 15 の Compose サービスに対応します。

| サービス | 由来モジュール | 役割 |
|---|---|---|
| `mysql` | mysql | 基盤と業務データ |
| `redis` | redis | キャッシュとトークン関連 |
| `nacos` | nacos | サービス発見と設定 |
| `rocketmq-namesrv` | rocketmq | メッセージルーティング |
| `rocketmq-broker` | rocketmq | メッセージ保存と配信 |
| `xxl-job-admin` | xxl-job | スケジューリングセンター |
| `omni-db-migrator` | platform | 一度きりのスキーマ移行とシード |
| `omni-auth` | auth | ログイン、テナント、RBAC、組織、辞書、監査 |
| `omni-base` | base | 基盤管理、ログ、ワークベンチ、タスク API |
| `omni-workflow` | workflow | BPMN モデル、タスク、承認、プロセスインスタンス |
| `omni-srm` | srm | サプライヤ、登録、評価、リスク、ポータル |
| `omni-procurement` | procurement | 資材、申請、承認ルート、見積、注文、入荷 |
| `omni-asset` | asset | 資産台帳、カード作成、受領/返却、移動、廃棄 |
| `omni-gateway` | gateway | 統一入口とルーティング |
| `omni-frontend` | platform | Vue フロントエンド |

catalog は 32 件のポート宣言、31 個の一意ポートを持ちます（`8080` は nacos と xxl-job の両方が宣言）。

| モジュール | 宣言ポート |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| srm | 8105, 19905, 9905 |
| procurement | 8106, 19906, 9906 |
| asset | 8107, 19907, 9907 |
| nacos | 8080, 8848, 9848 |
| xxl-job | 8080 |
| gateway | 8102, 19902 |
| mysql | 3306 |
| redis | 6379 |
| rocketmq | 9876, 10909, 10911, 10912 |

フロントエンド入口は 3000、業務サービスは workflow 8103、SRM 8105、購買 8106、資産 8107 です。`xxl-job` と nacos はどちらも 8080 を宣言します。本リポジトリの例示環境では `OMNI_XXL_JOB_HOST_PORT=18080` でスケジューリングセンター、`OMNI_ROCKETMQ_NAMESRV_HOST_PORT=19876` で NameServer をマッピングします。これは `.env.example` に登録された明示的なマッピングであり、ランダムな偏移ではありません。ポート競合時は解放するか明示変数を調整し、プリセット閉包を変えないでください。

## 利点とコスト

利点は、サプライヤのマスタと登録、購買申請と承認、見積/注文/入荷、資産カード作成とその後の移動/廃棄が、同じテナント、権限、データ範囲、監査を共有することです。MQ とスケジューリングも揃うため、Outbox 中継とユーザータスクを直接検証できます。

コストはリソースと業務前提が多いことです。

- catalog の `resourceHints` を合計すると、最小メモリ目安は 6784 MB、推奨は 11008 MB です。これは計画上の目安で実測上限ではありません。ビルドピーク、MySQL/RocketMQ のキャッシュ、JVM ヒープが加算されます。実測は `docker stats` とビルドログで確認してください。
- ワークフローランタイム、メッセージミドルウェア（NameServer + Broker）、スケジューリングセンターが増え、ポート、資格情報、ヘルスチェック、ログの面が大きく増えます。
- 業務は「ログインすれば動く」ものではありません。サプライヤは登録/招待とポータル関連、購買申請は承認ルートとワークフローモデル、入荷は権限のある注文と品質結果、資産カード作成は数量と資産管理フラグの条件を満たす必要があります。
- 身元は内部ユーザーとサプライヤポータルユーザーに分かれます。ログインできても `SUPPLIER` ロールと有効なポータル関連がなければ ポータルのプロフィールと評価（`srm:portal:profile` / `srm:portal:evaluation`）にアクセスできません。通常の `USER` は登録申請のみ可能です。

## 前提と設定

- **Docker とリソース**：本ページのメモリ/ディスク目安以上を確保し、3000、8100、8101、8102、8103、8105、8106、8107、3306、6379、8080、8848、9848、9876 などの空きを確認します。スケジューリングと NameServer のホストマッピングは `.env` の明示変数で確認します。
- **非公開 `.env`**：データベース、Redis、Nacos 認証、ゲートウェイ断言、`XXL_JOB_ACCESS_TOKEN`、`XXL_JOB_ADMIN_PASSWORD` など。実値をコミットせず、例示値を秘密として扱わないでください。
- **Nacos 設定**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-workflow.yml`、`omni-srm.yml`、`omni-procurement.yml`、`omni-asset.yml`、`omni-gateway.yml`。Nacos 認証を有効にする場合、全クライアントが同じ資格情報を使います。
- **移行とシード**：`omni-db-migrator` が一度だけ実行します。共有データベースが初期化済みなら、再インポート、再実行、クリアをしないでください。
- **内部身元**：管理者と `SRM_ADMIN`、`PROCUREMENT_MANAGER`、`PROCUREMENT_STAFF` などのシードロール。承認はワークフロー候補者とデータ範囲にも依存します。
- **サプライヤポータル身元**：`auth.sql`/`srm.sql` がテナント 1 に `supplier1`、`supplier2` とサプライヤ 1/2 へのポータル関連を提供し、ロールは `SUPPLIER` です。ポータル登録には `inviteToken` とクライアント `requestId` が必要です。有効な招待や関連がない場合、データベースを直接編集してポータルユーザーを「作る」ことはしないでください。
- **承認モデル**：承認ランタイムは `omni-workflow` にあり、購買/資産サービスに Flowable は埋め込みません。承認開始には公開済みモデルと解決可能な候補者/ロールが必要です。
- **メッセージとスケジューリング**：RocketMQ と `xxl-job` は閉包内です。Outbox メッセージには中継タスクが必要で、スケジューラー未準備時に「書き込み済み」を「配信済み」と扱わないでください。
- **業務データ**：サプライヤ登録/評価、資材カタログ、購買申請と承認ルート、注文、入荷、資産数量と品質結果。コンテナ起動はこれらが準備済みであることを意味しません。

### 起動と業務準備は別物

ヘルスチェックの通過はサービスの応答だけを示します。次を証明しません。

- サプライヤが登録済みで、ポータルユーザーが関連付けられている。
- ワークフローモデルが公開され、承認者にタスクが届く。
- 購買申請が承認され、注文を入荷できる。
- 入荷行が「品質合格 + 資産管理 + 正整数数量」を満たし、資産カードが作られる。
- Outbox メッセージが中継で配信済みである。

これらは業務順に準備・検証し、データベース状態の直接変更で迂回しないでください。

## 経路 1：ソースから supply-chain を起動

### クリーンなデータベースへの初回インストール

共有データが未初期化の独立環境で：

```bash
docker compose --parallel 1 --profile supply-chain up -d --build
docker compose ps
```

期待結果：migrator が実行後に終了し、mysql/redis/nacos/rocketmq/xxl-job と auth/base/workflow/srm/procurement/asset/gateway/frontend が起動、アプリケーションサービスが healthy、フロントエンド 3000 にアクセスできます。この経路は移行とシードを実行するため、空のデータベースまたは初期化が明示的に許可された環境だけに使います。

### 既存共有環境での原位置換サブセット

データベースが full で初期化済みなら migrator を再実行しません。8 つのアプリケーションイメージをビルドし、閉包外の CRM を停止して原位置換します。

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack stop omni-crm
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`：閉包内 8 アプリのイメージだけをビルドし、migrator を起動せず、mysql/redis/nacos/rocketmq/xxl-job も再構築しません。
- `docker stop`：閉包外の CRM を停止し、同じ環境に複数の業務実行面を残しません。基盤とデータボリュームは保持します。
- `up -d --no-build --no-deps --force-recreate`：新しいイメージで 8 アプリを原位置換し、依存を起動せず移行も実行しません。
- `ps`：実際のイメージと健康を確認します。healthy はプロセスとヘルスチェックの通過だけを示し、業務チェーンの準備完了ではありません。
- `curl`：フロントエンドの 200 を確認します。トークンなしの業務リクエストが返す 401 は認証拒否だけを示し、サービスとルートの可用性には認可済みリクエストの成功、登録と健全性の証拠も必要です。

停止リストは実際に登録されたサービスで埋めてください。mysql/redis/nacos/rocketmq/xxl-job とデータを保持し、`down -v` やボリュームクリアを実行しないでください。

## 経路 2：独立 supply-chain プロジェクトの生成

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate supply-chain --output generated/learning-supply-chain
```

- `preset create`：supply-chain を独立プロジェクトに削減し、独自の `scaffold.lock`、Compose、ドキュメント、Maven、フロントエンドソースを含めます。
- `preset validate`：生成物のモジュール閉包、Compose、ドキュメント、AI 入口、削減境界を検証し、アプリは起動しません。
- 生成物には独自のビルドコマンドがあります。生成物内で使い、元リポジトリで生成物の Compose を起動したり、生成物 migrator で共有データベースを上書きしたりしないでください。

過去の初期段階：G6 構造 PASS（13 モジュール/15 サービス）。G7 は生成物バックエンド BUILD SUCCESS、フロントエンド build/lint PASS（`VERIFY_PASS modules=13 services=15`）。交付ロール分離（ソース/socket をマウントしない）と生成物イメージの G11 原位置換は未実行です。そのため「生成物イメージが同じ環境で動作した」とは書けません。

## 初回利用（業務順）

1. `http://localhost:3000` を開き、開発テナントを選び、`admin` と**今回表示された CAPTCHA**で内部側にログインします。古い CAPTCHA の再利用や認証バイパスを使わないでください。
2. Wiki ホームを元のタブに残し、マイワークスペースを別タブで開きます。アプリ一覧から SRM、購買、資産、ワークフローをそれぞれ新しいタブで開きます。業務画面には現在のモジュールで許可されたメニューだけを表示し、承認タスクはワークスペースに集約します。
3. 業務を順に準備します：サプライヤ登録/評価 → 資材と承認ルート → 申請提出と承認 → 見積/注文 → 入荷確認 → 資産カード作成 →（任意）移動/廃棄承認。各段階は実際の API が返す ID と状態を基準にします。
4. サプライヤポータル：`supplier1` または `supplier2`（テナント 1、`SUPPLIER` ロール、サプライヤ 1/2 に関連）でポータルにログインします。ポータル登録には有効な `inviteToken` とクライアント `requestId` が必要です。`srm_supplier_portal_user` の直接書き込みや所有変更で迂回しないでください。
5. 承認：タスク一覧が空なら、モデルが公開済みか、現在のユーザーが候補者範囲か、タスクが作成済みかを確認します。タスク/候補者テーブルを直接変更しないでください。
6. 入荷とカード作成：`qualityStatus=PASS`、`assetManaged=true`、正整数の `assetQuantity` を満たす入荷行だけが資産カードを作ります。「カードがない」は業務ルールであり、サービス障害ではありません。
7. メニューがない場合はロール権限と `/api/auth/menus` を、ページは開けるが一覧が空ならテナント/データ範囲と前提データを確認します。先にデータベースを変更しないでください。

## 成功基準

- supply-chain 閉包のアプリサービスが healthy、migrator が期待どおり終了、RocketMQ と xxl-job が healthy。
- フロントエンド 3000 が 200、内部ログイン成功、権限に応じて SRM/購買/資産/ワークフローメニューが表示。
- サプライヤポータル身元がログインでき、自分のポータルデータだけが見える。内部とポータルを別々に検証。
- 実際のチェーンを順に完了：サプライヤ/資材準備 → 申請承認 → 注文 → 入荷 → 資産カード。各段階に実際の API 結果。
- 新しいプロジェクト、ネットワーク、ボリューム、ポート偏移がなく、共有データがクリア/再移行されていない。

**メニュー境界**：共有 full データベースでは CRM サービス停止後も CRM 権限コードが残ることがあります。これは supply-chain 閉包に CRM が含まれる証拠ではありません。supply-chain のみのメニューを検証するには、対応シードで初期化した独立の空データベース/テナントを使ってください。

## 停止と復旧

- 今回起動したアプリだけを停止し、mysql/redis/nacos/rocketmq/xxl-job とデータボリュームを保持します。
- 復旧：登録した元のサービス集合を `docker start` するか、明示サービスに `up -d --no-build --no-deps` を実行します。`down -v`、ボリュームクリア、グローバル prune は行わないでください。
- 置換失敗時は置換前に記録した旧イメージを同じサービス位置で復元し、サイドサービスを起動しないでください。

## よくある失敗

| 症状 | まず確認 | してはいけないこと |
|---|---|---|
| ポート競合 | 3000/8100/8101/8102/8103/8105/8106/8107/3306/6379/8080/8848/9848/9876 | 閉包変更やランダムなポート偏移 |
| Nacos 認証失敗 | `.env` と Nacos 資格情報の一致 | 認証無効化や例示秘密の書き込み |
| スケジューラー利用不可 | `XXL_JOB_*`、18080 マッピング、admin 健康 | Outbox 書き込みを配信済みと扱う |
| ポータルログイン後に権限なし | `SUPPLIER` ロール、ポータル関連、招待状態 | 関連テーブル直接書き込みや所有変更 |
| 承認タスクが空 | モデル公開、候補者、ロール、タスク作成 | タスク/候補者テーブル直接変更 |
| 入荷後に資産カードなし | 品質合格、資産管理フラグ、正整数数量、権限 | 資産カードを直接挿入 |
| メニューなし | ロール権限、`/api/auth/menus`、auth/base 健康 | データベースメニュー直接変更 |
| 移行の二重実行 | 共有データベースが初期化済みか | クリアして再実行、シード再インポート |

## 過去の段階別記録と検証範囲

- **検証済み（ソース経路）**：8 アプリイメージをビルドし同じ `omni-stack` で原位置換。auth/base/workflow/srm/procurement/asset/gateway が healthy、フロントエンド 200。4 つの業務ルートがトークンなしで 401。`admin` の実際の CAPTCHA ログインが 200、メニュー 57 ノード（SRM 11、購買 8、資産 5、ワークフロー 5 の権限コード）。読み取り専用照会でサプライヤ 2、ポータルユーザー 2、資材/申請/注文/資産は 0（空の業務データで失敗ではない）。終了後に 15 コンテナを復元。
- **検証済み（生成経路）**：G6 構造 13 モジュール/15 サービス PASS。G7 生成物バックエンド BUILD SUCCESS、フロントエンド build/lint PASS。
- **未検証**：サプライヤポータルの実ログイン（`supplier1` に既知の慣例パスワードを使うと「パスワードエラー」、それ以上の推測や迂回はしない）、サプライヤ登録 → 承認 → 注文 → 入荷 → カード作成の全チェーン、交付ロール分離と生成物イメージ置換、クリーン空データベース初回インストール、承認/メッセージ配信/スケジューリングの実結果。

> サプライヤポータルのシード関連（`SUPPLIER` ロールとサプライヤ 1/2）は存在しますが、「関連が存在する」は「実際のポータルログインを完了した」ではありません。本ページは制御されたテスト身元を実ログインとして書きません。

[プリセット概要](presets-overview.jp.md)、[SRM 事実ドキュメント](../srm.jp.md)、[購買設計](../design/procurement-design.md) に進んでください。
