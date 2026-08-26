# Procurement 実行モジュール アーキテクチャと実装基準

> 技術翻訳ドラフトです。人工確認が必要で、API 値、権限 code、状態名は翻訳しません。

## 1. 範囲と境界

`omni-procurement` は material category/material、requisition、approval rule、RFQ、quotation comparison、purchase order、goods receipt、overview を所有します。Workflow が Flowable runtime、SRM が supplier/portal、Asset が asset card を所有します。Procurement は Flowable と `omni-common-workflow` に依存しません。

MVP は Material → Requisition → Approval → RFQ → Quotation → Comparison → Purchase Order → Goods Receipt を実行します。契約、請求、決済、高度な sourcing、在庫管理は対象外です。

## 2. データと状態機械

集約ルートは Material/Category、ApprovalRoute、Requisition、RFQ、PurchaseOrder、GoodsReceipt です。すべての `proc_*` を tenant 分離し、子行は root から access を継承します。material/category/approval route/config は tenant 共有で owner DataScope を使いません。

Requisition、RFQ、PO、GR は server command、optimistic version、変更不能な業務証拠で遷移します。金額・数量は精度を定義した `BigDecimal`、承認金額区間は `[minAmount,maxAmount)` で上限なしを許可します。

## 3. 業務向け購買申請承認ルール

画面名は「購買申請承認ルール」で、routeCode や modelVersionId を直接操作させません。利用者は rule name、material category、含む下限、含まない上限、現在公開中の procurement Workflow を選びます。`routeCode` は server が `APR-{ULID}` で生成し変更不可です。exact category が default category より優先します。

新規 rule は `category=purchase` の current published model のみ選択できます。legacy 参照は自動改変せず報告します。実 match test、gap/overlap/default 分析、workflow invalid/unavailable 表示、安全 node preview、停止・削除 impact を提供します。

## 4. Tenant、RBAC、DataScope

TenantLine は `proc_*` のみ、`sys_mq_message` は relay のため除外します。Requisition は requester、RFQ/PO/GR は owner、child は root 経由で scope を継承します。Gateway → tenant filter → `@PreAuthorize` → DataScope → MyBatis → AccessGuard の順で fail closed し、write は tenant、visibility、state、version、permission を検証します。

## 5. Workflow とサービス間整合性

`RequisitionWorkflowCoordinator` は rule 解決と local start request を保存し、業務 transaction 外で Workflow を冪等起動します。completion event は Inbox 経由で local state を原子的に変更し、Flowable は Procurement table を直接更新しません。

SRM は active Supplier を提供し RFQ invitation を受けます。Portal quotation は requestId/expectedVersion を持ち、重複を冪等処理します。Goods Receipt は Asset 向け event を公開し、Asset 作成失敗で receipt を rollback しません。Outbox は tenantId 明示、Consumer は tenant 検証と Inbox+業務更新の同一 transaction を保証します。

## 6. API、UI、永続化と検証

Controller は `R<T>` / `R<PageResult<T>>`、write は `@PreAuthorize`。画面は `views/procurement/`、button は同じ `v-permission`、state/code と翻訳表示を分離します。承認 rule は 1440×900、1024×768、390×844 でページ水平 scroll を発生させません。

構造は `database/changelog/procurement/`、seed は `scripts/sql/seed/procurement.sql` と `scripts/sql/seed/auth.sql`。テストは状態、rule 境界/競合/gap、legacy model、Workflow retry、tenant/DataScope、quotation concurrency、PO/GR 冪等、Outbox/Inbox、Asset event、権限、XSS/audit、responsive UI、全体 browser flow を含みます。

[Procurement 利用フロー](../guides/procurement-flow.jp.md)を参照してください。
