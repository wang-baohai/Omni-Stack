# full プリセット：現リポジトリのすべてのコアと業務機能

full プリセットは Omni-Stack の現在機能の全集です。認証と基盤プラットフォーム、ワークフロー、CRM、SRM、購買、資産、メッセージとスケジューリングを同時に有効化します。エンドツーエンド検収、モジュール横断デモ、すべての入口を一度に見たいチームに向きます。リソース制約環境、単一ドメインだけの検証、「コンテナ全部起動」を「業務すべて閉ループ」と誤解する場面には向きません。本ページのコンポーネントは現在の `scaffold/presets/full.yaml`、`scaffold/catalog/modules.yaml`、`preset explain full` の実解析に由来し、実行結果は検証済み/未検証を分けて表記します。

本ページの実行結果はカバレッジ絞り込みの不具合修正前の段階別記録で、現行版の結果ではありません。初期の失敗や未完了は残し、その後の成功で過去の段階に遡って合格を付けません。旧 G7 の通過は現行版の全要件を証明できず、公開時は全適用要件を保持した G6/G7 と必要な G11 で再検証します。

## 誰に向くか

- 認証、テナント、RBAC、ワークフロー、CRM、サプライチェーン、資産の全入口を一度に検証するチーム。
- full で共有データベースを初期化し、他プリセットを原位サブセットとして検証するメンテナ。
- モジュール横断のメニュー、権限、データ範囲、メッセージ/スケジューリング連携を確認する検収担当。

向かないもの：ログインと基盤管理だけ先に通したい（core）、販売のみ（CRM）、サプライヤ/購買/資産のみ（supply-chain）、承認のみ（workflow）。full はリソース占有、起動時間、障害面が最大です。

## 8 個の明示モジュールから 14 個の閉包モジュールへ

`scaffold/presets/full.yaml` の明示モジュール：

```text
crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job
```

`preset explain full` が現在解決する閉包：

```text
platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq
```

増える `srm`、`procurement`、`workflow` は `asset` の依存連鎖（`asset → procurement, srm → workflow → base`）から来ます。`auth` と `platform` は `base` の依存から来ます。つまり `asset` を書けばサプライチェーン全体と承認ランタイムが full に入ります。`crm` は明示追加の独立業務ドメインです。

## 実サービスとポート

14 モジュールは次の 16 Compose サービスに対応します：

| サービス | 元モジュール | 役割 |
|---|---|---|
| `mysql` | mysql | プラットフォームと各業務データ |
| `redis` | redis | キャッシュとトークン関連データ |
| `nacos` | nacos | サービス発見と設定センター |
| `rocketmq-namesrv` | rocketmq | メッセージルーティング |
| `rocketmq-broker` | rocketmq | メッセージ保存と配送 |
| `xxl-job-admin` | xxl-job | スケジューリングセンター |
| `omni-db-migrator` | platform | 一回限りのスキーマ移行とシード初期化 |
| `omni-auth` | auth | ログイン、テナント、RBAC、組織、辞書、監査 |
| `omni-base` | base | 基盤管理、ログ、ワークスペースとタスク API |
| `omni-workflow` | workflow | BPMN モデル、ToDo、承認とプロセスインスタンス |
| `omni-crm` | crm | リード、顧客、連絡先、商談、フォローと販売パイプライン |
| `omni-srm` | srm | サプライヤ、登録審査、評価、リスクとポータル |
| `omni-procurement` | procurement | 資材、申請、承認ルート、RFQ、注文と入荷 |
| `omni-asset` | asset | 資産台帳、カード作成、払出/返却、移転と処分 |
| `omni-gateway` | gateway | 統一入口とルーティング |
| `omni-frontend` | platform | Vue フロントエンド |

catalog は 35 件のポート記録、34 個の一意ポートを宣言します（`8080` は nacos と xxl-job が同時宣言）：

| モジュール | 宣言ポート |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| crm | 8104, 19904, 9904 |
| srm | 8105, 19905, 9905 |
| procurement | 8106, 19906, 9906 |
| asset | 8107, 19907, 9907 |
| nacos | 8080, 8848, 9848 |
| xxl-job | 8080 |
| gateway | 8102, 19902 |
| mysql | 3306 |
| redis | 6379 |
| rocketmq | 9876, 10909, 10911, 10912 |

