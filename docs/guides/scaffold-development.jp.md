# プリセット、コンテナ開発、サービス拡張

対象：2026-10-17 に確認した CLI 0.6.1、サービステンプレート 1.5.1、CRUD テンプレート 1.1.1、プリセット配布テンプレート 1.1.0。実行者はローカル開発者で、業務ログインは不要です。generated は独立した学習用出力で、正式 seed ではありません。[初回起動](quick-start.jp.md)で Docker ツールイメージ、依存、作業ディレクトリのマッピングを準備します。サービス、ビルド、テスト、ブラウザーはローカル Docker で実行し、個人の未コミット変更がある正式作業場所で接続実験をしません。

## 1. プリセットを選択して生成する

プリセットはモジュールを選び、catalog が依存関係の閉包を計算します。preset list は core、workflow、crm、supply-chain、full を表示します。explain crm の explicit と resolved を確認してください。構成とリソース予算は[依存マトリクス](../preset-dependency-matrix.jp.md)を参照します。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset list
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset explain crm
```

部分起動は元のリポジトリを使います。新規プロジェクトの切り出しは、選択したコード、migration、seed、4言語 Wiki と適用する AI 資産を新しいディレクトリへコピーします。--dry-run で確認し、その引数を外して生成します。--apply はありません。出力先は未作成または空である必要があります。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/wiki-crm --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/wiki-crm
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate crm --output generated/wiki-crm
```

`preset created: crm@...` と `generated preset valid` を確認します。scaffold.lock の sourceDigest と scaffoldDigest は、それぞれ元プロジェクトと生成プロジェクトの scaffold/ ディレクトリを対象にします。catalog、プリセット、文書、AI 入力には個別のハッシュ/リソース検証があります。全アプリソースの署名でも、コンパイルや業務検証の証明でもありません。新しいディレクトリ自身の compose.tools.yaml、compose.docs.yaml、ai/adapters/bootstrap.mjs を使い、自分のリポジトリ URL と非公開 .env を設定します。元の絶対パスには依存しません。

## 2. 部分起動と lite モード

dev は up、down、status、doctor、logs を提供し、dev plan はありません。up は Docker 呼出し前に modules、services、mode と機能制限を表示します。core/workflow/crm は既定で lite になり、Nacos クライアント、XXL-JOB、MQ relay、自動検出を無効にします。設定上は業務トランザクションから Outbox に書き込めますが、実際の挿入や配信の証拠ではありません。SRM/Procurement/Asset を含む安全に停止できないコンシューマの閉包では、モード省略時は full、明示 lite は拒否となり、full は .env を優先します。

以下は標準 omni-stack と固定ポート（MySQL 3306）を使います。既存環境のポート、project、ボリューム、所有者を先に確認します。隔離検証は所有する専用ボリュームと内部ネットワークを使い、業務ポートを移動しません。down はボリュームを保持します。CLI ツールイメージには Node.js、Docker CLI、JDK 25 が必要で、Compose は Docker socket とプロジェクトディレクトリをマッピングします。doctor は終了コード 0 でも SKIP を含みます。Linux ツールコンテナでホストポート検査が省略された場合は Docker の公開ポートを別に確認します。Nacos のプローブはサーバー API/サーバー一覧を検査し、全アプリの登録を証明しません。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev doctor
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev up --preset crm --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev status --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev logs --module crm --tail 200 --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev down --project-name omni-stack
```

## 3. サービスパッケージの生成と接続

create-service は既定でプレビューのみです。--apply は omni-service.lock.json を持つパッケージを生成し、monorepo への接続は行いません。続いて service validate と service integrate を実行します。integrate も既定は読み取り専用で、別の --apply が Maven、catalog、Gateway、Compose、権限、メニュー、migration の入口を書き込みます。まず独立した core 学習プロジェクトを作ります。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create core --output generated/learning-core
cd generated/learning-core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- create-service inventory --output generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- create-service inventory --output generated/inventory-bundle --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service validate inventory --output generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service integrate inventory --source generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service integrate inventory --source generated/inventory-bundle --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-inventory,omni-db-migrator -am
```

計画に CONFLICT がなく、適用後に integration applied が出ることを確認します。ポート競合、パッケージハッシュの変化、Git 管理下の対象ファイルの未コミット変更は、保護を無効にせず解消します。omni-inventory Maven モジュールは接続後に存在します。サービスは Servlet 共通 starter を使います。共通設定の XssConfigProvider SPI が実際の classpath/設定で有効か確認し、必要に応じて拡張します。reactive Gateway チェーンは複製しません。業務検証、DataScope のテーブル対応、拒否テストは引き続き必要です。

