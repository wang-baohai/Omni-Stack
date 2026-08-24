# Omni-Stack

> Spring Boot 4 + Vue 3 ベースのマイクロサービススキャフォールドプラットフォーム。Harness 産業設計モデルを採用し、AI 支援開発のための業界ベストプラクティス基盤を提供します。
>
> **たった1コマンドでフルスタック起動：ミドルウェア + 5つのマイクロサービス + フロントエンド、合計12個の Docker コンテナ。**

**[中文](README.md)** | **[English](README.en.md)** | **[한국어](README.kr.md)**

**GitHub**: https://github.com/wang-baohai/Omni-Stack | **Gitee**: https://gitee.com/wang-baohai/Omni-Stack

**連絡先メール**: wangbaohai1993@gmail.com

---

## 主な特徴

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 + Spring Cloud Alibaba 2025.1.0.0 フルスタック最新技術
- **Docker フルスタックワンクリックデプロイ**：`start.bat` / `./start.sh` 1コマンドで12コンテナ起動（MySQL、Redis、Nacos、RocketMQ、XXL-JOB、4つのバックエンドマイクロサービス、フロントエンド）、詳しくは [Docker デプロイガイド](docs/docker-deployment.jp.md)
- **CRM プリセールス閉ループ**：独立 `omni-crm` サービスがリード、顧客、連絡先、商談、フォローアップ、変換、ダッシュボードをカバー — テナント、RBAC、データスコープ、XSS、監査、Outbox の機能を再利用
- **SRM サプライヤーライフサイクル**：独立 `omni-srm` サービスがサプライヤー准入、審査、等級付け、パフォーマンス評価、リスク管理、ポータルセルフサービス、サプライヤー 360 をカバー — 詳しくは [docs/srm.jp.md](docs/srm.jp.md)
- **マルチプロバイダーソーシャルログイン**：GitHub + Google + Gitee OAuth2 ワンクリックログイン（戦略パターンで拡張可能）、初回ログイン時に自動登録
- **3層 XSS 縦深防御**：Jackson デシリアライザー + Servlet Filter + Gateway セキュリティレスポンスヘッダー、テナント別設定、フロントエンド管理画面も完備
- **Common Starter エコシステム**：8つの自動設定モジュール（mybatis / redis / operlog / job / mqlog / workflow）、新しいサービスは依存関係を追加するだけで機能が使える、設定ゼロ
- **デュアルトラック定期タスク**：XXL-JOB 3.3.1 システムタスク + ユーザータスクのデュアルモード、フロントエンド Cron エディター + 実行ログのリアルタイムプッシュ、詳しくは [docs/scheduling.jp.md](docs/scheduling.jp.md)
- **Transactional Outbox 信頼性メッセージ**：ローカルアウトボックス + XXL-JOB リレー + 指数バックオフリトライ + デッドレター管理、詳しくは [docs/mq-reliability.jp.md](docs/mq-reliability.jp.md)
- **ビジュアル BPMN ワークフロー**：Flowable 7.x エンジン、フロントエンドのドラッグ&ドロップモデリング + デュアルバージョン管理 + マルチインスタンス会籤 + 動的候補者解決、詳しくは [docs/workflow.jp.md](docs/workflow.jp.md)
- **完全な RBAC 権限体系**：機能権限（動的メニュー + v-permission + @PreAuthorize）+ データ権限（DataPermissionInterceptor 6段階フィルタリング）、詳しくは [docs/architecture.jp.md](docs/architecture.jp.md)
- **AI ネイティブプロジェクト**：AGENTS.md 実行マニュアル + docs/ システムの真実 + Skills 行動拡張、最初の2層を固め、3層目は AI に高速生産を任せる

## 技術スタック

