# バックエンドパターンと規約

> 本書はバックエンドの層構造、コーディング規約と共通機能の導入境界を管理します。要件は新規・変更コードに適用し、既存コード全体や業務フローの検証完了を意味しません。
> モジュールと信頼境界は[アーキテクチャ](architecture.jp.md)、通信仕様は[API 契約](api-contract.jp.md)、環境変数は[Docker デプロイ](docker-deployment.jp.md)を参照してください。実操作の全手順は Wiki と開発ガイドが管理します。

## Layering

```text
Controller --> Service --> Mapper (DAO)
HTTP/validation   Domain rules   SQL/data access
                  Local transaction boundary
```

### Controller Layer

- HTTP 入力を受け、宣言済み制約を検証し、入口の権限・本人情報を確認して Service を呼びます。ドメインの状態遷移と永続化規則は Service が担います。
- 通常の業務 API は `R<T>` / `R<PageResult<T>>` を使用します。OAuth/OIDC、リダイレクト、Actuator などは各プロトコルの応答を維持します。
- `@Valid` は DTO に宣言された制約を実行するもので、テナント所属、可視性、一意性や業務状態の検証を代替しません。DTO は独立クラスまたは static 内部クラスとします。
- URL、HTTP メソッド、フィールドは実際の Controller が定義します。模式例・抜粋は完全な呼出可能 API ではありません。書込入口の追加では権限 seed、移行検証とフロントエンド権限も更新します。

### Service Layer

- `XxxService` と `XxxServiceImpl` を分け、業務実装に `@Service` を付けてコンストラクタで依存を渡します。
- 原子性が必要なローカル境界にトランザクションを宣言します。読み取り専用の要否は整合性要件で判断し、既存の全メソッドにトランザクションがあるとは推定しません。
- 新規ドメイン Service には本人情報・tenantId・業務引数を明示し、HTTP オブジェクトはアダプタに置きます。既存の例外はモジュール契約に従って扱います。

### Repository / DAO Layer

- 業務永続化は主に MyBatis-Plus の `XxxMapper` を使います。Flowable 内部の永続化はエンジンが管理し、それを汎用 JPA 導入の根拠とはしません。
- Mapper はデータアクセス、Service はドメイン判断を担います。値は `#{}` でバインドし、動的な表名・列名・ソート識別子には制御されたマッピングを用い、利用者入力を直接連結しません。
- 独自 SQL、子表結合とテナント横断ジョブでは所属・データ範囲を個別確認します。Starter の依存追加だけで全 SQL の保護は証明できません。

## Dependency Injection (DI)

以下は imports を省略したコンストラクタ注入の模式例で、本番 Controller の追加ではありません。

```java
// 构造器注入示意，省略 imports
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}
```

`@Autowired` フィールド注入は禁止です。通常は `@RequiredArgsConstructor` + `final` を使います。`@Lazy`、任意依存、初期化分岐が必要なら明示コンストラクタ、`@Bean` メソッド、`ObjectProvider` を使えます。既存の OperLogProducer / OperLogAutoConfiguration が例です。

### 設計上の根拠: なぜ @RequiredArgsConstructor を使い @Autowired を使わないのか

| 観点 | 利点と境界 |
|---|---|
| 依存の明示 | 必要な依存が引数で分かり、テストで直接 Mock を渡せる |
| フィールドの固定 | final は再代入を防ぐが、参照先の不変性やスレッド安全性は保証しない |
| 失敗の特定 | 手動構築の引数不足はコンパイルで検出できる一方、Spring Bean の不足・曖昧性・設定不備は起動時にも失敗する |
| 任意機能 | 任意依存には明示的な扱いが必要で、Lombok は実行条件を除去しない |

## Validation

`@Valid @RequestBody` と Jakarta Bean Validation を組み合わせます。以下は CreateDictTypeRequest の抜粋で、他フィールドと imports は省略しています。四言語ともソースの中国語診断文を保持します。

