# バックエンドパターンと規約

> 本ドキュメントは Omni-Stack バックエンドの内部構成を定義します。すべてのバックエンドコードはこれらのパターンに従う必要があります。  
> アーキテクチャ概要は [architecture.jp.md](architecture.jp.md) を参照してください。Docker デプロイ設定は [docker-deployment.jp.md](docker-deployment.jp.md) を参照してください。

## Layering

```
Controller --> Service --> Repository (DAO)
     |            |            |
  Param check   Business     Data access
  Result wrap   logic        SQL / ORM
                Transaction
```

### Controller Layer

- **責務**: HTTP リクエストの受信、パラメータの検証、Service の呼び出し、レスポンスのラッピング
- Controller に**ビジネスロジックを記述しない**
- すべてのメソッドは `R<T>`（成功）または `R<PageResult<T>>`（ページネーション）を返す
- リクエスト DTO は `@Valid`（Jakarta Bean Validation）で検証する
- DTO は Controller の内部静的クラスまたは独立したファイルとして定義できる
- RESTful スタイル: `GET /user/{id}`, `POST /user`, `GET /user/list`

### Service Layer

- **インタフェース + 実装**: `XxxService`（インタフェース）+ `XxxServiceImpl`（クラス）
- 実装クラスに `@Service` アノテーションを付与
- 実装メソッドに `@Transactional` を付与:
  - 読み取り操作: `@Transactional(readOnly = true)`
  - 書き込み操作: `@Transactional`
- Service レイヤーに `HttpServletRequest` / `HttpServletResponse` を含めない
- `@RequiredArgsConstructor` + `final` フィールドによるコンストラクタインジェクション

### Repository / DAO Layer

- MyBatis-Plus または JPA を使用。Mapper インタフェースの命名: `XxxMapper`
- Mapper にビジネスロジックを記述しない
- SQL パラメータ: 常に `#{}` を使用し、`${}` は使用しない（SQL インジェクション防止）

## Dependency Injection (DI)

```java
// CORRECT: Lombok によるコンストラクタインジェクション
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}

// FORBIDDEN: フィールドインジェクション
@Autowired
private UserService userService;
```

**ルール**: すべての依存性注入は `@RequiredArgsConstructor` + `final` フィールドを使用する必要があります。`@Autowired` によるフィールドインジェクションは禁止です。

### 設計上の根拠: なぜ @RequiredArgsConstructor を使い @Autowired を使わないのか

| 考慮点 | 根拠 |
|---------------|-----------|
| **不変性** | `final` フィールドにより、依存関係は一度構築されると変更不可となり、実行時の意図しない置換を防止 |
| **コンパイル時安全性** | 依存関係の欠落はコンパイルエラーとなり、実行時の `NullPointerException` ではなく早期に検出可能 |
| **テストのしやすさ** | コンストラクタインジェクションにより、Spring コンテナやリフレクションユーティリティなしで Mock オブジェクトを直接渡せる |
| **明確性** | すべての依存関係がクラスのコンストラクタに一目で分かり、`@Autowired` を探すために全フィールドをスキャンする必要がない |
| **Spring 公式推奨** | Spring チームは 4.x からコンストラクタインジェクションを推奨しており、フィールドインジェクションは 5.x 以降で非推奨とされている |

## Validation

- リクエスト DTO に Jakarta Bean Validation アノテーションを使用
- Controller メソッドパラメータに `@Valid` を付けて検証を発動
- `MethodArgumentNotValidException` と `BindException` は `GlobalExceptionHandler` でグローバルに捕捉

```java
@Data
public static class CreateUserRequest {
    @NotBlank(message = "Username is required")
    private String username;
    @NotBlank(message = "Email is required")
    private String email;
}
```

## Exception Handling

### BusinessException

```java
// ビジネスルール違反
throw new BusinessException("User not found");           // code: 500
throw new BusinessException(404, "User not found");      // code: 404
```

### GlobalExceptionHandler

`omni-common` に配置され、すべての例外を捕捉して `R<Void>` に変換します:

| 例外 | 処理 |
|-----------|----------|
| `BusinessException` | `log.warn` + `R.fail(code, message)` |
| `MethodArgumentNotValidException` | HTTP 400 + フィールドエラーの集約 + `R.fail(400, ...)` |
| `BindException` | HTTP 400 + フィールドエラーの集約 + `R.fail(400, ...)` |
| `Exception`（catch-all） | `log.error`（完全なスタックトレース付き）+ `R.fail("Internal server error")` |

### ルール

- 空の `catch` ブロックを使用しない
- フロー制御に例外を使用しない
- NPE 防御: `Optional`、`Objects.requireNonNull()`、早期 null チェックを使用
- 例外は `log.error("msg", e)` で完全なスタックトレースを保持してログ出力。`e.printStackTrace()` は使用しない