フロントエンド入口は 3000、業務サービスは CRM 8104、SRM 8105、購買 8106、資産 8107、ワークフロー 8103。`xxl-job` と nacos はともに 8080 を宣言します。本リポジトリのサンプル環境は `OMNI_XXL_JOB_HOST_PORT=18080` でスケジューリングセンターを、`OMNI_ROCKETMQ_NAMESRV_HOST_PORT=19876` で NameServer をマップします（`.env.example` の明示変数を参照）。これらはランダムなオフセットではありません。

## 利点とコスト

利点は全業務ドメインを一度に得られること：同一のテナント、RBAC、データ範囲、監査、ワークフロー、メッセージ、スケジューリングが CRM 販売連鎖とサプライチェーン/資産連鎖をカバーします。モジュール横断のメニュー、権限、データ隔離の検収に向き、他プリセットの共有データベース初期化源にもなります。

コストは最大です：

- catalog の `resourceHints` 合計で最低メモリ 7296 MB、推奨 11776 MB。Quick Start の full 初期提案は 12–16 GiB で、これは準備予算であり実測上限ではありません。ビルドピーク、MySQL/RocketMQ キャッシュ、JVM ヒープが重なります。
- 16 サービス、34 一意ポート、5 業務アプリ、1 メッセージブローカ（NameServer + Broker）、1 スケジューリングセンター。起動、ヘルスチェック、ログ、障害切り分けの面が最大。
- 業務前提が最多：CRM 販売データ、SRM サプライヤ登録審査とポータル、購買の承認ルート/注文/入荷、資産のカード作成と移転/処分ルール、ワークフローのモデル公開と候補者、メッセージ中継とスケジューラ実行。
- 「全コンテナ healthy」はサービス応答のみを示し、業務閉ループの証明ではありません。

## 前提と設定

- **Docker とリソース**：メモリ 12–16 GiB を準備しディスクを確保。3000、8100–8107、3306、6379、8080、8848、9848、9876 の空きを確認（スケジューラと NameServer のホストマップは `.env` 明示変数で照合）。
- **プライベート `.env`**：データベース、Redis、Nacos 認証、ゲートウェイアサーション、`XXL_JOB_ACCESS_TOKEN`、`XXL_JOB_ADMIN_PASSWORD` など。実値をコミットしない。
- **Nacos 設定**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-workflow.yml`、`omni-crm.yml`、`omni-srm.yml`、`omni-procurement.yml`、`omni-asset.yml`、`omni-gateway.yml`。認証有効時は全クライアントが同一資格情報を使います。
- **移行とシード**：`omni-db-migrator` は一回限り。共有 DB 初期化済みなら再インポート、移行再実行、消去をしない。
- **身元**：管理者、`CRM_ADMIN`/`SALES_MANAGER`/`SALES_REP`/`CRM_VIEWER`、`SRM_ADMIN`/`PROCUREMENT_MANAGER`/`PROCUREMENT_STAFF`、`SUPPLIER`、資産とワークフロー関連ロール。サプライヤポータルはポータル関連と招待も必要。
- **承認とメッセージ**：承認ランタイムは `omni-workflow`。Outbox 中継は `xxl-job`、業務イベントは RocketMQ に依存。スケジューラが未準備ならメッセージ書き込みは配送済みではありません。
- **業務データ**：CRM 販売連鎖、サプライヤ登録審査、購買申請/承認/注文/入荷、資産カード作成と移転/処分。コンテナ起動はこれらの前提完了を意味しません。

## 経路 1：ソースから full を起動

### クリーンデータベースの初回インストール

```bash
docker compose --parallel 1 --profile full up -d --build
docker compose ps
```

期待：migrator 実行後に終了、mysql/redis/nacos/rocketmq/xxl-job と 9 アプリサービスが起動、アプリ healthy、フロントエンド 3000 到達可能。この経路は移行とシードを実行するため、空 DB または初期化が明示許可された環境専用です。**本ページではこの経路を実測していません**：検証環境は full 初期化済みの共有 DB を使うため、空 DB 初回インストールは「未検証」表記を保ちます。

### 既存共有環境での原位全量置換

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`：9 アプリのイメージをビルド。migrator 起動なし、インフラ再構築なし。
- `up -d --no-build --no-deps --force-recreate`：9 アプリを原位置換。依存起動なし、移行なし。
- `ps`：実イメージと健全性を照合。healthy は応答のみ。
- `curl`：フロントエンド 200 を確認。トークンなしのゲートウェイ業務ルートは 503 ではなく 401。