```java
/** 字典类型编码 */
@NotBlank(message = "字典类型编码不能为空")
private String typeCode;

/** 字典类型名称 */
@NotBlank(message = "字典类型名称不能为空")
private String typeName;
```

該当する MVC advice が検証失敗、必須引数不足、型不一致を扱います。Service では引き続きテナント・権限・リソース・状態を検証します。ネスト、集合要素やメソッド引数の検証は宣言と呼出経路次第です。

## Exception Handling

### BusinessException

次の二つは独立した例です。単一引数のコンストラクタは業務コード 500、二引数は指定コードを設定します。HTTP ステータスとは分けて記録します。

```java
// 两个独立示例：默认业务码与显式业务码
throw new BusinessException("资源不存在");
throw new BusinessException(404, "资源不存在");
```

### GlobalExceptionHandler

omni-common の自動設定が GlobalExceptionHandler を import します。表はその MVC advice まで届いた例外に適用し、Gateway、Security Filter、OAuth プロトコルや応答確定後の例外は別の境界です。

| 例外 | HTTP ステータス | R.code と処理 |
|---|---|---|
| BusinessException | handler は未設定。通常の未変更応答では一般に 200 | 例外の業務コード、WARN |
| MethodArgumentNotValidException / BindException | 400 | 400、フィールドエラーを集約 |
| MissingServletRequestParameterException / MethodArgumentTypeMismatchException | 400 | 400、引数エラー |
| AccessDeniedException / AuthorizationDeniedException | 403 | 403、権限不足 |
| NoResourceFoundException | 404 | 404、サービス内の未一致リソース |
| HttpRequestMethodNotSupportedException | 405 | 405 |
| HttpMediaTypeNotSupportedException | 415 | 415 |
| その他 Exception | 500 | 500、ERROR にスタックを保存し応答は一般文言 |

エラー、null、日時・金額のシリアライズ仕様全体は [API 契約](api-contract.jp.md)が管理します。全 JSON 解析失敗が 400 になるわけではありません。

### ルール

- 対応が必要な例外を握りつぶさず、想定した拒否と予期しない障害を区別します。意図的なフォールバックにはログ、戻り値、回復動作を明示します。
- 境界で null を確認します。Optional / Objects.requireNonNull は業務エラー契約の代替ではありません。
- logger の例外引数で必要なスタックを残し、e.printStackTrace() を使いません。ログ・応答とも資格情報や個人情報の漏洩を防ぎます。

## Logging

@Slf4j と引数付きプレースホルダーを使います。実際の影響に応じ、失敗は ERROR、想定拒否・縮退は WARN、主要業務点は INFO、診断は DEBUG とします。

```java
// 使用占位符，示意日志不包含凭据
log.info("用户 {} 从 {} 登录", userId, ip);
```

System.out.println や文字列連結を使いません。パスワード、Bearer/Refresh Token、クライアントシークレット、完全な個人情報を通常ログに残しません。OperLog の規則だけで任意の独自フィールドの秘匿は証明できず、項目選択とアクセスは業務側の責任です。追跡・メトリクス・関連付けは[可観測性](observability.jp.md)を参照してください。

## OOP Conventions

- 用途に合う Lombok を選びます。Serializable 実装クラスには serialVersionUID を宣言しますが、全 DTO、record、ドメイン型に同インタフェースを強制しません。
- 通常は定数、静的変数、フィールド、コンストラクタ、public、private の順とし、カプセル化と責務で不変条件を明示します。
- 定数を equals の受信側にするとその null を避けられます。ボックス化は valueOf を使います。BigDecimal の金額は精度・丸め・比較規則を指定し、浮動小数点の近似比較は業務許容差を定めます。

## Collection & Concurrency

