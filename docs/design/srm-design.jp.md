# SRM サプライヤー管理 アーキテクチャと実装基準

> 技術翻訳ドラフトです。業務用語は人工確認が必要で、code と状態値は変更しません。

## 1. 範囲と境界

`omni-srm` は Supplier master、准入、lifecycle、evaluation、risk、invitation、portal-company association を所有します。Auth は user/role/tenant/DataScope、Workflow は承認 runtime、Procurement は RFQ/order、Asset は資産台帳を所有します。SRM は他サービス DB を読まず Flowable を内包しません。

MVP は招待、Portal 登録・入居、准入、active/suspend/blacklist、業績評価、risk indicator、Supplier 360、見積入力連携を含みます。契約、決済、高度な category 戦略は対象外です。

## 2. ドメインと lifecycle

Supplier、Invitation、Portal Association、Evaluation、Risk Indicator/Record、Inbox Event を明示的に管理し、すべての `srm_*` テーブルを tenant 分離します。子リソースは Supplier/Evaluation root から可視性を継承し、存在しない owner 列を子テーブルへ付加してはいけません。

状態遷移は server command で実行します。准入と有効化はデータと承認結果を検証し、停止・再開・blacklist は監査証拠を保存します。評価基準は version 管理し重み付き点数を決定的に計算します。risk level は有効 indicator から算出し任意編集しません。

## 3. Portal 入居と Saga

既定 `USER` は enroll のみ可能です。`srm:portal:profile` / `srm:portal:evaluation` には `SUPPLIER` role と active な `srm_supplier_portal_user` 関連が必要です。enrollment は `inviteToken` と client `requestId` を必須とし、requestId を冪等 key にします。

SRM は local association request と Outbox を先に commit し、Auth が role assignment を冪等処理して result event を返し、SRM が Inbox 経由で Saga を確定します。失敗時に未検証 active association を作りません。Portal user ID を内部 `owner_user_id/owner_unit_id` に書き込みません。

## 4. Security と DataScope

Gateway identity → tenant filter → functional permission → permission-aware DataScope → tenant/scope interceptor → AccessGuard の順で fail closed します。TenantLine は `srm_*` のみ、relay table は除外します。書き込みは tenant、root visibility、lifecycle、optimistic version を確認します。

管理 role と portal role は分離します。Portal API は認証 user の関連 Supplier を導出し、任意 supplierId を認可根拠にしません。PII は明示権限がない場合に mask し、ログ・event は最小 ID/state のみです。

## 5. サービス間整合性

Auth API は tenant 制約付きです。Workflow は冪等起動して結果 event を消費します。Procurement は active Supplier を選び RFQ invitation を公開し、Portal は requestId/version 付きで見積を Procurement に送ります。Asset は SRM DB を読まず明示 snapshot/ID を使います。

Outbox は tenantId を明示し、Consumer は tenant 検証、Inbox と業務変更の同一 transaction、重複・乱順耐性を実装します。SRM DB transaction 内で network call を行いません。

## 6. API、UI、永続化と検証

Controller は `R<T>` / `R<PageResult<T>>`、write は `@PreAuthorize` を使用します。管理画面は `views/srm/`、Portal は `views/supplier-portal/`、button は同じ `v-permission` を使用します。

構造は `database/changelog/srm/`、seed は `scripts/sql/seed/srm.sql` と `scripts/sql/seed/auth.sql` で管理し、manifest checksum/assertion を同時更新します。テストは lifecycle、tenant/DataScope、子 root 継承、Saga replay/failure、invite、評価計算、risk、quotation、PII/XSS/audit、管理/Portal ブラウザフローを含みます。

[SRM システム文書](../srm.jp.md)と[利用フロー](../guides/srm-flow.jp.md)を参照してください。