full に「閉包外アプリ」はありませんが、実際に登録されたサービス集合で照合してください。「クリーン」のためにインフラ停止やボリューム消去をしない。

## 経路 2：独立 full 工程を生成

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create full --output generated/learning-full
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate full --output generated/learning-full
```

生成物は独自の `scaffold.lock`、Compose、文書、AI 入口を持ちます。生成物に入ってからは独自のビルドコマンドを使い、元リポジトリから生成物の Compose プロジェクトを起動せず、生成物の migrator を共有 DB に実行しないでください。

過去の初期段階：ソース経路は 9 アプリイメージのビルドと原位置換、健全性/フロントエンド 200/実 CAPTCHA ログイン/全ドメインメニュー照合/停止復元まで完了。生成経路は固定位置での生成（G6 14 モジュール/16 サービス）、G7 生成物自己ビルド、交付役割（生成物とキャッシュのサブパスのみ、ソースなし、docker socket なし）自己充足、生成 9 イメージの原位置換と実ログイン/メニュー/ワークスペース検証、その後のリポジトリイメージ復元まで完了。交付ゲート `verify-preset-delivery full` は `automated-delivery-pass`（modules=14）ですが `release_ready=false`：生成物文書の訳文は指定審校待ちです。クリーン空 DB の初回インストールは未実測のままです。

## 初回使用

1. `http://localhost:3000` を開き、開発テナントを選び、`admin` と**今回表示された CAPTCHA** でログインします。古い CAPTCHA の再利用や認証裏口は使いません。
2. Wiki ホームは元のタブに残し、マイワークスペースを別タブで開きます。アプリ一覧と検索はアクセス可能な全メニューを対象にします。一覧から CRM、SRM、購買、資産、ワークフロー、システム/基盤モジュールを新しいタブで開きます。業務画面のメニューと検索は現在のモジュールに限定し、全体のアプリボタンは表示しません。
3. ドメインごとに検証し、全ページを一度に平坦化しない：
   - CRM：リード → フォロー → 変換 → 商談段階 → 受注/失注。
   - サプライチェーン：サプライヤ登録審査/資材 → 申請承認 → 注文 → 入荷 → 資産カード作成。
   - 承認：モデル公開 → 開始 → ToDo 処理 → インスタンス履歴。
   - ポータル：サプライヤポータル身元は**正式招待 → セルフ登録 → エンロール Saga** で取得（今回実測：エンロールは 25 秒以内に COMPLETED、ポータル関連 ACTIVE、再ログイン、`/supplier-portal` の 3 タブ利用可、内部管理側 403）。`supplier1`/`supplier2` のパスワードはシード内にハッシュのみでリポジトリから復元不可。推測も回避もせず、2 つ目のポータル身元は招待/登録/エンロール連鎖を再実行します。
4. メニューは見えても一覧が空なら、先にテナント/データ範囲と業務前提を確認。メニュー欠落はロール権限と `/api/auth/menus` を確認。DB メニューやロールを直接編集しない。
5. メッセージとスケジューリング：`xxl-job` の準備完了後に Outbox 中継を検証。「メッセージ表に記録あり」を「配送済み」の代用にしない。

## 成功基準

- full 閉包の 9 アプリとインフラが healthy、migrator が期待どおり終了。
- フロントエンド 3000 が 200。実ログイン成功、各業務メニューが権限に応じて出る。
- CRM、SRM、購買、資産、ワークフローそれぞれに開ける実入口が最低 1 つ。権限ある身元がシードデータを読める。
- モジュール横断連鎖は実 API 結果で判断。ヘルスチェックは業務閉ループではない。
- 新規プロジェクト、ネットワーク、ボリューム、ポートオフセットなし。共有データを消去せず移行を再実行しない。

**メニュー境界**：メニュー API は「テナント権限ツリー ∩ 現在身元の権限コード」のみ返し、サービス稼働とは無関係です。full 初期化の共有 DB で全業務メニューを見るのは期待どおり。他プリセットへ切替えると、業務権限コードは管理者などのロールに付与されたまま残るためメニューに業務入口が残ります（実測：core ウィンドウ 57 ノード中 36 が業務コード、共有 DB 付与照合と一致）。これは共有データの状態であり、サブセット閉包が全業務を含む証拠ではありません。core のシードには業務権限行がありません。workflow、CRM、supply-chain は未選択業務の権限を除去し、選択した業務の権限は保持します。空 DB の初回インストールでは未選択モジュールのメニューが残らない想定です。サブセット「他メニューなし」の実行時検証は独立空 DB/テナントが必要。共有 DB で権限削除やロール変更による模擬をしない。