| レイヤー | 技術 | バージョン |
|------|------|------|
| JDK | OpenJDK | 25 |
| バックエンドフレームワーク | Spring Boot | 4.0.6 |
| マイクロサービスフレームワーク | Spring Cloud + Spring Cloud Alibaba | 2025.1.1 / 2025.1.0.0 |
| API ゲートウェイ | Spring Cloud Gateway Server (WebFlux) | 5.0.1 |
| 登録/設定 | Nacos Server | v3.1.1 |
| フロー制御/サーキットブレーカー | Sentinel Dashboard | 1.8.8 |
| メッセージキュー | Apache RocketMQ | 5.3.2 |
| タスクスケジューリング | XXL-JOB Admin | 3.3.1 |
| ワークフローエンジン | Flowable BPMN | 7.x |
| フロントエンドフレームワーク | Vue 3 + TypeScript | 3.5.35 / 5.9.3 |
| ビルドツール | Vite 8 (Rolldown) | 8.2.1 |
| UI フレームワーク | Element Plus | 2.14.0 |
| 状態管理 | Pinia | 3.0.4 |
| Node.js | Node.js LTS | >= 22.12.0 |

## アーキテクチャ概要

```
                                 ┌─────────────────┐
                                 │    omni-auth     │
                                 │   Spring :8100   │
                                 │  Security+OAuth2 │
                                 └─────────────────┘
                                        ▲
┌─────────────────┐     ┌──────────────────┐
│   omni-frontend  │────>│   omni-gateway    │lb://
│   Vue 3 SPA     │/api │  WebFlux :8102    │────>┌─────────────────┐
│   Nginx :3000   │────>│  StripPrefix=2    │     │    omni-base     │
└─────────────────┘     └──────────────────┘     │   Spring :8101   │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │  omni-workflow   │
                            │                    │  Flowable :8103  │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │    omni-crm      │
                            │                    │   Sales :8104    │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │    omni-srm      │
                            │                    │   SRM :8105      │
                            │                    └─────────────────┘
                    ┌───────┴────────┐
                    │  MySQL :3306   │  永続ストレージ
                    │  Redis :6379   │  キャッシュ + 認証コード
                    │  Nacos :8848   │  サービスディスカバリ + 設定センター
                    │  RocketMQ      │  メッセージキュー（非同期配信）
                    │  XXL-JOB       │  分散タスクスケジューリング
                    └────────────────┘
```

**リクエストフロー**：ブラウザ `:3000` → Nginx リバースプロキシ → Gateway `:8102` → `lb://` → バックエンドサービス

## プロジェクト構成