- 容量は想定規模で選び、固定容量を一律に設定しません。CollectionUtils.isEmpty は null に対応し、非 null 集合では isEmpty() も有効です。
- 走査中の変更は適切な Iterator、removeIf、または別段階で行います。キーと値が必要なら entrySet() を優先します。
- 新規スレッドプールはキュー、上限、拒否方針、終了処理を明示し、制御されない Executors.newXxx() の既定設定を避けます。
- ConcurrentHashMap や原子変数は複数オブジェクトの不変条件を守りません。複合更新、DB 同時処理、承認占有はモジュールのロック・バージョン・トランザクション契約に従い、並行集合だけで検証済みとはしません。

## Naming Conventions (Java)

以下は新規コードの規約で、既存名はソースを事実とします。例えば OperType に Enum 接尾辞はありません。書式目的だけで公開型を改名しません。

| 型 | 規約・例 |
|---|---|
| パッケージ / クラス / メソッド・変数 | 小文字の点区切り、UpperCamelCase、lowerCamelCase |
| 定数 / boolean | UPPER_SNAKE_CASE、通常フィールドの is 接頭辞を避ける |
| 抽象クラス / 例外 | AbstractXxx / XxxException |
| enum / DTO | 意味が明確な型名。責務に応じ Request、Response、VO |
| Feign / Service / 実装 / Mapper | 所属モジュールと統一。XxxService / XxxServiceImpl / XxxMapper |

## Code Format (Java)

新規・変更コードは 4 スペース、K&R 中括弧、演算子とカンマ周辺の空白、メソッド間の空行を使います。120 文字と短い引数列を可読性の目安とし、必要なら引数オブジェクトを検討します。既存 Controller は 5 引数を超える場合があります。

imports は java、jakarta、第三者、com.omni の順でグループ化します。Controller 注釈パッケージを除き wildcard は禁止です。現在の Maven POM 検索では Checkstyle / Spotless / PMD の宣言が見つかっておらず、コンパイル成功は書式目標の検証を意味しません。

## Comments

新規 public クラス・メソッドに Javadoc を付け、重要フィールドや不明瞭な処理を必要に応じ説明します。コードコメントは中国語とし、名前の繰返しを避けます。TODO は `// TODO: [module] description`、FIXME は問題と境界を明示します。規約を既存コード全体の適合証明にしません。

## Security & Permission

### Functional Permission (API Authorization)

通常の保護 API は Gateway の JWT 検証後、サービスで信頼済みユーザー識別アサーションを検証して Authentication を構築し、メソッド権限を確認します。クライアントから直接渡された本人・権限 Header を信頼しません。署名対象とネットワーク信頼の制約は[アーキテクチャ](architecture.jp.md)が管理します。

以下の二注釈は Base 辞書型更新入口の抜粋で、リソース所属の確認を代替しません。

```java
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('dict:type:update')")
```

権限コードは dict:type:update のように resource:action 階層で命名します。新規書込では @PreAuthorize、scripts/sql/seed/auth.sql、database/seed/manifest.yaml、frontend v-permission を同期します。公開登録、OAuth/OIDC、内部 Token、MyJobController の行 ownership は独自契約に従います。通常のメソッド拒否は 403 ですが、Filter の失敗は実経路で判断します。

### Data Permission (DataPermission)

```text
Trusted identity --> Request context --> Permission-aware scope
    --> Domain table/aggregate policy --> SQL + explicit tenant predicates
    --> finally: restore/clear context
```

Auth MybatisPlusConfig は DataPermission → OptimisticLock → Pagination で、汎用 TenantLine はありません。DataPermissionHandlerImpl は sys_user、sys_org_unit、sys_role を対応付け、テナント条件は該当 Mapper/Service が明示的に担います。

| dataScope | Auth sys_user の例 |
|---|---|
| ALL / TENANT | この handler は条件を追加しない。全呼出のテナント横断を認可する意味ではない |
| DEPT_AND_BELOW / DEPT / CUSTOM | 解決した組織 ID 集合で primary_unit_id を制限 |
| SELF | 現在の利用者 id を制限 |