## Logging

Lombok `@Slf4j` と `log` オブジェクトを使用:

| レベル | 用途 |
|-------|-------|
| `ERROR` | 即座の対応が必要なシステムレベルのエラー |
| `WARN` | ビジネス例外、回復可能な問題 |
| `INFO` | 重要なビジネスフローのチェックポイント |
| `DEBUG` | 開発およびデバッグ |

```java
// CORRECT: パラメータ化プレースホルダー
log.info("User {} logged in from {}", userId, ip);

// FORBIDDEN: 文字列連結
log.info("User " + userId + " logged in");

// FORBIDDEN: コンソール出力
System.out.println("debug info");
```

- 機密情報（パスワード、トークン、ID番号）をログに出力しない

## OOP Conventions

- すべての POJO クラスは `Serializable` を実装し、`serialVersionUID` を宣言する
- Lombok を使用: `@Data`、`@Getter`、`@Slf4j`、`@RequiredArgsConstructor`
- クラスメンバーの順序: 静的定数 -> 静的変数 -> インスタンス変数 -> コンストラクタ -> public メソッド -> private メソッド
- `equals`: 定数/決定論的な値を左側に配置: `"success".equals(status)`
- ラッパー型: `valueOf()` を使用し、`new Integer()` は使用しない
- 浮動小数点比較: `BigDecimal` を使用するかイプシロンを指定

## Collection & Concurrency

- コレクションは初期容量を指定して初期化: `new ArrayList<>(16)`、`new HashMap<>(16)`
- 空チェック: `CollectionUtils.isEmpty()` を使用し、`== null` や `size() == 0` は使用しない
- イテレーション中の `remove` は禁止。`Iterator` または `removeIf()` を使用
- Map の走査: `entrySet()` を使用し、`keySet()` からの `get()` は使用しない
- スレッドプール: `ThreadPoolExecutor` を使用し、`Executors.newXxx()` は使用しない
- 並行修正: `ConcurrentHashMap`、`AtomicXxx` を使用。手動ロックは必要な場合にのみ使用

## Naming Conventions (Java)

| タイプ | スタイル | 例 |
|------|-------|---------|
| Package | lowercase, dot-separated | `com.omni.business.controller` |
| Class | UpperCamelCase | `UserController`, `BusinessException` |
| Method / Variable | lowerCamelCase | `getUserById`, `createTime` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE` |
| Boolean variable | No `is` prefix | `deleted`, `enabled` (not `isDeleted`) |
| Abstract class | `Abstract` prefix | `AbstractEntity` |
| Exception class | `Exception` suffix | `BusinessException` |
| Enum class | `Enum` suffix | `OrderStatusEnum` |
| DTO class | `Request` / `Response` / `VO` suffix | `CreateUserRequest`, `UserVO` |
| Feign interface | `FeignClient` suffix | `RemoteServiceFeignClient` |
| Service interface | `XxxService` | `UserService` |
| Service implementation | `XxxServiceImpl` | `UserServiceImpl` |
| Mapper interface | `XxxMapper` | `UserMapper` |

## Code Format (Java)

- インデント: 4 スペース、タブは使用しない
- 最大行長: 120 文字
- 中括弧: K&R スタイル（開き中括弧は同じ行）
- メソッド間に1行の空行
- 演算子の周りにスペース: `a + b`, `if (x == y)`
- カンマの後にスペース: `method(a, b, c)`
- メソッドパラメータは最大5つ。超える場合は Request オブジェクトにカプセル化
- インポート順序: `java.*` -> `jakarta.*` -> サードパーティ -> `com.omni.*`、グループ間に空行
- ワイルドカードインポート（`import xxx.*`）は禁止（Controller アノテーションパッケージを除く）

## Comments

- クラス、クラス属性、クラスメソッドには Javadoc（`/** ... */`）を記述する
- キーロジックの説明には `//` によるインラインコメントを使用
- 無意味なコメントは記述しない（例: `getName()` に対する `// get name`）
- TODO 形式: `// TODO: [module] description`、定期的にクリーンアップ
- FIXME 形式: 既知の問題に対して `// FIXME: description`

## Security & Permission

### Functional Permission (API Authorization)

Controller メソッドは `@PreAuthorize` で必要な権限コードを宣言します:

```java
@PreAuthorize("hasAuthority('system:user:create')")
@PostMapping
public R<Void> create(@Valid @RequestBody CreateUserRequest request) {
    userService.createUser(request);
    return R.ok();
}
```

- 権限コード形式: `resource:action`（例: `system:user:update`, `system:role:delete`）
- 権限セットは JWT Claims に含まれ、Spring Security がメソッド呼び出し前に自動的に検証
- 権限がない場合は 403 Forbidden を返す

