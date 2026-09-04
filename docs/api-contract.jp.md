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
- [15. SRM MVP 契約](#15-srm-mvp-契約)
- [16. Workflow クロスサービス契約](#16-workflow-クロスサービス契約)
- [17. Procurement MVP 契約](#17-procurement-mvp-契約)
- [18. Asset MVP 契約](#18-asset-mvp-契約)

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
| 200 | 409 | ステータス/並行性競合 | 楽観ロックバージョン不一致またはステートマシンが遷移を拒否 | フロントエンドがデータを更新してユーザーに再試行を促す |
| 200 | 503 | 下流依存が利用不可 | CRM が Auth データスコープ API を呼び出してフェイルクローズ | フロントエンドがサービス一時停止を表示。権限超過データにダウングレードしない |
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
| 409 | 楽観ロックまたはステータス競合 | "データが他のユーザーによって変更されました。更新して再試行してください" |
| 503 | 必須依存が利用不可 | "認証認可サービスが一時的に利用できません" |

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

**Gateway パスプレフィックス**：すべてのフロントエンドリクエストは `/api/<service>/<resource>` を使用します（例：`/api/auth/user/list`）。現在、Gateway は Auth・Base・Workflow・CRM・SRM・Procurement・Asset などの業務ルートに対して `StripPrefix` を使用せず、ダウンストリームの Controller が完全な `/api/**` パスを宣言して受け取ります。

---

## 5. Gateway ルーティング設定

### 5.1 ローカル開発環境ルート

Gateway の `application.yml` 内のルート設定（`spring.cloud.gateway.server.webflux.routes`）：

| ルート ID | パスマッチ | ターゲットサービス | StripPrefix | 説明 |
|---------|---------|---------|-------------|------|
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` | なし | OAuth2 認可サーバーエンドポイント |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` | なし | OpenID Connect ディスカバリーエンドポイント |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` | **なし** | Auth サービス REST API（完全なパスを使用） |
| `omni-base` | `/api/base/**` | `lb://omni-base` | **なし** | Base サービス（完全なパスを使用） |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` | **なし** | 定期タスク管理 |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` | **なし** | ワークフローエンジン |

### 5.2 Docker デプロイメントルート

Docker デプロイメント時、ルート設定は同じですが、ターゲットサービスの URI は Nacos サービスディスカバリーにより自動解決されます：

| フロントエンドリクエスト | Gateway ルート | ダウンストリーム受信パス | 説明 |
|---------|-------------|-------------|------|
| `GET /api/auth/user/list` | `lb://omni-auth` StripPrefix なし | `GET /api/auth/user/list` | Auth サービスは完全なパスを保持 |
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

### 8.3 内部サービスリクエストヘッダー

サービス間インターフェースはすべて `/api/internal/**` の下に置かれ、共有トークン認証を使用し、エンドユーザーの JWT は使用しません：

| ヘッダー | 必須 | 説明 |
|--------|------|------|
| `X-Internal-Token` | はい | サービス間共有トークン。`InternalApiAuthFilter` が検証 |
| `X-Tenant-Id` | はい | 現在の業務テナント。body/query の `tenantId` と一致する必要がある |
| `Content-Type: application/json` | JSON リクエストでは必須 | JSON リクエストボディ |

`InternalApiAuthFilter` はコンテナレベルの前置フィルターとして `/api/internal/**` を一括保護します。サービスのセキュリティチェーンが再度 Gateway ユーザー識別情報を要求してはなりません。トークンが欠落または不一致の場合は HTTP 401 を返します。サーバー側でトークンが未設定の場合はフェイルクローズで HTTP 503 を返します。ヘッダーと body/query のテナントが不一致の場合は業務コード 403 を返します。内部パスは `X-Gateway-Forwarded` やユーザー権限ヘッダーに依存してはなりません。

### 8.4 セキュリティレスポンスヘッダー（Gateway 注入）

`SecurityHeadersFilter`（WebFlux WebFilter）は、ゲートウェイを経由するすべてのレスポンスに以下を追加します：

| レスポンスヘッダー | 値 | 用途 |
|--------|-----|------|
| `X-Content-Type-Options` | `nosniff` | ブラウザーの MIME タイプスニッフィングを防止 |
| `X-Frame-Options` | `DENY` | ページが iframe にネストされるのを禁止し、クリックジャッキングを防止 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referer ヘッダーの情報漏洩を制御 |
| `X-Trace-Id` | 32 桁の小文字 16 進数文字列 | Gateway・Servlet・Feign とエラー フィードバックを関連付け |

Gateway は常に新しい `X-Trace-Id` を生成し、公網クライアントが提供する同名ヘッダーを信頼しません。ダウンストリームの Servlet サービスは有効な値を MDC とレスポンスヘッダーに書き込み、共通 Feign インターセプターは引き続き内部呼び出しへ伝播します。フロントエンドのエラーパネルはレスポンス内の traceId を表示でき、ログ調査では同じ値で完全な呼び出しチェーンを検索するのが優先です。

---

## 9. 認証ヘッダー

```
Authorization: Bearer <token>
```

- Axios リクエストインターセプター（`src/api/request.ts`）が `useUserStore()` の Token を使用して設定
- `omni-gateway` の `AuthFilter` により検証（JWT RS256 署名検証 + claims 抽出 + 身分ヘッダー注入）
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

Base path: `/api/auth/xss-config`（Gateway はプレフィックスを除去せず、ダウンストリームが完全なパスを保持）

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

### MQ 配信ランタイム状態

`GET /api/base/mq-message/runtime` は `base:mqmessage:list` 権限を要求し、現在の Outbox とバックグラウンド配信能力を返します：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "outboxWriteEnabled": true,
    "deliveryEnabled": false,
    "mode": "OUTBOX_ONLY"
  }
}
```

`OUTBOX_ONLY` は、業務トランザクションは引き続きローカル Outbox に書き込むものの、MQ relay/XXL-JOB が動作していないことを意味します。フロントエンドは縮退中の旨を表示し、再送操作を無効化する必要があります。`FULL` は書き込みと非同期配信の両方が有効であることを意味します。

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

---

## 15. SRM MVP 契約

### 15.1 サプライヤーとサブリソース

- 管理側のサプライヤーライフサイクルコマンドはすべて `version` を携行します；ブラックリスト復旧は
  `POST /api/srm/supplier/{id}/restore-from-blacklist` を使用します。
- 連絡先、資格、銀行口座パス内の `supplierId` はサブリソースの実際の帰属と一致しなければなりません；不一致は一律 404 を返します。
- `creditCode` はテナント内で一意；ページネーション `size` は最大 100。
- サプライヤー 360 は `GET /api/srm/supplier/{id}/overview` を使用し、返却内容は呼び出し元のサブリソース権限と PII 権限で引き続きトリミングされます。

### 15.2 ポータル登録

`POST /api/srm/portal/enroll` は Gateway が注入した tenant/user 身分のみを受け付け、リクエストは tenantId や userId を携行してはなりません：

```json
{
  "requestId": "client-generated-uuid",
  "inviteToken": "raw-token-returned-once",
  "name": "サンプルサプライヤー株式会社",
  "creditCode": "91320000EXAMPLE"
}
```

レスポンスのステータスは `PENDING_ROLE_ASSIGN`、`ROLE_ASSIGN_FAILED`、`COMPLETED`、`CANCELLED` のみを使用します。
現在のユーザーは `GET /api/srm/portal/enrollment` でステータスを照会でき、失敗後は
`POST /api/srm/portal/enrollment/retry` で冪等リトライを呼び出せます。ロール割り当てが完了するまで PortalUser は作成されず、企業プロフィールインターフェースも開放されません。

### 15.3 評価とリスク

- `GET /api/srm/evaluation/template/default/dimensions` は現在のテナントのデフォルトテンプレートと有効な次元を返します；フロントエンドはデータベース ID をハードコードしてはいけません。
- 評分範囲は 1–5 で、デフォルトテンプレートのすべての次元をカバーし、重複してはいけません。
- `GET /api/srm/risk/list` はサプライヤーの最新評価で集約したリスクサマリーのページネーションを返し、`riskLevel` でフィルタリングできます。
- `GET /api/srm/supplier/{id}/risk` は `indicators/latestAssessment/history` の集約ビューを返します。
- リスク指標の更新は `version` を携行；総合等級が非 RED から RED に変わった場合のみ `srm.risk.level-changed.v1` を生成します。

### 15.4 内部サプライヤーサマリー

後続の Procurement/Asset は `X-Internal-Token` と `X-Tenant-Id` の両方を携行する場合のみ呼び出せます：

- `GET /api/internal/supplier/{id}?tenantId={tenantId}`
- `GET /api/internal/supplier/search?tenantId={tenantId}&status=APPROVED&categoryCode={code}&limit=50`
- `POST /api/internal/supplier/batch`

GET の query tenantId、batch の body tenantId は `X-Tenant-Id` と完全一致しなければならず、さもなくば 403 を返します。batch リクエストボディは：

```json
{
  "tenantId": 1,
  "supplierIds": [101, 102, 101]
}
```

`supplierIds` は 1–100 個の正整数を含める必要があります；サーバー側は初出順で重複排除し返却順を保持し、存在しないまたは削除済み ID は結果から省略し、単一項の欠落でリクエスト全体を 404 にしません。レスポンスはサプライヤーの `id/supplierNo/name/status/levelCode/categoryCode` のみを含み、連絡先、銀行口座、その他の PII は返しません。

### 15.5 サプライヤーポータル見積

ポータルエンドポイントは `srm:portal:quotation`、`SUPPLIER` ロールを必要とし、かつ現在のユーザーに有効な
`srm_supplier_portal_user` 関連が存在しなければなりません。この権限ノードは `SUPPLIER` とプラットフォームルールで全権限ツリーを持つ `SUPER_ADMIN` のみに付与されます；SUPER_ADMIN ロールのみではポータル身分条件を満たさず、サプライヤーの代理見積はできません：

- `GET /api/srm/portal/quotation/invitations`
- `GET /api/srm/portal/quotation/invitations/{rfqId}`
- `POST /api/srm/portal/quotation`

招待リストは `R<List<RfqInvitationVO>>` を使用し、単一項目は最低限以下を含みます：

```json
{
  "rfqId": 1001,
  "rfqNo": "RFQ-202607-0001",
  "title": "オフィスパソコン調達見積依頼",
  "status": "SENT",
  "invitationStatus": "INVITED",
  "quotationDeadline": "2026-07-31 18:00:00",
  "currencyCode": "CNY",
  "invitedTime": "2026-07-21 10:00:00",
  "quotationId": 501,
  "quotationVersion": 2,
  "quotationStatus": "SUBMITTED",
  "totalAmount": "128000.0000",
  "validUntil": "2026-08-31 18:00:00"
}
```

招待詳細は上記フィールドに加え RFQ 行スナップショットを返し、既に見積がある場合は `currentQuotation` を返します：

```json
{
  "rfqId": 1001,
  "rfqNo": "RFQ-202607-0001",
  "title": "オフィスパソコン調達見積依頼",
  "status": "SENT",
  "invitationStatus": "INVITED",
  "quotationDeadline": "2026-07-31 18:00:00",
  "currencyCode": "CNY",
  "lines": [
    {
      "rfqLineId": 10011,
      "materialCode": "IT-LAPTOP-001",
      "materialName": "ビジネスノートPC",
      "unit": "台",
      "quantity": "20.000000",
      "remark": "3年保証付き"
    }
  ],
  "currentQuotation": null
}
```

提出リクエスト：

```json
{
  "requestId": "f93b7342-9416-45bd-95f2-1e7e6045686d",
  "rfqId": 1001,
  "version": 0,
  "validUntil": "2026-08-31 18:00:00",
  "lines": [
    {
      "rfqLineId": 10011,
      "unitPrice": "6400.000000",
      "deliveryDays": 7,
      "remark": "到着後検収"
    }
  ]
}
```

| フィールド | 型 | 必須 | 制約 |
|------|------|------|------|
| `requestId` | String | はい | 最大 64；テナント内で一意の冪等キー |
| `rfqId` | Long | はい | 正整数；現在のサプライヤーの有効な招待が存在しなければならない |
| `version` | Integer | はい | 初回提出は 0；修正時は現在の見積バージョンと等しくなければならない |
| `validUntil` | LocalDateTime | はい | `yyyy-MM-dd HH:mm:ss`；現在時刻より後かつ quotationDeadline より前ではいけない |
| `lines` | Array | はい | 非空；rfqLineId 集合は招待詳細の RFQ 行集合と完全一致しなければならない |
| `lines[].rfqLineId` | Long | はい | 正整数かつ重複不可 |
| `lines[].unitPrice` | Decimal String | はい | 十進文字列；`DECIMAL(19,6)`、0 より大きく整数部は最大 13 桁 |
| `lines[].deliveryDays` | Integer | はい | 0–3650 |
| `lines[].remark` | String | いいえ | 最大 500、プレーンテキスト |

リクエストは `tenantId/supplierId/rfqNo/material/quantity/currencyCode/lineAmount/totalAmount` を受け付けません。これらのフィールドはそれぞれ信頼できる身分ヘッダー、PortalUser、Procurement 招待詳細から読み取るか、サーバー側で
`unitPrice × quantity` により計算します；行金額と総金額は `DECIMAL(19,4)` で保存します。レスポンスは `R<QuotationVO>` で、見積ヘッダー、`version`、すべての行スナップショットを含みます。

冪等と並行ルール：

- `srm_quotation_request` は `(tenantId, requestId)`、正規化リクエストボディ SHA-256、quotationId、targetVersion を恒久的に保存します；同一 requestId・同一 requestHash のリトライは現在の見積スナップショットを返し、見積や Outbox を再度書き込みません。
- 同一 requestId が異なる rfqId やリクエスト内容に紐づく場合はビジネスコード 409 を返します。
- `(tenantId, rfqId, supplierId)` は未削除見積を最大 1 件；初回リクエストは作成センチネル `version=0` を使用し、初版が永続化され `version=1` でレスポンスされ、以降の更新は現在の version を携行しなければならず、期限切れバージョンや 0 の再使用はいずれも 409 を返します。
- 提出前に RFQ `status=SENT`、招待 `status IN (INVITED, QUOTED)`、締切時間、行集合を再検証しなければなりません；その他の RFQ ステータス（`DRAFT/CLOSED/AWARDED/CANCELLED`）は拒否します。Procurement が利用不可の場合は 503 を返し、オフライン書き込みは許可しません。

requestHash は requestId を含めず、本段階の正規化入力は
`rfqId/version/validUntil/lines`；lines は `rfqLineId` 昇順、単価は 6 桁小数に正規化し非科学記数法文字列を使用し、備考は trim 後に null/空白を null に統一します。サーバー側はフィールド順による同一意図の誤判定を避けるため、生の JSON バイトを直接ハッシュしてはいけません。

### 15.6 SRM と Procurement の見積内部契約

SRM は招待照会時に Procurement を呼び出します：

- `GET /api/internal/procurement/rfq/invitations?supplierId={supplierId}`
- `GET /api/internal/procurement/rfq/{rfqId}/invitation?supplierId={supplierId}`

リスト項目は最低限 `tenantId/rfqId/rfqNo/title/status/invitationStatus/supplierId/quotationDeadline/currencyCode/invitedTime` を含み；詳細は
`lines[{rfqLineId,materialCode,materialName,unit,quantity,remark}]` を追加します。SRM は PortalUser 関連から得た supplierId を使用しなければなりません。

Procurement は比較/選定時に SRM を呼び出します：

```http
GET /api/internal/quotation/batch?tenantId=1&rfqId=1001
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
```

レスポンスは `R<List<QuotationVO>>`。`QuotationVO` は
`id/rfqId/rfqNo/supplierId/supplierNameSnapshot/quotationTime/validUntil/totalAmount/currencyCode/status/version/lines` を含み；行は
`id/rfqLineId/materialCode/materialName/unit/unitPrice/quantity/lineAmount/deliveryDays/remark` を含みます。指定 tenant・RFQ かつサプライヤーが現在も APPROVED の未削除有効見積のみを返します。ポータル招待、見積レスポンスおよび内部 batch の `totalAmount/unitPrice/quantity/lineAmount` は一律 JSON 十進文字列を使用し、JavaScript の高精度金額や数量の喪失を避けるため JSON number の出力を禁止します。

見積ヘッダー、明細、`srm_quotation_request` と `srm.quotation.submitted.v1` Outbox は同一トランザクションでコミットします。イベントエンベロープは
`eventId/eventType/occurredAt/tenantId/payload` に従い、payload は最低限
`requestId/quotationId/quotationVersion/rfqId/rfqNo/supplierId/status/totalAmount/currencyCode/validUntil` を含みます。Procurement は eventId Inbox で冪等消費し、古い quotationVersion で新バージョンを上書きすることを拒否します。

---

## 16. Workflow クロスサービス契約

Workflow 内部エンドポイントは §8.3 の `X-Internal-Token` と `X-Tenant-Id` を統一的に使用し、レスポンスは標準の
`R<T>` を引き続き使用します。詳細な実行メカニズムは [workflow.jp.md](workflow.jp.md#28-クロスサービス内部契約) を参照。

### 16.1 冪等プロセス起動

```http
POST /api/internal/workflow/process-instance/start
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
Content-Type: application/json
```

リクエスト：

```json
{
  "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
  "tenantId": 1,
  "modelVersionId": 42,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1",
  "startUserId": 7,
  "startUserName": "buyer",
  "title": "購買申請 PR-202607-0001",
  "variables": {
    "requisitionId": 10001,
    "approvalAttempt": 1,
    "materialCategory": "IT_EQUIPMENT",
    "totalAmount": "120000.0000",
    "requesterUnitId": 12
  }
}
```

| フィールド | 型 | 必須 | 制約 |
|------|------|------|------|
| `requestId` | String | はい | 非空、最大 64；呼び出し元生成の冪等キー |
| `tenantId` | Long | はい | 正整数、`X-Tenant-Id` と等しくなければならない |
| `modelVersionId` | Long | はい | 正整数、モデルバージョンは現在のテナントに属し `processDefinitionId` が存在しなければならない |
| `businessType` | String | はい | 非空、最大 100 |
| `businessKey` | String | はい | 非空、最大 255 |
| `startUserId` | Long | はい | 正整数 |
| `startUserName` | String | いいえ | 最大 100 |
| `title` | String | いいえ | 最大 500；空値は `{businessType}:{businessKey}` に自動生成 |
| `variables` | Object | いいえ | プロセス変数；予約フィールド `requestId/businessType/businessKey` はサービスが上書き |

レスポンス：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
    "businessType": "PROCUREMENT_REQUISITION",
    "businessKey": "10001:1",
    "processInstanceId": "22501",
    "replayed": false
  }
}
```

冪等性は 2 つのテナント内一意キーで共同に保証されます：

- `(tenantId, requestId)`：リクエストレベル冪等；同一リクエスト ID を異なるビジネスに紐づけてはいけません。
- `(tenantId, businessType, businessKey)`：ビジネスレベル冪等；同一ビジネスは複数プロセスを起動してはいけません。
- 既に成功した同一意図のリトライは元の `processInstanceId` を返し、`replayed = true`。
- 処理中、リクエスト ID の競合、またはビジネスキーに対応する `modelVersionId/startUserId` の変化はいずれもビジネスコード 409 を返します。

### 16.2 タスク処理資格の検証

```http
POST /api/internal/workflow/task/assignment/validate
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
Content-Type: application/json
```

リクエスト：

```json
{
  "tenantId": 1,
  "taskId": "25017",
  "userId": 7,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1"
}
```

| フィールド | 型 | 必須 | 制約 |
|------|------|------|------|
| `tenantId` | Long | はい | 正整数、`X-Tenant-Id` と等しくなければならない |
| `taskId` | String | はい | 非空、最大 64 |
| `userId` | Long | はい | 正整数 |
| `businessType` | String | はい | 非空、最大 100 |
| `businessKey` | String | はい | 非空、最大 255 |

レスポンス：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "valid": true,
    "processInstanceId": "22501",
    "assignmentType": "CANDIDATE",
    "message": "検証通過"
  }
}
```

サービスは Flowable タスクテナント、インスタンス拡張記録テナント、`businessType + businessKey` ビジネス帰属を同時に一致させ、
`userId` が現在の `ASSIGNEE` または未受領タスクの `CANDIDATE` であることを確認しなければなりません。`assignmentType` は
`ASSIGNEE`、`CANDIDATE`、`NONE` のみ；タスクが存在しないか任一の境界が不一致の場合 `valid = false` を返します。

### 16.3 プロセス完了イベント

| 属性 | 値 |
|------|----|
| イベントタイプ | `workflow.process.completed.v1` |
| プロデューサー | `omni-workflow` |
| Stream binding | `workflow-domain-out-0` |
| Destination | `workflow-domain-event` |

```json
{
  "eventId": "3f206832-9dc1-4422-870a-a286a979404d",
  "eventType": "workflow.process.completed.v1",
  "occurredAt": "2026-07-21 10:30:00",
  "tenantId": 1,
  "producer": "omni-workflow",
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1",
  "processInstanceId": "22501",
  "result": "APPROVED",
  "completedTime": "2026-07-21 10:30:00"
}
```

| フィールド | 型 | 説明 |
|------|------|------|
| `eventId` | String(UUID) | イベント ID、Outbox `msgKey`、消費冪等キー |
| `eventType` | String | `workflow.process.completed.v1` に固定 |
| `occurredAt` | LocalDateTime | イベント記録の生成時刻、形式 `yyyy-MM-dd HH:mm:ss` |
| `tenantId` | Long | テナント ID |
| `producer` | String | `omni-workflow` に固定 |
| `businessType` | String | 呼び出し元のビジネスタイプ |
| `businessKey` | String | 呼び出し元のビジネス主キー |
| `processInstanceId` | String | Flowable プロセスインスタンス ID |
| `result` | Enum | `APPROVED`、`REJECTED`、`CANCELLED` |
| `completedTime` | LocalDateTime | プロセスの実際の完了または終了時刻 |

完了ステータスと `completionEventId` の条件付き更新および PENDING Outbox 記録は同一ローカルトランザクション内でコミットされます；
`completion_event_id IS NULL` は同一プロセスインスタンスが論理完了イベントを一度だけ生成することを保証するデータベースラッチです。
コミット後は信頼性メッセージリレーが非同期に配信・リトライし、転送セマンティクスは少なくとも一度なので、コンシューマーは `eventId` で冪等処理しなければなりません。

### 16.4 公開済みモデルバージョンの照会

```http
GET /api/internal/workflow/model-version/{modelVersionId}
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
```

成功レスポンス：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 301,
    "modelId": 30,
    "modelKey": "asset-transfer-approval",
    "category": "ASSET_TRANSFER",
    "version": 2,
    "processDefinitionId": "asset-transfer-approval:2:8801",
    "status": "PUBLISHED"
  }
}
```

エンドポイントはリクエストされたテナント内で引き続き利用可能なモデルとバージョンのみを返します。`modelKey` はテナント内で一意かつ BPMN process id と
一致するモデル識別子；`category` はクロスサービスのビジネス分類です。呼び出し元は `PUBLISHED` と非空の
`processDefinitionId` の検証に加え、安定したビジネスタイプを `category` に紐づけられます。Asset は移管モデル分類を
`ASSET_TRANSFER`、処分モデル分類を `ASSET_DISPOSAL` とすることを強制し、クロス再利用や他のビジネスモデルの使用は不可；
Workflow は実際の資産承認インスタンス作成前に同じ検証を再実行し、事前検証後のモデル変更ウィンドウを閉じます。

### 16.5 承認ルールの読み取り専用モデル集約

| メソッド | パス | 制約 |
|---|---|---|
| GET | `/api/internal/workflow/model-versions/published?category=purchase` | 現在のテナント、分類の完全一致、メインモデル有効かつ currentPublishedVersionId がデプロイ可能な公開済みバージョンを指す記録のみ返す |
| POST | `/api/internal/workflow/model-versions/resolve` | body は `{ "modelVersionIds": [1, 2] }`、1 回 1–200 個の正整数、リクエスト順に返却 |
| GET | `/api/internal/workflow/model-version/{id}/preview` | 安全承認図を返し、BPMN XML や designerJson は返さない |

バッチ解決の `availability` は `AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND` のみ。
安全プレビューはノード、秘匿化エッジ、モデルメタデータのみを含み；UserTask は `roleCode/approvalMode` を含めることができ、条件式は
「条件設定済み（内容は非表示）」のみを返します。分岐やループがある場合 `linearSummary=null`、フロントエンドは実際のパスがビジネスデータで決まることを提示しなければならず、
現在の組織を将来の実際の承認者に解決してはいけません。

---

## 17. Procurement MVP 契約

### 17.1 共通境界

- 外部 Base path は `/api/procurement`、Gateway は完全パスを保持し `StripPrefix` を使用しません。
- すべてのリクエストは Gateway が注入した `X-User-Id`、`X-Tenant-Id` と権限ヘッダーを使用；ビジネステーブルは TenantLine と permission-aware DataScope の両方で制約されます。
- 数量、単価は `DECIMAL(19,6)`、金額は `DECIMAL(19,4)` を使用；すべてのレスポンスの数量、単価、金額は JSON string であり、フロントエンドは JavaScript `number` でビジネス金額を計算してはいけません。
- 更新 body と削除 query は `version` を携行しなければなりません；バージョン競合はビジネスコード 409 を返します。
- 内部エンドポイントは統一的に `/api/internal/procurement/**` を使用し、`X-Internal-Token` と `X-Tenant-Id` を要求し、Gateway 経由で公開してはいけません。

### 17.2 資材とカテゴリ

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/procurement/material/category/list` | `procurement:material:list` | 最大 2 階層のカテゴリツリーを返す |
| POST | `/api/procurement/material/category` | `procurement:material:create` | カテゴリを作成；`categoryCode` は作成後変更不可 |
| PUT | `/api/procurement/material/category/{id}` | `procurement:material:update` | body は `version` を携行 |
| DELETE | `/api/procurement/material/category/{id}?version={version}` | `procurement:material:delete` | 子カテゴリや資材が存在する場合は 409 を返す |
| GET | `/api/procurement/material/list` | `procurement:material:list` | `keyword/categoryId/status/assetManaged/page/size` |
| GET | `/api/procurement/material/{id}` | `procurement:material:list` | 資材詳細を照会 |
| POST | `/api/procurement/material` | `procurement:material:create` | 資材を作成；`materialCode` は作成後変更不可 |
| PUT | `/api/procurement/material/{id}` | `procurement:material:update` | body は `version` を携行 |
| DELETE | `/api/procurement/material/{id}?version={version}` | `procurement:material:delete` | 論理削除 |

`assetManaged=true` のとき `unit` は `EA/PCS/UNIT/SET` のみ許可；購買申請はステータスが `ACTIVE` かつカテゴリが有効な資材のみを参照できます。

### 17.3 承認ルート

| メソッド | パス | 権限 |
|---|---|---|
| GET | `/api/procurement/approval-route/list` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/workflow-options` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route/match-preview` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/coverage` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/impact?routeId={id}` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route` | `procurement:approval-route:create` |
| PUT | `/api/procurement/approval-route/{id}` | `procurement:approval-route:update` |
| DELETE | `/api/procurement/approval-route/{id}?version={version}` | `procurement:approval-route:delete` |

新 UI の作成リクエストは `routeName/categoryCode/minAmount/maxAmount/modelVersionId/status` を含みます。`routeCode` はサーバー側で
`APR-{ULID}` を生成し作成後変更不可；1 つの互換公開サイクル内では、旧作成リクエストが欠落した
`routeName` のフォールバックとして `routeCode` を渡せ、サーバー側は非推奨ログを記録します。`priority` は互換の高度な呼び出し元のためにのみ保持；新規作成で未指定時はテナント設定ロック内で
同カテゴリの最大値に 10 を加え、フロントエンドはこのフィールドを表示しません。

`minAmount/maxAmount` は JSON 十進文字列を使用しなければならず（`maxAmount=null` を除く）、JSON number は 400 を返します。
アクティブ区間は `minAmount <= amount < maxAmount` を使用し、`maxAmount=null` は上限なしを意味します。同カテゴリのアクティブ区間は重複してはならず、
書き込みトランザクションはテナント設定行ロックで直列化検証します。新規作成や `modelVersionId` の交換時は現在のテナント、`category=purchase`、
`availability=AVAILABLE` の現在の公開済みバージョンのみ許可；遺留の非 purchase 参照はリストで `LEGACY_CATEGORY` とマークし、静かに移行できません。

`match-preview` リクエストは `{ "categoryCode": "IT_DEVICE", "totalAmount": "10000.0000" }`、レスポンスの
`outcome` は `MATCHED/NO_MATCH/AMBIGUOUS/WORKFLOW_UNAVAILABLE` のみ。購買申請提出と共同で
`ApprovalRouteResolver.evaluate` を呼び出します；提出パスは非 MATCHED 結果を既存の 409 に変換するため、ブラウザはヒットを計算しません。
`coverage` はすべての有効カテゴリについて 0 から無限の `COVERED/GAP/AMBIGUOUS` 半開区間を出力し、デフォルトフォールバック、
失効モデル、Workflow 利用不可をマークします。`impact` はメモリ内で指定ルールを除外後に同じアルゴリズムを再利用し、データベースは変更しません。

### 17.4 購買申請

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/procurement/requisition/list` | `procurement:requisition:list` | `keyword/status/categoryCode/page/size` |
| GET | `/api/procurement/requisition/{id}` | `procurement:requisition:list` | 通常詳細は引き続き requester DataScope で制約 |
| GET | `/api/procurement/requisition/{id}/approval-view?taskId={taskId}` | `procurement:requisition:approve` | まず Workflow がタスクが現在ユーザーと本購買申請に属することを検証 |
| POST | `/api/procurement/requisition` | `procurement:requisition:create` | DRAFT を作成 |
| PUT | `/api/procurement/requisition/{id}` | `procurement:requisition:update` | DRAFT/REJECTED のみ；REJECTED は更新後 DRAFT に戻る |
| DELETE | `/api/procurement/requisition/{id}?version={version}` | `procurement:requisition:delete` | DRAFT のみ |
| POST | `/api/procurement/requisition/{id}/submit` | `procurement:requisition:submit` | body `{ "version": 0 }` |
| POST | `/api/procurement/requisition/{id}/retry-start` | `procurement:requisition:submit` | `SUBMITTED + FAILED` のみ、元の Workflow 冪等スナップショットを再利用 |
| POST | `/api/procurement/requisition/{id}/cancel` | `procurement:requisition:cancel` | DRAFT または `SUBMITTED + FAILED` のみ |

作成/更新リクエスト例：

```json
{
  "title": "研究開発ノートPC調達",
  "reason": "新規社員の入社",
  "lines": [
    {
      "materialId": 101,
      "quantity": "2.000000",
      "estimatedUnitPrice": "8500.000000",
      "remark": "16GB メモリ以上"
    }
  ]
}
```

`lines[].quantity` と `lines[].estimatedUnitPrice` は JSON 十進文字列のみ受け付け；数値が JavaScript 安全範囲内でも JSON number は 400 を返します。

MVP はすべての行が同一カテゴリに属することを要求します。サービスは提出トランザクション内でアクティブな資材とカテゴリを一括再照会し、資材コード、名称、カテゴリ、単位スナップショットを刷新し行金額と総金額を再計算します；クライアントは総金額や `modelVersionId` を渡せません。新規提出ごとに `approvalAttempt + 1`、Workflow `businessKey={requisitionId}:{approvalAttempt}`；起動の不確実な失敗後の retry は永続化済みの `requestId/businessKey/modelVersionId` を再利用しなければなりません。

Workflow 完了イベントは `eventId` で `proc_event_inbox` に入ります。現在のラウンドのイベントは tenant、businessKey、processInstanceId と `APPROVING` ステータスがすべて一致する場合のみ購買申請を更新；旧ラウンドのイベントは冪等に無視し、ローカル起動確認より前のイベントは Inbox をロールバックしメッセージリトライをトリガーし、同一 eventId が異なる完全 payload に紐づくと 409 を返します。

### 17.5 見積依頼、比較と選定

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/procurement/rfq/supplier-options` | `procurement:rfq:create` または `procurement:rfq:list` | 現在のテナントの SRM 合格サプライヤー選択肢を照会 |
| GET | `/api/procurement/rfq/list` | `procurement:rfq:list` | `keyword/status/deadlineFrom/deadlineTo/page/size` |
| GET | `/api/procurement/rfq/{id}` | `procurement:rfq:list` | RFQ、行と招待スナップショットを照会 |
| GET | `/api/procurement/rfq/{id}/comparison` | `procurement:rfq:list` | SRM から現在の有効見積と完全な行スナップショットを再読込 |
| POST | `/api/procurement/rfq` | `procurement:rfq:create` | 承認済み購買申請から DRAFT を作成 |
| PUT | `/api/procurement/rfq/{id}` | `procurement:rfq:update` | DRAFT のみ；body は `version` を携行 |
| DELETE | `/api/procurement/rfq/{id}?version={version}` | `procurement:rfq:delete` | DRAFT のみ |
| POST | `/api/procurement/rfq/{id}/send` | `procurement:rfq:send` | body `{ "version": 0 }`、招待されたサプライヤーに公開 |
| POST | `/api/procurement/rfq/{id}/award` | `procurement:rfq:award` | 見積バージョンをロックし購買注文をアトミックに生成 |
| POST | `/api/procurement/rfq/{id}/cancel` | `procurement:rfq:cancel` | body `{ "version": 0 }`；DRAFT/SENT のみ |

作成と更新リクエストは `requisitionId/title/quotationDeadline/supplierIds` を含み；更新時の時間形式は
`yyyy-MM-dd HH:mm:ss` に統一。`SENT` の RFQ のみ比較と選定が可能です。招待ステータスは
`INVITED/QUOTED/EXPIRED/AWARDED/REJECTED`；選定後に落札招待は `AWARDED`、その他は
`REJECTED` になり、これらの終態はサプライヤーポータルの履歴閲覧専用で、見積を継続できません。

選定リクエスト例：

```json
{
  "rfqVersion": 2,
  "quotationId": 501,
  "quotationVersion": 3,
  "title": "研究開発ノートPC購買注文",
  "expectedDeliveryDate": "2026-08-15",
  "deliveryAddress": "上海市浦東新区サンプル路 1 号",
  "contactName": "山田太郎",
  "contactPhone": "13800000000"
}
```

サーバー側は同一トランザクションで RFQ と招待をロックし、SRM から `quotationId` の現在バージョン、tenant、サプライヤー、
通貨、有効期間および完全な行集合を再照会；`rfqVersion` または `quotationVersion` のいずれかが不一致なら 409 を返します。成功レスポンスは
`{ "rfq": ..., "purchaseOrder": ... }` で、不変な見積金額/納期スナップショットを保存；SRM の後続見積変化は既存の選定や購買注文を変更してはいけません。見積比較レスポンスの数量、単価、金額はすべて JSON 十進文字列です。

### 17.6 購買注文

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/procurement/purchase-order/list` | `procurement:purchase-order:list` | `keyword/status/expectedDeliveryFrom/expectedDeliveryTo/page/size` |
| GET | `/api/procurement/purchase-order/{id}` | `procurement:purchase-order:list` | 注文と不変な見積行スナップショットを照会 |
| PUT | `/api/procurement/purchase-order/{id}` | `procurement:purchase-order:update` | DRAFT のみ、タイトルと納品情報を変更可能 |
| DELETE | `/api/procurement/purchase-order/{id}?version={version}` | `procurement:purchase-order:delete` | DRAFT のみ |
| POST | `/api/procurement/purchase-order/{id}/send` | `procurement:purchase-order:send` | DRAFT → SENT、body は `version` を携行 |
| POST | `/api/procurement/purchase-order/{id}/confirm` | `procurement:purchase-order:confirm` | SENT → CONFIRMED、body は `version` を携行 |
| POST | `/api/procurement/purchase-order/{id}/cancel` | `procurement:purchase-order:cancel` | 入荷発生前のキャンセル、body は `version` を携行 |

外部 API は購買注文作成エンドポイントを提供しません；MVP 注文は RFQ 選定トランザクションでのみ生成でき、クライアントはサプライヤー、見積や
注文行を偽造できません。ステータスは `DRAFT/SENT/CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED/CANCELLED`。
リストの住所、連絡先、電話はデフォルトで秘匿化され、詳細は引き続き owner DataScope で制約；数量、単価、行金額、総金額は
常に JSON 十進文字列で返します。

### 17.7 入荷と品質検査

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/procurement/goods-receipt/list` | `procurement:goods-receipt:list` | `keyword/status/receiveTimeFrom/receiveTimeTo/page/size` |
| GET | `/api/procurement/goods-receipt/{id}` | `procurement:goods-receipt:list` | 入荷詳細を照会 |
| POST | `/api/procurement/goods-receipt` | `procurement:goods-receipt:create` | CONFIRMED/PARTIAL_RECEIVED 注文に DRAFT を作成 |
| POST | `/api/procurement/goods-receipt/{id}/confirm` | `procurement:goods-receipt:confirm` | body `{ "version": 0 }`、入荷を確認し注文の累計ステータスを更新 |
| POST | `/api/procurement/goods-receipt/{id}/quality-result` | `procurement:goods-receipt:confirm` | 確認済み入荷の PENDING 行のみを PASS/FAIL に変更 |

作成リクエストの `receiveTime` は `yyyy-MM-dd HH:mm:ss` を使用し、各行は
`poLineId/receivedQuantity/qualityStatus/remark` を含みます。`receivedQuantity` は JSON 十進文字列のみ受け付け、JSON
number は 400 を返します。DRAFT 作成は入荷済み数量を占有しません；確認トランザクションは購買注文をロックし、すべての CONFIRMED 入荷行で再累計検証し、並行過剰入荷を禁止します。部分と全量はそれぞれ注文を `PARTIAL_RECEIVED` と `RECEIVED` に進めます。

`qualityStatus=PASS`、資材 `assetManaged=true` かつ数量が正整数の行のみが資産候補に入ります。確認時に
`procurement.goods-receipt.confirmed.v1` を発行；PENDING がその後初めて PASS になったとき
`procurement.goods-receipt.quality-passed.v1` を発行し、同一バッチの新規通過行は 1 つのイベント ID を共有します。履歴補償読み取りは
`X-Internal-Token` に保護された
`GET /api/internal/procurement/goods-receipt/asset-candidates?tenantId={tenantId}&afterId={id}&size={size}` を使用；
リアルタイム消費とバックスキャンはいずれも `tenantId + goodsReceiptLineId + unitSequence` で冪等です。

2 つのイベントと履歴候補は入荷管理帰属 `ownerUserId/ownerUnitId` を携行しなければならず、Asset はそれを新資産の
管理帰属として継承；フィールドが欠落または正整数でない場合はフェイルクローズします。イベント行の `receivedQuantity/unitPrice/totalPrice`
は引き続き JSON 十進文字列を使用し、単位レベルカウント `assetQuantity` のみ正整数を使用します。Asset リアルタイムコンシューマーは
`consumerName + eventId` で Inbox 冪等ラッチも確立；同一イベント ID や来源単位が異なる完全なビジネス意図に紐づく場合は
競合を返し、作成済み資産を上書きしてはいけません。

### 17.8 調達概要

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/procurement/overview/summary` | `procurement:overview:list` | 調達フローの待办、注文ステータスと通貨別コミット金額 |
| GET | `/api/procurement/overview/spend-analysis?dimension={dimension}&limit={limit}` | `procurement:overview:list` | 次元と通貨別に確認済み調達支出を集約 |

`dimension` は必須で `CATEGORY`、`SUPPLIER`、`DEPARTMENT` のみ許可；`limit` はデフォルト 20、範囲 1–100。
DEPARTMENT は購買注文の担当部門 `ownerUnitId` を表します。支出は
`CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED` 購買注文のみを集計し、ドラフト、送信のみ、キャンセル済み注文は含みません。

サマリーレスポンス例：

```json
{
  "pendingApprovalRequisitionCount": 3,
  "waitingQuotationRfqCount": 2,
  "purchaseOrderStatusCounts": [
    { "status": "DRAFT", "count": 1 },
    { "status": "SENT", "count": 2 },
    { "status": "CONFIRMED", "count": 4 },
    { "status": "PARTIAL_RECEIVED", "count": 1 },
    { "status": "RECEIVED", "count": 5 },
    { "status": "CLOSED", "count": 8 },
    { "status": "CANCELLED", "count": 1 }
  ],
  "draftGoodsReceiptCount": 2,
  "committedAmountsByCurrency": [
    { "currencyCode": "CNY", "amount": "120000.0000" },
    { "currencyCode": "USD", "amount": "8500.0000" }
  ]
}
```

支出分析項目は `dimension/dimensionKey/dimensionName/currencyCode/amount` を含み、まず
`currencyCode` 昇順、次に同一通貨の `amount` 降順に並べます。`amount` は常に JSON 十進文字列；
異なる通貨は独立した記録を保持しなければならず、サーバー側もフロントエンドも直接加算してはいけません。サマリーの各集約 SQL は対応する購買申請、RFQ、購買注文または入荷集約ルートを直接ヒットし、
通常リストと同じ requester/owner DataScope と TenantLine を適用；支出分析は購買注文 owner 範囲を使用し、集約クエリでデータ権限を回避してはいけません。

---

## 18. Asset MVP 契約

### 18.1 共通境界

- 外部 Base path は `/api/asset`；Gateway は完全パスを保持し `StripPrefix` を使用しません。
- 外部リクエストは Gateway が注入した `X-User-Id`、`X-Tenant-Id`、`X-Username`、ロールと権限ヘッダーを使用。ビジネステーブルは常に TenantLine で制約され、管理リスト、サブリソースと概要はさらに permission-aware DataScope で制約されます。
- 管理リストは `owner_user_id/owner_unit_id` でフィルタ；`GET /api/asset/asset/my` は常に `current_user_id` で照会し、同一ユーザーが管理ロールを持つことで拡大できません。
- 書き込みコマンドは `version` を携行し楽観的ロック検証を実行；バージョンやアクティブ操作占有の不一致はビジネス競合を返します。
- 資産原価、残存価値と集計金額は `DECIMAL(18,2)` を使用し、リクエストとレスポンスはいずれも JSON 十進文字列を使用；JSON number は 400 を返します。通貨は 3 桁 ISO 4217 コードを使用。
- 資産ステータスは `IN_STOCK/ALLOCATED/IN_USE/MAINTENANCE/TRANSFER/DISPOSAL_PENDING/DISPOSED/SCRAPPED`。
- 内部エンドポイントは `/api/internal/asset/**` を使用し、`X-Internal-Token` と `X-Tenant-Id` を携行しなければならず、Gateway で明示的にブロックされます。

### 18.2 資産台帳とコマンド

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/asset/asset/list` | `asset:asset:list` | `keyword/status/categoryCode/ownerUnitId/locationCode/page/size`、管理帰属で照会 |
| GET | `/api/asset/asset/my` | `asset:asset:self` | `keyword/status/categoryCode/page/size`、常に現在の使用者を照会 |
| GET | `/api/asset/asset/{id}` | `asset:asset:list` | 管理範囲内の資産詳細を照会 |
| GET | `/api/asset/asset/{id}/history` | `asset:asset:list` | `page/size`、不変なステータス履歴を照会 |
| POST | `/api/asset/asset` | `asset:asset:create` | `IN_STOCK` 資産を手動作成 |
| PUT | `/api/asset/asset/{id}` | `asset:asset:update` | 基本資料を更新；ステータス、使用者、場所を直接更新不可 |
| DELETE | `/api/asset/asset/{id}?version={version}` | `asset:asset:delete` | ビジネス動作が未発生の手動在庫資産のみ削除 |
| POST | `/api/asset/asset/{id}/allocate` | `asset:asset:allocate` | `IN_STOCK → ALLOCATED` |
| POST | `/api/asset/asset/{id}/accept` | `asset:asset:accept` | 現在の使用者が `ALLOCATED → IN_USE` を実行 |
| POST | `/api/asset/asset/{id}/return` | `asset:asset:return` | 現在の使用者が返却、`IN_STOCK` に復元し使用帰属をクリア |
| POST | `/api/asset/asset/{id}/maintenance/start` | `asset:asset:maintenance` | `IN_USE → MAINTENANCE` |
| POST | `/api/asset/asset/{id}/maintenance/complete` | `asset:asset:maintenance` | `MAINTENANCE → IN_USE` |
| GET | `/api/asset/options/users` | 資産台帳/割当/移管/処分の関連権限のいずれか | 現在のテナントの有効ユーザー候補、主組織を返し、電話/メールを含まない |
| GET | `/api/asset/options/suppliers` | `asset:asset:create/update` | 現在のテナントの承認済みサプライヤーのキーワード候補 |
| GET | `/api/asset/options/transfer-assets` | `asset:transfer:create` | 現在の DataScope 内でアクティブ占有がなく移管可能なステータスの資産 |
| GET | `/api/asset/options/disposal-assets` | `asset:disposal:create` | 現在の DataScope 内でアクティブ占有がなく処分可能なステータスの資産 |

手動作成リクエストは
`name/categoryCode/specification/brand/model/supplierId/supplierNameSnapshot/purchaseDate/purchaseAmount/currencyCode/locationCode/warrantyExpiryDate/expectedLifeYears/remark/ownerUserId/ownerUnitId` を含みます。
`purchaseAmount` はデフォルトで `null` 可、非空時は JSON 十進文字列でなければならず；`currencyCode`、`ownerUserId` と `ownerUnitId` は必須。更新リクエストは必須の `version` を追加しますが `locationCode` は受け付けません。

割当リクエストは：

```json
{
  "version": 0,
  "targetUserId": 101,
  "targetUnitId": 12,
  "remark": "研究開発設備の受領"
}
```

受領、返却とメンテナンスコマンドは `{ "version": 0, "remark": "..." }` を使用。`accept/return` は権限検証に加え、資産の
`current_user_id` が現在のユーザーに等しいことも検証しなければならず；管理範囲はこの行ごとの帰属検証を代替できません。

### 18.3 Procurement 入荷連携

Asset は `procurement.goods-receipt.confirmed.v1` と
`procurement.goods-receipt.quality-passed.v1` を消費します。イベントエンベロープと入荷行フィールドは 17.7 を権威とし；Asset は
`qualityStatus=PASS && assetManaged=true && assetQuantity>0` の単位レベル資産のみを処理します。

- リアルタイム消費は `consumerName + eventId` で `ast_inbox_event` に書き込み、同一イベント ID が異なる完全なビジネス意図に紐づかないことを検証します。
- リアルタイム消費と履歴バックスキャンは共同で
  `tenantId + goodsReceiptLineId + unitSequence` により来源一意キーを確立し、いかなる入口も資産を重複作成できません。
- 新資産は入荷管理帰属 `ownerUserId/ownerUnitId` を継承し、PO、GR、サプライヤー、資材、カテゴリ、通貨と金額スナップショットを保存します。
- 内部制御の補償エンドポイントは
  `POST /api/internal/asset/procurement/backfill?tenantId={tenantId}&afterId={id}&size={size}`；リクエストヘッダー
  `X-Tenant-Id` は query `tenantId` と完全一致しなければなりません。`size` は 1–100、レスポンスは本ページの処理結果と次のカーソルを返します。

### 18.4 移管

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/asset/transfer/list` | `asset:transfer:list` | `keyword/status/page/size`、関連資産を通じて管理 DataScope を継承 |
| GET | `/api/asset/transfer/{id}` | `asset:transfer:list` | 移管詳細を照会 |
| GET | `/api/asset/transfer/{id}/approval-view?taskId={taskId}` | `asset:transfer:approve` | Workflow が現在のタスク割当を検証後、tenant で読み取り専用承認ビューを読む |
| POST | `/api/asset/transfer` | `asset:transfer:create` | 申請を作成し、資産をアトミックに占有し Workflow を起動 |
| POST | `/api/asset/transfer/{id}/retry-start` | `asset:transfer:retry` | `PENDING_APPROVAL + PENDING` または `START_FAILED + FAILED` 申請に元の冪等スナップショットを再利用して起動 |
| POST | `/api/asset/transfer/{id}/cancel` | `asset:transfer:cancel` | `START_FAILED + FAILED` の明確な失敗申請のみキャンセルし資産を復元 |
| POST | `/api/asset/transfer/{id}/complete` | `asset:transfer:complete` | 承認通過後に引き継ぎを完了、資産は `IN_USE` に入る |

作成リクエストは：

```json
{
  "assetId": 10001,
  "toUserId": 102,
  "toUnitId": 12,
  "toLocation": "SH-A-03-021",
  "reason": "職位変更"
}
```

作成は資産が `IN_STOCK/ALLOCATED/IN_USE` かつアクティブ操作がない場合のみ許可。申請は元の使用帰属、場所と
`previousAssetStatus` を保存。サーバー側は現在のテナントと `category=ASSET_TRANSFER` で公開済みかつ起動可能な Workflow モデルバージョンを自動解決し冪等スナップショットを永続化；クライアントは `modelVersionId` を提供または選択してはいけません。
`retry-start/cancel/complete` の body はいずれも `{ "version": 0 }`。

### 18.5 破棄とスクラップ処分

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/asset/disposal/list` | `asset:disposal:list` | `keyword/disposalType/status/page/size`、関連資産を通じて管理 DataScope を継承 |
| GET | `/api/asset/disposal/{id}` | `asset:disposal:list` | 処分詳細を照会 |
| GET | `/api/asset/disposal/{id}/approval-view?taskId={taskId}` | `asset:disposal:approve` | Workflow が現在のタスク割当を検証後、tenant で読み取り専用承認ビューを読む |
| POST | `/api/asset/disposal` | `asset:disposal:create` | 申請を作成し、資産をアトミックに占有し Workflow を起動 |
| POST | `/api/asset/disposal/{id}/retry-start` | `asset:disposal:retry` | `PENDING_APPROVAL + PENDING` または `START_FAILED + FAILED` 申請に元の冪等スナップショットを再利用して起動 |
| POST | `/api/asset/disposal/{id}/cancel` | `asset:disposal:cancel` | `START_FAILED + FAILED` の明確な失敗申請のみキャンセルし資産を復元 |
| POST | `/api/asset/disposal/{id}/complete` | `asset:disposal:complete` | 承認通過後に実物処分を完了 |

作成リクエストは
`assetId/disposalType/reason/residualValue/disposalMethod` を含み；`disposalType` は
`DISCARD/SCRAP` のみ許可、`residualValue` は非空時 JSON 十進文字列でなければなりません。申請は
`ASSET_DISPOSAL + businessKey` でサーバー側が自動解決した `category=ASSET_DISPOSAL` の Workflow モデルを起動し、
クライアントは `modelVersionId` を提供してはいけません。
`DISCARD` 完了後に資産は `DISPOSED` に入り、`SCRAP` 完了
後に `SCRAPPED` に入り、いずれも回復不可の終態です。

### 18.6 Workflow 完了イベントと操作ステータス

移管と処分のステータスは
`PENDING_APPROVAL/START_FAILED/APPROVED/REJECTED/COMPLETED/CANCELLED` に統一され、Workflow 起動ステータスは
`PENDING/STARTED/FAILED`。

Workflow は 16.3 の `workflow.process.completed.v1` で承認結果を公開します。Asset コンシューマーは：

1. `eventId/eventType/producer/tenantId/businessType/businessKey/processInstanceId/result` を検証；
2. `consumerName + eventId` で Inbox 冪等ラッチを確立し、同一イベント ID が異なる完全 payload に紐づくことを拒否；
3. 現在の申請、確認済みプロセスインスタンスとアクティブステータスと完全一致するイベントのみ受け入れ；
4. ローカル起動確認より前に到達したイベントは Inbox をロールバックしメッセージリトライをトリガー；
5. `APPROVED` は申請をビジネス完了待ちに進めるのみ；`REJECTED/CANCELLED` は同一トランザクションで
   `previousAssetStatus` を復元し、申請をクローズし資産の `active_operation_*` をクリア。

Workflow 起動呼び出しはローカル作成トランザクションのコミット後に発生します。ネットワーク異常、409/その他の結果を確定できない非 200 レスポンス、またはローカル確認失敗は
いずれもリモートで受理済みの可能性があり、したがって `PENDING_APPROVAL + PENDING` を保持し、ローカルキャンセルを禁止し、同一冪等スナップショットでのリトライを許可します。
Workflow ビジネスレスポンス 404 はモデルバージョンが起動不可かつリモートトランザクションがインスタンスを作成しなかったことを意味し、申請は
`START_FAILED + FAILED` に入る；この明確な失敗ステータスはリトライやローカルキャンセルを許可します。
两类のリトライはいずれも冪等スナップショットを再利用しなければならず：`businessType` は移管/処分集約タイプから固定に導出され、永続化済みの
`requestId/businessKey/modelVersionId/workflowStartUserId/workflowStartUserName` を再利用し、
異なるユーザーがリトライを実行する場合も元の発起者身分を使い続けることを含み、第 2 のプロセスインスタンス作成を禁止します。

### 18.7 資産概要

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/asset/overview/summary` | `asset:overview:list` | 管理範囲内の各ステータス数量と通貨別原価 |
| GET | `/api/asset/overview/distribution?dimension={dimension}&limit={limit}` | `asset:overview:list` | ステータス、カテゴリ、管理部門または場所で集約 |

`dimension` は必須で `STATUS/CATEGORY/DEPARTMENT/LOCATION` のみ許可；`limit` はデフォルト 20、範囲 1–100。
すべての集約 SQL は管理台帳と同じ owner DataScope と TenantLine を適用しなければなりません。金額は通貨別に独立した記録を保持し十進文字列を出力し、異なる通貨を直接加算してはいけません。
