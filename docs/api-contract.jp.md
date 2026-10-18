# API 契約

> 本書はレスポンス、フィールド、ルート、ID ヘッダー、サービス間メッセージの現在の通信形式を定義します。実際の動作はソース、設定、Controller/DTO が決定します。例の ID、日時、住所、状態は構造の説明用であり、現在の環境データや検証結果ではありません。
> 状態遷移と実装上の制約は各ドメインの事実・設計文書で管理します。設定、ログイン、ブラウザー操作、復旧は [Wiki](wiki/index.jp.md) と[認証ガイド](guides/authentication.jp.md)を参照してください。ローカルのプロトコル認可と外部ソーシャルログインは別のフローです。

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

通常の業務 Controller は `R<T>`、ページ取得は `R<PageResult<T>>` を返します。OAuth2/OIDC プロトコル、ソーシャルログインのリダイレクト、Actuator、フィルターやフレームワークが生成するレスポンスに、この形式を一律に適用することはできません。

```json
{"code":200,"message":"success","data":{"id":1}}
```

```json
{"code":500,"message":"操作失败","data":null}
```

```json
{"code":400,"message":"username: 用户名不能为空","data":null}
```

`R.ok(data)` は業務コード 200、message `success` を使用します。`R.ok()` と失敗レスポンスの data は null です。`R.fail(message)` の既定コードは 500 で、コード指定のオーバーロードは指定値を使用します。JSON プロパティの順序は契約に含みません。

バックエンドの唯一の型は [R.java](../omni-backend/omni-common-core/src/main/java/com/omni/common/core/result/R.java) です。フロントエンドの唯一の宣言は [src/types/api.ts](../omni-frontend/src/types/api.ts) の `ApiResponse<T>` です。`data: T` という宣言は実行時の null を除去しません。本書ではコンパイル可能な型を複製せず、利用側は各エンドポイントの空値を処理します。

---

## 2. エラーコード一覧

### 2.1 システムエラーコード

HTTP ステータスと `R.code` は別々に確認します。

| HTTP ステータス | 業務コード | 現在の対応 |
|---|---|---|
| 200 | 200 | 通常の業務成功 |
| 200 | 例外発生箇所で指定したコード。既定は 500 | `BusinessException`。業務 400/403/404/409/503 を含む |
| 400 | 400 | 対応する advice が処理する Bean 検証、バインド、query パラメーター欠落、型不一致 |
| 403 | 403 | `AccessDeniedException/AuthorizationDeniedException` |
| 404 / 405 / 415 | HTTP ステータスと同じ | advice が処理するリソース、メソッド、メディア型のエラー |
| 500 | 500 | 専用ハンドラーのない例外 |

JSON 読み取りエラーの対応はサービスごとに異なります。SRM/Procurement の advice は `HttpMessageNotReadableException` を 400 に変換しますが、現在の Asset の共通ハンドラーでは 500 になります。制御された MockMvc では、Asset 金額を JSON number または不正な十進数文字列で送ると 500 になりました。リクエストが有効という意味ではなく、全サービスで同じステータスを保証するものでもありません。

### 2.2 業務エラーコード

業務コードは現在の例外発生箇所で決まり、message から推測しません。CAPTCHA、アカウント、テナントの失敗には既定の 500 を使う経路があります。業務 400、403、404、409、503 が HTTP 200 の本文に入る場合もあります。フロントエンドに 410、423、429、502 の表示分類があっても、すべてのエンドポイントがその分岐を実装しているわけではありません。

### 2.3 Gateway エラーコード

| HTTP ステータス / code | 条件 | 現在の message |
|---|---|---|
| 401 / 401 | Bearer 認証がない | 请先登录 |
| 401 / 401 | JWT の期限切れ | 登录已过期，请重新登录 |
| 401 / 401 | 失効記録への一致を含む、その他の JWT/セキュリティ検証失敗 | 认证失败，请重新登录 |
| 503 / 503 | test 以外の設定で署名鍵がない・弱い、または署名生成失敗 | 网关身份签名未就绪，请联系管理员 |

これは `AuthFilter` の分岐です。他のフィルター、レート制限、下流サービス、フレームワークには別のエラー対応があります。表の中国語は診断用の実際の message 値であり、フロントエンドの認証状態判定には使いません。

### 2.4 ソーシャルログインエラーコード

`user_denied/invalid_callback/social_login_failed` は 302 Location の query 値であり、`R.code` ではありません。条件と遷移先は第 10 節で定義します。

### 2.5 フロントエンドのエラー処理

[request.ts](../omni-frontend/src/api/request.ts) と [error-display.ts](../omni-frontend/src/utils/error-display.ts) は HTTP ステータス、業務コード、レスポンスがない場合の Axios 通信エラーコードを分類します。HTTP 401/403 を本文の業務コードより優先し、それ以外は業務コード、HTTP ステータスの順に使用します。