Auth handler はコンテキスト欠落でも条件を追加しない場合があり、Interceptor の存在は入口認可の代替になりません。通常の二引数ロール解決と permission 指定の三引数解決は異なり、後者は対象権限を与えるロールだけを統合し、該当なしなら拒否します。全六範囲と優先度は[コアフロー](core-flows.jp.md)を参照してください。

common-service 永続化を有効にした CRM/SRM/Procurement/Asset は TenantLine → DataPermission → OptimisticLock → Pagination を使います。テーブル方針、owner 対応、親集約経由の子表継承、AccessGuard は各ドメインが所有します。TenantLine は業務プレフィックスだけに適用し、sys_mq_message はテナント横断中継の対象です。

新しいテーブルは所属サービスの DataScopeTablePolicy / handler を更新し、owner、現在の使用者、テナント共有、子関係を区別します。存在しない owner 列への条件や組織 ID をユーザー外部キーとみなす対応を作りません。DataPermission は Pagination より前です。

OnlineUserServiceImpl は Redis オンライン利用者を明示確認します。ALL は横断動作を保ち、他はテナント所属の後に SELF または組織範囲を検査します。強制ログアウトは Redis 更新と成功監査の前に対象確認し、本人・テナント・有効範囲欠落は業務コード 403 です。この固有動作を全 SQL に一般化しません。

### ThreadLocal Usage Guidelines

Auth DataScopeResolveFilter は try 内で解決・設定し finally でクリアします。内部パスには別分岐があります。common-service の本人 Filter とメソッド DataScope Aspect は各ライフサイクルで復元・クリアを管理します。

DataScopeContext、本人コンテキスト、XssRuleHolder は非同期タスクへ自動伝播しません。必要な本人・tenantId を明示しタスク側で管理します。信頼性メッセージは ThreadLocal からテナントを推定しません。

### XSS Protection (Three-Layer Defense Architecture)

```text
JSON String conversion --> Jackson 2/3 XSS modules + request rules
Query/form access --> XssHttpServletRequestWrapper
Gateway response --> SecurityHeadersFilter (no input sanitization)
```

