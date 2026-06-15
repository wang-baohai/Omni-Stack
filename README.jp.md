# Omni-Stack

> Spring Boot 4 + Vue 3 で構築されたマイクロサービススキャフォールディングプラットフォーム。Harness 産業デザインパターンを採用し、AI 支援開発の業界ベストプラクティス基盤を提供します。

**[中文](README.md)** | **[English](README.en.md)**

---

## 特徴

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 — 最新のフルスタック技術
- **Spring Cloud Gateway 5.x** (WebFlux) リアクティブゲートウェイ、Nacos サービスディスカバリと構成管理
- **Sentinel** フロー制御とサーキットブレーカー、**OpenFeign** 宣言的サービス呼び出し
- **マルチプロバイダーソーシャルログイン**: GitHub + Google + Gitee OAuth2 ワンクリックログイン（ストラテジーパターン `OAuth2ProviderHandler` 拡張可能）、フロントエンドに WeChat ログインエントリ予約済み、HMAC-SHA256 state 署名で改ざん防止、初回ログイン時自動ユーザー登録
- **Vue 3.5** + TypeScript 5.9 + Vite 8 + Element Plus 2.14 モダンフロントエンド
- **Pinia 3** 状態管理 + **Vue Router 5** ナビゲーションガード
- **Harness 産業デザインパターン**: 三層ハイトモデル（Architecture → Patterns → Code）、`docs/` ディレクトリにシステム真実を格納
- **AI ネイティブエンジニアリング**: AGENTS.md 実行マニュアル + Skills 行動拡張、AI 支援開発ワークフローに対応
- **3つのユーザー作成パス**: セルフ登録（CAPTCHA + デフォルトロール）、管理者バックエンド作成、ソーシャルログイン初回自動登録
- **Maven Wrapper** 内蔵 — クローン後すぐにビルド可能、システムへの Maven インストール不要

## 技術スタック

| 層 | 技術 | バージョン |
|----|------|-----------|
| JDK | OpenJDK | 25 |
| バックエンド | Spring Boot | 4.0.6 |
| クラウド | Spring Cloud | 2025.1.1 |
| Cloud Alibaba | Spring Cloud Alibaba | 2025.1.0.0 |
| ゲートウェイ | Spring Cloud Gateway Server (WebFlux) | 5.0.1 |
| ディスカバリ / 構成 | Nacos Server | v3.1.1 |
| フロー制御 | Sentinel Dashboard | 1.8.8 |
| フロントエンド | Vue 3 + TypeScript | 3.5.35 / 5.9.3 |
| ビルドツール | Vite 8 (Rolldown) | 8.0.14 |
| UI フレームワーク | Element Plus | 2.14.0 |
| 状態管理 | Pinia | 3.0.4 |
| ルーター | Vue Router | 5.0.7 |
| Node.js | Node.js LTS | >= 22.12.0 |

## プロジェクト構造

```
Omni-Stack/
├── AGENTS.md                        # AI 実行マニュアル（制約 + ビルドコマンド + チェックリスト）
├── docker-compose.yml               # ミドルウェアオーケストレーション（MySQL, Redis, Nacos, Sentinel）
├── docs/                            # システム真実ドキュメント（Architecture + Patterns + Contract）
│   ├── architecture.md                # システム境界、モジュールマップ、データフロー、RBAC 権限システム
│   ├── api-contract.md                # レスポンス形式、エラーコード、ページネーション、命名規則
│   ├── backend-patterns.md            # バックエンド階層化、バリデーション、例外、ログ、セキュリティ、OOP 規約
│   ├── frontend-patterns.md           # フロントエンド構成、API 層、状態管理、権限制御、コンポーネント規約
│   └── core-flows.md                  # ログイン/OAuth2/RBAC 権限フローのエンドツーエンド追跡
├── scripts/
│   └── sql/
│       ├── init-all.sql               # 権威データベース初期化スクリプト（DDL + シードデータ）
│       └── init-nacos.sql           # Nacos v3.1.1 MySQL 永続化初期化スクリプト
├── omni-backend/                    # Maven マルチモジュールバックエンド
│   ├── mvnw / mvnw.cmd                # Maven Wrapper (3.9.16)
│   ├── pom.xml                        # 親 POM（依存関係管理）
│   ├── omni-common/                   # 共有ライブラリ：統一レスポンス、グローバル例外、Jackson 構成
│   ├── omni-auth/                     # 認証サービス：ログイン、キャプチャ、JWT、OAuth2 (ポート 8100)
│   └── omni-gateway/                  # API ゲートウェイ (WebFlux, ポート 8102)
├── omni-frontend/                   # Vue 3 SPA (開発サーバー ポート 3000)
│   ├── package.json
│   ├── vite.config.ts
│   ├── eslint.config.mjs
│   └── src/
│       ├── api/                       # API 層（ドメイン別ファイル分割）
│       ├── stores/                    # Pinia ストア（Composition API スタイル）
│       ├── router/                    # ルート定義 + ナビゲーションガード
│       ├── views/                     # ページコンポーネント
│       ├── layout/                    # アプリシェル（サイドバー + ヘッダー + コンテンツ）
│       ├── types/                     # 共有型定義（ApiResponse, PageResult）
│       └── styles/                    # グローバルスタイル
└── .qoder/
    └── skills/
        └── grill-me/SKILL.md          # AI Skill: デザインストレステスト
```