### Data Permission (DataPermission)

MyBatis-Plus `DataPermissionInterceptor` に基づく行レベルのデータフィルタリングを実装し、ビジネスコードへの侵入をゼロにします。

**コアコンポーネントの連携**:

```
Gateway → X-User-Id/X-Tenant-Id Header
    ↓
DataScopeResolveFilter (@Order(0), OncePerRequestFilter)
    ↓ ロールを照会 → dataScope をマージ（最も寛大なものを優先）→ アクセス可能なユニット ID を解決
    ↓
DataScopeContext (ThreadLocal に userId, tenantId, primaryUnitId, effectiveScope, accessibleUnitIds を格納)
    ↓
DataPermissionInterceptor (MyBatis-Plus InnerInterceptor)
    ↓ DataPermissionHandlerImpl.getSqlSegment(Table, Expression, String) を呼び出し
    ↓ sys_user テーブルにのみ WHERE 句を追加
    ↓
ビジネス SQL の実行（自動的にフィルタリングされた結果セット）
    ↓
DataScopeContext.clear() (finally ブロック、ThreadLocal のリークを防止)
```

**6段階のデータスコープ**:

| dataScope | SQL の動作 |
|-----------|-------------|
| `ALL` | 条件を追加しない（テナント間でも可視） |
| `TENANT` | 条件を追加しない（既存の tenant_id フィルタで十分） |
| `DEPT_AND_BELOW` | `WHERE sys_user.primary_unit_id IN (現在の部門および子孫 ID)` |
| `DEPT` | `WHERE sys_user.primary_unit_id IN (現在の部門 ID)` |
| `CUSTOM` | `WHERE sys_user.primary_unit_id IN (カスタム部門 + 子孫 ID)` |
| `SELF` | `WHERE sys_user.id = {現在のユーザーID}` |

**マルチロールのマージ**: ユーザーが複数のロールを持つ場合、最も優先度の高い dataScope を使用します（ALL > TENANT > DEPT_AND_BELOW > DEPT > CUSTOM > SELF）。

**新規テーブルへのデータ権限の拡張**:

1. `DataPermissionHandlerImpl` にターゲットテーブル名と対応するカラムマッピングを追加
2. ターゲットテーブルには `sys_user` への外部キーカラム（例: `primary_unit_id`, `create_by`）が含まれている必要がある
3. `DataPermissionInterceptor` は `PaginationInnerInterceptor` より前に登録しなければならない

**メモリ内フィルタリングモード**: データベースクエリ以外のデータ（例: Redis のオンラインユーザーリスト）については、Controller が `DataScopeContext.get()` からデータスコープを読み取り、`primaryUnitId` で手動フィルタリングを行います。

### ThreadLocal Usage Guidelines

- `DataScopeContext` は `ThreadLocal<DataScopeInfo>` を使用してリクエストスコープのコンテキストを格納
- スレッドプール環境でのリークを防ぐため、`try/finally` ブロックで**必ず**クリアする
- 書き込みタイミング: `DataScopeResolveFilter.doFilterInternal()` の `try` ブロックより前
- クリアタイミング: `DataScopeResolveFilter.doFilterInternal()` の `finally` ブロック

### XSS Protection (Three-Layer Defense Architecture)

XSS 防御は3層の縦深防御を採用し、設定はデータベース + Redis キャッシュで管理され、テナントごとの分離をサポートします。

```
Layer 1: Jackson StringDeserializer — @RequestBody JSON 内の String フィールドを自動クリーニング
Layer 2: Servlet Filter + HttpServletRequestWrapper — クエリパラメータをクリーニング
Layer 3: Gateway WebFilter — セキュリティレスポンスヘッダーを追加
```

**コアコンポーネント**:

| コンポーネント | モジュール | 責務 |
|-----------|--------|----------------|
| `XssConfigProvider` | omni-common-core | SPI インタフェース、具体的なサービスモジュールが実装 |
| `XssSettings` / `XssRule` | omni-common-core | 設定値オブジェクト（enabled + ルールリスト） |
| `XssSanitizer` | omni-common | コア浄化ロジック（HTML_TAG / EVENT_HANDLER / DANGEROUS_PROTOCOL / CUSTOM_PATTERN） |
| `XssStringDeserializer` | omni-common | Jackson String デシリアライザラッパー、自動クリーニング |
| `XssFilter` | omni-common | OncePerRequestFilter、設定の読み込み + ThreadLocal の設定 |
| `XssHttpServletRequestWrapper` | omni-common | getParameter/getParameterValues をオーバーライド |
| `XssAutoConfiguration` | omni-common | Filter + Jackson SimpleModule の自動登録 |
| `XssConfigProviderImpl` | omni-auth | 設定の読み込み実装（Redis キャッシュ + DB フォールバック） |
| `SecurityHeadersFilter` | omni-gateway | X-Content-Type-Options / X-Frame-Options / Referrer-Policy の追加 |