omni-common XssAutoConfiguration は Servlet 環境で XssConfigProvider Bean が存在する場合に /* Filter を登録し、classpath に応じ Jackson 2/3 String モジュールを登録します。JSON 浄化には実際の converter が該当モジュールとリクエストスレッドの規則を使う必要があり、未登録の実装だけでは不十分です。

XssFilter はテナント Header から設定を取得します。テナント欠落・解析不能、無効設定、空規則なら通過します。有効時は getParameter / getParameterValues を包装し finally で規則をクリアします。getParameterMap は上書きしておらず、全入力取得方式を保証しません。

| ruleType | 現在の処理 |
|---|---|
| HTML_TAG | 指定タグ・対応内容を除去。縦棒区切りのタグ名をリテラルとして escape |
| EVENT_HANDLER | 設定パターンに一致するイベント属性を除去 |
| DANGEROUS_PROTOCOL | 大文字小文字を区別せず指定プロトコルを除去。縦棒区切りをリテラル扱い |
| CUSTOM_PATTERN | 正規表現置換。不正な regex は WARN を記録して規則をスキップ |

規則フィルタは出力エンコーディングやリッチテキストの信頼方針を代替しません。Gateway の X-Content-Type-Options: nosniff、X-Frame-Options: DENY、Referrer-Policy は応答防御で、入力を浄化しません。

Auth XssConfigProviderImpl は xss:enabled:{tenantId} / xss:rules:{tenantId} を TTL 30 分で使います。開閉・規則更新後は XssConfigServiceImpl が両キーを削除し、TTL 任せにしません。削除はローカルトランザクション内なので commit と並行 cache 再投入の競合解消を証明しません。Auth Provider は全 Redis 障害を捕捉して DB に戻るわけでもありません。

common-service CachedServiceXssConfigProvider は cache 失敗後に Auth を試し、失敗・結果なしなら DefaultXssSettingsFallback の有効な基準規則へ戻ります。正式な設定が無効ならその状態を保持します。Provider の設定順、Jackson と Filter Bean を同時確認します。[開発ガイド](guides/scaffold-development.jp.md)を参照してください。

## MQ Message Sending Records & Compensation Management (omni-common-mqlog)

信頼性を要する送信は omni-common-mqlog と DB・Job 能力を使います。ReliableMessageRelay / ReliableMessageTemplate の send(bindingName, payload, tenantId[, msgKey]) は正の tenantId を明示します。Outbox と業務更新を同時 commit できるのは実際のローカルトランザクションがある場合で、リモートサービスや Broker のトランザクションを含みません。

sys_mq_message DDL の唯一の所有者は database/changelog/common/0001-mq-schema.yaml で、旧 schema.sql は主生成経路ではありません。mqRelayHandler は @XxlJob と @SystemJobMeta を併記します。中継は PENDING/FAILED をテナント横断走査し、内外管理クエリは tenantId で制限します。MessageSender は broker_type で選び、現実装は RocketMqMessageSender です。

状態、指数バックオフ、死信再送・スキップと拡張は [MQ 信頼性](mq-reliability.jp.md)、断点と回復は [Outbox 開発](wiki/outbox-development.jp.md)が管理します。依存・注釈の存在だけで実行器、Broker、消費や補償の稼働は証明できません。

## Operation Log (OperLog)

@OperLog AOP は注釈付き入口の操作情報と任意スナップショットを MQ 経由で Base に保存します。全イベント、完全監査、無損失やコンプライアンスを保証しません。

### Core Recording Flow

```text
@OperLog --> OperLogAspect --> OperLogProducer
    --> ReliableMessageRelay / Outbox (if available)
    --> StreamBridge direct send (fallback)
    --> Base OperLogConsumer --> sys_oper_log
XXL-JOB operLogArchiveHandler --> sys_oper_log_archive
```

OperLogProducer は ReliableMessageRelay があれば Outbox、なければ StreamBridge を使い、送信例外を WARN にします。直送は戻り boolean を確認せず、信頼できる配信受領証ではありません。業務と同一トランザクションになるかも呼出境界次第です。

Base 消費側は event_id の一意制約による DuplicateKeyException を無視し、他例外はミドルウェアへ返します。eventId のない旧メッセージにはサービスが決定的 legacy ID を作ります。全業務消費の exactly-once 証明ではありません。

OperLogArchiver は @XxlJob / @SystemJobMeta 付き operLogArchiveHandler です。既定メタデータは cron 0 0 2 * * ?、retentionDays 180、バッチ 1000。実際のスケジューリングには XXL-JOB 設定・実行が必要です。コールドテーブル挿入後にホットテーブル削除し、失敗バッチで停止します。同 Bean 内から archiveBatch を呼ぶため、その @Transactional だけで現経路の proxy トランザクション保護を断定できません。コールドテーブルの存在は長期保持方針やコンプライアンス検証を証明しません。

### @OperLog Annotation Usage

以下は既存 DictTypeController.update の imports/Javadoc を省略した抜粋です。中国語 module と引数はソース通りです。Auth のユーザー管理を導入例にしません。

```java
@OperLog(module = "字典类型管理", operType = OperType.UPDATE, entityClass = SysDictType.class, idExpr = "#id")
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('dict:type:update')")
public R<SysDictType> update(@PathVariable Long id,
                              @RequestHeader("X-Tenant-Id") Long tenantId,
                              @Valid @RequestBody UpdateDictTypeRequest request) {
    String operator = SecurityContextHolder.getContext().getAuthentication().getName();
    return R.ok(dictTypeService.updateType(id, tenantId, request, operator));
}
```

| 引数 | 現在の意味 |
|---|---|
| module / operType | module は空文字が既定だが規約で明示。operType に既定なし |
| entityClass / idExpr | 既定 Object.class / 空文字。スナップショット有効時に Mapper と引数・結果の SpEL で ID を取得 |
| recordParams / recordSnapshot | 既定 true。機密・大きな本文の操作では無効化可能 |
| excludeFields | 追加除外項目。既定空配列、秘匿時に項目名を正規化 |

### OperType Enum

| 種類 | スナップショット有効かつ構成条件を満たす場合 |
|---|---|
| CREATE | 結果 ID から newValue を照会 |
| UPDATE | 前後を照会しトップレベルの値を比較 |
| DELETE | oldValue を照会、newValue は null |
| QUERY / EXPORT / IMPORT | このスナップショット分岐ではエンティティを読まない |

### Development Constraints

- 操作ログが必要な新規書込は機密性に応じ注釈を設定します。**omni-auth は omni-common-operlog 依存も @OperLog も禁止**です。認証イベントは sys_audit_log に保存し、ログイン記録は読み取り専用投影で、別 sys_login_log は作りません。
- Mapper と SpEL の実解決が必要です。tenantId 属性を持つスナップショットは現在の正 tenantId と一致するものだけを残し、属性のないグローバル型は既存動作を保ちます。スナップショット確認は書込認可ではありません。
- OperLogSanitizer は対象項目、電話、メール、資格情報断片の規則秘匿で、任意テキスト全部の秘匿ではありません。長い文字列は先頭 4000 文字に ...[TRUNCATED] を付加するため全長 4000 以下ではありません。JSON が不正になることがあり、DB スナップショット列は JSON なので長大スナップショットの保存成功を保証しません。
- 対象メソッドの例外は伝播します。後処理は Exception を捕捉し、SpEL / Mapper 探索には局所フォールバックがあります。実行前スナップショットの照会と ID cast はすべて保護内ではなく、全収集失敗が業務に影響しないとは断定できません。
- ログ responseStatus は通常 200、対象例外時 500 で、最終 MVC HTTP ステータスや R.code ではありません。

### Module Responsibilities

| モジュール | コンポーネント |
|---|---|
| omni-common-core | OperLog、OperType、OperLogMessage。production に Spring 依存なし |
| omni-common-operlog | Aspect、Producer、Sanitizer、EntityDiffer と条件付き自動設定 |
| omni-base | Consumer、OperLogService、Archiver、ホット／コールドテーブル |

## Common Starter Onboarding Specification

### Starter モジュール概要

common は 10 モジュールで、core が基礎型、残り 9 が組合せ能力です。依存と全モジュール表の唯一の入口は[アーキテクチャ](architecture.jp.md)です。

| モジュール | 境界 |
|---|---|
| omni-common-core | 基礎型。production の Spring 自動設定なし |
| omni-common | Servlet Web/MVC、Jackson、例外 advice、条件付き XSS |
| omni-common-mybatis | 既定 OptimisticLock → MySQL Pagination、YAML の列対応・論理削除・ID 規約 |
| omni-common-redis | 同期 RedisTemplate、RedisUtils、接続既定値 |
| omni-common-redis-reactive | reactive 依存と timeout YAML。このモジュール独自の AutoConfiguration.imports なし |
| omni-common-job | 条件付き XXL-JOB 実行器とシステムジョブ登録など |
| omni-common-mqlog | Outbox、中継、送信戦略、管理。DB/Job/Broker 設定も必要 |
| omni-common-operlog | 注釈 AOP と送信。Servlet/AspectJ/Mapper/StreamBridge 等の依存が必要 |
| omni-common-workflow | 共通 Workflow 能力。業務ドメインへエンジン runtime を導入しない |
| omni-common-service | Servlet 本人情報、内部 API、DataScope、永続化、XSS の組合せ |

独自 AutoConfiguration.imports を持つ能力モジュールは 8 個です。entry は登録入口であり、条件充足や Bean 稼働の証明ではありません。全モジュールが同じ機構とは記述しません。

### 新規サービス接入手順

Servlet ドメインは common-service を優先し OperLog/Job/MQ を選択します。Gateway/Auth/Base/Workflow は固有境界を維持します。識別情報の設定、GATEWAY_ASSERTION_SECRET、OMNI_INTERNAL_API_TOKEN は非公開環境から取得し、有効機能は起動検証を満たす必要があります。

テナント・データ範囲を有効化したら一意かつ表に合う TenantTablePolicy/DataScopeTablePolicy を提供します。アサーション Filter は AuthorizationFilter 前、本人 context は事前認証後とし、Servlet への二重登録を避けます。XSS Provider は条件利用側より先に構成し、FilterRegistration と Jackson 2/3 Bean を併せて検証します。全導入・生成器・Docker 手順は[開発ガイド](guides/scaffold-development.jp.md)が管理します。

### Override Mechanism

- MyBatis 既定 interceptor は MybatisPlusInterceptor 型の欠落条件です。プロジェクト命名に従う Bean と正しい順序を保ちます。
- RedisTemplate は redisTemplate という名前、RedisUtils は戻り型の欠落条件です。置換条件を混同しません。
- XSS は Provider Bean と Servlet/classpath 条件に依存します。一部 OperLog Bean に ConditionalOnMissingBean はありません。全 Starter が同じ方法で上書き可能とはせず、設定を読み container context を検証します。

### Redis Starter Mutual Exclusion

WebFlux Gateway は omni-common-redis-reactive のみ、Servlet は同期モジュールを使い、同サービスに混在させません。Netty event loop で同期呼出を実行せず、reactive 依存だけで業務の同期操作が非同期化されるとも考えません。

## Configuration Reference

### application.yml Key Configuration Items

以下はリポジトリ YAML と正式 Compose の区別です。全設定は[デプロイ](docker-deployment.jp.md)と現設定を参照します。

| 項目 | 現規約または正式 Docker 値 |
|---|---|
| server.port / SERVER_PORT | ホスト入口 8100–8107、アプリ container HTTP 8080、Frontend 3000 |
| spring.application.name | omni-auth/base/gateway/workflow/crm/srm/procurement/asset、サービス別 |
| spring.datasource.* | 正式 mysql:3306、omni_app、OMNI_DB_PASSWORD。Gateway に業務データソースなし |
| spring.data.redis.* | 正式 redis:6379、DB とシリアライズはサービス別 |
| spring.cloud.nacos.discovery.server-addr | 正式 nacos:8848。Auth/Base/Workflow とドメインの変数対応は各設定に従う |
| spring.cloud.nacos.discovery.ip | Compose は container 検出用に空。ホスト loopback 固定ではない |
| auth.jwks.uri / AUTH_JWKS_URI | Gateway YAML `http://localhost:8100/oauth2/jwks`、Compose `http://omni-auth:8080/oauth2/jwks` |
| auth.jwks.cache-ttl | Gateway 既定 5m |
| spring.profiles.active | Spring Profile、Compose profile、CLI preset を区別 |

パスワード、Nacos 本人・署名秘密、Gateway アサーション秘密、内部 Token は非公開設定を使います。ソース中の旧 fallback を推奨資格情報にしません。

### MyBatis-Plus Configuration

common-mybatis YAML は map-underscore-to-camel-case=true、論理削除列 deleted（削除 1、有効 0）、id-type=auto を宣言します。実エンティティ・サービスは固有方針を持てるため、全テーブルに同じ列があるとは推定しません。独自 interceptor も DataPermission を Pagination 前に保ちます。

### XXL-JOB Configuration (omni-base)

| 項目 | 現 Base または正式 Docker 値 |
|---|---|
| xxl.job.admin.addresses | YAML http://127.0.0.1:18080/xxl-job-admin、Compose http://xxl-job-admin:8080/xxl-job-admin |
| xxl.job.executor.appname | omni-base |
| xxl.job.executor.port | Base YAML 9901。属性クラスの汎用 fallback 9999、他サービスは個別設定 |
| xxl.job.executor.address / ip | Compose http://omni-base:9901/ / omni-base |
| xxl.job.access-token | 汎用属性は空。Compose は両端一致の非公開 XXL_JOB_ACCESS_TOKEN を必須とする |
| xxl.job.executor.enabled | 自動設定は既定有効、Compose は XXL_JOB_ENABLED で制御 |

管理者資格情報は非公開環境で設定します。既定 cron、登録成功、実際の実行は別状態です。システム handler は @XxlJob と @SystemJobMeta を併記し、利用者ジョブ・補償は[スケジューリング](scheduling.jp.md)が管理します。

## Service Layer Design Patterns

### Interface + Implementation Separation

```text
XxxService (contract)
    ^ implements
XxxServiceImpl (business behavior; local transactions where configured)
```

インタフェースは呼出分離と Mock に役立ちますが、置換時も意味とトランザクション契約を維持します。Feign は独立したリモートプロトコル用のアダプタで、任意 Service インタフェースがそのまま利用可能クライアントになるわけではありません。

### Transaction Management Strategy

| 場面 | 規約と限界 |
|---|---|
| 読み取り専用 | @Transactional(readOnly = true) はトランザクション設定・ヒントで、認可や一般的な書込禁止の証明ではない |
| ローカル書込 | @Transactional は既定 REQUIRED。実 proxy 呼出、rollback 規則、DB 境界を確認 |
| リモート副作用 | ローカルトランザクションと Outbox は送信予定を記録できるが、完了済み HTTP/Broker/外部アカウント操作を戻せない |
| 独立トランザクション | REQUIRES_NEW は適用 proxy と成功 commit が必要。ログ保持自体を保証しない |

注釈だけで同クラス内呼出の proxy 通過を推定せず、全ログ送信を独立トランザクションと書きません。重要状態、占有と冪等性は各ドメイン事実・回帰に従います。

### Exception Handling Chain

```text
Controller --> Service --> matching MVC advice --> R
Gateway / Security Filter / protocol endpoint --> own response boundary
```

Auth は common-core と common の両方に依存します。common imports がグローバル advice を登録し、AuthExceptionHandler は com.omni.auth.controller に限定され共存します。「common に依存しない」という旧 Javadoc より現 POM/imports が優先です。実応答は該当 handler または Filter/プロトコルが決めます。中国語エラー文を翻訳済みの実バックエンド値として示しません。

## Troubleshooting Guide

再現は[トラブルシューティング](guides/troubleshooting.jp.md)、呼出・変数・断点は[ソースデバッグ](wiki/source-debugging.jp.md)が管理します。アサーション、メソッド権限、テナント、可視性、トランザクションを分けて確認し、空一覧、403、404、接続失敗を同一視しません。

[AGENTS.md](../AGENTS.md) に従いローカル Docker で構築・テスト・実行します。container/session、終了コード、既存副作用を記録し、ログ・非公開設定を守ります。実行中の処理を先に確認し、データ初期化・認可無効化・共有資源削除で成功扱いにしません。

## Testing

JUnit 5 / Mockito はロジックを分離します。MockMvc は特定 MVC wire の証拠になっても、本人・Service・エンジンの Mock は実 Gateway、DB、Flowable、Broker、browser フローを証明しません。SpringBootTest も実依存・環境・assertion が範囲を決め、名前だけでは統合検証済みになりません。

テストは各 src/test/java に置き、動作と前提が分かる名前でモジュール規約に従います。既存テストは複数業務・common を対象とし Auth/CRM だけではありません。条件付き skip は pass に算入しません。

バックエンド実装変更後は Docker で Maven Wrapper reactor を実行します。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
```

JDK 25、JAVA_HOME と順序は tools container/reactor が提供します。文書のみの変更では実装・build 入力の完全一致を確認して既存証拠を再利用でき、出典、pass、skip、制約を開示します。Markdown 描画、単体・build 成功を全業務の受入証明にしません。
