# CRM モジュール アーキテクチャと実装基準

> 翻訳状態：`docs/docs-manifest.yaml` の中国語ソース摘要に基づく技術ドラフトです。業務用語は人工確認が必要です。識別子、権限コード、状態値は翻訳しません。

## 1. 設計結論と範囲

`omni-crm` は Lead、Customer、Contact、Opportunity、Activity、営業概要を扱う独立したテナント対応サービスです。Auth が本人確認、テナント所属、RBAC、permission-aware DataScope を所有し、CRM は Auth DB を直接読みません。Flowable は `omni-workflow`、RocketMQ は at-least-once、XXL-JOB はレコード単位ではなく一括リマインダーを担当します。

MVP はリードファネル、期限超過フォロー、Customer 360、商談パイプライン、転換率・受注率、監査を提供します。公海プール、一括入出力、タグ、マージ、契約、高度な承認は後続段階です。

## 2. ドメインとデータ

集約ルートは Lead、Customer、Contact、Opportunity、Activity です。すべての `crm_*` テーブルに `tenant_id` を持たせ、認可対象ルートは owner user/unit、version、論理削除・監査列を持ちます。インデックスは tenant を先頭に owner、状態、次回フォロー、重複検索を構成します。

金額は `DECIMAL(18,2)` / `BigDecimal`、通貨は ISO 4217 code です。MVP はテナントごとに1通貨とし、異なる通貨を合計しません。業務番号はテナント内一意で、`MAX + 1` を禁止します。

Lead 変換は行をロックし、可視性と状態を確認し、同一 CRM トランザクションで Customer/Contact/Opportunity を作成または関連付け、変更不能な conversion 記録、Lead 更新、Outbox event を保存します。一意な conversion 記録で再試行を冪等化します。

## 3. 状態機械

- Lead：new/working/qualified から converted または disqualified。
- Customer：active/inactive/blacklisted。状態変更は専用 command のみ。
- Opportunity：テナント pipeline の stage を移動し、closed-won/closed-lost を終端とし、履歴を必ず保存。
- Activity：planned/completed/cancelled。完了と取消は任意更新ではなく command。

合法・不正遷移はサーバーで検証しテストします。

## 4. テナント、RBAC、DataScope

信頼経路は Gateway identity → tenant filter → `@PreAuthorize` → permission-aware DataScope → MyBatis tenant/scope → `CrmRecordAccessGuard` です。tenant、scope、internal token の欠落、timeout、不一致は 403/503 で fail closed し、無条件検索へ退化しません。

`@PreAuthorize` と `@CrmDataScope` は同じ完全な permission code を使用し、ThreadLocal は `finally` で消去します。TenantLine は `crm_*` のみに適用し、全テナント relay 用 `sys_mq_message` は除外します。不可視行は ID 推測防止のため 404、更新は tenant + id + scope + version で行います。

Customer 360 の Customer、Contact、Opportunity、Activity は個別に権限と scope を解決します。既定 `USER` に CRM 権限を与えません。`v-permission` は表示補助であり最終境界は backend です。

## 5. API とサービス間整合性

Controller は `R<T>` / `R<PageResult<T>>` を返し、ページ上限は100です。書き込み API は resource/action 権限を持ち、owner 指定は tenant 制約付き Auth API で検証します。

ライフサイクルと権限は固定 enum で、辞書は表示 code だけを提供します。Workflow は tenant/business key で冪等起動し、Inbox event で完了します。Flowable は CRM DB を直接更新せず、CRM トランザクション内で Feign/MQ 通信を行いません。

event は `ReliableMessageRelay.send(bindingName, envelope, tenantId, eventId)` で送り、ID、状態、最小 snapshot のみを含めます。Consumer は tenant を検証し system context を設定・解除し、`crm_inbox_event` と業務変更を同一トランザクションで保存し、event 一意性と aggregate version で重複・乱順を防ぎます。

## 6. プライバシー、XSS、監査、UI

操作ログは PII をマスクし、snapshot 除外、Trace/event ID、同一 tenant/scope の snapshot 読取を保証します。CRM は `XssConfigProvider` を実装し Redis DB 0 の tenant XSS key を参照します。ログ、Outbox、backup、dead letter は PII 管理対象です。

画面は `views/crm/**/index.vue`、API は `src/api/crm.ts` に置きます。すべての操作に同じ `v-permission` を使用し、API 値と翻訳表示を分離します。

## 7. 非機能要件と受入

すべての一覧をページングし、楽観ロック、変換、Outbox/Inbox、定期 claim を冪等化します。MQ 停止時も業務と Outbox を commit し後送します。テストは状態機械、競合変換、tenant/DataScope、fail closed、version 競合、Outbox rollback、Inbox 重複・順序、PII、XSS、権限、主要ブラウザフローを含みます。

実装場所：`omni-backend/omni-crm/`、`omni-frontend/src/views/crm/`、`database/changelog/crm/`、`scripts/sql/seed/crm.sql`、`scripts/sql/seed/auth.sql`。詳細は [CRM 文書](../crm.jp.md) と[利用フロー](../guides/crm-flow.jp.md)を参照してください。