現行の計画とレンダラーは zh-CN、en-US、ja-JP、ko-KR の画面 locale を扱います。対象に .git がある場合、四つの locale ファイルすべてを書込前の未コミット変更検査に含めます。.git がない学習プロジェクトにはこの Git 保護がありません。必要ならクリーンな基準を作成してコミットします。四言語の登録は業務用語の翻訳完了を意味しません。新規文書/訳文は present-unverified のままにし、実際の意味レビュー後に承認済み AI_REVIEW を記録できます。人手レビューは必須ではなく、生成器による自動承認も行いません。

<!-- omni:modules:procurement -->
## 4. CRUD の生成

現行 CRUD 仕様は procurement のみをサポートします。同モジュールを含む独立学習環境で scaffold/specs/material-brand.yaml を使います。generate crud --spec の --check は仕様検証、--dry-run は計画のレンダリング、--apply は複数ファイルへの原子的書込です。dry-run と apply は排他的です。既存テーブルと競合しないよう module、permission、DataScope、フィールドを先に確認します。

以下の cd ../.. は第 3 節の続きで、generated/learning-core から元のリポジトリのルートへ戻ります。この節から読む場合は、先に元のルートへ移動し、この cd だけを省略してください。

```bash
cd ../..
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
cd generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --apply
```

出力は仕様検証や生成処理を示し、業務動作の証明ではありません。バックエンド、フロントエンド、権限拒否、DB fresh/upgrade を検証し、四言語の説明を更新します。Liquibase は forward-only changeSet を追加し、seed のハッシュと assertion を更新します。暫定 migration SQL は追加しません。

CRUD が現在生成するのは中国語の説明と ja-JP/ko-KR の用語 YAML で、完全な四言語文書ではありません。実行時の locale 登録は四言語ですが、エンティティ/フィールド名には元言語の仮置き値が残る場合があります。実際の翻訳、画面確認、承認された意味レビューが必要です。AI_REVIEW は現行の文書方針を満たしますが、登録やロック検証だけでは承認状態になりません。

<!-- omni:modules:end -->

## 5. モジュールの追加・削除と更新

停止：小さい dev up 閉包でコードと更新経路を残します。切り出し：preset create でロック、ナビゲーション、画像、AI 規則を含む新規プロジェクトを作ります。恒久削除：逆依存、Gateway/Nacos/Compose、Maven、メニュー/ルート、権限/seed、更新、consumer、job を調査し、固有機能を先に移します。実行済み migration は変更しません。既存データの更新には独立したバックアップと復旧計画が必要です。