```
Omni-Stack/
├── AGENTS.md                           # AI 実行マニュアル（ハード制約 + ビルドコマンド + チェックリスト）
├── start.bat / start.sh                # Docker フルスタックワンクリック起動スクリプト
├── stop.bat / stop.sh                  # ワンクリック停止スクリプト
├── docker-compose.yml                  # 12コンテナ フルスタックオーケストレーション
├── docker/
│   ├── backend/Dockerfile              # バックエンド マルチステージビルド（Maven コンパイル + JRE 実行）
│   ├── frontend/Dockerfile             # フロントエンド マルチステージビルド（npm ビルド + Nginx）
│   ├── frontend/nginx.conf             # Nginx リバースプロキシ設定
│   └── rocketmq/broker-docker.conf     # RocketMQ Broker 設定
├── docs/                               # システムの真実ドキュメント（多言語対応の深い技術文書）
│   ├── architecture.md                 #   システム境界、モジュールマップ、データフロー、RBAC 権限体系
│   ├── api-contract.md                 #   レスポンス形式、エラーコード、ページネーション、命名規則
│   ├── backend-patterns.md             #   バックエンド階層化、バリデーション、例外、ログ、セキュリティ権限
│   ├── frontend-patterns.md            #   フロントエンドディレクトリ、API 層、状態管理、権限制御
│   ├── core-flows.md                   #   ログイン/OAuth2/RBAC 権限フローのエンドツーエンド追跡
│   ├── scheduling.md                   #   定期タスクシステム 技術ドキュメント
│   ├── workflow.md                     #   ワークフローエンジン 技術ドキュメント
│   ├── mq-reliability.md              #   信頼性メッセージ送信 技術ドキュメント
│   ├── crm.md                          #   CRM プリセールス システムトゥルース（Harness ドキュメント）
│   ├── srm.md                          #   SRM サプライヤー関係管理 システムトゥルース（Harness ドキュメント）
│   ├── design/srm-design.md            #   SRM MVP 設計および実装ベースライン
│   └── docker-deployment.md            #   Docker フルスタックデプロイ 詳細ガイド
├── scripts/sql/                        # データベース初期化スクリプト
│   ├── init-all.sql                    #   権威 DDL + シードデータ
│   ├── init-nacos.sql                  #   Nacos MySQL 永続化
│   └── init-xxl-job.sql               #   XXL-JOB データベース
├── omni-backend/                       # Maven マルチモジュール バックエンド
│   ├── omni-common-core/               #   純粋 POJO：R<T>, PageResult, XSS SPI
│   ├── omni-common/                    #   Web 自動設定：Jackson, CORS, XSS Filter
│   ├── omni-common-mybatis/            #   MyBatis-Plus Starter
│   ├── omni-common-redis/              #   ブロッキング Redis Starter
│   ├── omni-common-redis-reactive/     #   リアクティブ Redis Starter（Gateway 専用）
│   ├── omni-common-operlog/            #   操作ログ Starter
│   ├── omni-common-job/                #   定期タスク Starter
│   ├── omni-common-mqlog/              #   MQ メッセージ信頼性 Starter
│   ├── omni-common-workflow/           #   ワークフロー Starter
│   ├── omni-auth/                      #   認証サービス (8100)
│   ├── omni-base/                      #   基礎データサービス (8101)
│   ├── omni-workflow/                  #   ワークフローエンジンサービス (8103)
│   ├── omni-crm/                       #   CRM プリセールス閉ループサービス (8104)
│   ├── omni-srm/                       #   SRM サプライヤー関係管理サービス (8105)
│   └── omni-gateway/                   #   API ゲートウェイ (8102)
└── omni-frontend/                      # Vue 3 SPA (3000)
```

## Docker ワンクリックデプロイ（推奨）

1コマンドで全コンテナを起動：ミドルウェア（MySQL、Redis、Nacos、RocketMQ、XXL-JOB）+ 6つのバックエンドマイクロサービス + フロントエンド。

### 前提条件

| ソフトウェア | バージョン要件 | 説明 |
|------|---------|------|
| Docker Desktop | 任意の安定版 | Windows は WSL2 バックエンドが必要 |
| Git | 任意 | プロジェクトのクローン |

> JDK、Node.js、Maven のインストールは不要です。すべて Docker コンテナ内でビルド・実行されます。

### 起動

| プラットフォーム | コマンド |
|------|------|
| Windows | `start.bat` を右クリック → **管理者として実行** |
| Linux / macOS | `./start.sh` |

スクリプトが自動で実行：Docker 検出 → Docker エンジン起動（未起動の場合）→ ポート保護（Windows Hyper-V/WSL2）→ ミドルウェアイメージのプル → アプリケーションイメージのビルド → 全コンテナ起動。

```bash
# 全サービスを起動
./start.sh

# 指定サービスのみ起動（例：ミドルウェアのみ）
./start.sh mysql redis

# サービス状態を確認
docker compose ps

# 全サービスを停止
./stop.sh
```

### サービスポート