**ルールタイプ**:

| ruleType | マッチングと浄化方式 |
|----------|-------------------------------|
| `HTML_TAG` | ペアタグと自己閉じタグを除去 |
| `EVENT_HANDLER` | `on*` 属性を除去 |
| `DANGEROUS_PROTOCOL` | `javascript:` / `vbscript:` / `data:` などのプロトコル文字列を置換 |
| `CUSTOM_PATTERN` | カスタム正規表現による置換 |

**新サービスへの拡張**: `XssConfigProvider` インタフェースを実装するだけで、XSS 防御機能を自動的に取得できます。`omni-common` の依存関係導入後、`AutoConfiguration.imports` により自動設定されます。

**キャッシュ戦略**: Redis キー `xss:enabled:{tenantId}` + `xss:rules:{tenantId}`、TTL 30分。すべての書き込み操作（オン/オフ切り替え、ルール CRUD）後にキャッシュを能動的に無効化します。

## MQ Message Sending Records & Compensation Management (omni-common-mqlog)

MQ メッセージ送信記録と補償管理システムは Transactional Outbox + XXL-JOB 非同期配信アーキテクチャに基づき、すべてのマイクロサービスに信頼性の高いメッセージ送信能力を提供します。プラグアンドプレイ、ビジネスコードゼロです。

### Core Architecture

```
ビジネストランザクション (@Transactional)
    ↓
ReliableMessageTemplate.send(bindingName, payload)
    ↓ INSERT sys_mq_message (status=PENDING) -- 同じローカルトランザクション内
    ↓
XXL-JOB mqRelayHandler (10s ポーリング)
    ↓
MqMessageRelayService.relayAll()
    ↓ PENDING/FAILED かつ next_retry_time <= NOW() を一括照会
    ↓
MessageSender.send(message) -- 戦略パターン、broker_type 別にルーティング
    ↓
成功 → status=SENT | 失敗 → retry_count++, 指数バックオフ | 上限超過 → DEAD_LETTER
```

### Core Components

| コンポーネント | 責務 |
|-----------|----------------|
| `ReliableMessageTemplate` | `send(bindingName, payload)` / `send(bindingName, payload, msgKey)` のオーバーロードを提供。呼び出し元のトランザクション内でメッセージレコードを INSERT |
| `MqMessageRelayService` | 配信待ちメッセージをポーリングし、`MessageSender` 戦略を呼び出して配信、リトライバックオフとデッドレターマークを処理 |
| `MqMessageRelayJob` | XXL-JOB handler (`@XxlJob("mqRelayHandler")` + `@SystemJobMeta`)、relay ロジックをトリガー |
| `MessageSender` | 戦略インタフェース、`broker_type` 別にルーティング。現在の実装: `RocketMqMessageSender`（StreamBridge ベース）、`KafkaMessageSender` の拡張が可能 |
| `MqMessageInternalController` | Feign 内部照会 API (`/api/internal/mq-message`)、集約照会サービス用 |

### Steps to Onboard a New Service

1. POM に `omni-common-mqlog` の依存関係を追加
2. `omni-common-mybatis`（データベース）と `omni-common-job`（XXL-JOB）が既に含まれていることを確認
3. RocketMQ 送信能力が必要な場合、`spring-cloud-starter-stream-rocketmq` の依存関係を追加
4. `sys_mq_message` テーブルは自動作成される（`schema.sql` + `CREATE TABLE IF NOT EXISTS`）
5. `mqRelayHandler` は XXL-JOB に自動登録される（各サービスエグゼキュータの AppName は異なるため、handler name は自然に分離される）
6. ビジネスコードに `ReliableMessageTemplate` をインジェクションし、`send()` を呼び出す

### Exponential Backoff Strategy

リトライ間隔: `2^retryCount × 10s`。1回目: 20s、2回目: 40s、3回目: 80s。`max_retry`（デフォルト 3）を超えると DEAD_LETTER ステータスに移行。

### Dead Letter Handling

- **再送**: PENDING/FAILED/DEAD_LETTER ステータスを PENDING にリセットし、`retry_count` をクリア。relay タスクの次のポーリングで再配信
- **無視**: DEAD_LETTER → SKIPPED、再配信不要を確認した最終状態

## Operation Log (OperLog)

操作ログシステムは AOP + RocketMQ 非同期アーキテクチャに基づき、Controller メソッドからリクエストコンテキストとエンティティ変更スナップショットを自動的に収集し、ビジネス操作の包括的な監査証跡を提供します。

### Core Recording Flow