## 停止と復元

- 今回起動したアプリだけを停止。mysql/redis/nacos/rocketmq/xxl-job とデータボリュームは保持。
- 復元：登録済み元サービス集合を `docker start`、または明示サービスに `up -d --no-build --no-deps`。`down -v`、ボリューム消去、グローバル prune は禁止。
- 置換失敗時は置換前に記録した旧イメージを同じサービス位置で復元。迂回サービスを起動しない。

## よくある失敗

| 現象 | 最初に確認 | してはいけないこと |
|---|---|---|
| ポート衝突 | 3000/8100–8107/3306/6379/8080/8848/9848/9876 | 閉包変更やランダムなポートオフセット |
| Nacos 認証失敗 | `.env` と Nacos 資格情報の一致 | 認証無効化、サンプルシークレットの書き込み |
| ゲートウェイ 503 | アサーションキー、下流登録、ルート設定 | フロントエンドアドレスだけ変更 |
| 特定の業務メニューが欠落 | ロール権限、`/api/auth/menus`、対象サービス健全性 | DB メニュー/ロールを直接編集 |
| ページは開くが一覧が空 | テナント、データ範囲、業務前提 | 空データを開始失敗とみなす、手作業で行を作成 |
| 承認 ToDo が空 | モデル公開、候補者、ロール、タスク作成 | タスク/候補者テーブルを直接編集 |
| 入荷後に資産カードなし | 検品合格、資産管理フラグ、正整数数量 | 資産カードを直接挿入 |
| メッセージが長期 PENDING | `xxl-job` 中継、RocketMQ、max_retry | PENDING を配送済みと書く |
| 移行が二重実行 | 共有 DB が初期化済みか | DB 消去やシード再インポート |
| Maven ダウンロード `Remote host terminated the handshake` | 企業ネットワークの TLS 変動。同じコマンドを後で再行 | 依存バージョンやリポジトリ座標の変更 |
| `npm ci` ECONNRESET | ネットワーク変動。再行しオフラインキャッシュ保持 | lockfile 削除や lint スキップ |
| コンテナ内ダウンロード全滅（DNS/握手） | ホストプロキシへ `HTTP_PROXY/HTTPS_PROXY` 設定（コンテナ内は `host.docker.internal:<混合ポート>`）、イメージビルドに同名 `--build-arg` | 製品やプリセットのせいにすること |
| 検証コンテナに `docker`/BuildKit がない | docker CLI と buildx プラグインを入れ `DOCKER_BUILDKIT=1` | 生成物をホスト一時ディレクトリへコピーしてビルド |
| コンテナ再構築のたび Maven ローカルリポジトリが再ダウンロード | `-Dmaven.repo.local` を固定検証ボリュームのキャッシュに向ける | イメージにプライベートリポジトリ設定を焼き込む |

## 過去の段階別記録と検証範囲

- **検証済み**：`preset explain full` が 8 明示/14 閉包モジュールを解決。16 サービス、35 件のポート宣言/34 一意ポートは catalog 由来。リソースヒントはモジュール合計 7296/11776 MB。ソース経路の 9 アプリイメージビルド/原位置換/健全性/フロントエンド 200/実ログイン/全ドメインメニュー/停止復元。生成経路の G6/G7、交付役割自己充足、生成 9 イメージの原位置換と実入口検証、リポジトリイメージ復元。交付ゲート `automated-delivery-pass`。Outbox 中継とスケジューラ実行はポータルエンロール Saga（25 秒以内にロール割当 COMPLETED）で実証。
- **未検証**：クリーン空 DB の初回インストール。モジュール横断業務連鎖の深掘りフロー（CRM 変換/受注、サプライチェーン注文入荷カード、承認の多段合議のエンドツーエンド）。公開準備完了（`release_ready=false`、訳文は指定審校待ち）。

続きは[プリセット総覧](presets-overview.jp.md)、[機能ツアー](feature-tour.jp.md)、[アーキテクチャ事実](../architecture.jp.md)へ。