| サービス | ポート | 説明 |
|------|------|------|
| **フロントエンド** | **http://localhost:3000** | **アクセス入口、Nginx から Gateway へリバースプロキシ** |
| 認証サービス | http://127.0.0.1:8100 | Spring Security + OAuth2（ループバック診断専用） |
| 基礎データサービス | http://127.0.0.1:8101 | ディクショナリ/組織/ユーザー/ログ/タスク（ループバック診断専用） |
| API ゲートウェイ | http://localhost:8102 | Spring Cloud Gateway (WebFlux) |
| ワークフローエンジン | http://127.0.0.1:8103 | Flowable BPMN（ループバック診断専用） |
| CRM サービス | http://127.0.0.1:8104 | リード、顧客、商談、フォローアップ |
| SRM サービス | http://127.0.0.1:8105 | サプライヤー、ポータル、評価、リスク |
| Procurement サービス | http://127.0.0.1:8106 | 購買申請、見積、発注、入荷 |
| Asset サービス | http://127.0.0.1:8107 | 資産台帳、移管、廃棄 |
| MySQL | 127.0.0.1:13306 | `root` + `.env` の `MYSQL_ROOT_PASSWORD` |
| Redis | 127.0.0.1:6379 | `.env` の `REDIS_PASSWORD` |
| Nacos コンソール | http://127.0.0.1:8080 | 認証情報は `.env` から注入 |
| XXL-JOB スケジューリングセンター | http://127.0.0.1:18080 | ローカル初期アカウント、実行トークンは `.env` から注入 |
| RocketMQ NameServer | localhost:19876 | ホストマッピングポート（コンテナ内は 9876） |

バックエンドの直接アドレスはローカル開発・診断専用です。本番環境では Frontend と Gateway だけを公開し、下流サービスはプライベートネットワーク内に保持してください。

### 動作確認

```bash
# 1. フロントエンドにアクセス
open http://localhost:3000

# 2. 認証コード API を確認
curl http://localhost:3000/api/auth/captcha

# 3. 全コンテナの状態を確認
docker compose ps
```

ローカルデモ用シードには初回連携専用の `admin` / `admin123` が含まれます。初回ログイン直後に変更し、本番環境ではリポジトリのシード認証情報を使用しないでください。テナント作成時は初期管理者パスワードを明示指定する必要があり、バックエンドは共通の既定パスワードを生成しません。

### よくある問題

| 問題 | 原因 | 解決策 |
|------|------|---------|
| イメージのプルに失敗 | 国内ネットワークの問題 | Docker ミラーを設定：`"registry-mirrors": ["https://docker.1ms.run"]` |
| ポートバインドに失敗 (Windows) | Hyper-V/WSL2 のポート予約と競合 | `start.bat` がポート保護を自動処理、管理者権限で実行が必要 |
| RocketMQ ポート 9876 の競合 | Windows Hyper-V の予約ポート範囲 | ホストマッピングを 19876 に変更済み、コンテナ内は 9876 のまま |
| 502 Bad Gateway | Nginx リバースプロキシのポート設定ミス | nginx.conf の proxy_pass にコンテナ内部ポート `8080` を使用しているか確認（ホストポート `8102` ではない） |
| Nacos の起動に失敗 | ヘルスチェックエンドポイントの変更 | Nacos v3.1.1 は `GET /nacos/` を使用（`/nacos/actuator/health` ではない） |
| ビルドタイムアウト | Maven 依存関係のダウンロードが遅い | バックエンド Dockerfile にアリババクラウド Maven ミラーを内蔵済み |

> 詳細なトラブルシューティングガイドは [docs/docker-deployment.jp.md](docs/docker-deployment.jp.md) を参照

## ローカル開発

デバッグやコード修正が必要な場面向け。ミドルウェアは Docker、バックエンドとフロントエンドはローカルで実行します。

### 前提条件

| ソフトウェア | バージョン | 説明 |
|------|------|------|
| JDK | 25 | `JAVA_HOME` の設定が必須 |
| Node.js | >= 22.12.0 | npm 含む |
| Docker Desktop | 任意 | ミドルウェアのみ実行 |

### 手順

