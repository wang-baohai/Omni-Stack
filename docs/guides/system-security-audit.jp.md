# システム設定、セキュリティ設定、監査ログ

テナント設定、XSS 防御、オンラインセッション、ログイン記録、操作ログ、信頼性メッセージ記録の保守方法を説明します。

## 1. 基本データ

システム管理にはテナント、組織、ユーザー、ロール、権限、OAuth2 クライアント、オンラインユーザー、認可記録、監査、XSS 設定があります。基本データには辞書があります。変更前にテナントと権限範囲を確認してください。

辞書は安定した表示列挙に使います。翻訳は表示だけを変え、状態値、権限コード、API パラメータは変えません。

## 2. OAuth2 クライアント

クライアント ID、許可グラント、リダイレクト URI、スコープ、同意要否を設定します。本番では HTTPS、正確な URI、Secret 管理、最小グラント、定期ローテーションと認可レビューが必要です。

## 3. XSS 防御

`@RequestBody` を持つ新規サービスは `XssConfigProvider` SPI を実装します。共通層はリクエストフィルター、Jackson 文字列サニタイズ、Gateway ヘッダーを提供します。

ルールや有効状態の変更時は直ちに `xss:enabled:{tenantId}` と `xss:rules:{tenantId}` を無効化し、TTL だけに依存しません。イベント属性、スクリプトプロトコル、許可するリッチテキスト境界を再検証します。

## 4. セキュリティヘッダー

Gateway は `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy` を付与します。入力サニタイズ、出力エンコード、CSP、認可の代替ではありません。

## 5. 監査情報

| 情報 | 場所 | 用途 |
|---|---|---|
| ログインログ | `sys_login_log` | 成否、送信元、認証方式 |
| 操作ログ | 管理画面 | 業務更新、操作者、結果、Trace ID |
| MQ 記録 | メッセージ画面 | Outbox、再試行、デッドレター、相関 |

`omni-auth` は `@OperLog` を使わず、認証はログイン記録で保持します。外部照会はテナントで絞り、バックグラウンド relay の全テナント走査は意図した設計です。

## 6. 調査フロー

Trace ID を取得し、操作ログ、MQ 記録、Tempo、Loki の順で同期・非同期経路を関連付けます。デッドレターの再送は下流の冪等性と状態を確認してから行います。

## 7. 本番チェック

開発用アカウントと各種秘密を交換し、MySQL、Redis、Nacos、XXL-JOB、観測・管理ポートを公開しません。TLS、最小権限、保持期間、マスキング、通知先、復旧演習を設定します。画像やレポートに認証情報や個人データを含めません。

## 8. 管理画面ページの4言語スクリーンショット

正式な画像はドキュメント専用 Playwright ケース `omni-frontend/e2e-docs/flows/management.flows.spec.ts` により実際の実行スタック上で生成され、言語別ディレクトリに保存され、他言語の画像を再利用せず、プレースホルダー画像やモック応答を使用しません。

- 前提条件：ローカル Compose フルスタックが実行中、フロントエンド `127.0.0.1:3000`；`omni-auth` と `omni-base` ヘルス。
- 操作者：`admin`（`SUPER_ADMIN`、システム管理・基礎データ・監視メニュー権限を保持）。
- 操作：ログイン後にテナント、組織、ロール、権限、辞書、オンラインユーザー、監査ログ、認可記録、XSS 防護、操作ログページに順に移動。
- 期待状態：ページタイトルと列ラベルが現在の言語でレンダリング、リストは実際のシステムデータを表示；レコードがない場合は製品自身の空状態を表示（撮影失敗ではない）。
- トークン：`E2eTokenFixture` がテストプロセス内で短期 JWT（TTL 1200 秒）を発行、収尾で破棄し、ドキュメント・ログ・リポジトリに書き込まない。
- 本グループはすべて**読み取り専用採集**：設定や監査データを一切作成・変更・削除しないため、書き込みスイッチは不要でデータ収尾もない。

機微情報説明：認可記録ページは OAuth2 `client_id`（公開識別子）、主体と認可タイプのみを表示し、**client secret・トークン・パスワードを含まない**；オンラインユーザーページは現在の環境で空状態であり、セッショントークンを含まない。