```
Controller メソッド (@OperLog アノテーション)
    ↓
OperLogAspect (AOP @Around アドバイス)
    ↓ リクエストコンテキストを収集: username, tenantId, IP, URL, リクエストパラメータ
    ↓ エンティティ変更スナップショット: UPDATE/DELETE 操作前に oldValue を照会、操作後に newValue を照会
    ↓ EntityDiffer.diff(): フィールドレベルの diff 比較（UPDATE のみ）
    ↓
OperLogProducer.send(OperLogMessage)
    ↓ RocketMQ 非同期送信
    ↓
omni-base コンシューマー
    ↓ INSERT INTO sys_oper_log (ホットテーブル)
    ↓
OperLogArchiver (@Scheduled 毎日 02:00)
    ↓ 180日を超えるレコードをホットテーブルから sys_oper_log_archive (コールドテーブル) に移行
    ↓ バッチ処理（1バッチ 1000件）、移行後にホットテーブルから削除
```

### @OperLog Annotation Usage

```java
@OperLog(module = "User Management", operType = OperType.CREATE, entityClass = SysUser.class, idExpr = "#result.data.id")
@PreAuthorize("hasAuthority('system:user:create')")
@PostMapping
public R<UserVO> create(@Valid @RequestBody CreateUserRequest request) {
    return R.ok(userService.createUser(request));
}
```

**Annotation Parameter Reference**:

| パラメータ | タイプ | 必須 | 説明 |
|-----------|------|----------|-------------|
| `module` | String | Yes | ビジネスモジュール名、例: "User Management", "Dictionary Type Management" |
| `operType` | OperType | Yes | 操作タイプの列挙値（下表を参照） |
| `entityClass` | Class<?> | Conditional | ターゲットエンティティクラス、AOP が自動 diff スナップショットに使用。QUERY/EXPORT/IMPORT タイプでは指定不要 |
| `idExpr` | String | Conditional | SpEL 式、メソッドパラメータまたは戻り値からエンティティ ID を抽出。例: `"#id"`, `"#result.data.id"` |

### OperType Enum

| 列挙値 | 意味 | entityClass の指定 | 説明 |
|------------|---------|---------------------|-------------|
| `CREATE` | 新規作成 | 推奨 | AOP が戻り値から新規エンティティ ID を抽出し newValue を照会 |
| `UPDATE` | 更新 | Yes | AOP が操作前に oldValue、操作後に newValue を照会し、フィールドレベルの diff を実行 |
| `DELETE` | 削除 | Yes | AOP が操作前に oldValue を照会、newValue は null |
| `QUERY` | 照会 | No | 照会行為のみ記録、エンティティスナップショットなし |
| `EXPORT` | エクスポート | No | エクスポート行為のみ記録、エンティティスナップショットなし |
| `IMPORT` | インポート | No | インポート行為のみ記録、エンティティスナップショットなし |

### Development Constraints

1. **すべての新規書き込み操作 Controller メソッドに `@OperLog` を付与**し、`module` と `operType` を指定。エンティティ変更が伴う場合は `entityClass` と `idExpr` も指定
2. **omni-auth モジュールでは `@OperLog` を無効化**: 認証行為はログインログ（`sys_login_log`）と監査ログ（`sys_audit_log`）で完全に記録されるため、omni-auth では `omni-common-operlog` の依存関係を含めず、Controller メソッドに `@OperLog` を使用しない
3. **新マイクロサービスの導入**: `pom.xml` に `omni-common-operlog` の依存関係を追加し、RocketMQ を設定。モジュールは `AutoConfiguration.imports` により AOP アドバイスと MQ プロデューサーを自動登録
4. **`entityClass` の用途**: AOP が `ApplicationContext` を通じて対応するエンティティタイプの `BaseMapper` を検索し、自動的に `selectById` を実行して変更前後のスナップショットを取得
5. **`idExpr` SpEL 構文**: メソッドパラメータ（`#id`, `#request.id`）と戻り値（`#result.data.id`）の参照をサポート。解析失敗時は警告ログを記録するがビジネスロジックに影響しない
6. **JSON スナップショット制限**: 1件あたり oldValue/newValue 最大 4000文字。超える場合は自動切り詰め
7. **ホット/コールドテーブル分離**: ホットテーブル `sys_oper_log` は直近 180日間のデータを保持して高速照会に対応。コールドテーブル `sys_oper_log_archive` はコンプライアンス向けに長期保存。アーカイブタスクは毎日 02:00 に実行、1バッチ 1000件。失敗時は当該アーカイブを停止

### Module Responsibilities