```bash
# 1. ミドルウェアを起動（ミドルウェアのみ、アプリケーションコンテナは起動しない）
./start.sh mysql redis nacos rocketmq-namesrv rocketmq-broker xxl-job-admin

# Nacos の準備を待つ（約30秒）、http://localhost:8080 で確認

# 2. バックエンドをビルドして起動
export JAVA_HOME="/path/to/jdk-25"
cd omni-backend && ./mvnw clean install
cd omni-auth && ./mvnw spring-boot:run       # ポート 8100（新しいターミナルで続行）
cd omni-base && ./mvnw spring-boot:run        # ポート 8101
cd omni-gateway && ./mvnw spring-boot:run     # ポート 8102
cd omni-workflow && ./mvnw spring-boot:run    # ポート 8103

# 3. フロントエンドを起動
cd omni-frontend && npm install && npm run dev  # ポート 3000
```

> Maven Wrapper が内蔵済み（3.9.16）、Maven のグローバルインストールは不要です。ビルド順序は Maven reactor が自動解決します。

### ソーシャルログイン設定

GitHub、Google、Gitee の3つの OAuth2 プロバイダーをサポートしています。認証情報は `application-local.yml`（`.gitignore` で除外済み）に設定します。詳しくは [docs/core-flows.jp.md](docs/core-flows.jp.md) を参照してください。

## 機能概要

### 認証とログイン

| ログインページ | 登録ページ |
|--------|--------|
| ![ログインページ](docs/images/login.png) | ![登録ページ](docs/images/register.png) |

| データダッシュボード | ソーシャルログイン |
|----------|----------|
| ![データダッシュボード](docs/images/dashboard.png) | ![ソーシャルログイン](docs/images/social-login-buttons.png) |

| 認可同意 | デバイスコードログイン |
|----------|------------|
| ![認可同意](docs/images/social-consent.png) | ![デバイスコードログイン](docs/images/social-device-init.png) |

| デバイスコード検証 | |
|------------|--|
| ![デバイスコード検証](docs/images/social-device-verify.png) | |

### システム管理

| ユーザー管理 | ディクショナリ管理 |
|----------|----------|
| ![ユーザー管理](docs/images/system-user.png) | ![ディクショナリ管理](docs/images/system-dict.png) |

| XSS防御設定 | |
|--------------|--|
| ![XSS防御設定](docs/images/system-xss.png) | |

### 定時タスク

| システムタスク | マイタスク |
|----------|----------|
| ![システムタスク](docs/images/job-system.png) | ![マイタスク](docs/images/job-workspace.png) |

### 運用監視

| 操作ログ | MQメッセージログ |
|----------|-------------|
| ![操作ログ](docs/images/monitor-operlog.png) | ![MQメッセージログ](docs/images/monitor-mqmessage.png) |

### ワークフロー

| BPMNデザイナー | 承認フロー |
|-------------|----------|
| ![BPMNデザイナー](docs/images/workflow-designer.png) | ![承認フロー](docs/images/workflow-approval.png) |

### CRM 営業管理

CRM モジュールは、プリセールスの全プロセスをカバーします：リード獲得 → フォローアップ → 顧客作成 → 商談推進 → 受注/失注。6 層のセキュリティ防御（Gateway JWT → テナント検証 → 機能権限 → データスコープ → SQL 傍受 → 行レベル認可）によりマルチテナントのデータ分離を保証します。詳しくは [CRM システムトゥルース](docs/crm.jp.md) を参照してください。

| 営業ダッシュボード | リード管理 |
|-------------------|-----------|
| ![営業ダッシュボード](docs/images/crm-overview.png) | ![リード管理](docs/images/crm-lead-list.png) |
| 統計カード + 売上ファネル + フォローアップ一覧で全体像を一望 | リード一覧は検索・フィルタ・分配・一括操作に対応 |

| リード変換 | 顧客管理 |
|-----------|----------|
| ![リード変換](docs/images/crm-lead-convert.png) | ![顧客管理](docs/images/crm-customer-list.png) |
| 適格リードをワンクリックで顧客 + 連絡先 + 商談に変換、行ロックで冪等性を保証 | 顧客一覧は移管・ステータス変更・ブラックリスト管理に対応 |

