# workflow プリセット：基盤プラットフォームと BPMN・承認

workflow は core の認証・基盤に独立したワークフローサービスを加えます。BPMN モデリング、プロセス公開、タスク、承認、プロセスインスタンスが必要で、CRM/SRM/購買/資産の業務はまだ不要な場面向けです。構成は現在のプリセットと catalog の解析結果で、実行結果は「検証済み/未検証」に分けます。

本ページの実行結果はカバレッジ絞り込みの不具合修正前の段階別記録で、現行版の結果ではありません。初期の失敗や未完了は残し、その後の成功で過去の段階に遡って合格を付けません。旧 G7 の通過は現行版の全要件を証明できず、公開時は全適用要件を保持した G6/G7 と必要な G11 で再検証します。

## 明示 5 から閉包 8 へ

`scaffold/presets/workflow.yaml` の明示モジュール：

```text
workflow, gateway, mysql, redis, nacos
```

`preset explain workflow` の閉包：

```text
platform, auth, base, workflow, nacos, gateway, mysql, redis
```

`workflow` は `base`、`gateway` は `base`、`base` は `auth` に依存します。`auth`/`base` を明示しなくてもログイン、権限、組織、辞書、基盤管理は閉包に残ります。

## 実際のサービスとポート

8 モジュールは 9 つの Compose サービスに対応します。

| サービス | 由来 | 役割 |
|---|---|---|
| `mysql` | mysql | 業務、基盤、Flowable データ |
| `redis` | redis | キャッシュ、トークン関連 |
| `nacos` | nacos | サービス発見と設定 |
| `omni-db-migrator` | platform | 一度きりのスキーマ/シード |
| `omni-auth` | auth | ログイン、テナント、RBAC、組織、辞書、監査 |
| `omni-base` | base | 基盤管理、ログ、ワークスペース、タスク API |
| `omni-workflow` | workflow | BPMN、デプロイ、タスク、承認、プロセス |
| `omni-gateway` | gateway | 統一入口とルート |
| `omni-frontend` | platform | Vue フロントエンド |

catalog 宣言ポート：

| モジュール | ポート |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

`omni-workflow` は独立サービス（8103）で、`omni-base`/`omni-auth` に統合しません。Flowable は `omni-workflow` のみに存在し、業務サービスは Flowable 依存を追加しません。

## 前提と設定

- **Docker/ポート**：3000、8100、8101、8102、8103、3306、6379、8080、8848、9848 を空けます。
- **非公開 `.env`**：DB、Redis、Nacos 認証、ゲートウェイのアサーション秘密。実値はコミットしません。
- **Nacos 設定**：`omni-common.yml`、`omni-auth.yml`、`omni-base.yml`、`omni-workflow.yml`、`omni-gateway.yml`。認証情報を一致させます。
- **移行とシード**：`omni-db-migrator` は一度きり。初期化済み共有 DB を再移行・消去しません。
- **BPMN モデル**：`wf_process_model.model_key` はテナント内で一意、BPMN `<process id>` と一致。公開前に `BpmnXmlValidator` を通します。デモモデルはシード由来でも、実際の ID は今回の実行で確認します。
- **候補者とロール**：`omni:assignment` JSON 拡張が唯一の候補者解決入口で、タスク開始時に解析されます。ログインアカウントが自動的に候補者になるわけではなく、ロールとデータ範囲が必要です。

## 経路 1：ソースから workflow を起動

### クリーン DB の初回インストール

```bash
docker compose --parallel 1 --profile workflow up -d --build
docker compose ps
```

期待：migrator が終了し、mysql/redis/nacos/auth/base/workflow/gateway/frontend が起動、アプリが healthy、フロントエンド 3000 に到達。移行とシードを実行するため、空 DB または初期化が許可された環境だけに使います。

### 既存共有環境の原位サブセット

full で初期化済みなら migrator を再実行しません。今回のイメージをビルドし、workflow 閉包外の業務アプリを停止してから置換します。

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-gateway omni-frontend
docker compose -p omni-stack stop omni-crm omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