## アーキテクチャ概要

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   omni-frontend  │────>│   omni-gateway    │────>│    omni-auth     │
│   Vue 3 SPA     │/api │  WebFlux :8102    │lb://│   Spring :8100  │
│   :3000         │────>│  StripPrefix=2    │────>│  Security+OAuth2│
└─────────────────┘     └──────────────────┘     └─────────────────┘
                            │
                    ┌───────┴────────┐
                    │  MySQL :3306   │  永続化ストレージ
                    │  Redis :6379   │  キャッシュ + キャプチャ
                    │  Nacos :8848   │  ディスカバリ + 構成
                    │  Sentinel :8858│  フロー制御
                    └────────────────┘
```

**リクエストフロー**:

```
ブラウザ :3000  --/api/**-->  Vite プロキシ  -->  Gateway :8102  --lb://-->  バックエンドサービス
```

- フロントエンドは Vite 開発サーバー経由で `/api/**` を Gateway にプロキシ
- Gateway ディスカバリロケーターが Nacos 登録サービスのルートを自動作成

## 環境準備

### 必須ソフトウェア

| ソフトウェア | バージョン要件 | 備考 |
|-------------|--------------|------|
| JDK | 25 | `JAVA_HOME` 環境変数の設定が必須 |
| Node.js | >= 22.12.0 | npm 含む |
| Docker Desktop | 安定版 | ミドルウェア（MySQL, Redis, Nacos, Sentinel）の実行に使用 |

> **注意**: Maven Wrapper (3.9.16) が内蔵されています。すべての Maven コマンドは `./mvnw` で実行してください。

### 環境変数

| 変数 | デフォルト値 | 説明 |
|------|------------|------|
| `JAVA_HOME` | - | **必須** — JDK 25 のインストールパス |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos サーバーアドレス |
| `NACOS_NAMESPACE` | (空) | Nacos 名前空間 |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel ダッシュボードアドレス |
| `VITE_API_BASE_URL` | `/api` | フロントエンド API ベース URL |
| `GITHUB_CLIENT_ID` | (内蔵) | GitHub OAuth App の Client ID |
| `GITHUB_CLIENT_SECRET` | (内蔵) | GitHub OAuth App の Client Secret |
| `GITHUB_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/github/callback` | GitHub 認可コールバック URL |
| `GITEE_CLIENT_ID` | (内蔵) | Gitee OAuth App の Client ID |
| `GITEE_CLIENT_SECRET` | (内蔵) | Gitee OAuth App の Client Secret |
| `GITEE_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/gitee/callback` | Gitee 認可コールバック URL |
| `GOOGLE_CLIENT_ID` | (内蔵) | Google Cloud Console OAuth 2.0 クライアントの Client ID |
| `GOOGLE_CLIENT_SECRET` | (内蔵) | Google Cloud Console OAuth 2.0 クライアントの Client Secret |
| `GOOGLE_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/google/callback` | Google 認可コールバック URL |
| `OAUTH2_STATE_SECRET` | (内蔵) | OAuth2 state パラメータの HMAC-SHA256 署名キー、全ソーシャルログインプロバイダーで共有 |

### ソーシャルログイン構成（GitHub / Google / Gitee）

システムは `OAuth2ProviderHandler` ストラテジーパターンを採用しており、各プロバイダーがインターフェースを実装するだけで組み込めます。新しいプロバイダーの追加にコアロジックの変更は不要です。

#### 1. OAuth クライアントの作成

**GitHub**：

1. GitHub にログイン → Settings → Developer settings → [OAuth Apps](https://github.com/settings/developers) → New OAuth App
2. 以下を入力：
   - **Application name**: Omni-Stack（任意の名前）
   - **Homepage URL**: `http://localhost:3000`
   - **Authorization callback URL**: `http://localhost:8100/api/auth/oauth2/github/callback`
3. 作成後に **Client ID** と **Client Secret** をコピー

**Google**：

1. [Google Cloud Console](https://console.cloud.google.com/) にログイン → APIs & Services → Credentials
2. OAuth 2.0 Client ID を作成（アプリケーションタイプは Web application を選択）
3. Authorized redirect URIs に以下を追加：`http://localhost:8100/api/auth/oauth2/google/callback`
4. 作成後に **Client ID** と **Client Secret** をコピー

**Gitee**：

1. Gitee にログイン → 設定 → [サードパーティアプリケーション](https://gitee.com/oauth/applications) → アプリケーション作成
2. 以下を入力：
   - **アプリケーション名**: Omni-Stack（任意の名前）
   - **アプリケーションホームページ**: `http://localhost:3000`
   - **アプリケーションコールバック URL**: `http://localhost:8100/api/auth/oauth2/gitee/callback`
3. 作成後に **Client ID** と **Client Secret** をコピー

#### 2. 認証情報の構成

環境変数を設定するか、`omni-auth/src/main/resources/application.yml` を編集：

```yaml
auth:
  oauth2:
    github:
      client-id: ${GITHUB_CLIENT_ID:あなたのClientID}
      client-secret: ${GITHUB_CLIENT_SECRET:あなたのClientSecret}
      redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/github/callback}
    google:
      client-id: ${GOOGLE_CLIENT_ID:あなたのClientID}
      client-secret: ${GOOGLE_CLIENT_SECRET:あなたのClientSecret}
      redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/google/callback}
    gitee:
      client-id: ${GITEE_CLIENT_ID:あなたのClientID}
      client-secret: ${GITEE_CLIENT_SECRET:あなたのClientSecret}
      redirect-uri: ${GITEE_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/gitee/callback}
    state-secret: ${OAUTH2_STATE_SECRET:あなたのStateSecret}
```

> **注意**：`redirect_uri` は対応する OAuth クライアントで設定したコールバック URL と完全に一致する必要があります。`state-secret` は state パラメータの HMAC-SHA256 署名に使用されるため、ランダムな文字列を設定してください。

#### 3. 使用方法

フロントエンドのログインページで「GitHub」、「Google」、または「Gitee」ボタンをクリックするとソーシャルログインが開始されます。初回ログイン時にローカルユーザーが自動作成されます（ユーザー名形式：GitHub は `gh_{login}`、Google は `go_{email_prefix}`、Gitee は `ge_{login}`）。

## クイックスタート

### ステップ 1: ミドルウェアの起動

```bash
# 全ミドルウェアを一括起動（MySQL, Redis, Nacos, Sentinel）
docker compose up -d

# サービス状態を確認
docker compose ps
```

> バックエンドサービスを起動する前に、Nacos が完全に起動するまで約 30 秒お待ちください。`http://127.0.0.1:8080/` にアクセスして Nacos の起動を確認してください（デフォルト認証情報: nacos/nacos）。
> MySQL コンテナは初回起動時に `scripts/sql/init-all.sql` を自動実行してデータベースを初期化します。

### ステップ 2: バックエンドのビルドと起動

```bash
# JAVA_HOME を設定（Spring Boot 4 プラグインには JDK 17+ が必要）
export JAVA_HOME="/path/to/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"

# 全モジュールをビルド
cd omni-backend
./mvnw clean install

# Auth サービスを起動（ポート 8100）
cd omni-auth
./mvnw spring-boot:run

# Gateway を起動（ポート 8102、新規ターミナルで）
cd omni-gateway
./mvnw spring-boot:run
```

**ビルド順序**: `omni-common` を先にインストールする必要があります。

### ステップ 3: フロントエンドの起動

```bash
cd omni-frontend

# 依存関係をインストール
npm install

# 開発サーバーを起動（ポート 3000、/api を Gateway :8102 に自動プロキシ）
npm run dev
```

### ステップ 4: サービスの確認

| 確認項目 | コマンド / URL | 期待される結果 |
|---------|---------------|---------------|
| フロントエンド | `http://localhost:3000` | ログインページ |
| Gateway ルート | `curl http://localhost:8102/actuator/gateway/routes` | JSON ルート一覧 |
| Nacos コンソール | `http://127.0.0.1:8080/` | Nacos 管理画面 |
| Sentinel コンソール | `http://localhost:8858` | Sentinel ダッシュボード |

**起動順序**: MySQL → Redis → Nacos → Sentinel → バックエンド（Auth, Gateway）→ フロントエンド

## サービスポート

| サービス | ポート | 説明 |
|---------|-------|------|
| フロントエンド開発サーバー | 3000 | Vite 開発サーバー、/api リクエストをプロキシ |
| 認証サービス | 8100 | Spring Security + OAuth2 Authorization Server |
| API ゲートウェイ | 8102 | Spring Cloud Gateway (WebFlux) |
| MySQL | 3306 | メインデータベース（omni_auth） |
| Redis | 6379 | キャプチャキャッシュ |
| Nacos | 8080, 8848 | 管理画面 (8080) + サービスディスカバリと構成管理 (8848) |
| Sentinel | 8858 | フロー制御ダッシュボード |

## モジュール詳細

### omni-common（共有ライブラリ）

全バックエンドモジュールで共有される基盤インフラ。**単独では実行できません**:

| コンポーネント | ファイル | 責任 |
|--------------|---------|------|
| 統一レスポンス | `R<T>` | 全 API が `{ code, message, data }` を返却 |
| ページネーション | `PageResult<T>` | ページネーションレスポンス `{ records, total, size, current, pages }` |
| ビジネス例外 | `BusinessException` | エラーコード付きビジネス例外 |
| 例外ハンドラー | `GlobalExceptionHandler` | 全例外をキャッチし `R<Void>` に変換 |
| Jackson 構成 | `JacksonConfig` | Java 8 日時シリアライゼーション (`yyyy-MM-dd HH:mm:ss`) |
| Web 構成 | `WebMvcConfig` | CORS 設定 |
| ベースエンティティ | `BaseEntity` | 監査フィールド (id, createTime, updateTime, createBy, updateBy) |

> `omni-common` は Spring Boot 自動構成 (`AutoConfiguration.imports`) を使用して Bean を登録します。下流モジュールは手動で `@ComponentScan` を追加する必要がありません。

### omni-auth（認証サービス）

Spring Security 7 + OAuth2 Authorization Server ベースの認証マイクロサービス:

- **ユーザーログイン**: ユーザー名 + パスワード + キャプチャ + マルチテナント、RS256 JWT を発行
- **マルチプロバイダーソーシャルログイン**: `OAuth2ProviderHandler` ストラテジーパターンに基づく拡張可能なソーシャルログインアーキテクチャ、GitHub・Google・Gitee の三つのプロバイダーを接入済み、フロントエンドに WeChat ログインエントリ予約済み。HMAC-SHA256 state 署名で改ざん防止、初回ログイン時にローカルユーザーを自動作成しサードパーティ ID を関連付け（`sys_user_oauth_provider` テーブル）
- **OAuth2 認可**: Authorization Code + PKCE フロー、サードパーティ連携に対応
- **デバイス認可グラント**（RFC 8628）：IoT デバイス、CLI ツールなどブラウザレス環境向けに `omni-device` クライアント経由で認可機能を提供。フロントエンドの `/device` ページでデバイス側の認可リクエストとトークンポーリングをシミュレートし、`/device/verify` ページでユーザーが別のデバイスでスキャンまたはコード入力により認可を完了
- **クライアント管理**: `oauth2_registered_client` の CRUD、動的登録をサポート
- **マルチテナント RBAC**: `tenantId:username` 形式のユーザー解決 + ロール権限ツリー
- **RBAC 権限システム**: 機能権限（動的メニューフィルタリング + `v-permission` ボタンレベル制御 + `@PreAuthorize` API 認可）+ データ権限（MyBatis-Plus `DataPermissionInterceptor` SQL 自動インターセプト、6 レベル dataScope ゼロ侵入フィルタリング）
- **JWT 署名**: RSA キーペア、JWK エンドポイントで Gateway に公開鍵を提供

### omni-gateway（API ゲートウェイ）

Spring Cloud Gateway Server (WebFlux) ベースのリアクティブゲートウェイ:

- ルート転送: Nacos 登録サービスのバックエンドに自動ルーティング（StripPrefix=2）
- サービスディスカバリ: Nacos 登録サービスを自動ルーティング
- 認証フィルター: `AuthFilter`（JWT RS256 署名検証 + claims 抽出 + ID ヘッダー注入）
- CORS 処理: `CorsConfig` によるクロスオリジンリクエスト対応

### omni-frontend（Vue 3 SPA）

| 層 | ディレクトリ | 責任 |
|----|------------|------|
| API | `src/api/` | ドメイン別ファイル、共有 Axios インスタンス、型安全 |
| ストア | `src/stores/` | Pinia Composition API スタイル、ドメイン別ストア |
| ルーター | `src/router/` | 遅延ロードルート + ナビゲーションガード（デフォルト認証必須） |
| ビュー | `src/views/` | ページコンポーネント、SFC 順序: script → template → style。OAuth2 デバイス認可グラントのフロントエンドインタラクション用 `device/` サブディレクトリを含む |
| レイアウト | `src/layout/` | アプリシェル（サイドバー + ヘッダー + コンテンツエリア） |
| 型 | `src/types/` | 共有型定義（ApiResponse, PageResult の単一ソース） |
| スタイル | `src/styles/` | グローバルリセット + レイアウトスタイル |

## RBAC 権限システム

プロジェクトは完全な RBAC 権限モデルを実装しており、機能権限とデータ権限の二つの独立サブシステムに分かれています。詳細な設計は [`docs/architecture.md`](docs/architecture.md) の RBAC Permission System セクションを、エンドツーエンドフローは [`docs/core-flows.md`](docs/core-flows.md) の Flow 5 と Flow 6 を参照してください。

### 機能権限

ユーザーが「何ができるか」を制御する三層防護：

| 層 | メカニズム | 実装 |
|----|-----------|------|
| 動的メニュー | バックエンドがユーザー権限に基づきメニューツリーを再帰フィルタリング | `MenuController` -> `usePermissionStore` -> 動的ルート登録 |
| ボタン制御 | Vue カスタムディレクティブによる DOM 表示/非表示制御 | `v-permission="'system:user:create'"` -> `display:none` |
| API 認可 | Spring Security メソッドレベル権限チェック | `@PreAuthorize("hasAuthority('system:user:create')")` |

### データ権限

MyBatis-Plus `DataPermissionInterceptor` による SQL 自動インターセプト — ビジネスコードへのゼロ侵入で、ユーザーが「どのデータを見られるか」を制御：

| dataScope | 説明 |
|-----------|------|
| `ALL` | 全データ（テナント間） |
| `TENANT` | 自テナントの全データ |
| `DEPT_AND_BELOW` | 自部門および下位部門 |
| `DEPT` | 自部門のみ |
| `CUSTOM` | カスタム部門セット |
| `SELF` | 自分のデータのみ |

**コアフロー**：リクエスト到着 -> `DataScopeResolveFilter` がロールの dataScope を解決（最も寛大なものが優先）-> `DataScopeContext`（ThreadLocal）に書き込み -> `DataPermissionInterceptor` が WHERE 条件を自動追加 -> リクエスト完了時にコンテキストをクリア。

## ユーザー作成

3つのユーザー作成パスをサポートしています。すべてのパスで `USER` デフォルトロール（`data_scope=SELF`、自分のデータのみ閲覧可能）が自動割り当てされます：

| パス | エントリポイント | 認証要件 | テナント | パスワード |
|------|-----------------|---------|---------|-----------|
| セルフ登録 | 登録ページ `/register` | なし（公開） | ユーザーがドロップダウンから選択 | ユーザー設定（BCrypt） |
| 管理者作成 | ユーザー管理ページ | `system:user:create` | 管理者指定 | 管理者設定（BCrypt） |
| ソーシャルログイン | OAuth2 コールバック | なし（サードパーティ認証） | HMAC state パラメータ | なし（ソーシャルのみ） |

詳細フローは [`docs/core-flows.md`](docs/core-flows.md) Flow 7 を参照してください。

## 権限連携モデル

テナント、組織、ロール、機能権限、データ権限の5要素が連携して完全なアクセス制御を実現します：

```
テナント(Tenant) ─── 分離境界：ユーザー名はテナント内で一意、データはデフォルトでテナント別に分離
  │
  ├── ユーザー(User) ─── 1つのテナントに所属、複数のロールを保持可能
  │     │
  │     ├── ロール(Role) ─── ユーザーと権限を結ぶ架け橋
  │     │     ├── 機能権限(Permission) ─── 「何ができるか」を制御（メニュー/ボタン/API）
  │     │     └── データ範囲(DataScope) ─── 「どのデータが見えるか」を制御
  │     │
  │     └── 組織ユニット(OrgUnit) ─── ユーザーの所属部署、データ権限のアンカー
  │
  └── 権限ツリー(Permission Tree) ─── DIRECTORY → MENU → BUTTON → API 4層構造
```

**連携フロー**：

1. **ログイン時**：`(tenantId, username)` でユーザー検索 → ロール読み込み → 権限読み込み → JWT 発行
2. **機能制御**：JWT `scope` クレームに権限コードを格納 → フロントエンド動的メニュー + `v-permission` ボタン非表示 → バックエンド `@PreAuthorize` API 認可
3. **データ制御**：ロールの `data_scope` で可視範囲を決定 → `DataScopeResolveFilter` が最も寛大な範囲を解決 → MyBatis-Plus が WHERE 条件を自動追加
4. **組織連携**：ユーザーの `primaryUnitId` をデータ権限のアンカーとして使用 → `DEPT`/`DEPT_AND_BELOW` 範囲はマテリアライズドパスで階層を検索

## 統一レスポンス形式

全 API は `R<T>` ラッパーを使用します。フロントエンドとバックエンドは厳密な契約一貫性を維持します。詳細は [`docs/api-contract.md`](docs/api-contract.md) を参照してください。

**成功レスポンス**:
```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1, "username": "demo", "email": "demo@example.com" }
}
```

**エラーレスポンス**:
```json
{
  "code": 400,
  "message": "username: Username is required; email: Email is required"
}
```

**ページネーションレスポンス**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{ "id": 1, "username": "demo" }],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

## 開発者ガイド（新メンバー必読）

### 1. コードを書く前にドキュメントを読む

本プロジェクトは **Harness 産業デザインパターン** に従い、システム知識を三層に整理しています:

| 層 | 内容 | 場所 |
|----|------|------|
| Layer 1: Architecture | システム境界、モジュール責任、データフロー、RBAC 権限システム、制約 | `docs/architecture.md` |
| Layer 2: Patterns | バックエンド/フロントエンドコーディングパターン、API 契約、セキュリティ、コアフロー | `docs/backend-patterns.md`、`docs/frontend-patterns.md`、`docs/api-contract.md`、`docs/core-flows.md` |
| Layer 3: Code | 具体的な関数、クラス、コンポーネント実装 | ソースファイル |

**ルール**: コードを変更する前に、対応する `docs/` ドキュメントを確認してください。アーキテクチャや契約が変更される場合は、まず `docs/` を更新してからコードを修正します。

### 2. バックエンド開発規約

- **階層化**: Controller → Service (インターフェース) → ServiceImpl → Repository
- **DI**: `@RequiredArgsConstructor` + `final` フィールド。`@Autowired` フィールド注入は禁止
- **戻り値**: すべての Controller メソッドは `R<T>` を返す
- **例外**: ビジネスエラーは `BusinessException` をスロー。`GlobalExceptionHandler` が統一処理
- **ログ**: `@Slf4j` + パラメータ化プレースホルダー。`System.out.println` は禁止
- **詳細規約**: `docs/backend-patterns.md` を参照

### 3. フロントエンド開発規約

- **API 層**: ドメイン別ファイル（`src/api/user.ts`）、`request.ts` の共有 Axios インスタンスを使用
- **型**: 共有型は `src/types/api.ts` のみ — 重複定義は禁止
- **ストア**: Pinia Composition API スタイル、`use` プレフィックス命名
- **コンポーネント**: SFC 順序 `<script setup>` → `<template>` → `<style scoped>`
- **ルーター**: 遅延ロード + `meta` 宣言 (title, icon, requiresAuth)
- **詳細規約**: `docs/frontend-patterns.md` を参照

### 4. コミット前チェックリスト

```bash
# バックエンドコンパイル確認
cd omni-backend && ./mvnw clean install

# フロントエンドビルド + Lint 確認
cd omni-frontend && npm run build && npm run lint
```

完全なチェックリストは `AGENTS.md` の Completion Checklist セクションを参照してください。

### 5. よくある落とし穴

| 落とし穴 | 原因 | 解決策 |
|---------|------|--------|
| Gateway ルートが読み込まれない | 5.x で構成プレフィックスが変更 | `spring.cloud.gateway.server.webflux` を使用 — AGENTS.md Important Notes を参照 |
| Maven クラスバージョンエラー | JAVA_HOME が JDK 25 を指していない | `JAVA_HOME` を JDK 25 ディレクトリに設定 |
| フロントエンド型不整合 | `ApiResponse` が複数箇所で定義されている | `@/types/api` からのみインポート — 重複定義禁止 |
| Actuator gateway エンドポイント 404 | 明示的な有効化が必要 | `management.endpoint.gateway.enabled: true` を構成 |
| GitHub ソーシャルログインコールバック 404 | OAuth App が未作成または Client ID がプレースホルダー | 上記「ソーシャルログイン構成」に従い GitHub OAuth App を作成し、実認証情報を記入 |
| Google ソーシャルログインコールバック 404 | Google Cloud Console OAuth クライアントが未作成または Client ID がプレースホルダー | 上記「ソーシャルログイン構成」に従い Google Cloud Console で OAuth 2.0 クライアントを作成し、実認証情報を記入 |
| Gitee ソーシャルログインコールバック 404 | Gitee サードパーティアプリケーションが未作成または Client ID がプレースホルダー | 上記「ソーシャルログイン構成」に従い Gitee でサードパーティアプリケーションを作成し、実認証情報を記入 |
| Google ログイン後コールバックページで停止 | データベースに `sys_user_oauth_provider` テーブルが存在しない | `init-all.sql` が実行済みであることを確認。このテーブルは全プロバイダーのバインディングを保存 |
| GitHub ログイン後コールバックページで停止 | データベースに `sys_user_oauth_provider` テーブルが存在しない | `init-all.sql` が実行済みであることを確認（同テーブルを含む）、または手動で作成 |
| Gitee ログイン後コールバックページで停止 | GitHub と同じ — `sys_user_oauth_provider` テーブルが存在しない | `init-all.sql` が実行済みであることを確認。このテーブルは全プロバイダーのバインディングを保存 |
| ソーシャルログイン state 署名検証失敗 | `OAUTH2_STATE_SECRET` が未構成または再起動後に変更 | 固定の `OAUTH2_STATE_SECRET` 環境変数を設定し、署名キーの一貫性を確保 |
| Nacos 再起動後に構成が消える | 組み込み Derby データベース使用、永続化なし | 本プロジェクトの `init-nacos.sql` を使用して MySQL 外部ストレージに切り替え |

## AI ネイティブエンジニアリング実践

本プロジェクトは AI 支援開発ワークフローに対応しています:

- **`AGENTS.md`**: AI 実行マニュアル — ハード制約、実行ルール、完了チェックリストを定義
- **`docs/` ディレクトリ**: システム真実ドキュメント — AI がコードを変更する前にこれらを読んでシステム文脈を理解
- **`.qoder/skills/`**: AI 行動拡張ユニット（例: `/grill-me` デザインストレステスト Skill）

コア原則: **Layer 1 と Layer 2（Architecture + Patterns）を先に定義することで、Layer 3（Code）を AI に全力で高速生産させることができます。**

## ライセンス

[Apache License 2.0](LICENSE)