- 業務失敗は `businessCode/httpStatus/traceId/i18nKey/displayMessage` を持つ `ApiError` を送出します。HTTP 失敗では AxiosError を保持して情報を追加します。同じコンストラクターを使う二つの経路ではありません。
- サーバーや Axios の生の message は直接表示しません。コードに基づく四言語の安全な文言を使い、400 の項目別ヒントには検証済みの ASCII フィールド名だけを使います。
- 401 でも認証情報を送るエンドポイントではセッションと入力を保持します。それ以外はセッションの有無で未ログインと期限切れを区別し、どちらも `shouldLogout=true` として安全にセッションを削除しログイン画面へ移動します。認証情報用の判定対象は `/auth/login`、`/auth/session-login`、`/auth/register`、`/auth/social-login`、`/srm/portal/auth/login` です。この分類は Gateway の許可リストとは異なります。
- 403 はセッションを保持します。400/409/5xx、通信失敗、タイムアウトは各分類の翻訳文を使い、技術メッセージから意味を推測しません。競合時の再取得とリトライ判断は呼び出し側が行います。

---

## 3. ページネーション契約

バックエンドの型は [PageResult.java](../omni-backend/omni-common-core/src/main/java/com/omni/common/core/result/PageResult.java)、フロントエンドの型は [src/types/api.ts](../omni-frontend/src/types/api.ts) に一本化されています。レスポンスの data は次を含みます。

| フィールド | 意味 |
|---|---|
| `records` | 現在のページのレコード |
| `total` | 総レコード数 |
| `size` | ページサイズ |
| `current` | 現在のページ番号 |
| `pages` | コンストラクターが `(total + size - 1) / size` で計算 |

query の一般例は `page=1&size=10` です。範囲、既定値、フィルター、上限は各 Controller/検索 DTO が定義し、例の既定値が全エンドポイントの制限になるわけではありません。コンストラクターは渡された records を保持し、null→[] の共通変換やページ引数検証は行いません。有効な size を指定し、空レコードは各エンドポイントの定義に従って処理します。

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

**Gateway パス接頭辞**：通常のフロントエンド業務リソースは `/api/<service>/<resource>` を使います。例は /api/auth/user/list です。Auth、Base、Workflow、CRM、SRM、Procurement、Asset は StripPrefix を使わず完全なパスを保持します。プロトコルと内部パスは各エンドポイントの定義に従います。

表は業務リソースの命名規約です。操作コマンド、OAuth2/OIDC、内部 API は実際の Controller のルートを使用し、例から未実装のエンドポイントを推定してはいけません。

---

## 5. Gateway ルーティング設定

### 5.1 ローカル開発環境のルート

現在の [Gateway application.yml](../omni-backend/omni-gateway/src/main/resources/application.yml) の `spring.cloud.gateway.server.webflux.routes` は 12 個の明示的なルートを定義し、サービス検出による自動ルーティングは無効です。