| 顧客 360 ビュー | 連絡先管理 |
|----------------|-----------|
| ![顧客360](docs/images/crm-customer-360.png) | ![連絡先管理](docs/images/crm-contact-list.png) |
| 顧客の全次元ビュー：連絡先・商談・フォローアップ活動を一つのドロワーに | 連絡先は顧客に紐付け、主要連絡先のマーキングに対応 |

| 商談管理 | 商談カンバン |
|---------|------------|
| ![商談管理](docs/images/crm-opportunity-list.png) | ![商談カンバン](docs/images/crm-opportunity-board.png) |
| 商談テーブルでステージ・金額・確率・予想クロージング日を表示 | カンバンボードで商談をステージ列ごとに視覚的に管理 |

| フォローアップ活動 | |
|-------------------|--|
| ![フォローアップ活動](docs/images/crm-activity-timeline.png) | |
| 活動一覧で全てのフォローアップを記録、計画/完了/取消のステータスフローに対応 | |

### SRM サプライヤー管理

SRM モジュールはサプライヤーの全ライフサイクルをカバーします：登録/准入 → 審査 → 等級分類 → パフォーマンス評価 → リスク管理 → 淘汰/退出。5 層の信頼チェーン（Gateway JWT → テナント検証 → 機能権限 → データスコープ → SQL 傍受 → 行レベル認可）によりマルチテナントのデータ分離を保証します。詳しくは [SRM システムトゥルース](docs/srm.jp.md) を参照してください。

- **サプライヤー基本データ**：サプライヤー番号自動採番、連絡先、資格、銀行口座（PII マスキング）、カテゴリ/等級自動マッピング
- **准入 & ポータル**：招待トークン（SHA-256 ハッシュ）、Outbox/Saga クロスサービス役割割り当てによるセルフサービス入居
- **パフォーマンス評価**：加重スコアリング（1-5 → パーセンタイル 20-100）、自動等級マッピング（戦略/優先/合格/淘汰）
- **リスク管理**：6 次元指標（財務/コンプライアンス/供給/協力/品質/資格）、総合リスクレベル（GREEN/YELLOW/RED）
- **サプライヤー 360**：ブロック単位の権限制御 — 権限に基づいて異なるユーザーが異なるサプライヤー 360 セクションを閲覧

| サプライヤー概要 | サプライヤー一覧 |
|-----------------|----------------|
| ![サプライヤー概要](docs/images/srm-overview.png) | ![サプライヤー一覧](docs/images/srm-supplier-list.png) |
| 統計カード + サプライヤー分布 + 等級概要、主要指標を一目で把握 | サプライヤー一覧は検索・フィルタ・分配・一括操作に対応、准入審査の起点 |

| パフォーマンス評価 | リスクダッシュボード |
|-------------------|-------------------|
| ![パフォーマンス評価](docs/images/srm-evaluation.png) | ![リスクダッシュボード](docs/images/srm-risk.png) |
| 加重スコアカード（品質/納期/価格/サービス）、百分制スコアと等級の自動マッピング | 6 次元リスク指標の信号機可視化、資格期限アラート、総合リスクレベル |

| 招待管理 | サプライヤーポータル |
|----------|-------------------|
| ![招待管理](docs/images/srm-invite.png) | ![サプライヤーポータル](docs/images/srm-portal.png) |
| 招待トークンの発行と取消、サプライヤー准入の入口を制御 | サプライヤーのセルフサービス入居、企業情報メンテナンス、パフォーマンス閲覧 |

## モジュール概要

### バックエンドマイクロサービス