scaffold/catalog/modules.yaml と scaffold/presets/*.yaml で依存閉包と資源の所有者を管理し、派生表と5プリセット検証を更新します。[カスタムプリセット](../custom-preset-tutorial.jp.md)、[保守](../preset-maintenance.jp.md)、[更新](../preset-upgrade-guide.jp.md)を参照してください。Maven module の削除だけでは完了しません。

## 6. 検証と後片付け

完了にはコンテナ JDK 25 の Reactor、frontend lint/build、該当 E2E 型検証、権限/データ分離の拒否テスト、fresh/upgrade、プリセット/ロック、4言語本文と[現行方針](../adr/0003-authorized-ai-translation-review.md)に基づく実際の意味レビューが必要です。学習プロジェクトのルートで実行します。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev doctor
```

ボリュームは保持します。今回の一意な業務キーで教材データを片付けます。generated の正確な対象と新しい個人ファイルを確認してから所有する出力を削除します。不明なディレクトリの上書き、git clean/reset --hard、Docker 全体の prune は行いません。ログと token はリポジトリ外へ置きます。[ソース調査](../wiki/source-debugging.jp.md)も参照してください。

## 7. 実際の端末結果と照合する

[2026-10-12 の検証記録](../reference/evidence/scaffold-2026-10-12.json)には入力ソースのハッシュ、ツールとイメージの識別情報、時刻、検証範囲があります。以下はローカル Docker の実際の端末テキストで、四言語で共用します。私有出力パスのプレースホルダーをそのまま shell に貼り付けないでください。

[プリセット選択](../reference/evidence/scaffold-preset-select-2026-10-12.txt)では list、explain crm、core/supply-chain の生成と検証、core の移動後の再検証を実行しました。これだけで五つのプリセットの独立配布ゲートすべてが合格したことにはなりません。

[サービス接続](../reference/evidence/scaffold-create-service-2026-10-12.txt)の基本 inventory-basic はロック対象 32 ファイルを生成し、接続で 38 ファイルを変更しました。MQ/Job/DataScope を持つ inventory-sample の黄金ビルドは別工程で、生成 33、接続 39 ファイルです。core は rocketmq と xxl-job を含まないため、それらを必要とするパッケージの接続を書き込み前に拒否します。依存を含むプリセットを選び、この保護を維持してください。

<!-- omni:modules:procurement -->
[CRUD 生成](../reference/evidence/scaffold-crud-generate-2026-10-12.txt)は実際の --check、--dry-run、--apply、再生成を記録しています。再生成は unchanged で、ロック対象 18 ファイル、初回トランザクションは 29 ファイルです。別の黄金 fixture では backend clean verify と frontend build/lint が成功し、新規 lint エラーは 0 でした。生成した supply-chain プロジェクト全体の業務検証を代替するものではありません。
<!-- omni:modules:end -->

[軽量実行](../reference/evidence/scaffold-lite-mode-2026-10-12.txt)では、移動後の core 自身の CLI、14 モジュールの backend、frontend のビルド成果物を使い、ホストポートを公開しない独立環境を起動しました。実行コンテナ六つが準備完了となり、一回限りの migrator は終了コード 0 でした。Gateway → Auth のテナント取得は成功し、匿名で保護された Base API を呼ぶと 401 を返しました。Nacos、XXL-JOB、MQ relay と consumer は無効です。この読み取り専用 probe は Outbox 書き込みやメッセージ配送を検証していません。doctor のホストポート検査と Nacos 登録検査は SKIP であり、PASS ではありません。

この過去の環境は確認済み成果物の非公開事前ビルドイメージを使用しました。正式な多段階 Docker ビルドの既存ネットワーク制限は別の未合格ゲートです。--no-build を通常起動の近道にしません。当時 down が名前付きボリュームを保持した後、その実行で所有したものだけを削除しました。最終コンテナ、業務ボリューム、ネットワークは 0 で、元の教材環境の 24 コンテナの識別情報は不変でした。この日付付き記録は現在稼働するコンテナを示しません。

2026-10-17 の[現 CLI 記録](../reference/evidence/scaffold-cli-2026-10-17.json)と[実際の端末テキスト](../reference/evidence/scaffold-cli-2026-10-17.txt)は成功した 10 コマンドを補足します。プリセットの list/explain/プレビュー、正当なサービスパッケージのプレビュー/生成/検証、CRUD --check、dev/dev up/service integrate のヘルプです。基本パッケージはテンプレート 1.5.1 のロック対象 32 ファイルで、一度だけ作成して非公開証拠ディレクトリに保持しています。最初は数字だけのハイフン区切り部分から無効な Java パッケージ名が派生し、書込前に拒否されました。正当なサービス ID に直した後は残りの手順だけを実行し、成功済みの最初の三コマンドは繰り返していません。

既存の確認済みツールイメージと依存キャッシュを使用しました。完全なプリセット生成、サービス接続、業務起動、Maven 再実行は行っていません。サービス関連 11 件、CRUD 13 件のテストと生成物比較には別の範囲付き証拠があり、現行の全 golden と五プリセット構造マトリクスは引き続き検証が必要です。過去の人手署名条項は履歴として保持し、現行[承認方針](../adr/0003-authorized-ai-translation-review.md)では実際の AI_REVIEW で足り、人手レビューは必須ではありません。過去ログや自動生成状態を新しい文書承認には変えません。

<!-- omni:modules:crm -->
## 8. CRM 拡張とコンテナテスト

開発者向けで業務ログインは不要です。個人変更のない独立学習プロジェクトで crm/full を使い、[CRM の現行事実](../crm.jp.md) と第 3 節を読んでから変更します。以下は手順であり、今回の文書改訂ではこの業務/DB 操作を再実行していません。

### 8.1 集約、権限、イベントの追加

1. `database/changelog/crm/` に forward-only changeSet を追加し、既存 master に接続します。実行済み移行は書き換えません。`CrmTenantEntity` は BaseEntity に tenantId を追加します。owner、version、deleted の有無は具体的な集約テーブル/エンティティに依存し、すべての新規表に必須ではありません。実際の owner 対応、楽観ロック、削除方針に基づいて schema/エンティティを設計し、基本の監査規約を維持します。
2. 独立した要求/応答 DTO、Mapper、Service インターフェース/実装、Controller を追加します。実在の `CrmDataPermissionHandler` に owner 対応を登録し、存在しない owner 列を条件にしません。
3. `scripts/sql/seed/auth.sql` に API 権限とロール割当を追加します。必要なら `crm.sql`、`database/seed/manifest.yaml` の SHA-256 と検証条件も更新します。`@PreAuthorize` と `@ServiceDataScope` は同じ完全コードを使い、書込に `@OperLog`、ボタンに `v-permission` を付けます。
4. 同じトランザクションで tenantId を明示し `ReliableMessageRelay.send("crm-domain-out-0", envelope, tenantId, eventId)` を呼びます。payload に完全な PII を含めません。コンシューマはエンベロープ最上位の `eventId` を読み、コンシューマ識別、テナント、業務意図とともに原子的に重複/衝突を確認します。Broker msgKey だけで業務一回実行は保証されません。
5. 他テナント/owner、identity/scope 欠落、古い version、不正状態、ロールバック、重複メッセージ、PII 分岐を検証します。不可視は 404、version/状態競合は 409 を期待し、実 API の HTTP と R.code を記録します。

現行 Opportunity にパイプライン管理 UI はありません。段階管理 API/UI と既存参照の移行は別途設計が必要で、テーブル直接変更だけでは拡張完了になりません。

### 8.2 通常のビルドとテスト

学習プロジェクトのルートで実行します。backend ツールコンテナは JDK 25/JAVA_HOME を提供します。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-crm -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

先に第 6 節に従って依存を導入します。各モジュールの BUILD SUCCESS、failures/errors/skipped、フロントエンド終了コードを確認します。CRM_TEST_MYSQL_URL 未設定では MySQL 4 件がスキップされ、ビルド成功だけでは DB 統合検収になりません。ソース/報告ハッシュが一致する場合は再利用し、無変更の再ビルドを避けます。

### 8.3 MySQL インターセプタ検証が必要な場合

`CrmMysqlInterceptorIntegrationTest` は `crm_lead` を DROP/CREATE します。今回新規作成した破棄可能な空 DB だけを対象にし、既存業務 DB は使いません。リポジトリ外に非公開 mysql-it.env（MYSQL_DATABASE=crm_it と強い MYSQL_ROOT_PASSWORD）、crm-it.env（CRM_TEST_MYSQL_URL は `jdbc:mysql://crm-it-mysql:3306/crm_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`、CRM_TEST_MYSQL_USERNAME=root、同じ CRM_TEST_MYSQL_PASSWORD）を用意します。資格情報は Git、画像、共有端末記録に含めません。

以下の PowerShell 例は学習プロジェクトのルートから今回所有する名前で実行します。非公開ディレクトリ/ボリューム名は操作者が設定します。名前衝突時は所有関係を確認し、上書きしません。

```powershell
$private = 'C:\Private\crm-it-yourrun'
$network = 'crm-it-yourrun'
$cache = 'crm-it-maven-yourrun'
$mysql = 'crm-it-mysql-yourrun'
docker network create $network
docker run -d --name $mysql --network $network --network-alias crm-it-mysql --env-file "$private\mysql-it.env" mysql:8.4
docker logs $mysql
```

ログで MySQL ready を確認後、実行します。失敗時は非公開ファイル、ネットワーク、DB 名を確認し、専用テスト DB の制約を外しません。

```powershell
docker run --rm --network $network --env-file "$private\crm-it.env" --mount "type=bind,source=$PWD,target=/workspace" --mount "type=volume,source=$cache,target=/root/.m2" -w /workspace/omni-backend eclipse-temurin:25-jdk ./mvnw -pl omni-crm -am -Dtest=CrmMysqlInterceptorIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

成功条件は対象報告が 4 件、失敗/エラー/スキップが各 0 であることです。他モジュールに一致テストがないことは対象 4 件の成功証拠ではありません。これは MyBatis の tenant/owner/ページング/scope 欠落を検証し、ログイン、実 Feign、業務変換の並行性、Broker 配送は含みません。イメージ識別、ソースハッシュ、時刻、全報告を記録し資格情報を秘匿化します。現行改訂報告の 4 件は依然スキップで、この手順は新しい実行結果ではありません。

今回作成したコンテナ、ネットワーク、キャッシュで個人データがないと確認してから正確な名前で片付けます。MySQL イメージは /var/lib/mysql に匿名ボリュームを使用します。下例の -v はこの破棄可能なテストコンテナとともに匿名ボリュームだけを削除し、名前付きボリュームは削除しません。Maven キャッシュは既定で保持し、全体 prune は使いません。

```powershell
docker rm -f -v $mysql
docker network rm $network
```

失敗時は秘匿化したログ/報告を保存し、原因を修正して再試験します。成功した業務作成は繰り返しません。ソース：[テスト](../../omni-backend/omni-crm/src/test/java/com/omni/crm/integration/CrmMysqlInterceptorIntegrationTest.java)、[エンティティ](../../omni-backend/omni-crm/src/main/java/com/omni/crm/entity/CrmTenantEntity.java)、[データ権限](../../omni-backend/omni-crm/src/main/java/com/omni/crm/security/CrmDataPermissionHandler.java)、[イベントエンベロープ](../../omni-backend/omni-crm/src/main/java/com/omni/crm/dto/DomainEventEnvelope.java)。

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## 9. SRM 拡張とコンテナテスト

独立 supply-chain/full 学習プロジェクトの開発者向けで業務ログインは不要です。[SRM 現行事実](../srm.jp.md)と本ガイドのコンテナ/サービス統合手順を先に読みます。

1. database/changelog/srm/ に forward-only changeSet を追加し master に接続します。実行済み移行や init-all.sql は変えません。直接 owner 集約は SrmOwnedEntity と OWNED_TABLES、子は SrmTenantEntity と親/孫関連を使い、不存在 owner 列の条件を追加しません。
2. Entity、Mapper、独立要求/応答 DTO、Service インターフェース/実装、Controller を追加します。現行 Supplier/Evaluation/Risk/Quotation を参照し、テナント、行認可、状態、version を迂回しません。
3. モジュール seed は scripts/sql/seed/srm.sql、API 権限/ロールは scripts/sql/seed/auth.sql、SHA-256/assertion は database/seed/manifest.yaml を更新します。ここで owner 端点は内部管理ユーザー向けの業務 API で、Internal-Token によるサービス間プロトコルではありません。同じ完全コードの @PreAuthorize/@ServiceDataScope、書込の @OperLog、ボタンの v-permission を使います。ポータル profile/evaluation は SUPPLIER、該当権限、ACTIVE PortalUser 関連を要します。USER の inviteToken/requestId による登録は別のフローで、管理側 owner をポータルアカウントとして使いません。
4. 業務トランザクションで tenantId を明示して ReliableMessageRelay.send を呼びます。最上位 eventId とテナント/業務意図を原子的に確認し、Broker msgKey に一回実行を任せません。payload に口座/連絡情報/招待を入れず、見積金額は十進文字列にします。
5. 越権/他テナント、識別/スコープ欠落、古い版、不正状態、重複/衝突イベント、ロールバック、PII を検証します。ボタン非表示、HTTP 200、Outbox 挿入だけでなく実 HTTP/R.code を確認します。

第 6 節に従い依存を導入し、学習ルートで実行します。backend ツールは JDK 25/JAVA_HOME を提供します。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-srm -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

該当ソース変更時だけビルドを繰り返し、ハッシュが一致する有効報告は再利用できます。Reactor、failures/errors/skipped、フロント終了コードを確認し、イメージ識別、ソースハッシュ、時刻、秘匿化報告を保持します。

単体/設定/Controller の報告は各アサーション範囲のみを証明し、実 CAPTCHA、Workflow/MQ、DB 並行性や手順の実行を証明しません。実操作は [SRM Wiki](srm-flow.jp.md) のアカウント、招待、一意接頭辞、証拠範囲に従います。成功業務キーは先に照会し重複作成しません。DB 統合は破棄可能環境で行い共有業務 DB を使いません。

失敗時は詳細/version、ロール、tenant/owner または ACTIVE PortalUser を確認し trace/コードを保存します。ロール Saga と承認開始の再試行は別能力で、COMPLETED やエラー文字列だけで再開始しません。

今回所有し個人内容がない出力だけを片付け、名前付きボリューム/必要キャッシュを保持します。全体 prune、git clean/reset --hard は使いません。既存データのアップグレード/ロールバックは別途検証します。ソース：[範囲](../../omni-backend/omni-srm/src/main/java/com/omni/srm/security/SrmDataPermissionHandler.java)、[状態機械](../../omni-backend/omni-srm/src/main/java/com/omni/srm/domain/SrmStateMachine.java)、[見積](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/QuotationServiceImpl.java)、[リスク](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/RiskServiceImpl.java)。
<!-- omni:modules:end -->