| ルート ID | Path 一致 | 転送先 |
|---|---|---|
| `block-internal-api` | `/internal/**,/api/internal/**` | `no://op` |
| `block-internal-api-services` | `/omni-auth/internal/**,/omni-auth/api/internal/**,/omni-base/internal/**,/omni-base/api/internal/**,/omni-workflow/internal/**,/omni-workflow/api/internal/**,/omni-crm/internal/**,/omni-crm/api/internal/**,/omni-srm/internal/**,/omni-srm/api/internal/**,/omni-procurement/internal/**,/omni-procurement/api/internal/**,/omni-asset/internal/**,/omni-asset/api/internal/**,/omni-gateway/internal/**` | `no://op` |
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` |
| `omni-base` | `/api/base/**` | `lb://omni-base` |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` |
| `omni-crm` | `/api/crm/**` | `lb://omni-crm` |
| `omni-srm` | `/api/srm/**` | `lb://omni-srm` |
| `omni-procurement` | `/api/procurement/**` | `lb://omni-procurement` |
| `omni-asset` | `/api/asset/**` | `lb://omni-asset` |

通常の業務ルートは `StripPrefix` を使わず完全なパスを保持します。二つの `no://op` ルートは `SetStatus=404` で内部パスを遮断しますが、認証などの前段フィルターが先に別のエラーを返す場合があります。

### 5.2 Docker デプロイ時のルート

正式な Compose は Nacos で同じ `lb://` サービス名を解決します。到達できるかは環境、設定の上書き、起動済みモジュールに依存し、ルート設定だけでは下流の正常性を証明できません。例えば `/api/base/dict/type/list` はそのまま Base に到達し、`/api/job/**` も Base が受け取ります。

### 5.3 AuthFilter 許可リスト

完全一致：`/api/auth/login`、`/api/auth/session-login`、`/api/auth/register`、`/api/auth/captcha`、`/api/auth/tenants`、`/favicon.ico`。

前方一致：`/api/auth/oauth2/`、`/actuator/`、`/oauth2/`、`/.well-known/`、`/login`、`/error`。startsWith による判定であり、完全一致に置き換えてはいけません。`/api/auth/**` 全体が公開されるわけでもありません。

許可リストはこのフィルターの JWT 検証だけを省略し、ルート、Controller、下流の認可を作成しません。公開リクエストでも偽装された信頼ヘッダーと Bearer Authorization を除去し、OAuth2 に必要な Basic Authorization を保持して `X-Gateway-Forwarded=true` を設定します。

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

### 8.1 Gateway が設定するリクエストヘッダー

保護対象パスの JWT 検証後、Gateway はクライアントの Authorization と同名の信頼ヘッダーを削除し、次を設定します。

| ヘッダー | 取得元・意味 |
|---|---|
| `X-User-Id` | JWT ユーザー ID。正整数 |
| `X-Tenant-Id` | JWT テナント ID。正整数 |
| `X-User-Name` | JWT ユーザー名。`X-Username` ではない |
| `X-User-Roles` | ロール claim |
| `X-User-Scopes` | 権限 claim |
| `X-Gateway-Forwarded` | `true` |
| `X-Gateway-Assertion` | Gateway が生成した ID アサーション |

ロール・権限の claim が List ならコンマで連結し、その他の非 null 値は文字列、欠落時は空文字列にします。アサーションは `userId|tenantId|username|issuedAt` を HMAC-SHA256 で署名し、padding なしの `base64url(payload):base64url(signature)` を使用します。test 以外の設定では強い秘密鍵が必要です。サービス側も事前認証チェーンで検証し、同名ヘッダーがあるだけで ID を信頼してはいけません。

### 8.2 フロントエンドが送るヘッダー

Axios は `useUserStore().token` から `Authorization: Bearer <JWT>` を設定し、`getTenantIdFromToken(token)` でテナントヘッダーを取得します。独立して編集できるテナント項目を信頼する方式ではありません。Gateway は保護対象の信頼ヘッダーを上書きします。JSON の Content-Type は実際の本文に従い、共有 Axios インスタンスに明示的な共通 Content-Type 設定はありません。

### 8.3 サービス内部のリクエストヘッダー

本書の業務内部 API は `X-Internal-Token` と明示的な `X-Tenant-Id` を使用し、エンドユーザー JWT で内部認証を代用しません。JSON 本文には `Content-Type: application/json` を指定します。body/query tenantId が必要なエンドポイントではヘッダーとの一致も確認し、不一致時のエラーは各節で定義します。

検証器はサービス設定に従って動作します。`omni-common-service` の内部フィルターは無効なリクエストトークンに HTTP 401 を返しますが、関連機能が必要とする秘密トークンが未設定・弱い場合は設定検証器が起動を止めます。互換用 MQ `InternalApiAuthFilter` はサーバートークン未設定で HTTP 503、リクエストトークン欠落・不一致で 401 を返します。全サービスに共通の実行時 503 分岐ではありません。

内部ルートは Gateway が明示的に遮断し、クライアントの `X-Gateway-Forwarded` やロール・権限ヘッダーを信頼しません。信頼済み内部呼び出し元も毎回正しい業務テナントを渡す必要があり、トークン自体はテナントを選択しません。

### 8.4 セキュリティレスポンスヘッダー（Gateway が設定）

| レスポンスヘッダー | 現在の値 |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `X-Trace-Id` | tracer の実 trace ID。span がなければハイフンを除いた 32 文字の UUID |

[SecurityHeadersFilter](../omni-backend/omni-gateway/src/main/java/com/omni/gateway/config/SecurityHeadersFilter.java) は公開リクエストの同名 trace ヘッダーを除去します。実際の tracer span があればその ID を下流リクエストとレスポンスに設定します。span がなければレスポンス専用の fallback を生成し、下流リクエストには設定しません。レスポンスヘッダーだけで Gateway、Servlet、Feign の完全な関連付けを証明することはできません。フロントエンドは 1–128 文字の安全な ASCII traceId だけを受け入れ、異常値を拒否します。

---

## 9. 認証ヘッダー

フロントエンドのインターセプターが `Authorization: Bearer <token>` を設定します。Gateway は JWT 署名、期限、正整数のユーザー・テナント、適用対象の失効記録を検証して ID を設定します。現在の失効検証は JWT の jti に依存し、jti のないトークンにもブラックリスト照会済みとは言えません。公開範囲の完全一致・前方一致は第 5.3 節に一本化し、ユーザー・ロール管理には有効な認証とエンドポイント権限が必要です。

---

## 10. ソーシャルログインエンドポイント

次のブラウザーナビゲーション用エンドポイントは正常な認可フローで HTTP 302 を返し、`R<T>` で包みません。提供者識別子は `github`、`google`、`gitee` です。

| メソッド | URL / パラメーター | 契約 |
|---|---|---|
| GET | `/api/auth/oauth2/{provider}?tenant_id=1` | `tenant_id` 必須。提供者とテナントを検証してから提供者の認可画面へ302 |
| GET | `/api/auth/oauth2/{provider}/callback`。`code`、`state`、`error` は任意のバインド引数 | 下表の拒否、引数欠落、業務失敗、成功を処理。任意バインドでも成功には code/state が必要 |

| コールバック条件 | HTTP 302 Location |
|---|---|
| 提供者の `error` が空白以外 | フロントエンド `/login?error=user_denied` |
| `code` または `state` が欠落/空白 | フロントエンド `/login?error=invalid_callback` |
| state 検証・提供者のコード交換失敗等の `BusinessException` | フロントエンド `/login?error=social_login_failed&message=<URLエンコード済みメッセージ>` |
| 成功 | 設定されたフロントエンド `/callback#token=<URLエンコード済みJWT>&username=<URLエンコード済みユーザー名>` |

`OAuth2StateUtils` の形式は `tenantId|timestamp|hex(HMAC-SHA256(tenantId|timestamp, secret))` です。完全性を検証し、10分を超えて古い時刻を拒否してテナントを復元します。現実装には消費済み記録、ランダム nonce、ブラウザーセッションとの結合がなく、完全な一回限りの CSRF・リプレイ防御を証明しません。アプリ資格情報は外部ユーザーのログイン・同意を代替しません。

正式 Docker Compose の入口は Gateway 経由の `http://localhost:8102/api/auth/oauth2/{provider}/callback` です。本番は設定した Frontend/Gateway の HTTPS ドメインを使用します。提供者登録値は実際の redirect_uri と一致させ、非公開コンテナアドレスを公開入口にしません。開始時の引数・提供者・テナント検証失敗は Auth のエラー処理に従い、302 を保証しません。

ソース：[SocialLoginController](../omni-backend/omni-auth/src/main/java/com/omni/auth/controller/SocialLoginController.java)、[OAuth2StateUtils](../omni-backend/omni-auth/src/main/java/com/omni/auth/util/OAuth2StateUtils.java)、[Compose 設定](../compose.apps.yaml)。設定、画面操作と失敗再現は[認証ガイド](guides/authentication.jp.md)が管理します。ローカル認可コード・PKCE とデバイスコードは別のプロトコルです。

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
PUT /api/auth/xss-config/toggle?enabled=true
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
| PUT | `/api/auth/xss-config/rules/{id}/toggle?enabled=true` | `system:xssconfig:update` | ルール有効/無効の切り替え |

**ruleType 列挙値**：`HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### 権限コード

| 権限コード | 説明 |
|--------|------|
| `system:xssconfig:list` | XSS 設定とルールを閲覧 |
| `system:xssconfig:update` | グローバルスイッチの切り替え、ルール更新、ルールステータスの切り替え |
| `system:xssconfig:create` | ルール作成 |
| `system:xssconfig:delete` | ルール削除 |

ルールの更新・削除・状態切替は Gateway が注入する `X-Tenant-Id` で所属を確認します。不在または他テナントの場合、更新とキャッシュ失効前に HTTP 200 + `R.code=404` を返します。テナントが正整数でない場合は業務コード 400。既存の権限コードは維持します。

スイッチ API の `enabled` は必須クエリです。作成には非空の `ruleName`、`ruleType`、`pattern` が必要で、更新は非 null フィールドを扱い、指定された pattern の正規表現構文を検証します。対応型は上記のとおりですが、現在の DTO に列挙検証はなく、不明な実行型は処理を行いません。適用条件、Jackson 2/3、権威読取とキャッシュ境界は[核心フロー](core-flows.jp.md)を参照してください。

---

## 12. Base サービス辞書管理エンドポイント

Base は Gateway の `/api/base/**` ルート経由で完全パスの API を公開する。認証チェーンと既定ポートはコアフローを参照。タイプとデータに各 6 エンドポイントがあり、検証済みの現テナント文脈を使用する。

### 辞書タイプのエンドポイント

基底パス: `/api/base/dict/type`

| メソッド | パス | 権限 | 動作 |
| --- | --- | --- | --- |
| GET | `/api/base/dict/type/list` | `dict:type:list` | テナント別ページ検索。typeCode/typeName 部分一致、任意 status、sort/id 昇順 |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | 現テナントのタイプ取得。欠落・他テナント ID は業務コード 404 |
| POST | `/api/base/dict/type` | `dict:type:create` | typeCode/typeName は空白不可。コードはテナント内で一意 |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | 非 null フィールドのみ更新。typeCode は変更不可 |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | 所属検証後、同テナント同タイプのデータを連鎖削除 |
| PUT | `/api/base/dict/type/{id}/status?status=...` | `dict:type:update` | status クエリは必須。更新後にキャッシュ無効化 |

ページ既定値は `page=1,size=10`。タイプ検索は `typeCode/typeName/status` を任意指定する。タイプ作成は `remark/sort`、データ作成は `tagType/remark/sort` が任意。サービスが `status=1`、sort 未指定時は 0 を設定する。

### 辞書データのエンドポイント

基底パス: `/api/base/dict/data`

| メソッド | パス | 権限 | 動作 |
| --- | --- | --- | --- |
| GET | `/api/base/dict/data/options?typeCode=...` | 認証済み。個別操作権限なし | 認証のみ。value/label の有効データ選択肢をキャッシュして返す |
| GET | `/api/base/dict/data/list?typeCode=...` | `dict:data:list` | 管理ページは DB を直接参照。typeCode 必須、sort/id 昇順 |
| POST | `/api/base/dict/data` | `dict:data:create` | typeCode/dictValue/dictLabel は空白不可。現テナントの親タイプ存在を検証 |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | 非 null フィールドのみ更新。DTO に typeCode はない |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | 所属検証後に削除してキャッシュ無効化 |
| POST | `/api/base/dict/data/refresh-cache?typeCode=...` | `dict:data:refresh` | typeCode クエリ必須。キー削除後、有効データを取得して再格納 |

更新 DTO は非 null フィールドのみ処理し、null 送信による消去には対応しない。`/options` は `value` と `label` のみを持つ `R<List<DictOptionVO>>` を返す。管理ページは `R<PageResult<SysDictType>>` または `R<PageResult<SysDictData>>`。

### 辞書権限コード

| 権限コード | 用途 |
| --- | --- |
| `dict:type:list` | タイプ一覧・詳細 |
| `dict:type:create` | タイプ作成 |
| `dict:type:update` | タイプ更新・状態変更 |
| `dict:type:delete` | タイプ連鎖削除 |
| `dict:data:list` | 管理データ一覧 |
| `dict:data:create` | データ作成 |
| `dict:data:update` | データ更新 |
| `dict:data:delete` | データ削除 |
| `dict:data:refresh` | キャッシュ更新 |

### テナント分離

一覧・作成・選択肢・更新はテナントを明示的に渡す。6 個の ID 指定読み書き入口も Gateway の `X-Tenant-Id` をサービスへ渡し、正の ID と所属を検証してから返却・書き込み・キャッシュ操作を実行する。タイプ一意制約は `(tenant_id,type_code)`。

欠落・他テナント ID はともに `BusinessException(404)` となり、共通ハンドラーが HTTP 200 + `R.code=404` に変換する。テナント ID が欠落または非正数なら業務コード 400。Gateway とメソッド権限の検証は別途実行する。キャッシュ・クライアントのライフサイクル・ソース入口はコアフローとフロントエンド規約に集約する。

[Flow 9](core-flows.jp.md) / [Composable](frontend-patterns.jp.md)

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

結果は設定値から計算します。`outboxWriteEnabled=true`、`deliveryEnabled = omni.mqlog.relay.enabled && xxl.job.executor.enabled`（どちらも読取時の既定値は true）で、mode は `FULL/OUTBOX_ONLY` です。relay プロセス、XXL-JOB のスケジュール、Broker、DB、メッセージ到達は検査しません。FULL は関連スイッチが有効、OUTBOX_ONLY は少なくとも一つの配信スイッチが無効であることを示します。信頼性と復旧は [MQ 信頼性](mq-reliability.jp.md)を参照してください。

---

## 13. API バージョン管理ポリシー

現在の Gateway と Controller は `/api/auth/user/list` 等、バージョン接頭辞のない `/api/...` を使用します。`/api/v1/...`・`/api/v2/...` の並行ルートは未実装です。

将来の URL バージョン、破壊的変更の移行と旧版の保持期間は実装済み契約ではありません。必要なら設計・ADR で利用者、互換範囲、移行とサポート期間を先に決め、実装と文書を同期します。未実装の接頭辞や6か月保守を保証しません。現在のフィールド、エラーと状態は本契約とソースに従います。

---

## 14. Null セマンティクス

`R.ok()` と失敗レスポンスの data は null で、現在の通常 mapper はその null フィールドを出力します。[] になるのは生成側が実際に空の List を返した場合です。`PageResult` は null records を保持し、共通の変換保証はありません。任意項目の null、空文字列、省略は各 DTO、serializer、エンドポイントに従います。

JSON バインド、業務入力項目、ID の取得元は別の制約です。未知のプロパティが一律に 400 になるわけではなく、制御された現在の Asset DTO バインドでは追加項目が無視されました。DTO にない tenant、user、金額スナップショットが操作可能な入力になるわけではありませんが、パーサーが偽装項目を必ず明示的に拒否するとも言えません。定義された項目だけを送信してください。

---

## 15. SRM MVP 契約

### 15.1 サプライヤーとサブリソース

- 管理側のサプライヤーライフサイクルコマンドはすべて `version` を含めます；ブラックリスト復旧は
  `POST /api/srm/supplier/{id}/restore-from-blacklist` を使用します。
- 連絡先、資格、銀行口座パス内の `supplierId` はサブリソースの実際の帰属と一致しなければなりません；不一致は一律 404 を返します。
- `creditCode` はテナント内で一意；ページネーション `size` は最大 100。
- サプライヤー 360 は `GET /api/srm/supplier/{id}/overview` を使用し、返却内容は呼び出し元のサブリソース権限と PII 権限で引き続きトリミングされます。

### 15.2 ポータル登録

`POST /api/srm/portal/enroll` は Gateway が注入した tenant/user の IDのみを受け付け、リクエストは tenantId や userId を含めてはいけません：

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

登録 DTO の必須項目は requestId（非空・最大64）、inviteToken（非空・最大256）、name（非空・最大200）、creditCode（非空・最大50）です。creditCode は trim して大文字にします。例は実在企業の登録コードではありません。通常の未知項目とは異なり、この DTO は JsonAnySetter で tenantId/userId を含むすべての未定義項目を明示的に拒否します。SRM advice は JSON 読み取り例外を400に変換します。

### 15.3 評価とリスク

- `GET /api/srm/evaluation/template/default/dimensions` は現在のテナントのデフォルトテンプレートと有効な次元を返します；フロントエンドはデータベース ID をハードコードしてはいけません。
- 採点範囲は 1–5 で、デフォルトテンプレートのすべての次元をカバーし、重複してはいけません。
- `GET /api/srm/risk/list` はサプライヤーの最新評価で集約したリスクサマリーのページネーションを返し、`riskLevel` でフィルタリングできます。
- `GET /api/srm/supplier/{id}/risk` は `indicators/latestAssessment/history` の集約ビューを返します。
- リスク指標の更新は `version` を指定します。総合等級が非 RED から RED に変わった場合のみ `srm.risk.level-changed.v1` を生成します。

評価作成ではテナントの有効なデフォルトテンプレートをサービスが選び、DTO に templateId はありません。items は 1–100 件で有効な全評価軸を重複なく含めます。score は 1–5、小数は最大一桁です。dimensionId はデフォルトテンプレート API から取得し、テンプレート、評価軸名、重みはサーバーが決定します。

### 15.4 内部サプライヤーサマリー

後続の Procurement/Asset は `X-Internal-Token` と `X-Tenant-Id` の両方を指定する場合のみ呼び出せます：

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
      "unit": "EA",
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
| `lines[].remark` | String | いいえ | 最大500。サービスが trim し空白を正規化 |

`tenantId/supplierId/rfqNo/material/quantity/currencyCode/lineAmount/totalAmount` はこの DTO で操作できる入力ではありません。サービスが信頼済み ID、PortalUser、Procurement 招待スナップショットから取得・計算します。追加 JSON 項目は無視される場合があり、必ず明示的エラーになるとは限りません。`R<QuotationVO>` はヘッダー、version、行スナップショットを含み、金額・数量の通信形式は次節で定義します。

冪等と並行ルール：

- `srm_quotation_request` はテナント内 requestId、正規化した SHA-256、quotationId、targetVersion を保存します。過去の COMPLETED は、新規リクエストのサプライヤー状態、RFQ、期限、招待明細の検証より先に再生されます。現在の見積スナップショットを返し、見積・Outbox を再度書き込みません。成功再生で新規送信の検証をすべて繰り返すわけではありません。
- 同じ requestId で hash、supplierId、rfqId が異なる場合、RESERVED・無効な記録の場合、または現在の見積が targetVersion より古い場合は業務 409 です。
- 新規リクエストは現在承認済みのサプライヤーから送信し、SENT RFQ、INVITED/QUOTED 招待、期限を読み直します。validUntil は現在より後、RFQ 期限以降でなければなりません。1–200 行で全 RFQ 行を過不足・重複なく含めます。依存先が利用不可なら 503 で閉じます。
- `(tenantId, rfqId, supplierId)` ごとに未削除見積は最大一件です。初回 version=0 は version 1 として保存し、更新には現在の version を使い、競合は 409 です。
- requestHash は requestId を含まず、`rfqId/version/validUntil/lines` を対象とします。rfqLineId 順に並べ、単価を小数 6 桁の非指数表記に正規化し、備考は trim して null/空白を統一します。生の JSON バイト列はハッシュしません。`unitPrice × quantity` は小数 4 桁で HALF_UP してから合計します。

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
`R<T>` を引き続き使用します。詳細な実行メカニズムは [workflow.jp.md](workflow.jp.md#6-内部起動と完了イベント) を参照。

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

冪等記録はテナント内の `(tenantId, requestId)` と `(tenantId, businessType, businessKey)` の二つの一意キーを使います。競合時に比較するのは業務種別・キー、modelVersionId、startUserId です。title、variables、startUserName は比較せず、完全な payload hash もありません。

成功記録は元の processInstanceId と replayed=true を返しますが、requestId は今回の値を返します。処理中、業務・モデル・開始ユーザーの不一致は業務 409 です。同じ requestId の FAILED 分岐は原子的にリトライを確保できますが、別 requestId で同じ FAILED 業務キーに一致してもその分岐には自動で入りません。

実際の新規開始では PUBLISHED と非空 processDefinitionId を再検証し、`ASSET_TRANSFER/ASSET_DISPOSAL/SRM_SUPPLIER_ONBOARDING` の分類を完全一致させます。title が空白なら `{businessType}:{businessKey}` です。任意の variables が信頼済み ID になるわけではありません。開始ユーザー、テナント、保護変数、ルーティングの制約は [Workflow](workflow.jp.md)を参照してください。

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

ヘッダーと本文の tenantId が異なる場合は先に業務 403 を返します。その後、Flowable タスクと拡張記録のテナント、businessType/businessKey、現在の ASSIGNEE または未取得タスクの CANDIDATE を確認します。タスクなし、業務不一致、資格なしは `R.code=200,data.valid=false` で、assignmentType は `ASSIGNEE/CANDIDATE/NONE` です。false は承認コマンド実行時の権限・並行実行検証の代わりにはなりません。

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

接続済みの承認完了と明示的終了の経路が、完了状態・completionEventId の更新と PENDING Outbox 追加を同じローカルトランザクションで行います。`completion_event_id IS NULL` が論理完了イベントの重複を防ぎます。すべての Flowable engine-end や即時終了を扱う共通リスナーはありません。成功や Outbox コミットは Broker 配信・消費完了を意味しません。有効な配信機能と利用可能な依存先がある場合に relay が非同期で配信・再試行し、消費側は eventId で冪等化します。

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

テナントを限定し、PUBLISHED と非空 processDefinitionId を要求します。存在しない・利用不可のバージョンは業務 404 です。modelKey と BPMN process id の一致は公開時の検証が維持し、返却文字列だけで engine の現在の実行可能性を証明することはできません。

分類別の現在バージョンは `GET /api/internal/workflow/model-version/current?category={category}` でも取得できます。新規開始では `ASSET_TRANSFER/ASSET_DISPOSAL/SRM_SUPPLIER_ONBOARDING` を分類に結び付け、同じ意図の成功再生は既存インスタンスを使います。内部終了は `POST /api/internal/workflow/process-instance/{processInstanceId}/terminate?reason={reason}` で、内部トークンに加えて X-Tenant-Id、X-User-Id を明示します。reason は任意です。

### 16.5 承認ルールの読み取り専用モデル集約

| メソッド | パス | 現在の制約 |
|---|---|---|
| GET | `/api/internal/workflow/model-versions/published?category=purchase` | 現在のテナント、完全一致の分類、有効なモデル、現在の公開版、非空 processDefinitionId |
| POST | `/api/internal/workflow/model-versions/resolve` | body は `{ "modelVersionIds": [1, 2] }`。1–200 個の正整数を初出順で重複排除 |
| GET | `/api/internal/workflow/model-version/{id}/preview` | 安全な承認グラフとメタデータ。生の BPMN XML/designerJson は返さない |

availability は `AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND` で、モデル・バージョンのメタデータから計算します。Flowable のデプロイ、停止、実行可否をリアルタイムで検査するものではありません。

ルート項目は `approvalPreviewVersion/modelVersion/nodes/edges/hasBranches/linearSummary` です。nodes は `id/name/type/roleCode/approvalMode/description`、type は `START/END/APPROVAL/GATEWAY/SERVICE`。edges は `id/name/source/target/defaultBranch/conditionSummary` です。条件は実際の固定文言「已配置条件（内容已隐藏）」だけを返します。分岐があるか単一路線の要約を作れない場合、linearSummary は null です。現在の組織プレビューを将来の実際の承認者とみなしてはいけません。

### 16.6 デザイナーの候補者プレビュー

`POST /api/workflow/identity/resolve-preview` は `workflow:identity:list` と信頼済みテナントヘッダーが必要です。入力は `assignmentType/roleCode/anchorType/anchorParams/scopeMode/simulateUserId` で assignmentType は必須です。data は `candidateCount/candidates`、候補者は `userId/username/nickname/unitName` を含みます。解決失敗・互換モード・基点の意味は [Workflow 第 3 節](workflow.jp.md#3-候補者リスナー複数人承認)を参照し、内部の安全なグラフ DTO と混同しないでください。

### 16.7 インスタンス参照のテナント境界

外部 `GET /api/workflow/process-instance/{id}/progress` と `GET /api/workflow/process-instance/{id}/approval-records` は Gateway の X-Tenant-Id を明示的に使います。欠落・不正値は失敗し、正でないテナントは業務 400 です。同じテナントの履歴インスタンスがない場合と他テナントの場合は、活動・コメント・候補者を読む前に共に業務 404 です。参加者 ACL を追加する変更ではなく、すべての履歴定義 API に同じ保護があることも意味しません。

## 17. Procurement MVP 契約

### 17.1 共通境界

- 外部基底パスは `/api/procurement` で完全なパスと Gateway の ID・権限を使います。TenantLine は proc_* 業務表だけを対象とし、Outbox スキャンはこのテナントフィルターを使いません。
- DataScope は購買申請に requester_user_id/requester_unit_id、RFQ/PO/GR に owner_user_id/owner_unit_id を使い、子行は集約ルートから継承します。品目、分類、承認ルート、設定はテナント内で共有し、存在しない owner 列は追加しません。
- 数量・単価は DECIMAL(19,6)、金額は DECIMAL(19,4) で、レスポンスは十進数文字列です。入力も各 DTO の文字列制約に従い、業務金額を JavaScript number で計算してはいけません。
- 更新・削除・バージョン付き状態コマンドは DTO に従って版を指定します。RFQ 選定は rfqVersion/quotationVersion を使うため、すべての書き込みに version 項目があるわけではありません。作成には既存行の版はありません。
- 内部 `/api/internal/procurement/**` は X-Internal-Token/X-Tenant-Id を要求し Gateway が遮断します。状態・トランザクション境界は [Procurement 設計](design/procurement-design.jp.md)を参照してください。

### 17.2 資材とカテゴリ

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/procurement/material/category/list` | `procurement:material:list` | 最大 2 階層のカテゴリツリーを返す |
| POST | `/api/procurement/material/category` | `procurement:material:create` | カテゴリを作成；`categoryCode` は作成後変更不可 |
| PUT | `/api/procurement/material/category/{id}` | `procurement:material:update` | body は `version` を指定 |
| DELETE | `/api/procurement/material/category/{id}?version={version}` | `procurement:material:delete` | 子カテゴリや資材が存在する場合は 409 を返す |
| GET | `/api/procurement/material/list` | `procurement:material:list` | `keyword/categoryId/status/assetManaged/page/size` |
| GET | `/api/procurement/material/{id}` | `procurement:material:list` | 資材詳細を照会 |
| POST | `/api/procurement/material` | `procurement:material:create` | 資材を作成；`materialCode` は作成後変更不可 |
| PUT | `/api/procurement/material/{id}` | `procurement:material:update` | body は `version` を指定 |
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
| PUT | `/api/procurement/rfq/{id}` | `procurement:rfq:update` | DRAFT のみ；body は `version` を指定 |
| DELETE | `/api/procurement/rfq/{id}?version={version}` | `procurement:rfq:delete` | DRAFT のみ |
| POST | `/api/procurement/rfq/{id}/send` | `procurement:rfq:send` | body `{ "version": 0 }`、招待されたサプライヤーに公開 |
| POST | `/api/procurement/rfq/{id}/award` | `procurement:rfq:award` | 見積バージョンをロックし購買注文をアトミックに生成 |
| POST | `/api/procurement/rfq/{id}/cancel` | `procurement:rfq:cancel` | body `{ "version": 0 }`；DRAFT/SENT のみ |

作成は `requisitionId/title/quotationDeadline/supplierIds`、下書き更新は `version/title/quotationDeadline/supplierIds` を受け付け、requisitionId は変更しません。quotationDeadline は `yyyy-MM-dd HH:mm:ss` の未来日時で、supplierIds は 1–100 個の正整数です。比較・選定は SENT RFQ のみ可能です。招待状態は `INVITED/QUOTED/EXPIRED/AWARDED/REJECTED` で、最後の二つは履歴参照だけを許可し、見積を再送できません。

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
| POST | `/api/procurement/purchase-order/{id}/send` | `procurement:purchase-order:send` | DRAFT → SENT、body は `version` を指定 |
| POST | `/api/procurement/purchase-order/{id}/confirm` | `procurement:purchase-order:confirm` | SENT → CONFIRMED、body は `version` を指定 |
| POST | `/api/procurement/purchase-order/{id}/cancel` | `procurement:purchase-order:cancel` | 入荷発生前のキャンセル、body は `version` を指定 |

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

2 つのイベントと履歴候補は入荷管理帰属 `ownerUserId/ownerUnitId` を指定しなければならず、Asset はそれを新資産の
管理帰属として継承；フィールドが欠落または正整数でない場合はフェイルクローズします。イベント行の `receivedQuantity/unitPrice/totalPrice`
は引き続き JSON 十進文字列を使用し、単位レベルカウント `assetQuantity` のみ正整数を使用します。Asset リアルタイムコンシューマーは
`consumerName + eventId` で Inbox 冪等ラッチも確立；同一イベント ID や取得元単位が異なる完全なビジネス意図に紐づく場合は
競合を返し、作成済み資産を上書きしてはいけません。

### 17.8 調達概要

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/procurement/overview/summary` | `procurement:overview:list` | 調達フローの未処理タスク、注文ステータスと通貨別コミット金額 |
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

- 外部基底パスは `/api/asset` で完全なパスを保持します。ID ヘッダーは `X-User-Id/X-Tenant-Id/X-User-Name` とロール・権限で、X-Username ではありません。TenantLine は ast_* だけに適用し、Outbox relay は全テナントを走査します。
- 管理参照は owner_user_id/owner_unit_id、子リソースは資産の範囲を継承します。自分の資産・受領・返却は current_user_id に固定され、管理権限によって個人資産の参照範囲は広がりません。
- 更新・削除・既存資産のコマンドは version を指定し、作成には既存版はありません。操作占有・バージョンの競合では書き込みを拒否します。
- 原価・残存価額・集計金額は DECIMAL(18,2) で、非 null の入出力は JSON 十進数文字列です。数値・不正文字列は拒否しますが、現在の共通 JSON 読み取り例外は 500 に変換され、400 は保証しません。currencyCode は英字三文字を検証するだけで、ISO 4217 登録表との照合ではありません。
- 状態は `IN_STOCK/ALLOCATED/IN_USE/MAINTENANCE/TRANSFER/DISPOSAL_PENDING/DISPOSED/SCRAPPED` です。
- 内部 `/api/internal/asset/**` は X-Internal-Token/X-Tenant-Id を要求し Gateway が遮断します。ライフサイクル・資産計上・承認トランザクションは [Asset 設計](design/asset-design.jp.md)を参照してください。

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
  `tenantId + goodsReceiptLineId + unitSequence` により取得元一意キーを確立し、いかなる入口も資産を重複作成できません。
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
二種類のリトライはいずれも冪等スナップショットを再利用しなければならず：`businessType` は移管/処分集約タイプから固定に導出され、永続化済みの
`requestId/businessKey/modelVersionId/workflowStartUserId/workflowStartUserName` を再利用し、
異なるユーザーがリトライを実行する場合も元の開始ユーザーの ID を使い続けることを含み、第 2 のプロセスインスタンス作成を禁止します。

### 18.7 資産概要

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/asset/overview/summary` | `asset:overview:list` | 管理範囲内の各ステータス数量と通貨別原価 |
| GET | `/api/asset/overview/distribution?dimension={dimension}&limit={limit}` | `asset:overview:list` | ステータス、カテゴリ、管理部門または場所で集約 |

`dimension` は必須で `STATUS/CATEGORY/DEPARTMENT/LOCATION` のみ許可；`limit` はデフォルト 20、範囲 1–100。
すべての集約 SQL は管理台帳と同じ owner DataScope と TenantLine を適用しなければなりません。金額は通貨別に独立した記録を保持し十進文字列を出力し、異なる通貨を直接加算してはいけません。