| モジュール | コンポーネント | 責務 |
|--------|-----------|----------------|
| `omni-common-core` | `OperLog` アノテーション, `OperType` 列挙値, `OperLogMessage` POJO | 純 POJO レイヤー、Spring 依存なし |
| `omni-common-operlog` | `OperLogAspect`, `OperLogProducer`, `EntityDiffer`, `OperLogAutoConfiguration` | AOP アドバイス + MQ プロデューサー + エンティティ diff + 自動設定 |
| `omni-base` | `OperLogConsumer`, `OperLogArchiver` | MQ コンシューマーがホットテーブルに書き込み + コールドテーブルへの定期アーカイブ |

## Common Starter Onboarding Specification

プロジェクトは共通能力を組合せ Starter とオプション能力モジュールに分割します。Servlet ビジネスサービスは `omni-common-service` を優先的に使用し、Gateway・Auth・Workflow はそれぞれの特殊な境界に応じて下位モジュールを選択します。

### Starter モジュール概要

| モジュール | 責務 | 自動設定内容 | 適用サービスタイプ |
|--------|------|-------------|------------------|
| `omni-common-core` | 純 POJO 層 | なし（Spring 依存なし） | 全モジュール |
| `omni-common` | Web 自動設定 | `JacksonConfig`（時刻シリアライズ）、`WebMvcConfig`（CORS）、`GlobalExceptionHandler`、`XssAutoConfiguration`（Filter + Jackson Module） | Servlet サービス |
| `omni-common-mybatis` | データベース能力 | `MybatisPlusAutoConfiguration`：`MybatisPlusInterceptor`（MySQL `PaginationInnerInterceptor`）+ YAML デフォルト設定（キャメルケースマッピング、論理削除、自動増分 ID） | Servlet サービス |
| `omni-common-redis` | ブロッキング Redis | `RedisAutoConfiguration`：`RedisTemplate<String, Object>`（Jackson シリアライズ）+ `RedisUtils` ユーティリティ + Lettuce 接続プール設定 | Servlet サービス |
| `omni-common-redis-reactive` | リアクティブ Redis | `spring-boot-starter-data-redis-reactive` + YAML デフォルトタイムアウト設定 | WebFlux サービス（Gateway など） |
| `omni-common-mqlog` | 信頼性 MQ メッセージ送信 | `ReliableMessageTemplate`（Transactional Outbox）、`MqMessageRelayService`（XXL-JOB 非同期配信）、`MessageSender` ストラテジーインターフェース、`MqMessageInternalController`（Feign 内部照会）、`schema.sql`（自動テーブル作成） | Servlet サービス（MQ 能力が必要） |
| `omni-common-service` | Servlet ビジネスサービスのセキュリティとコンテキストの組合せ | Gateway 事前認証 Filter、不変リクエスト識別、内部 API Token、DataScope SPI/アスペクト、Tenant/DataPermission 固定順序、Auth XSS フォールバックとセキュリティベースライン | CRM/SRM/Procurement/Asset などの Servlet ビジネスサービス |

**自動設定登録メカニズム**：すべての starter は `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` ファイルで登録されます。これは Spring Boot 3+/4+ の標準メカニズムです（旧 `spring.factories` の代替）。

### 新規サービス接入手順

1. **POM 依存**：Servlet ビジネスサービスは組合せ Starter を宣言し、必要に応じて OperLog/Job/MQ を追加します：
   ```xml
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-service</artifactId></dependency>
   ```