| モジュール | ポート | 役割 | 詳細ドキュメント |
|------|------|------|---------|
| omni-auth | 8100 | 認証認可：ログイン、JWT、OAuth2、RBAC、XSS 設定管理 | [core-flows.jp.md](docs/core-flows.jp.md) |
| omni-base | 8101 | 基礎データ：ディクショナリ、組織、ユーザー、ログ、定期タスク、MQ メッセージ管理 | [scheduling.jp.md](docs/scheduling.jp.md) |
| omni-workflow | 8103 | ワークフローエンジン：BPMN モデル管理、承認、プロセスインスタンス | [workflow.jp.md](docs/workflow.jp.md) |
| omni-crm | 8104 | CRM：リード、顧客、連絡先、商談、フォローアップ、営業ダッシュボード | [crm.jp.md](docs/crm.jp.md) |
| omni-srm | 8105 | SRM：サプライヤー基本データ、准入、評価、リスク、ポータル、サプライヤー 360 | [srm.jp.md](docs/srm.jp.md) |
| omni-gateway | 8102 | API ゲートウェイ：ルーティング転送、JWT 検証、CORS、セキュリティレスポンスヘッダー | [architecture.jp.md](docs/architecture.jp.md) |

### Common Starter エコシステム（8モジュール）

新しいマイクロサービスは依存関係を追加するだけで機能が使えます。`AutoConfiguration.imports` によるゼロ設定自動構成：

| モジュール | 機能 | 対象サービス |
|------|------|---------|
| `omni-common-core` | 純粋 POJO：`R<T>`、`PageResult`、`BaseEntity`、XSS SPI、UserJobHandler SPI | 全サービス |
| `omni-common` | Web 自動設定：Jackson、CORS、グローバル例外処理、XSS Filter | Servlet サービス |
| `omni-common-mybatis` | MyBatis-Plus + MySQL ドライバー + ページネーションプラグイン | Servlet サービス |
| `omni-common-redis` | ブロッキング Redis + RedisTemplate シリアライズ + RedisUtils | Servlet サービス |
| `omni-common-redis-reactive` | リアクティブ Redis（WebFlux サービス専用、**ブロッキング式との混用不可**） | Gateway |
| `omni-common-operlog` | 操作ログ：@OperLog AOP + RocketMQ 非同期 + エンティティ diff + ホット/コールドテーブルアーカイブ | ビジネスサービス |
| `omni-common-job` | 定期タスク：XXL-JOB 自動構成 + @SystemJobMeta デュアルアノテーションドリブン | ビジネスサービス |
| `omni-common-mqlog` | 信頼性メッセージ：Transactional Outbox + リレー配信 + デッドレター管理 | Servlet サービス |
| `omni-common-workflow` | ワークフロー：Flowable 自動構成 + ApprovalService SPI | ワークフローサービス |

> 詳細な設計は [docs/backend-patterns.jp.md](docs/backend-patterns.jp.md) と [docs/architecture.jp.md](docs/architecture.jp.md) を参照

### フロントエンド

Vue 3 + TypeScript + Vite 8 + Element Plus + Pinia 3、開発規範の詳細は [docs/frontend-patterns.jp.md](docs/frontend-patterns.jp.md) を参照してください。

| レイヤー | ディレクトリ | 役割 |
|------|------|------|
| API 層 | `src/api/` | ドメイン別に分割、統一 Axios インスタンス、型安全 |
| Store 層 | `src/stores/` | Pinia Composition API、1 Store 1 ドメイン |
| ルーティング層 | `src/router/` | 遅延読み込み + ナビゲーションガード |
| ビュー層 | `src/views/` | SFC 順序：script → template → style |
| 型層 | `src/types/` | 共有型の唯一の情報源（重複定義禁止） |

## 開発ガイド（新メンバー必読）

本プロジェクトは **Harness 産業設計モデル** を採用しています。システム知識は3層構造：**Architecture → Patterns → Code**。コードを修正する前に、対応する `docs/` ドキュメントを読んでください。

| ルール | 説明 |
|------|------|
| 依存性注入 | `@RequiredArgsConstructor` + `final` フィールド、`@Autowired` は禁止 |
| 戻り値 | 全 Controller は `R<T>` を返却、ページネーションは `R<PageResult<T>>` を使用 |
| 例外処理 | ビジネス例外は `BusinessException` をスロー、`GlobalExceptionHandler` で統一処理 |
| ログ | `@Slf4j` + パラメータ化プレースホルダー、`System.out.println` は禁止 |
| 権限 | 書き込み操作は必ず `@PreAuthorize` を宣言、形式は `resource:action` |
| フロントエンド型 | `ApiResponse`/`PageResult` は `src/types/api.ts` からのみインポート |
| フロントエンドコンポーネント | SFC 順序：`<script setup>` → `<template>` → `<style scoped>` |

