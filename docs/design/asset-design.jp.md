# Asset 管理モジュール アーキテクチャと実装基準

> 技術翻訳ドラフトです。人工確認が必要で、code、API 値、状態名は保持します。

## 1. 範囲と境界

`omni-asset` は asset ledger、Procurement receipt ingestion、assignment/acceptance/return、transfer、disposal、overview を所有します。Procurement は PO/receipt、Workflow は承認 runtime、Auth は identity/RBAC/DataScope、SRM は Supplier を所有します。Asset は他サービス DB を読まず Flowable と `omni-common-workflow` を含みません。

MVP は物理 asset card と custody を管理します。会計減価償却、保守 work order、高度な倉庫は後続範囲です。

## 2. Domain と lifecycle

主な集約は Asset、Transfer、Disposal、Inbox Event です。すべての `ast_*` を tenant 分離します。管理一覧は `owner_user_id/owner_unit_id`、My Assets・accept・return は固定 `current_user_id` を使用します。Transfer/Disposal の子行は `ast_asset` から access を継承します。

状態変更は available、pending acceptance、in use、transfer/disposal processing、returned、disposed の server command で行い、任意 status edit を禁止します。書き込みは tenant、visibility、state、active operation、version を検証します。

## 3. Procurement receipt ingestion

`ProcurementGoodsReceiptConsumer` は `qualityStatus=PASS`、`assetManaged=true`、`assetQuantity` が正の正確な整数の場合だけ card を作成します。小数を丸めません。

Inbox event ID で重複 event を防ぎ、source receipt-line + unit sequence で異なる message ID による同じ card の重複も防ぎます。`purchaseAmount`、`residualValue`、event price は JSON number ではなく decimal string で転送します。

Backfill は認証済み internal API、明示 tenant、bounded page、同じ validation/idempotency を使用し、通常 ingestion rule を迂回しません。

## 4. Assignment、Transfer、Disposal

Assignment は custody と acceptance action を作り、割り当てられた固定 current user だけが accept/return できます。管理 scope を自己操作認可へ流用しません。

Transfer と Disposal は `ast_asset.active_operation_type/active_operation_id` を原子的に共有し、同時に1 operation だけを許可します。開始時に `previous_asset_status` と occupancy を保存し、local transaction 外で Workflow を冪等起動します。cancel/reject は同一 transaction で previous status を復元し occupancy を clear します。重複・乱順 event は副作用を増やしません。

## 5. Tenant、RBAC、DataScope と integration

TenantLine は `ast_*` のみ、`sys_mq_message` は除外します。Gateway → tenant filter → `@PreAuthorize` → aggregate scope → MyBatis → AccessGuard で fail closed します。不可視行は 404、write は resource/action permission と同じ `v-permission` を使います。

Workflow は idempotent request + Inbox event で連携し Asset table を直接更新しません。Procurement event/backfill DTO は version 付きです。Outbox は tenantId 明示、Consumer は tenant context を設定し `finally` で解除します。

## 6. API、UI、永続化と検証

Controller は `R<T>` / `R<PageResult<T>>`。`views/asset/` に overview、ledger、My Assets、transfer、disposal の responsive 状態を実装します。構造は `database/changelog/asset/`、RBAC seed は `scripts/sql/seed/auth.sql` で管理します。

テストは lifecycle、tenant/DataScope、固定 current user、receipt 条件・正確な数量、二重冪等、decimal string、backfill replay、accept/return、Transfer/Disposal 競合、cancel/reject 復元、Workflow retry/duplicate/order、Outbox/Inbox、権限、XSS/audit、全 browser flow を含みます。

[Asset 利用フロー](../guides/asset-flow.jp.md)を参照してください。