2. **application.yml**：データソースと Redis に加えて、サービス識別と有効化するセキュリティ能力を明示的に設定します。内部 Token は環境変数から取得する必要があり、弱いデフォルト値は使用できません：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://127.0.0.1:3306/omni_xxx?useSSL=false&serverTimezone=Asia/Shanghai
       username: root
       password: root
     data:
       redis:
         host: 127.0.0.1
         port: 6379
         database: 0
   omni:
     service:
       name: omni-xxx
       display-name: Xxx
       gateway-preauth:
         enabled: true
       internal-api:
         enabled: true
         token: ${OMNI_INTERNAL_API_TOKEN}
       tenant:
         enabled: true
       data-scope:
         enabled: true
       xss:
         enabled: true
   ```
3. **ドメイン SPI**：tenant/data-scope を有効化する際は、それぞれ一意の `TenantTablePolicy` と `DataScopeTablePolicy` を提供します。ドメインのテーブル名や owner 列を Starter に入れてはいけません。
4. **セキュリティチェーン**：Starter が提供する Gateway Filter を `AuthorizationFilter` の前に、識別コンテキスト Filter を Gateway Filter の後に配置します。両者の Servlet コンテナによる重複登録は禁止されています。
5. **オプション能力**：OperLog・Job・MQ・Workflow は引き続き独立した依存として、業務ニーズに応じて有効化します。

現在 `omni-common-service` は v0 の自動設定とテストを提供しており、CRM・SRM・Procurement・Asset はすべて移行・モジュールテスト・分離ランタイム再検証を完了しています。Starter の XSS Provider 自動設定は `XssAutoConfiguration` より先に有効化される必要があり、コンテキストテストで Provider、Servlet FilterRegistration、Jackson 2/3 クリーニングモジュールを同時にアサートし、条件評価順序によるサイレント失效を回避します。

### Override Mechanism

すべての starter の自動設定 Bean は `@ConditionalOnMissingBean` を使用しており、サービス側で必要に応じてオーバーライドできます:

- **MybatisPlusInterceptor**: `mybatisPlusInterceptor` Bean を定義してデフォルトのページネーション設定をオーバーライドできます。典型的なケース: `DataPermissionInterceptor` の追加（**必ず `PaginationInnerInterceptor` より前に登録すること**）
- **RedisTemplate**: `@Bean(name = "redisTemplate")` を定義してデフォルトのシリアライゼーション戦略を置換できます
- **XSS 設定**: `XssAutoConfiguration` は `XssConfigProvider` Bean に条件依存しており、SPI が実装されていない場合は XSS フィルタチェーンが有効化されない

### Redis Starter Mutual Exclusion

`omni-common-redis`（ブロッキング）と `omni-common-redis-reactive`（リアクティブ）は**同一サービスで混在使用不可**:

- **WebFlux サービス**（例: Gateway）: `omni-common-redis-reactive` のみ使用可能。ブロッキング Redis 呼び出しは Netty イベントループスレッドのスターベーションを引き起こす
- **Servlet サービス**: `omni-common-redis`（ブロッキング）を使用。`RedisUtils` が同期 API を提供

## Configuration Reference

### application.yml Key Configuration Items

以下はバックエンドマイクロサービスの `application.yml` におけるコア設定項目です:

| 設定キー | 説明 | デフォルト | Docker 環境変数オーバーライド |
|------------|-------------|---------|-------------------|
| `server.port` | サービスポート | 8100/8101/8102/8103 | `SERVER_PORT=8080` |
| `spring.application.name` | Nacos サービス名 | omni-auth/base/gateway/workflow | — |
| `spring.datasource.url` | データベース接続 | `jdbc:mysql://127.0.0.1:3306/omni_xxx` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | データベースユーザー | `root` | `SPRING_DATASOURCE_USERNAME` |
| `spring.datasource.password` | データベースパスワード | `root` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.data.redis.host` | Redis ホスト | `127.0.0.1` | `SPRING_DATA_REDIS_HOST` |
| `spring.data.redis.port` | Redis ポート | `6379` | `SPRING_DATA_REDIS_PORT` |
| `spring.data.redis.database` | Redis DB インデックス | 0 | `SPRING_DATA_REDIS_DATABASE` |
| `spring.cloud.nacos.discovery.server-addr` | Nacos アドレス | `127.0.0.1:8848` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` |
| `spring.cloud.nacos.discovery.ip` | 登録 IP | `127.0.0.1` | `SPRING_CLOUD_NACOS_DISCOVERY_IP` |
| `auth.jwks.uri` | JWKS エンドポイント | `http://localhost:8100/oauth2/jwks` | `AUTH_JWKS_URI` |
| `auth.jwks.cache-ttl` | 公開鍵キャッシュ TTL | `5m` | — |
| `spring.profiles.active` | 有効化 Profile | `default` | `SPRING_PROFILES_ACTIVE` |

### MyBatis-Plus Configuration

| 設定キー | 説明 | デフォルト |
|------------|-------------|---------|
| `mybatis-plus.configuration.map-underscore-to-camel-case` | アンダースコアからキャメルケースへの変換 | `true` |
| `mybatis-plus.global-config.db-config.logic-delete-field` | 論理削除フィールド | `deleted` |
| `mybatis-plus.global-config.db-config.logic-delete-value` | 論理削除値 | `1` |
| `mybatis-plus.global-config.db-config.logic-not-delete-value` | 論理未削除値 | `0` |
| `mybatis-plus.global-config.db-config.id-type` | ID 戦略 | `AUTO`（データベースオートインクリメント） |

### XXL-JOB Configuration (omni-base)

| 設定キー | 説明 | デフォルト |
|------------|-------------|---------|
| `xxl.job.admin.addresses` | Admin アドレス | `http://127.0.0.1:18080/xxl-job-admin` |
| `xxl.job.executor.appname` | エグゼキュータ名 | `omni-base` |
| `xxl.job.executor.port` | エグゼキュータポート | `9999` |
| `xxl.job.accessToken` | 通信トークン | `default_token` |

## Service Layer Design Patterns

### Interface + Implementation Separation

```
UserService (interface)     — ビジネスメソッドのシグネチャを定義
    ↑ implements
UserServiceImpl (@Service)  — ビジネスロジックの実装 + @Transactional
```

