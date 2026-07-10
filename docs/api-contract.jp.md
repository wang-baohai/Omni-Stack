# API 契約

> 本ドキュメントはフロントエンドとバックエンド間の権威ある API 契約を定義します。双方はこれらの構造に準拠しなければなりません。いかなる逸脱もチームの明示的な承認が必要です。  
> ソーシャルログインの完全なフローは [core-flows.jp.md](core-flows.jp.md) を参照してください。データ辞書とワークフローのエンドポイントについてはそれぞれのドキュメントを参照してください。

---

## 目次

- [1. レスポンスラッパー](#1-レスポンスラッパー)
- [2. エラーコード一覧](#2-エラーコード一覧)
- [3. ページネーション契約](#3-ページネーション契約)
- [4. RESTful URL 規約](#4-restful-url-規約)
- [5. Gateway ルーティング設定](#5-gateway-ルーティング設定)
- [6. 命名規約](#6-命名規約)
- [7. 日時フォーマット](#7-日時フォーマット)
- [8. リクエストヘッダー規約](#8-リクエストヘッダー規約)
- [9. 認証ヘッダー](#9-認証ヘッダー)
- [10. ソーシャルログインエンドポイント](#10-ソーシャルログインエンドポイント)
- [11. XSS 設定管理エンドポイント](#11-xss-設定管理エンドポイント)
- [12. Base サービス辞書管理エンドポイント](#12-base-サービス辞書管理エンドポイント)
- [13. API バージョン管理ポリシー](#13-api-バージョン管理ポリシー)
- [14. Null セマンティクス](#14-null-セマンティクス)

---

## 1. レスポンスラッパー

すべての API レスポンスは統一された `R<T>` ラッパーを使用します。

```json
// 成功
{
  "code": 200,
  "message": "success",
  "data": { ... }
}

// 失敗（ビジネスエラー）
{
  "code": 500,
  "message": "操作に失敗しました"
}

// 失敗（バリデーションエラー）
{
  "code": 400,
  "message": "username: ユーザー名は必須です; email: メールアドレスは必須です"
}
```

### バックエンド型：`R<T>`

```java
@Data
public class R<T> implements Serializable {
    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) { ... }
    public static <T> R<T> fail(String message) { ... }
    public static <T> R<T> fail(int code, String message) { ... }
}
```

**配置場所**：`omni-common-core` モジュール、`com.omni.common.core.result.R`。

### フロントエンド型：`ApiResponse<T>`

```typescript
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
```

**正規の配置場所**：`src/types/api.ts`（唯一の信頼できる情報源；他のファイルで重複定義しないでください）。

---

## 2. エラーコード一覧

### 2.1 システムレベルエラーコード

| HTTP ステータスコード | ビジネスコード | シナリオ | 発生条件 | 処理側 |
|------------|--------|------|---------|--------|
| 200 | 200 | 成功 | `R.ok(data)` | — |
| 400 | 400 | パラメータバリデーション失敗 | `MethodArgumentNotValidException` / `BindException` が `GlobalExceptionHandler` に捕捉された場合 | フロントエンドが `message` 内のフィールドエラーを表示 |
| 401 | 401 | 未認証 | Gateway `AuthFilter` が 401 JSON を返却 | フロントエンドがログインページに自動リダイレクト |
| 403 | 403 | 権限不足 | `AccessDeniedException` / `AuthorizationDeniedException` が `GlobalExceptionHandler` に捕捉された場合 | フロントエンドが「権限不足」メッセージを表示 |
| 200 | 404 | リソースが存在しない | `throw new BusinessException(404, "xxxが存在しません")` | フロントエンドがエラーメッセージを表示 |
| 200 | 500 | ビジネス例外 | `BusinessException` が `GlobalExceptionHandler` に捕捉された場合 | フロントエンドがエラーメッセージを表示 |
| 500 | 500 | 不明なシステムエラー | フォールバック `Exception` ハンドラー | フロントエンドが「サーバー内部エラー」を表示 |

### 2.2 ビジネスレベルエラーコード

| ビジネスコード | シナリオ | メッセージ例 |
|--------|------|---------|
| 500 | 認証コード無効/期限切れ | "認証コードが期限切れです" |
| 500 | 認証失敗 | "ユーザー名またはパスワードが正しくありません" |
| 500 | アカウント無効化 | "アカウントが無効化されています" |
| 500 | アカウントロック | "アカウントがロックされています。N 分後に再試行してください" |
| 500 | テナントが存在しない/無効 | "テナントが存在しないか、無効化されています" |
| 400 | 一意性制約違反 | "ユーザー名が既に存在します" / "タスクタイプコードが既に存在します" |
| 404 | リソースが存在しない | "組織ユニットが存在しません" / "辞書データが存在しません" |
| 403 | 権限不足 | "権限が不足しているため、アクセスを拒否します" |

### 2.3 Gateway レベルエラーコード

| HTTP ステータスコード | シナリオ | レスポンス形式 |
|------------|------|---------|
| 401 | JWT 署名無効 | `{"code":401,"message":"Invalid JWT signature","data":null}` |
| 401 | JWT 期限切れ | `{"code":401,"message":"JWT token expired","data":null}` |
| 401 | Token 失効済み | `{"code":401,"message":"Token has been revoked","data":null}` |
| 401 | Authorization ヘッダー欠如 | `{"code":401,"message":"Missing Authorization header","data":null}` |

### 2.4 ソーシャルログインエラーコード

| error パラメータ | 意味 | 発生条件 |
|------------|------|---------|
| `user_denied` | ユーザーが認可を拒否 | サードパーティプラットフォームからのコールバックに `error=access_denied` が含まれる |
| `invalid_callback` | コールバックパラメータ欠如 | code または state が空 |
| `social_login_failed` | ログインフロー異常 | state 検証失敗、サードパーティ API エラー、ユーザー情報取得失敗、ユーザー無効化 |

### 2.5 フロントエンドエラー処理フロー

Axios レスポンスインターセプター（`src/api/request.ts`）は `res.code !== 200` をチェックします：
1. `ElMessage.error(res.message)` でエラーメッセージを表示
2. コードが `401` の場合：`userStore.logout()` を呼び出し `/login` にリダイレクト
3. `Promise.reject(new Error(res.message))` を返却

**HTTP ステータスコード処理**：
- HTTP 401（Gateway JWT 検証失敗）：Axios `onError` インターセプターで捕捉し、Token をクリアしてログインページにリダイレクト
- HTTP 403（権限不足）：`ElMessage.error("権限不足")` を表示して前のページに戻る
- HTTP 400（パラメータバリデーション失敗）：`GlobalExceptionHandler` が返すフィールドレベルのエラーメッセージを表示

---

## 3. ページネーション契約

### バックエンド型：`PageResult<T>`

```java
@Data
public class PageResult<T> implements Serializable {
    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;   // 自動計算: (total + size - 1) / size

    public PageResult(List<T> records, long total, long size, long current) { ... }
}
```

### フロントエンド型：`PageResult<T>`

```typescript
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
```

**正規の配置場所**：`src/types/api.ts`。

### 使用パターン

```java
// バックエンド Controller
@GetMapping("/list")
public R<PageResult<UserVO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
    return R.ok(userService.listUsers(page, size));
}
```

```typescript
// フロントエンド API 呼び出し
export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/auth/user/list?page=${page}&size=${size}`,
  )
}
```

### ページネーションパラメータ規約

| パラメータ | 型 | デフォルト値 | 説明 |
|------|------|--------|------|
| `page` | int | 1 | 現在のページ番号（1 から開始） |
| `size` | int | 10 | 1 ページあたりの件数 |
| `records` | List | — | 現在のページのデータリスト |
| `total` | long | — | 総レコード数 |
| `pages` | long | — | 総ページ数（自動計算） |

---

## 4. RESTful URL 規約

| 操作 | HTTP メソッド | URL パターン | 例 |
|------|-----------|---------|------|
| ID で検索 | GET | `/{resource}/{id}` | `GET /user/1` |
| ページネーションリスト | GET | `/{resource}/list` | `GET /user/list?page=1&size=10` |
| 作成 | POST | `/{resource}` | `POST /user` |
| 更新 | PUT | `/{resource}/{id}` | `PUT /user/1` |
| 削除 | DELETE | `/{resource}/{id}` | `DELETE /user/1` |
| バッチ操作 | POST | `/{resource}/batch` | `POST /user/batch` |

**Gateway パスプレフィックス**：すべてのフロントエンドリクエストは `/api/<service>/<resource>` を使用します（例：`/api/auth/user/list`）。Gateway は `/api/<service>` を除去し（StripPrefix=2）、ダウンストリームサービスは `/<resource>` を受け取ります。

**例外**：Base サービスの `/api/base/**` ルートには StripPrefix フィルターが**ありません**。Base サービスのコントローラーは完全なパスを使用します（例：`@RequestMapping("/api/base/dict/type")`）。

---

## 5. Gateway ルーティング設定

### 5.1 ローカル開発環境ルート

Gateway の `application.yml` 内のルート設定（`spring.cloud.gateway.server.webflux.routes`）：

| ルート ID | パスマッチ | ターゲットサービス | StripPrefix | 説明 |
|---------|---------|---------|-------------|------|
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` | なし | OAuth2 認可サーバーエンドポイント |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` | なし | OpenID Connect ディスカバリーエンドポイント |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` | 2 | Auth サービス REST API |
| `omni-base` | `/api/base/**` | `lb://omni-base` | **なし** | Base サービス（完全なパスを使用） |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` | **なし** | 定期タスク管理 |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` | **なし** | ワークフローエンジン |

### 5.2 Docker デプロイメントルート

Docker デプロイメント時、ルート設定は同じですが、ターゲットサービスの URI は Nacos サービスディスカバリーにより自動解決されます：

| フロントエンドリクエスト | Gateway ルート | ダウンストリーム受信パス | 説明 |
|---------|-------------|-------------|------|
| `GET /api/auth/user/list` | `lb://omni-auth` + StripPrefix=2 | `GET /user/list` | Auth サービスはプレフィックスを除去 |
| `GET /api/base/dict/type/list` | `lb://omni-base` StripPrefix なし | `GET /api/base/dict/type/list` | Base サービスは完全なパスを保持 |
| `POST /api/workflow/model` | `lb://omni-workflow` StripPrefix なし | `POST /api/workflow/model` | Workflow サービスは完全なパスを保持 |
| `GET /api/job/type/list` | `lb://omni-base` StripPrefix なし | `GET /api/job/type/list` | Job ルートは Base サービスへ |

### 5.3 AuthFilter ホワイトリストパス

以下のパスは JWT 検証をスキップします（`AuthFilter` がインターセプトしない）：

```
/api/auth/login          — ログイン
/api/auth/register       — 新規登録
/api/auth/captcha        — CAPTCHA
/api/auth/tenants        — テナント一覧
/api/auth/oauth2/        — ソーシャルログイン
/actuator/               — ヘルスチェック
/oauth2/                 — OAuth2 エンドポイント
/.well-known/            — OIDC ディスカバリー
/login                   — Spring Security ログイン
/error                   — エラーページ
```

---

## 6. 命名規約

### リクエスト/レスポンス DTO

| 型 | サフィックス | 例 |
|------|------|------|
| 作成リクエスト | `CreateXxxRequest` | `CreateUserRequest` |
| 更新リクエスト | `UpdateXxxRequest` | `UpdateUserRequest` |
| ビューオブジェクト | `XxxVO` | `UserVO` |
| クエリパラメータ | `XxxQuery` | `UserQuery` |

DTO は Controller の静的内部クラス（シンプルな場合）または独立ファイル（複雑な場合）として定義できます。

### フィールド命名

- Java フィールド：`lowerCamelCase`（例：`createTime`、`userName`）
- JSON シリアライゼーション：`lowerCamelCase`（Java フィールド名に直接一致）
- URL パスセグメント：`kebab-case` または単一語（例：`/user/list`、`/user/getAllUsers` ではない）

---

## 7. 日時フォーマット

`JacksonConfig.java` で設定：

| Java 型 | JSON 形式 | 例 |
|-----------|----------|------|
| `LocalDateTime` | `yyyy-MM-dd HH:mm:ss` | `2026-05-28 14:30:00` |
| `LocalDate` | `yyyy-MM-dd` | `2026-05-28` |

タイムスタンプは文字列としてシリアライズされ、数値タイムスタンプではありません（`WRITE_DATES_AS_TIMESTAMPS` は無効化されています）。

**設定場所**：`omni-common` モジュールの `JacksonConfig`。`AutoConfiguration.imports` により自動的に有効化されます。`omni-common` に依存するすべてのサービスは、一貫した日時フォーマットを自動的に取得します。

---

## 8. リクエストヘッダー規約

### 8.1 Gateway が注入するリクエストヘッダー

Gateway の `AuthFilter` は JWT 検証成功後、ダウンストリームリクエストに以下のヘッダーを注入します：

| ヘッダー | 型 | 説明 | 例 |
|--------|------|------|------|
| `X-User-Id` | String | ユーザー ID | `"1"` |
| `X-User-Name` | String | ユーザー名 | `"admin"` |
| `X-Tenant-Id` | String | テナント ID | `"1"` |
| `X-User-Roles` | String | カンマ区切りのロールコード | `"SUPER_ADMIN,DEPT_LEADER"` |
| `X-User-Scopes` | String | スペース/カンマ区切りの権限コード | `"dict:type:list dict:data:create"` |

### 8.2 フロントエンドが送信するリクエストヘッダー

| ヘッダー | 送信元 | 説明 |
|--------|------|------|
| `Authorization: Bearer <JWT>` | Axios インターセプターが自動注入 | `useUserStore()` から Token を取得 |
| `X-Tenant-Id` | Axios インターセプターが自動注入 | `useUserStore()` からテナント ID を取得 |
| `Content-Type: application/json` | Axios デフォルト | JSON リクエストボディ |

### 8.3 セキュリティレスポンスヘッダー（Gateway 注入）

`SecurityHeadersFilter`（WebFlux WebFilter）は、ゲートウェイを経由するすべてのレスポンスに以下を追加します：

| レスポンスヘッダー | 値 | 用途 |
|--------|-----|------|
| `X-Content-Type-Options` | `nosniff` | ブラウザーの MIME タイプスニッフィングを防止 |
| `X-Frame-Options` | `SAMEORIGIN` | クリックジャッキングを防止 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referer ヘッダーの情報漏洩を制御 |

---

## 9. 認証ヘッダー

```
Authorization: Bearer <token>
```

- Axios リクエストインターセプター（`src/api/request.ts`）が `useUserStore()` の Token を使用して設定
- `omni-gateway` の `AuthFilter` により検証（JWT RS256 署名検証 + claims 抽出 + 身份ヘッダー注入）
- 公開パスは認証不要：`/api/auth/**`、`/actuator/**`、`/favicon.ico`

---

## 10. ソーシャルログインエンドポイント

ソーシャルログインエンドポイントは HTTP 302 リダイレクトを返します（標準の `R<T>` レスポンスではありません）。これはフロントエンドが `window.location.href` によりブラウザーナビゲーションをトリガーするためです。

| HTTP メソッド | URL | 説明 |
|-----------|-----|------|
| GET | `/api/auth/oauth2/{provider}?tenant_id=1` | サードパーティログインを開始し、302 リダイレクトでサードパーティ認可ページへ遷移 |
| GET | `/api/auth/oauth2/{provider}/callback?code=XXX&state=YYY` | サードパーティコールバックを処理し、成功時は 302 リダイレクトでフロントエンドコールバックページへ遷移 |

### ログイン開始

```
# GitHub
GET /api/auth/oauth2/github?tenant_id=1
→ 302 Location: https://github.com/login/oauth/authorize?client_id=...&redirect_uri=...&scope=...&state=...

# Google
GET /api/auth/oauth2/google?tenant_id=1
→ 302 Location: https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&response_type=code&scope=openid+profile+email&state=...

# Gitee
GET /api/auth/oauth2/gitee?tenant_id=1
→ 302 Location: https://gitee.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&scope=user_info&state=...
```

- `{provider}` は `github`、`google`、`gitee` をサポート
- `tenant_id` は必須で、ログイン対象のテナントを指定
- State パラメータには HMAC-SHA256 署名（`tenantId|timestamp|hmac`）が含まれ、CSRF 攻撃を防止

### コールバック処理

```
# GitHub/Google/Gitee コールバック
GET /api/auth/oauth2/{provider}/callback?code=XXX&state=YYY

→ 成功: 302 Location: /callback#token=<JWT>&username=<username>
→ 失敗: 302 Location: /login?error=<error_code>&message=<message>
```

### Docker デプロイメント時の OAuth2 コールバック URL 設定

Docker デプロイメント時、ソーシャルログインの `redirect_uri` は**ホストマシンからアクセス可能な URL** を使用する必要があります：

| デプロイメント環境 | redirect_uri 例 |
|---------|------------------|
| ローカル開発 | `http://localhost:8100/api/auth/oauth2/github/callback` |
| Docker デプロイメント | `http://<ホストマシンIP>:8100/api/auth/oauth2/github/callback` |
| 本番環境 | `https://your-domain.com/api/auth/oauth2/github/callback` |

> **注意**：Docker デプロイメントでは、Auth サービスコンテナの内部ポートは 8080 ですが、OAuth2 コールバック URL はホストマシンのマッピングポート 8100 を使用する必要があります（サードパーティプラットフォームはホストマシンのパブリック/LAN 到達可能アドレスにコールバックする必要があるため）。

### フロントエンドコールバックページ

`/callback` ページ（`src/views/callback/index.vue`）の役割：
1. URL フラグメントから `token` と `username` を解析
2. `localStorage` に保存（`useUserStore` 経由）
3. ダッシュボードにリダイレクト

> 完全なフローのシーケンス図は [core-flows.jp.md](core-flows.jp.md) Flow 4 を参照してください。

---

## 11. XSS 設定管理エンドポイント

Base path: `/api/auth/xss-config`（Gateway StripPrefix=2 → ダウンストリーム `/xss-config/...`）

### 現在の XSS 設定を取得

```
GET /api/auth/xss-config/settings
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
  "data": {
    "enabled": false,
    "rules": [
      { "id": 1, "ruleType": "HTML_TAG", "pattern": "script" }
    ]
  }
}
```

### グローバルスイッチの切り替え

```
PUT /api/auth/xss-config/toggle
Authorization: Bearer <token>
X-Tenant-Id: 1

@PreAuthorize("hasAuthority('system:xssconfig:update')")
Response 200: { "code": 200, "message": "success" }
```

### ルール CRUD

| HTTP メソッド | URL | 権限コード | 説明 |
|-----------|-----|--------|------|
| GET | `/api/auth/xss-config/rules/list?page=1&size=10` | `system:xssconfig:list` | ページネーションリスト |
| POST | `/api/auth/xss-config/rules` | `system:xssconfig:create` | ルール作成 |
| PUT | `/api/auth/xss-config/rules/{id}` | `system:xssconfig:update` | ルール更新 |
| DELETE | `/api/auth/xss-config/rules/{id}` | `system:xssconfig:delete` | ルール削除 |
| PUT | `/api/auth/xss-config/rules/{id}/toggle` | `system:xssconfig:update` | ルール有効/無効の切り替え |

**ruleType 列挙値**：`HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### 権限コード

| 権限コード | 説明 |
|--------|------|
| `system:xssconfig:list` | XSS 設定とルールを閲覧 |
| `system:xssconfig:update` | グローバルスイッチの切り替え、ルール更新、ルールステータスの切り替え |
| `system:xssconfig:create` | ルール作成 |
| `system:xssconfig:delete` | ルール削除 |

---

## 12. Base サービス辞書管理エンドポイント

Base サービス（`omni-base :8101`）はデータ辞書管理を提供し、「タイプ + データ」の二段階構造を採用しています。

**ルート説明**：Gateway ルート `Path=/api/base/**` には StripPrefix フィルターが**ありません**。Base サービスのコントローラーは完全なパスを使用します（例：`@RequestMapping("/api/base/dict/type")`）。

### Dictionary Type エンドポイント

Base path: `/api/base/dict/type`

| HTTP メソッド | URL | 権限コード | 説明 |
|-----------|-----|--------|------|
| GET | `/api/base/dict/type/list?page=1&size=10&typeCode=&typeName=&status=` | `dict:type:list` | ページネーションリスト、フィルター対応 |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | ID で検索 |
| POST | `/api/base/dict/type` | `dict:type:create` | 作成（テナント内の typeCode 一意性を検証） |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | 更新（部分更新） |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | 削除（関連データのカスケード削除） |
| PUT | `/api/base/dict/type/{id}/status` | `dict:type:update` | 有効/無効の切り替え |

**リクエスト例**：

```
GET /api/base/dict/type/list?page=1&size=10
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
  "data": {
    "records": [
      { "id": 1, "typeCode": "sys_user_gender", "typeName": "ユーザー性別", "status": 1, "sort": 0 }
    ],
    "total": 3,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### Dictionary Data エンドポイント

Base path: `/api/base/dict/data`

| HTTP メソッド | URL | 権限コード | 説明 |
|-----------|-----|--------|------|
| GET | `/api/base/dict/data/list?typeCode=sys_user_gender&page=1&size=10` | `dict:data:list` | typeCode でページネーション検索 |
| POST | `/api/base/dict/data` | `dict:data:create` | 作成（親タイプの存在を検証） |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | 更新（部分更新） |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | 単件削除 |
| POST | `/api/base/dict/data/refresh-cache` | `dict:data:refresh` | Redis キャッシュの手動リフレッシュ |

**リクエスト例**：

```
POST /api/base/dict/data
Authorization: Bearer <token>
X-Tenant-Id: 1
Content-Type: application/json

{
  "typeCode": "sys_user_gender",
  "dictValue": "3",
  "dictLabel": "非公開",
  "tagType": "warning",
  "sort": 3
}

@PreAuthorize("hasAuthority('dict:data:create')")
Response 200: { "code": 200, "data": { "id": 8, ... } }
```

### 辞書権限コード

| 権限コード | 説明 |
|--------|------|
| `dict:type:list` | 辞書タイプリストを閲覧 |
| `dict:type:create` | 辞書タイプを作成 |
| `dict:type:update` | 辞書タイプの更新/ステータス切り替え |
| `dict:type:delete` | 辞書タイプを削除（カスケード） |
| `dict:data:list` | 辞書データリストを閲覧 |
| `dict:data:create` | 辞書データを作成 |
| `dict:data:update` | 辞書データを更新 |
| `dict:data:delete` | 辞書データを削除 |
| `dict:data:refresh` | 辞書キャッシュを手動リフレッシュ |

### テナント分離

すべてのリスト検索と作成操作には `X-Tenant-Id` リクエストヘッダーが必要です（フロントエンドが JWT Token から抽出し、Gateway が注入）。データは SQL クエリレベルで `tenant_id` により分離されます。辞書タイプの一意性制約の範囲は `(tenant_id, type_code)` です。

---

## 13. API バージョン管理ポリシー

### 現在の決定

スキャフォールディング段階では URL バージョン番号を使用しません。API が安定し、複数のコンシューマーが存在するようになった時点で、プレフィックスバージョン制御を導入します。

### 将来の進化パス

| 段階 | バージョン戦略 | URL 例 |
|------|---------|---------|
| **現在（スキャフォールディング）** | バージョン番号なし | `/api/auth/user/list` |
| **V1（API 安定後）** | URL プレフィックスバージョン | `/api/v1/auth/user/list` |
| **V2（Breaking Change）** | URL プレフィックスバージョン | `/api/v2/auth/user/list` |

**バージョニングルール**：
- フィールドの追加（後方互換）：バージョン変更不要
- フィールドの削除/名前変更：新バージョンが必要
- リクエスト/レスポンス構造の変更：新バージョンが必要
- 旧バージョンは最低 6 ヶ月間維持

---

## 14. Null セマンティクス

- `null` フィールドは JSON 出力に含まれます（省略しません）
- 空コレクションは `null` ではなく `[]` として返されます
- オプションの単一値は、存在しないことを示すために空文字列ではなく `null` を使用します