| ページ | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| テナント管理（tenant） | ![テナント管理（簡体字中国語）](../images/zh-CN/system-tenants.png) | ![テナント管理（英語）](../images/en-US/system-tenants.png) | ![テナント管理（日本語）](../images/ja-JP/system-tenants.png) | ![テナント管理（韓国語）](../images/ko-KR/system-tenants.png) |
| 組織管理（organization） | ![組織管理（簡体字中国語）](../images/zh-CN/system-organizations.png) | ![組織管理（英語）](../images/en-US/system-organizations.png) | ![組織管理（日本語）](../images/ja-JP/system-organizations.png) | ![組織管理（韓国語）](../images/ko-KR/system-organizations.png) |
| ロール管理（role、data-scope 入口を含む） | ![ロール管理（簡体字中国語）](../images/zh-CN/system-roles.png) | ![ロール管理（英語）](../images/en-US/system-roles.png) | ![ロール管理（日本語）](../images/ja-JP/system-roles.png) | ![ロール管理（韓国語）](../images/ko-KR/system-roles.png) |
| 権限管理（permission、menu ノードを含む） | ![権限管理（簡体字中国語）](../images/zh-CN/system-permissions.png) | ![権限管理（英語）](../images/en-US/system-permissions.png) | ![権限管理（日本語）](../images/ja-JP/system-permissions.png) | ![権限管理（韓国語）](../images/ko-KR/system-permissions.png) |
| 辞書管理（dictionary） | ![辞書管理（簡体字中国語）](../images/zh-CN/system-dictionaries.png) | ![辞書管理（英語）](../images/en-US/system-dictionaries.png) | ![辞書管理（日本語）](../images/ja-JP/system-dictionaries.png) | ![辞書管理（韓国語）](../images/ko-KR/system-dictionaries.png) |
| オンラインユーザー（online-user、空状態） | ![オンラインユーザー（簡体字中国語）](../images/zh-CN/system-online-users.png) | ![オンラインユーザー（英語）](../images/en-US/system-online-users.png) | ![オンラインユーザー（日本語）](../images/ja-JP/system-online-users.png) | ![オンラインユーザー（韓国語）](../images/ko-KR/system-online-users.png) |
| 監査ログ（audit） | ![監査ログ（簡体字中国語）](../images/zh-CN/system-audit-log.png) | ![監査ログ（英語）](../images/en-US/system-audit-log.png) | ![監査ログ（日本語）](../images/ja-JP/system-audit-log.png) | ![監査ログ（韓国語）](../images/ko-KR/system-audit-log.png) |
| 認可記録（oauth2） | ![認可記録（簡体字中国語）](../images/zh-CN/system-auth-records.png) | ![認可記録（英語）](../images/en-US/system-auth-records.png) | ![認可記録（日本語）](../images/ja-JP/system-auth-records.png) | ![認可記録（韓国語）](../images/ko-KR/system-auth-records.png) |
| XSS 防護（xss） | ![XSS 防護（簡体字中国語）](../images/zh-CN/system-xss-config.png) | ![XSS 防護（英語）](../images/en-US/system-xss-config.png) | ![XSS 防護（日本語）](../images/ja-JP/system-xss-config.png) | ![XSS 防護（韓国語）](../images/ko-KR/system-xss-config.png) |
| 操作ログ（operation-log） | ![操作ログ（簡体字中国語）](../images/zh-CN/system-operation-log.png) | ![操作ログ（英語）](../images/en-US/system-operation-log.png) | ![操作ログ（日本語）](../images/ja-JP/system-operation-log.png) | ![操作ログ（韓国語）](../images/ko-KR/system-operation-log.png) |

## 9. 辞書タイプ新規作成の3状態スクリーンショット（4言語）

`omni-frontend/e2e-docs/flows/system-dictionary.flows.spec.ts` により実際の実行スタック上で生成、カバレッジリストの `detail-and-action-states` と `failure-states` に対応。