**設計上の根拠**:
- インタフェースレイヤーは OpenFeign クライアントで再利用可能
- 単体テストでインタフェースを直接 Mock でき、実装クラスに依存しない
- 実装クラスを置き換えても呼び出し先に影響しない

### Transaction Management Strategy

| シナリオ | アノテーション | 説明 |
|----------|------------|-------------|
| 読み取り専用クエリ | `@Transactional(readOnly = true)` | DB クエリのパフォーマンスを最適化、書き込み操作を禁止 |
| 書き込み操作 | `@Transactional` | デフォルト REQUIRED 伝播レベル |
| クロスサービス呼び出し | `@Transactional` + Outbox パターン | ローカルトランザクションで Outbox テーブルに書き込み、リモートトランザクションに直接関与しない |
| 独立トランザクション | `@Transactional(propagation = REQUIRES_NEW)` | 例: ロギング — メイントランザクションがロールバックされてもログが保持されることを確保 |

### Exception Handling Chain

```
Controller メソッド
    │ Service メソッドを呼び出し
    ▼
Service レイヤー
    │ ビジネス検証失敗 → throw new BusinessException(400, "Username already exists")
    │ リソースが存在しない   → throw new BusinessException(404, "User not found")
    ▼
GlobalExceptionHandler (@RestControllerAdvice)
    │ @ExceptionHandler(BusinessException.class)
    │   → log.warn + R.fail(code, message)
    │ @ExceptionHandler(MethodArgumentNotValidException.class)
    │   → HTTP 400 + フィールドエラーの集約 + R.fail(400, "field: message")
    │ @ExceptionHandler(AccessDeniedException.class)
    │   → HTTP 403 + R.fail(403, "Insufficient permissions")
    │ @ExceptionHandler(Exception.class)
    │   → log.error（完全なスタックトレース）+ R.fail("Internal server error")
    ▼
統一 R<Void> レスポンス
```

**omni-auth の特別な処理**: Auth モジュールは `omni-common-core`（`omni-common` ではない）に依存しているため、`GlobalExceptionHandler` はコンポーネントスキャンの範囲外です。Auth モジュールは `AuthExceptionHandler`（スコープ付き `@RestControllerAdvice`）を通じて同等の例外処理を提供します。

## Troubleshooting Guide

### Common Issues

| 問題 | 考えられる原因 | 解決策 |
|-------|---------------|----------|
| `@PreAuthorize` が機能しない | `GatewayPreAuthFilter` が未登録 | SecurityConfig で `addFilterBefore(new GatewayPreAuthFilter(), AuthorizationFilter.class)` を確認 |
| ページネーションクエリが空を返す | `PaginationInnerInterceptor` が未登録 | `MybatisPlusConfig` のインターセプタ登録順序を確認 |
| `@Transactional` が無効 | メソッドが public でない / 自己呼び出し | `@Transactional` は public メソッドにのみ有効。同一クラス内のメソッド呼び出しはプロキシをバイパスする |
| Redis 接続タイムアウト | コンテナネットワークに到達不可 | `docker compose exec omni-auth ping redis` で接続を確認 |
| Nacos 登録失敗 | アドレス設定の誤り | `server-addr` が `localhost` ではなくコンテナ名 `nacos:8848` を使用していることを確認 |
| JWT 検証失敗 | 公開鍵キャッシュの有効期限切れ | `auth.jwks.cache-ttl` 設定を確認、または Gateway を再起動してキャッシュをクリア |
| XXL-JOB 登録失敗 | Admin が起動していない | `xxl-job-admin` コンテナが正常に動作していることを確認 |
| MQ メッセージが配信されない | Relay Job がトリガーされていない | XXL-JOB Admin コンソールで `mqRelayHandler` のスケジュール状態を確認 |

### Debugging Tips

```bash
# バックエンドサービスのログを確認
docker compose logs -f omni-auth

# 環境変数を確認
docker compose exec omni-auth env | grep SPRING

# データベース接続をテスト
docker compose exec omni-auth sh -c 'nc -zv mysql 3306'

# Nacos に登録されたインスタンスを確認
curl -s http://localhost:8848/nacos/v1/ns/instance/list?serviceName=omni-auth
```

## Testing

Auth、CRM、および一部の Common モジュールにはテストが追加されています。バックエンド変更時のガイドライン:

- **単体テスト**: JUnit 5 + Mockito、`src/test/java/` に配置
- **結合テスト**: `@SpringBootTest` + 組み込みデータベースまたは Test Containers
- テストクラス命名: `XxxTest`（単体）または `XxxIntegrationTest`（結合）
- テストメソッド命名: `should_<expected>_when_<condition>`