```bash
# コミット前の検証
cd omni-backend && ./mvnw clean install        # バックエンドコンパイル
cd omni-frontend && npm run build && npm run lint  # フロントエンドビルド + Lint
```

> 完全な規約は [docs/backend-patterns.jp.md](docs/backend-patterns.jp.md) と [docs/frontend-patterns.jp.md](docs/frontend-patterns.jp.md) を参照、API 契約は [docs/api-contract.jp.md](docs/api-contract.jp.md) を参照

## よくある落とし穴

| 落とし穴 | 説明 | 解決策 |
|------|------|---------|
| Gateway のルーティングが効かない | 5.x で設定プレフィックスが変更 | `spring.cloud.gateway.server.webflux` を使用 |
| Maven クラスバージョンエラー | JAVA_HOME が JDK 25 を指していない | `JAVA_HOME` を JDK 25 のディレクトリに設定 |
| Redis Starter の混用 | ブロッキング式が WebFlux サービスに導入される | Gateway は `omni-common-redis-reactive` のみ使用可能 |
| Docker 502 エラー | Nginx proxy_pass のポート設定ミス | コンテナ間通信は内部ポート `8080` を使用、ホストマッピングポートではない |
| Docker ポート競合 | Hyper-V/WSL2 の予約ポート | `start.bat` が自動処理、管理者権限での実行が必要 |
| Nacos ヘルスチェック失敗 | v3.1.1 のエンドポイント変更 | `GET /nacos/` を使用、`/nacos/actuator/health` ではない |
| フロントエンドの型不一致 | `ApiResponse` が複数箇所で定義 | `@/types/api` からのみインポート |
| Stream コンシューマー OFFLINE | function.definition のネームスペースエラー | `spring.cloud.function` 配下に配置、`spring.cloud.stream.function` ではない |

## AI ネイティブプロジェクト

- **`AGENTS.md`**：AI 実行マニュアル、ハード制約 + 実行ルール + 完了チェックリスト
- **`docs/`**：システムの真実ドキュメント、AI がコードを修正する前に読んでシステムの文脈を理解
- **`.qoder/skills/`**：AI 行動拡張ユニット（例：`/grill-me` 方案ストレステスト）

> **最初の2層（Architecture + Patterns）を固めれば、3層目（Code）は安心して AI に高速生産を任せられます。**

## ライセンス

[Apache License 2.0](LICENSE)

---

## プロジェクトを応援する

このプロジェクトがお役に立てれば、Star で応援していただけると嬉しいです！

**GitHub**: [https://github.com/wang-baohai/Omni-Stack](https://github.com/wang-baohai/Omni-Stack)
**Gitee**: [https://gitee.com/wang-baohai/Omni-Stack](https://gitee.com/wang-baohai/Omni-Stack)

皆様の [PR](https://github.com/wang-baohai/Omni-Stack/pulls) をお待ちしています！

---

**© Wang Baohai**

<!-- omni:preset-table:start -->
## プロジェクトプリセット

| プリセット | 明示モジュール | 依存クロージャ |
|---|---|---|
| core | base, gateway, mysql, redis, nacos | platform, auth, base, nacos, gateway, mysql, redis |
| crm | crm, gateway, mysql, redis, nacos | platform, auth, base, crm, nacos, gateway, mysql, redis |
| full | crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq |
| supply-chain | asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq |
| workflow | workflow, gateway, mysql, redis, nacos | platform, auth, base, workflow, nacos, gateway, mysql, redis |

[選択ガイド](docs/preset-quick-selection.jp.md) · [プリセット依存関係マトリクス](docs/preset-dependency-matrix.jp.md)
<!-- omni:preset-table:end -->