停止リストは実環境の登録から記入し、mysql/redis/nacos とデータを保持します。`down -v` やボリューム消去は行いません。

## 経路 2：独立 workflow プロジェクトを生成

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create workflow --output generated/learning-workflow
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate workflow --output generated/learning-workflow
```

生成物は独自の `scaffold.lock`、Compose、文書、AI 入口を持ちます。生成物内でビルドし、元リポジトリからその Compose プロジェクトを起動せず、生成物 migrator を共有 DB に実行しません。

過去の初期段階：G6 構造（8 モジュール/9 サービス）と G7 生成物のバックエンド/フロントエンド build・lint は通過。交付役割の隔離と生成物イメージの原位置換は未完了です。

## 初回利用

1. `http://localhost:3000` を開き、開発テナント、シードアカウント、**今回表示された CAPTCHA** を入力します。
2. ホームは Wiki です。マイワークスペースを別タブで開き、承認と個人タスクを一画面で扱います。アプリ一覧からワークフローモジュールを新しいタブで開き、モデルとインスタンスの入口をロールに応じて確認します。
3. 推奨順序：モデル設計/検証 → 公開（一意な model_key）→ プロセス開始 → 候補者がタスク処理 → プロセスと履歴を確認。
4. タスクが空なら、現在の身元が候補者範囲か、プロセスが公開済みか、タスクが作成済みかを確認します。タスク表を直接編集しません。

## 成功基準

- workflow 閉包アプリが healthy、migrator が期待どおり終了。
- フロントエンド 3000 が 200。
- 実際の CAPTCHA ログインが成功し、workflow メニュー/タスク入口が権限に応じて出る。
- 公開済みモデルからプロセスを開始でき、候補者がタスクを処理できる。
- 新しいプロジェクト/ネットワーク/ボリューム/ポート偏移がない。共有データを消去・再移行していない。

**メニュー境界**：full の共有 DB には CRM/SRM/購買/資産の権限コードが残ることがあります。これは共有データの状態で、workflow 閉包がそれらを含む証拠ではありません。「workflow に他の業務メニューがない」ことを検証するには、workflow シードで初期化した独立空 DB/テナントを使います。

## 停止と復元

- 今回起動したアプリだけを停止（コンテナ名または明示サービス）。mysql/redis/nacos とデータを保持。
- 復元：登録済みの元サービス集合を `docker start`、または明示サービスに `up -d --no-build --no-deps`。`down -v`、ボリューム消去、グローバル prune は禁止。
- 置換失敗時は置換前の旧イメージを同じ位置で復元し、側路サービスを起動しない。

## よくある失敗

| 現象 | 先に確認 | してはいけない |
|---|---|---|
| ポート競合 | 8103/3000/8100/8101/8102/3306/6379/8080/8848/9848 | 閉包変更やランダム偏移 |
| Nacos 認証失敗 | `.env` と Nacos 資格情報の一致 | 認証無効化、サンプル秘密 |
| モデル公開失敗 | model_key の一意性、BPMN process id 一致、XML 検証 | DB 直接書き込みで回避 |
| タスクが空 | 候補者拡張、ロール、タスク作成、プロセス公開 | タスク/候補者表の直接編集 |
| ゲートウェイ 503 | アサーションキー、下流登録、ルート | フロントエンド URL だけ変更 |
| メニュー読み込み失敗 | auth/base/workflow/gateway 状態、Nacos、`/api/auth/menus` | メニュー表の直接編集 |

## 過去の段階別記録と検証範囲

- **検証済み**：workflow 閉包 8 モジュール/9 サービス/18 ポート、5 アプリイメージのビルドと原位置換、auth/base/workflow/gateway healthy、フロントエンド 200、生成物 G6/G7 ビルド。
- **未検証**：workflow の実 CAPTCHA ログイン（今回 2 回の目視入力が失敗）、workflow メニュー/タスク/承認の実操作、クリーン DB 初回インストール、業務メニューなし、生成物イメージ置換と交付役割隔離、定期タスク（workflow に xxl-job なし）。

[プリセット総覧](presets-overview.jp.md)または[ワークフロー事実文書](../workflow.jp.md)へ。