- 前提条件：ローカル Compose フルスタックが実行中、`omni-base` ヘルス；辞書タイプテーブルに実際のベースラインデータが存在（採集前 17 件）。
- 操作者：`admin`（`dict:type:list`/`dict:type:create`/`dict:type:delete` 権限が必要）。
- 書き込みスイッチ：本グループは**データを作成する**ため、`E2E_MUTATIONS=true` を明示設定した時のみ実行；未設定時はグループ全体がスキップされ、あらゆる書き込み呼び出しは直接エラーを投げる。
- データ帰属と収尾：各言語が一意の `typeCode`（本ラウンドの `runStamp` を含む）を自作し、作成成功時に登録；afterAll が正式な `DELETE /api/base/dict/type/{id}` 契約で 1 件ずつクリーンアップし、応答とリスト再照合を検証。
- 実測収尾結果：4 passed / 0 skipped；`registered=4 deleted=4 residual=0`；`sys_dict_type` はベースライン **17** 行に戻り、`E2EDICT-%` 残留 **0**、`base-dictionary-catalog` シードアサーションは **101** 行を再現（本バッチで汚染されていない）。

| 状態 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 新規作成ダイアログ（create-or-edit） | ![辞書新規作成ダイアログ（簡体字中国語）](../images/zh-CN/system-dictionary-create-form.png) | ![辞書新規作成ダイアログ（英語）](../images/en-US/system-dictionary-create-form.png) | ![辞書新規作成ダイアログ（日本語）](../images/ja-JP/system-dictionary-create-form.png) | ![辞書新規作成ダイアログ（韓国語）](../images/ko-KR/system-dictionary-create-form.png) |
| 必須項目検証失敗（failure-or-forbidden） | ![辞書必須項目検証失敗（簡体字中国語）](../images/zh-CN/system-dictionary-create-validation.png) | ![辞書必須項目検証失敗（英語）](../images/en-US/system-dictionary-create-validation.png) | ![辞書必須項目検証失敗（日本語）](../images/ja-JP/system-dictionary-create-validation.png) | ![辞書必須項目検証失敗（韓国語）](../images/ko-KR/system-dictionary-create-validation.png) |
| 作成成功（key-action-success） | ![辞書作成成功（簡体字中国語）](../images/zh-CN/system-dictionary-create-success.png) | ![辞書作成成功（英語）](../images/en-US/system-dictionary-create-success.png) | ![辞書作成成功（日本語）](../images/ja-JP/system-dictionary-create-success.png) | ![辞書作成成功（韓国語）](../images/ko-KR/system-dictionary-create-success.png) |

### 登録済みの i18n PRODUCT_DEFECT（本バッチでは修正しない）

検証失敗画像では、**4言語 UI すべてでエラーメッセージが中国語**（例 `typeCode: 字典类型编码不能为空`）。実測根本原因：

1. `views/base/dict/index.vue` は `form rules` を一切定義せず、フロントエンドは必須検証を行わず（`.el-form-item__error` が空）、エラーは完全にバックエンド 400 応答に依存；
2. `CreateDictTypeRequest` の `@NotBlank(message = "字典类型编码不能为空")` は**中国語ハードコード**で、バックエンドメッセージ国際化に未接続；
3. また：ja-JP/ko-KR ではダイアログタイトルと `Type Code`/`Type Name`/`Remark` ラベルが英語でレンダリングされる。言語パックの対応キーの**取値自体が英語**だからである（「排序/並び順/정렬」のみ翻訳済み）。
   `npm run ui:i18n:parity`（4言語各 2319 キー、0 欠落）と `npm run ui:i18n:check`（0/0 項目）はいずれも通過するため、**翻訳完全度**の問題でありハードコード欠陥ではない。

上記スクリーンショットは**実際の文言をそのまま保持**し、モック・美化・隠蔽せず、本バッチで製品コードも変更していない；修正には製品側の意思決定（バックエンドメッセージ国際化方案 + ja/ko 翻訳の補完）が必要。

未カバーの 2 つの required flow：`config`（パラメータ設定）と `login-record`（ログイン記録）。実測で `sys_permission` に対応権限コードがなく、フロントエンドにも対応 view ディレクトリがないため、製品が現在ページを提供していない；制約に従い **required flow を削除せず、勝手に exempt とマークせず**、カバレッジリストで明示的な gap として保持。

[MQ 信頼性](../mq-reliability.jp.md)、[可観測性](../observability.md)、[Docker デプロイ](../docker-deployment.jp.md)を参照してください。

