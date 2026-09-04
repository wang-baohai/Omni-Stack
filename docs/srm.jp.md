# SRM サプライヤー関係管理

> 本文書は SRM モジュールのシステム上の唯一の真実です。AI が SRM コードを変更する前に必ず読んでください。
> アーキテクチャ全体は [architecture.md](architecture.md)、API 契約は [api-contract.md](api-contract.md)、開発規範は [backend-patterns.md](backend-patterns.md) / [frontend-patterns.md](frontend-patterns.md) を参照してください。
> 設計ベースラインのアーカイブは [design/srm-design.md](design/srm-design.md) にあります。

[中文](srm.md) | [English](srm.en.md) | [한국어](srm.kr.md)

SRM は独立したマイクロサービス `omni-srm` であり、サプライヤーの全ライフサイクル管理のクローズドループをカバーします：サプライヤー登録/准入 → 審査 → グレード分類 → パフォーマンス評価 → リスク管理 → 淘汰/退出。調達実行（購買依頼、見積依頼、発注、入荷）と資産処分は SRM の範囲外であり、それぞれ今後構築される `omni-procurement` と `omni-asset` で実装されます。

## 1. サービス境界

| 項目 | 値 |
|---|---|
| Maven モジュール | `omni-srm` |
| サービスポート | `8105` |
| 管理ポート | `19905` |
| XXL-JOB エグゼキュータ | `omni-srm` / `9905` |
| データベース | `omni_srm` |
| Gateway ルーティング | `/api/srm/**` → `lb://omni-srm`（StripPrefix を使用しない） |
| Redis | DB 0、Auth の XSS 設定を共有、キープレフィックス `srm:` |

**依存モジュール**：`omni-common-core`、`omni-common`、`omni-common-mybatis`、`omni-common-redis`、`omni-common-operlog`、`omni-common-job`、`omni-common-mqlog`。

**`omni-common-workflow` に依存してはいけません**。MVP 段階のサプライヤー准入審査はシンプルなステートマシンで実装し、Flowable エンジンを導入しません。

**クロスサービス呼び出し**：OpenFeign + `X-Internal-Token` を通じて Auth の内部 API を呼び出します。SRM は userId/unitId のみを保持し、`omni_auth` をクロスデータベースで参照しません。

## 2. ドメインモデル

### 2.1 集約とテーブル

| 集約 | テーブル | 責務 |
|---|---|---|
| Supplier | `srm_supplier`、`srm_supplier_contact`、`srm_supplier_qualification`、`srm_supplier_bank_account` | サプライヤー基本データ、連絡先、資格、銀行口座 |
| Evaluation | `srm_evaluation_template`、`srm_evaluation_dimension`、`srm_evaluation`、`srm_evaluation_item` | 評価テンプレート、評価ディメンション、評価記録、採点明細 |
| Risk | `srm_risk_indicator`、`srm_risk_assessment` | リスク指標、総合リスク評価 |
| Portal | `srm_supplier_invite`、`srm_supplier_enrollment`、`srm_supplier_portal_user` | 招待、入居記録（Saga）、ポータルアカウント関連付け |

```mermaid
erDiagram
    SRM_SUPPLIER ||--o{ SRM_SUPPLIER_CONTACT : has
    SRM_SUPPLIER ||--o{ SRM_SUPPLIER_QUALIFICATION : holds
    SRM_SUPPLIER ||--o{ SRM_SUPPLIER_BANK_ACCOUNT : owns
    SRM_SUPPLIER ||--o{ SRM_EVALUATION : evaluated_by
    SRM_SUPPLIER ||--o{ SRM_RISK_ASSESSMENT : assessed
    SRM_SUPPLIER ||--o{ SRM_SUPPLIER_ENROLLMENT : enrolls
    SRM_SUPPLIER ||--o{ SRM_SUPPLIER_PORTAL_USER : authorizes
    SRM_EVALUATION_TEMPLATE ||--o{ SRM_EVALUATION_DIMENSION : contains
    SRM_EVALUATION_TEMPLATE ||--o{ SRM_EVALUATION : uses
    SRM_EVALUATION ||--o{ SRM_EVALUATION_ITEM : scores
    SRM_SUPPLIER ||--o{ SRM_RISK_INDICATOR : has
    SRM_RISK_ASSESSMENT ||--o{ SRM_RISK_INDICATOR : aggregates
```

### 2.2 共通フィールド規則

すべての `srm_*` テーブルに `tenant_id` が必須です。認可可能なビジネステーブルには追加で以下が必要です：

- `tenant_id` — テナント分離
- `owner_user_id` — SELF スコープと業務担当者
- `owner_unit_id` — DEPT/DEPT_AND_BELOW/CUSTOM スコープ
- `version` — 楽観ロック
- `deleted` — 論理削除
- `id/create_time/update_time/create_by/update_by` — 監査フィールド

**主要制約**：

- ユーザー/組織 ID は Auth が管理。クロスデータベース外部キーなし、フロントエンド送信のユーザー名や ownerUnitId を信頼しない
- `supplier_no` はデータベース ID から生成、テナント内で一意。`SELECT MAX(...) + 1` は禁止
- `credit_code`（統一社会信用コード）はテナント内で一意
- 銀行口座番号は PII マスキングを使用、完全な値は `srm:pii:view` を持つユーザーのみに返却
- 日時は統一 `yyyy-MM-dd HH:mm:ss` 形式
- 通常の PUT で owner やライフサイクル status を直接変更不可
- 外部リクエストで裸の `selectById/updateById/deleteById` を使用禁止
- `owner_user_id` は内部調達担当者のみ；ポータルアカウントは `srm_supplier_portal_user` で関連付け、owner フィールドの再利用は禁止

### 2.3 主要テーブル詳細

**`srm_supplier`** — サプライヤー基本テーブル：`supplier_no/name/normalized_name/supplier_type/industry_code/credit_code/website/phone/email/region/address/category_code/level_code/status/assigned_time/last_evaluation_time`。`level_code` 列挙：STRATEGIC/PREFERRED/QUALIFIED/ELIMINATED、評価により自動調整または手動設定。`status` は8つの状態（ステートマシン参照）。

**`srm_supplier_contact`** — 連絡先：サプライヤーごとに有効な主要連絡先は最大1つ（`primary_flag` + `status` + `deleted` による `primary_supplier_guard` 一意制約）。owner は Supplier の owner の権限スナップショット；サプライヤー移管時に同一トランザクションで同期。

**`srm_supplier_qualification`** — 資格：`qualification_name/certificate_no/issuing_authority/issue_date/expiry_date/status`。`expiry_date` は資格期限アラートに使用（30日以内 → YELLOW、期限切れ → RED）。MVP では添付ファイルを保存しません。

**`srm_supplier_bank_account`** — 銀行口座：`account_no` は PII フィールド。サプライヤーごとに複数の銀行口座を管理可能；1つをデフォルトとしてマーク。

**`srm_supplier_portal_user`** — ポータルユーザー関連付け：`tenant_id + user_id` が一意、1つの Auth ユーザーが同一テナントで1つのサプライヤーのみにマッピング。

**`srm_supplier_enrollment`** — 入居記録（Saga）：`request_id` で冪等性確保。`status` は PENDING_ROLE_ASSIGN/ROLE_ASSIGN_FAILED/COMPLETED/CANCELLED。`active_user_guard` で同一 tenant + userId のアクティブ入居は最大1つ。

**`srm_supplier_invite`** — 招待：元の inviteToken は作成時に一度だけ返却、データベースには SHA-256 ハッシュのみ保存。`version` 条件付きアトミック増加で `used_count` を管理し、並行超過使用を防止。

**`srm_evaluation_template`** + **`srm_evaluation_dimension`** — 評価テンプレート：MVP ではテナントごとに1つのデフォルトテンプレート（`default_flag=1`）を提供し、4つの事前設定ディメンション：品質（30%）、納期（30%）、価格（20%）、サービス（20%）。`weight` の合計は100に等しいこと。

**`srm_evaluation`** + **`srm_evaluation_item`** — 評価記録：`score` は1-5点（`DECIMAL(3,1)`）。`total_score` は加重パーセント制で自動計算（範囲20-100）。評価完了後、サプライヤー等級を自動マッピング：≥90 戦略級、≥75 優先級、≥60 合格級、<60 淘汰候補。評価項目は追加のみ、修正インターフェースなし。

**`srm_risk_indicator`** — リスク指標：`indicator_type` 列挙：FINANCIAL/COMPLIANCE/SUPPLY/COOPERATION/QUALITY/CERTIFICATE。`risk_level` 列挙：GREEN/YELLOW/RED。CERTIFICATE 指標は資格期限日から自動計算。

**`srm_risk_assessment`** — 総合リスク評価：`overall_level` は全指標の最高等級を取得（RED > YELLOW > GREEN）。

## 3. セキュリティアーキテクチャ

### 3.1 5層信頼チェーン

```
Gateway JWT 検証 → SRM テナントチェック → Spring Security @PreAuthorize
→ @SrmDataScope アスペクト → MyBatis DataPermission インターセプター → SrmRecordAccessGuard 行レベル書込み認可
```

1. Gateway が RS256 JWT を検証、`X-User-*`、`X-Tenant-Id`、`X-Gateway-Forwarded` を上書き注入
2. `GatewayPreAuthFilter` が `Authentication` を構築、userId/tenantId を検証
3. Controller の `@PreAuthorize` で機能権限を検証
4. `@SrmDataScope(permissionCode)` アスペクトが Auth 内部 API を呼び出し dataScope を解決
5. MyBatis-Plus が tenant + owner 条件を追加
6. `SrmRecordAccessGuard` が行レベル書込み認可を検証

**フェイルクローズ**：tenant 欠落 → 401、scope 欠落 → `id=-1`（データ閲覧ゼロ）、Auth 利用不可 → 503。決して無フィルタリングに degraded しません。

### 3.2 MyBatis インターセプター順序

SRM は独自の `mybatisPlusInterceptor` Bean を定義；順序は固定で入替不可：

```
TenantLineInnerInterceptor → DataPermissionInterceptor → PaginationInnerInterceptor
```

- TenantLine は `srm_*` テーブルのみ処理
- `sys_mq_message` は両権限インターセプターから除外（Relay は設計上全テナントをスキャン）
- DataPermission は Pagination の前に配置し、COUNT とレコードが同じスコープを共有

### 3.3 DataScope マッピング

| dataScope | SQL 条件 |
|---|---|
| SELF | `owner_user_id = currentUserId` |
| DEPT | `owner_unit_id = primaryUnitId` |
| DEPT_AND_BELOW / CUSTOM | `owner_unit_id IN accessibleUnitIds` |
| TENANT / ALL | owner 条件なし、TenantLine は常に保持 |

評価とリスクは `supplier_id` を通じて Supplier の owner にスコープを継承。Template/Dimension はテナントスコープ + 機能権限のみ。ポータルユーザー（SUPPLIER ロール）は内部 owner dataScope を使用せず、`srm_supplier_portal_user` で supplierId を解決、見つからない場合はフェイルクローズ。

### 3.4 行レベル書込み認可

DataPermissionInterceptor は書込みを保護しません。更新/削除/承認/凍結/ブラックリストコマンドは以下が必須：

1. `tenant_id + id + data scope` で可視レコードを検索（不可視 → 404、ID 列挙防止）
2. ステートマシンとビジネス不変量を検証
3. `tenant_id + id + version` 条件で更新
4. 影響行数 ≠ 1 の場合は競合を返却

### 3.5 権限コード一覧

| リソース | 権限コード |
|---|---|
| Overview | `srm:overview:list` |
| Supplier | `srm:supplier:list/create/update/delete/approve/reject/suspend/resume/blacklist/restore/eliminate/transfer` |
| Contact | `srm:contact:list/create/update/delete` |
| Qualification | `srm:qualification:list/create/update/delete` |
| Bank Account | `srm:bank-account:list/create/update/delete` |
| Evaluation | `srm:evaluation:list/create/view` |
| Risk | `srm:risk:list/update/assess` |
| Invite | `srm:invite:list/create/revoke`、`srm:portal:invite` |
| Owner 候補 | `srm:owner:list` |
| PII 閲覧 | `srm:pii:view` |
| Portal | `srm:portal:enroll/profile/evaluation/quotation` |

`/` は同一リソース内の複数完全権限コードの略記；データベースには個別に保存。`@PreAuthorize` と `@SrmDataScope` は同じ完全権限コードを使用。

### 3.6 PII マスキング

- 完全な銀行口座番号、連絡先電話番号、メールは `srm:pii:view` を持つユーザーのみに返却
- その他のユーザーにはバックエンド VO がマスク値を直接返却（`6222****1234`、`138****1234`、`a***@example.com`）；フロントエンドに依存しない
- リストはデフォルトでマスク；詳細は権限に応じて決定
- ポータル SUPPLIER ロールは自身のデータに完全アクセスを暗黙的に許可

### 3.7 XSS 防御

SRM は `XssConfigProvider` SPI を実装し、Redis DB 0 の `xss:enabled:{tenantId}` と `xss:rules:{tenantId}` を読み取ります。キャッシュミス時は Auth にフォールバックまたは内蔵ベースラインルールを使用；保護を無効化しません。MVP の備考はプレーンテキストのみ許可、`v-html` はフロントエンドで禁止。

### 3.8 ロールと dataScope

| ロール | dataScope | 能力 |
|---|---|---|
| `SRM_ADMIN` | TENANT | 現在のテナントの全 SRM 機能/データ |
| `PROCUREMENT_MANAGER` | DEPT_AND_BELOW | 部門および下位、サプライヤー評価、リスク管理 |
| `PROCUREMENT_STAFF` | SELF | 自身のデータと日常業務 |
| `SUPPLIER` | SELF | ポータルセルフサービス：入居後の企業情報管理、自身のパフォーマンス閲覧 |
| `SUPER_ADMIN` | ALL | 全機能、SRM データは現在のテナントに限定 |

デフォルト USER ロールは `srm:portal:enroll` のみ付与され、SRM 管理やポータル profile/evaluation/quotation 権限は付与されません。入居完了後に SUPPLIER ロールを追加して初めて profile/evaluation/quotation にアクセスできます。`srm:portal:quotation` は `SUPPLIER` と、プラットフォーム規則により全権限ツリーを保持する `SUPER_ADMIN` にのみ厳格に付与され、`SRM_ADMIN`・`PROCUREMENT_MANAGER` などの内部ロールがサプライヤーに代わって見積を行うことはできません。見積 Controller は SUPPLIER ロールと有効な PortalUser 関連を同時に要求し、SUPER_ADMIN だけではサプライヤーになりすませません。

## 4. ステートマシンとコアフロー

### 4.1 サプライヤーライフサイクル

```
[*] → REGISTERING → PENDING_REVIEW（Auth ユーザーとロール作成成功）
[*] → REGISTERING → REGISTERING_FAILED（Auth 作成/ロール割り当て失敗）
REGISTERING_FAILED → REGISTERING（バックエンドリトライ）
[*] → PENDING_REVIEW（管理者作成）
PENDING_REVIEW → APPROVED（承認）
PENDING_REVIEW → REJECTED（却下）
REJECTED → PENDING_REVIEW（再提出）
APPROVED → SUSPENDED（協力停止）
SUSPENDED → APPROVED（協力再開）
APPROVED → BLACKLISTED（ブラックリスト追加）
BLACKLISTED → APPROVED（ブラックリスト解除、srm:supplier:restore 必要）
APPROVED/SUSPENDED → ELIMINATED（淘汰退出）
ELIMINATED → [*]（終端状態、回復不可）
```

- `APPROVED` 状態のサプライヤーのみ調達モジュールから参照可能
- `BLACKLISTED` は `srm:supplier:blacklist` 権限が必要
- `ELIMINATED` は終端状態で回復不可
- 管理者作成のサプライヤーは直接 `PENDING_REVIEW` に入る
- `REGISTERING/REGISTERING_FAILED` はポータルのクロスサービス登録専用

### 4.2 パフォーマンス評価フロー

```
POST /evaluation (supplierId, period, items[])
→ SELECT Supplier FOR UPDATE + tenant/scope
→ Query Template (default)
→ INSERT Evaluation + Items（トランザクション内）
→ パーセント制 totalScore = SUM(item.score / 5 × item.weight) を計算
→ 等級をマッピングし Supplier.level_code を UPDATE
→ INSERT Outbox event（同一トランザクション）
```

評価は四半期ごとを推奨しますが MVP では強制せず、管理者が手動で開始します。評価完了後、システムは自動的に：
1. 加重合計スコアを計算（1-5点をパーセント制に正規化、範囲20-100）
2. 新しいサプライヤー等級をマッピング（≥90 戦略級、≥75 優先級、≥60 合格級、<60 淘汰候補）
3. `srm_supplier.level_code` を更新
4. `last_evaluation_time` を記録

### 4.3 リスク評価フロー

```
手動/自動リスク指標更新
→ 総合リスクレベルを再計算（全指標の最高レベルを取得）
→ INSERT/UPDATE srm_risk_assessment
→ レベルが RED に変更された場合、Outbox イベント通知を書込み
```

資格期限アラートロジック：`expiry_date - today <= 30` → CERTIFICATE 指標自動 YELLOW；`expiry_date < today` → CERTIFICATE 指標自動 RED。XXL-JOB 定期タスクによる事前スキャンは Phase 2 で有効化予定（MVP：手動トリガーまたは無効）。

### 4.4 ポータル口座開設と入居

```
POST /api/auth/register（公開 Auth 自己登録、デフォルト USER ロール割り当て）
→ ログインして JWT を取得
→ POST /api/srm/portal/enroll（認証済み、inviteToken + 企業情報）
→ INSERT 入居申請と Supplier (status=REGISTERING)
→ INSERT Outbox srm.portal-role.assign-requested.v1
→ Auth が Outbox を消費し SUPPLIER ロールを割り当て
→ MQ auth.portal-role.assigned.v1 が返却
→ SRM が消費：INSERT PortalUser 関連付け、Supplier → PENDING_REVIEW
```

ポータル口座開設と入居は2つのセキュリティ境界に分離：
- 口座開設は公開 `POST /api/auth/register` のみ使用；SRM はパスワードを扱わない
- 入居にはテナント固有の inviteToken が必須；テナント、有効期限、使用回数を確認
- 統一社会信用コード（credit_code）はテナント内で一意
- 入居は requestId で冪等性確保；1つの userId は1つのサプライヤーのみにマッピング
- SRM は Outbox/Saga で Auth に既存 USER アカウントへの SUPPLIER ロール追加を要求；ロール割り当て失敗時は `REGISTERING_FAILED` を維持

## 5. API エンドポイントインデックス

### 5.1 共通契約

- 全レスポンス：`R<T>`、ページネーション：`R<PageResult<T>>`
- `page=1`、`size=10`、最大 `size=100`
- Entity は Request/Response として使用しない；状態コマンドは専用 DTO を使用
- 日付パラメータ：`@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")`
- 状態/承認/評価リクエストは `version` を保持
- 書込みエンドポイントは `@PreAuthorize` と `@OperLog` の両方を宣言

### 5.2 エンドポイント一覧

全エンドポイントは `/api/srm` プレフィックス。

| ドメイン | エンドポイント |
|---|---|
| Overview | `GET /overview/summary`、`/risk-dashboard` |
| Supplier | `GET /supplier/list`、`/{id}`、`/{id}/overview`、`POST /supplier`、`PUT/DELETE /supplier/{id}` |
| Supplier コマンド | `POST /supplier/{id}/submit`、`/approve`、`/reject`、`/suspend`、`/resume`、`/blacklist`、`/restore-from-blacklist`、`/eliminate`、`/transfer` |
| Contact | `GET /supplier/{id}/contact/list`、`POST /supplier/{id}/contact`、`PUT/DELETE /contact/{id}` |
| Qualification | `GET /supplier/{id}/qualification/list`、`POST /supplier/{id}/qualification`、`PUT/DELETE /qualification/{id}` |
| Bank Account | `GET /supplier/{id}/bank-account/list`、`POST /supplier/{id}/bank-account`、`PUT/DELETE /bank-account/{id}` |
| Insight | `GET /supplier/{id}/evaluation/history`、`/supplier/{id}/risk` |
| Evaluation | `GET /evaluation/list`、`/{id}`、`POST /evaluation` |
| Risk | `GET /risk/list`、`PUT /risk/indicator/{id}`、`POST /risk/assessment/{supplierId}` |
| Owner オプション | `GET /options/owners` |
| Portal 招待 | `GET /portal/invite/list`、`POST /portal/invite`、`POST /portal/invite/{id}/revoke`（管理側） |
| Portal 入居 | `POST /portal/enroll`（認証済み、inviteToken 付き） |
| Portal 企業情報 | `GET /portal/profile`、`PUT /portal/profile`、`GET /portal/contacts`、`GET /portal/qualifications`、`GET /portal/bank-accounts` |
| Portal パフォーマンス | `GET /portal/evaluations`、`GET /portal/evaluations/{id}` |

### 5.3 サプライヤー360 チャンク権限

`/supplier/{id}/overview` は連絡先、資格、銀行口座、評価履歴、リスク概要を返却。各チャンクはそれぞれの list 権限で独立にデータスコープを解決；権限欠如時はそのチャンクをクエリしない。実装は `SrmPermissionScopeExecutor` でチャンクごとにスコープを確立・クリーンアップ。

## 6. クロスサービス統合

### 6.1 Auth Feign

- SRM は userId/unitId のみ保持；割り当て前に Auth 内部 API でユーザー存在、有効、同テナントを検証
- ownerUnitId は Auth の権威ある主組織から取得；フロントエンドを信頼しない
- リスト表示は ID を収集後1回のバッチ API 呼び出し；行ごとの Feign（N+1）は禁止
- Auth 利用不可時：dataScope → 503 フェイルクローズ；表示エンリッチ → ID/不明ユーザーを返却可能

### 6.2 Outbox イベント

`ReliableMessageRelay.send("srm-domain-out-0", envelope, tenantId, eventId)` でローカル Outbox に書込み；tenantId は明示的に渡すこと。

イベントエンベロープは `eventId`、`eventType`、`tenantId`、`payload` を含む。定義済みイベント：

- `srm.supplier.registered.v1`
- `srm.supplier.approved.v1`
- `srm.supplier.rejected.v1`
- `srm.supplier.suspended.v1`
- `srm.supplier.blacklisted.v1`
- `srm.supplier.eliminated.v1`
- `srm.portal-role.assign-requested.v1`
- `auth.portal-role.assigned.v1`（Auth が発行、SRM が消費）
- `auth.portal-role.assign-failed.v1`（Auth が発行、SRM が失敗マーク）
- `srm.evaluation.completed.v1`
- `srm.risk.level-changed.v1`

イベントは ID と状態スナップショットのみ伝達；完全な銀行口座番号、連絡先電話、メール、inviteToken は含まない。消費者は冪等でなければならない。

### 6.3 操作ログ

`@OperLog` は PII 脱感作をサポート（銀行口座番号、連絡先電話、メール、サプライヤー電話）。入居招待 inviteToken は認証情報として扱い、ログへの書込みは禁止。

### 6.4 内部 API

SRM は Procurement/Asset サービスに以下の機能を提供します：
- `GET /api/internal/supplier/{id}?tenantId={tenantId}` — サプライヤー概要
- `GET /api/internal/supplier/search?tenantId={tenantId}&status=APPROVED&categoryCode={code}` — 承認済みサプライヤー検索
- `POST /api/internal/supplier/batch` — body は `{tenantId,supplierIds}`。入力の最初の出現順に最大 100 件の概要を返し、存在しない ID は省略
- `GET /api/internal/quotation/batch?tenantId={tenantId}&rfqId={rfqId}` — RFQ の有効見積・バージョン・行スナップショットを取得
- 全内部 API は `X-Internal-Token` と `X-Tenant-Id` を使用し、query/body テナントはヘッダーと一致が必要。Gateway 経由で公開せず、サプライヤー概要に連絡先や銀行口座の PII は含まれない

SRM がポータル可視の RFQ を照会する場合は Procurement を呼び出します：

- `GET /api/internal/procurement/rfq/invitations?supplierId={supplierId}`
- `GET /api/internal/procurement/rfq/{rfqId}/invitation?supplierId={supplierId}`

supplierId は現在の userId に対応する `srm_supplier_portal_user` のみ指定でき、ポータルリクエストが渡す値は受け付けません。すべての内部リクエストは `X-Tenant-Id` も同時に運搬し、API に追加の query/body テナントがある場合はヘッダーと一致が必要です。

RFQ `status=SENT`、招待 `status IN (INVITED, QUOTED)`、かつ quotationDeadline 未超過の場合のみ見積の送信・更新を許可し、`DRAFT/CLOSED/AWARDED/CANCELLED` は一律拒否します。

## 7. 硬制約

SRM コードを変更する前に遵守すべきルール：

1. **テナント分離**：全 `srm_*` テーブルに `tenant_id` 必須；TenantLine は常に追加；通常 API はクロステナント不可
2. **楽観ロック**：全书込みは `tenant_id + id + version` 条件で更新
3. **フェイルクローズ**：tenant 欠落 → 401、scope 欠落 → `id=-1`、Auth 不可用 → 503、決して degraded しない
4. **ThreadLocal クリーンアップ**：`SrmDataScopeContext` と `SrmTenantContext` は `finally` ブロックで必ずクリア、メモリリーク防止
5. **権限二重宣言**：書込みエンドポイントは `@PreAuthorize`（機能権限）と `@SrmDataScope`（データスコープ）の両方を同じ完全権限コードで宣言
6. **バックエンド PII マスキング**：`srm:pii:view` なし時バックエンド VO はマスク値を直接返却；フロントエンドに依存しない
7. **明示的 Outbox tenantId**：`ReliableMessageRelay.send()` は `Long tenantId` を明示的に渡す；ThreadLocal 暗黙解決は禁止
8. **インターセプター順序**：TenantLine → DataPermission → Pagination、入替不可
9. **書込み認可**：DataPermissionInterceptor は書込みを保護しない；AccessGuard 行レベル検証が必須
10. **ステートマシン**：通常 PUT は status 変更を受け付けない；専用コマンドエンドポイントを使用
11. **MySQL DATETIME 範囲**：`LocalDateTime.MIN/MAX` をクエリパラメータとして使用不可
12. **評価テンプレート読取専用**：MVP テンプレートはテナント初期化で自動作成；動的設定 UI なし
13. **ポータル分離**：ポータルユーザーは `srm_supplier_portal_user` で関連付け；内部 owner dataScope の再利用禁止
14. **`sys_mq_message` は権限インターセプターから除外**：Relay は全テナントをスキャン；ユーザークエリは依然明示的テナントフィルタリングが必要
15. **owner とポータルの分離**：`owner_user_id` は内部調達担当者；ポータル `user_id` はサプライヤーログインアカウント；混同禁止

## 8. フロントエンド構造

```
omni-frontend/src/
├── api/
│   ├── srm-overview.ts          # 概要統計 + リスクダッシュボード
│   ├── srm-supplier.ts          # サプライヤー CRUD + コマンド + サブリソース
│   ├── srm-evaluation.ts        # 評価 CRUD
│   ├── srm-risk.ts              # リスク指標 + 評価
│   └── srm-portal.ts            # ポータル入居/資料/パフォーマンス
├── views/
│   ├── srm/
│   │   ├── overview/index.vue   # サプライヤー概要 + リスクダッシュボード
│   │   ├── supplier/index.vue   # サプライヤー管理
│   │   ├── evaluation/index.vue # パフォーマンス評価
│   │   ├── risk/index.vue       # リスク管理
│   │   └── invite/index.vue     # 招待管理
│   └── supplier-portal/
│       └── index.vue            # サプライヤーポータルワークスペース（単一ページ）
└── components/srm/
    ├── SupplierOverview.vue     # サプライヤー360ビュー
    ├── SupplierPicker.vue       # サプライヤーセレクター
    ├── SupplierResourcesDrawer.vue  # サプライヤーサブリソースドロワー
    ├── EvaluationScorecard.vue  # 評価スコアカード
    ├── RiskIndicator.vue        # リスク指標カード
    └── RiskDashboard.vue        # リスクダッシュボードコンポーネント
```

- `ApiResponse/PageResult` は `src/types/api.ts` からのみインポート
- ボタンは `v-permission` で同一コードを使用；バックエンドが最終セキュリティ境界
- サプライヤー360は Drawer コンポーネントを使用
- リスクダッシュボードは赤/黄/緑の信号カードを使用、リスクレベルでのフィルタリングをサポート
- サプライヤーポータルはロールベースルーティング；SUPPLIER ロールはポータルページのみ閲覧可能

## 9. 拡張ガイド

### 新しい集約ルートの追加

1. `omni_srm` データベースにテーブル追加；`tenant_id`、`owner_user_id`、`owner_unit_id`、`version`、`deleted`、監査フィールドが必須
2. Entity（SrmOwnedEntity を継承）、Mapper、Service インターフェース + Impl、Controller を作成
3. `SrmDataPermissionHandler` に新しいテーブルの owner 列マッピングを登録
4. `init-all.sql` に DDL と権限シードデータを追加
5. Controller の書込みエンドポイントに `@PreAuthorize` + `@SrmDataScope` を宣言、新しい `srm:<resource>:<action>` 権限コードを使用

### 権限コードの追加

1. `init-all.sql` の `sys_permission` に新しい権限を挿入、type は `API`
2. `sys_role_permission` でロールに割り当て
3. Controller メソッドに `@PreAuthorize("hasAuthority('srm:<resource>:<action>')")` + `@SrmDataScope("srm:<resource>:<action>")` を宣言
4. フロントエンドの対応ボタンに `v-permission="'srm:<resource>:<action'"` を追加

### Outbox イベントの統合

1. Service ビジネスメソッド内で、同一トランザクションにて `ReliableMessageRelay.send("srm-domain-out-0", envelope, tenantId, eventId)` を呼び出し
2. `tenantId` はコンテキストから明示的に取得；ThreadLocal は禁止
3. イベントエンベロープは統一形式に従う；payload に完全な PII を含まない
4. 消費者は `payload.eventId` で重複排除し冪等でなければならない

### Procurement 見積統合

ポータルエンドポイント：

- `GET /api/srm/portal/quotation/invitations`：現在の PortalUser の RFQ 招待を一覧し、ローカルの見積状態をマージします。
- `GET /api/srm/portal/quotation/invitations/{rfqId}`：招待、RFQ 行スナップショットと現在の見積を返します。
- `POST /api/srm/portal/quotation`：見積を提出するか、`version` を指定して更新します。

提出リクエストは `requestId/rfqId/version/validUntil/lines[{rfqLineId,unitPrice,deliveryDays,remark}]` のみを受け付けます。tenantId、supplierId、サプライヤー名、RFQ 番号、品目、単位、数量、通貨、行金額と総金額は、すべて信頼できる身分情報・PortalUser・Procurement 招待詳細から取得するかサーバ側で計算します。

`srm_quotation.request_id` は最後に成功したリクエストを保存し、`srm_quotation_request` は `(tenant_id, request_id)` でリクエスト履歴と SHA-256 requestHash を永久に保持し、`(tenant_id, quotation_id, target_version)` で結果バージョンを関連付けます。`srm_quotation` は `(tenant_id, rfq_id, active_supplier_guard)` により、同一サプライヤーが同一 RFQ に対して未削除の見積を 1 件のみ持つことを保証します。`srm_quotation_line.rfq_line_id` は必須で、提出行の集合は RFQ スナップショットと完全一致しなければなりません。金額精度は：単価/数量 `DECIMAL(19,6)` かつ 0 より大、行金額/総金額 `DECIMAL(19,4)` かつ 0 より大。

見積、明細、`srm_quotation_request` と `srm.quotation.submitted.v1` Outbox は同一トランザクションでコミットする必要があります。同一 requestId+requestHash の再試行は現在の見積スナップショットを返し、イベントを重複発行してはなりません；同一 requestId で意図が異なる場合は 409 を返します。初回リクエストは作成センチネル `version=0` を使用し、初版見積は `version=1` から開始することで、並行する作成意図が初版を更新可能版と誤認するのを防ぎます。イベント payload には少なくとも `requestId/quotationId/quotationVersion/rfqId/rfqNo/supplierId/status/totalAmount/currencyCode/validUntil` を含み、Procurement は eventId Inbox で冪等に消費します。

### サプライヤー見積フローのスクリーンショット（四言語）

正式画像はドキュメント専用 Playwright テストケース `omni-frontend/e2e-docs/flows/srm.flows.spec.ts` により実稼働スタック上で生成され、言語別ディレクトリに保存します。他言語の画像を流用せず、プレースホルダや成功レスポンスのモックで代替しません。三つのステップは同一の実 fixture（同一の見積依頼・同一の見積）を共有するため、三枚は同一業務チェーンの連続した状態であり、互いに関係のないページスナップショットではありません。

共通前提条件（三ステップとも同一）：

| 項目 | 内容 |
|---|---|
| 環境 | ローカル Compose フルスタック稼働、フロントエンド `127.0.0.1:3000`、ゲートウェイ経由で `omni-procurement` と `omni-srm` に到達 |
| データ前提 | `admin` が正式 API で一意な品目カテゴリと品目を作成 → `procurement-approval` プロセスモデルに紐づく購買申請承認ルールを作成 → 購買申請を作成して承認に提出 → 承認後に見積依頼を作成し `send` を実行（DRAFT→SENT、`supplier1` を招待） |
| 操作者 | データ構築は `admin`（`SUPER_ADMIN` と `PROCUREMENT_MANAGER` ロールおよび `SAME_UNIT` 候補スコープが必要）；撮影ページの操作者は `supplier1`（`SUPPLIER` ロールかつ `srm_supplier_portal_user` 関連付け済み） |
| トークン | `E2eTokenFixture` がテストプロセス内で発行する短期 JWT（TTL 1200 秒）。プロセスメモリとリポジトリ外の一時ファイルにのみ存在し、終了時に破棄；ドキュメント・ログ・バージョン管理には書き込まない |
| 書き込みスイッチ | `E2E_MUTATIONS=true` を明示的に設定した時のみ実行；未設定ならグループ全体をスキップし、あらゆる書き込み呼び出しは例外を投げる |
| ビューポート | 1440×900、ドキュメント用クロックを固定しアニメーションを無効化、四言語で完全一致 |

共有ローカル環境では一覧に他の歴史的な見積依頼行が表示される場合があります；テストケースのアサーションと撮影判定は本実行の `runStamp` で識別される単一の見積依頼のみを対象とし、終了処理も本実行で帰属が確認できたデータのみをクリーンアップします。

#### ステップ 1：招待一覧（未見積）

- 操作者：`supplier1`
- 操作：サプライヤーポータルを開き「見積回答」タブに切り替える
- 期待状態：本実行の単一の見積依頼が一覧に表示され、招待状態は `INVITED`、現在の見積列は「未見積」、操作列は「見積を提出」

| zh-CN | en-US |
|---|---|
| ![サプライヤーポータル見積招待一覧（簡体字中国語）](images/zh-CN/srm-portal-quotation-invitations.png) | ![サプライヤーポータル見積招待一覧（英語）](images/en-US/srm-portal-quotation-invitations.png) |

| ja-JP | ko-KR |
|---|---|
| ![サプライヤーポータル見積招待一覧（日本語）](images/ja-JP/srm-portal-quotation-invitations.png) | ![サプライヤーポータル見積招待一覧（韓国語）](images/ko-KR/srm-portal-quotation-invitations.png) |

#### ステップ 2：見積フォーム（単価と有効期の入力）

- 操作者：`supplier1`
- 操作：対象行で「見積を提出」をクリックしてダイアログを開き、単価 `123.45` を入力し、見積有効期を見積締切に設定する
- 期待状態：ダイアログタイトルに本実行の見積依頼番号が付く；行スナップショットに品目コードと名称、数量 `2`、単位と通貨 `CNY` が表示され、RFQ 状態は `SENT`、単価と有効期ともに入力済み

| zh-CN | en-US |
|---|---|
| ![サプライヤーポータル見積フォーム（簡体字中国語）](images/zh-CN/srm-portal-quotation-form.png) | ![サプライヤーポータル見積フォーム（英語）](images/en-US/srm-portal-quotation-form.png) |

| ja-JP | ko-KR |
|---|---|
| ![サプライヤーポータル見積フォーム（日本語）](images/ja-JP/srm-portal-quotation-form.png) | ![サプライヤーポータル見積フォーム（韓国語）](images/ko-KR/srm-portal-quotation-form.png) |

#### ステップ 3：提出成功（QUOTED と見積総額）

- 操作者：`supplier1`
- 操作：ダイアログで「見積を提出」をクリックして実際の見積を送信し、続いて「見積依頼を更新」をクリックする
- 期待状態：「見積を提出しました」が表示されてダイアログが閉じる；招待状態は `srm.quotation.submitted.v1` MQ イベントにより非同期で `QUOTED` に転移し、現在の見積列に総額 `CNY 246.9`（単価 `123.45` × 数量 `2`）が表示され、操作列は「見積を修正」になる

| zh-CN | en-US |
|---|---|
| ![サプライヤーポータル見積提出成功（簡体字中国語）](images/zh-CN/srm-portal-quotation-submitted.png) | ![サプライヤーポータル見積提出成功（英語）](images/en-US/srm-portal-quotation-submitted.png) |

| ja-JP | ko-KR |
|---|---|
| ![サプライヤーポータル見積提出成功（日本語）](images/ja-JP/srm-portal-quotation-submitted.png) | ![サプライヤーポータル見積提出成功（韓国語）](images/ko-KR/srm-portal-quotation-submitted.png) |

### SRM 管理側ページのスクリーンショット（四言語）

同様に `omni-frontend/e2e-docs/flows/management.flows.spec.ts` により実稼働スタック上で生成。**読み取り専用の採取**であり、サプライヤーデータを作成・変更・削除しないため、書き込みスイッチもデータ終了処理も不要です。前提条件と操作者は前節と同一（`admin` / `SUPER_ADMIN`、短期 JWT は `E2eTokenFixture` がプロセス内で発行し終了時に破棄）。

- 操作：ログイン後にサプライヤー管理、業績評価、リスク管理、リスク指標設定、招待管理のページを順に開く。
- 期待状態：ページタイトルと列ラベルが現在の言語で描画される；採取時点でデータベースにはサプライヤー/評価/リスク/招待の実レコードが各 1 件、リスク指標設定が 9 件存在。

| ページ | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| サプライヤー管理（lifecycle） | ![サプライヤー管理（簡体字中国語）](images/zh-CN/srm-suppliers.png) | ![サプライヤー管理（英語）](images/en-US/srm-suppliers.png) | ![サプライヤー管理（日本語）](images/ja-JP/srm-suppliers.png) | ![サプライヤー管理（韓国語）](images/ko-KR/srm-suppliers.png) |
| 業績評価（evaluation） | ![業績評価（簡体字中国語）](images/zh-CN/srm-evaluations.png) | ![業績評価（英語）](images/en-US/srm-evaluations.png) | ![業績評価（日本語）](images/ja-JP/srm-evaluations.png) | ![業績評価（韓国語）](images/ko-KR/srm-evaluations.png) |
| リスク管理（risk） | ![リスク管理（簡体字中国語）](images/zh-CN/srm-risks.png) | ![リスク管理（英語）](images/en-US/srm-risks.png) | ![リスク管理（日本語）](images/ja-JP/srm-risks.png) | ![リスク管理（韓国語）](images/ko-KR/srm-risks.png) |
| リスク指標設定（risk） | ![リスク指標設定（簡体字中国語）](images/zh-CN/srm-risk-config.png) | ![リスク指標設定（英語）](images/en-US/srm-risk-config.png) | ![リスク指標設定（日本語）](images/ja-JP/srm-risk-config.png) | ![リスク指標設定（韓国語）](images/ko-KR/srm-risk-config.png) |
| 招待管理（invite） | ![招待管理（簡体字中国語）](images/zh-CN/srm-invites.png) | ![招待管理（英語）](images/en-US/srm-invites.png) | ![招待管理（日本語）](images/ja-JP/srm-invites.png) | ![招待管理（韓国語）](images/ko-KR/srm-invites.png) |

本グループは一覧/設定ビューのみを閉じるもので、`admission-lifecycle`（完全な Portal 登録 Saga と参入承認が必要）や `detail-and-action-states`（詳細ダイアログと操作結果が必要）を閉じることとは**同等ではありません**；`stable-mobile-flow` は次の小節のレスポンシブ採取により閉じます。したがって SRM は引き続き `partial` です。

### サプライヤーポータルのレスポンシブ安定性スクリーンショット（四言語）

`omni-frontend/e2e-docs/flows/srm-portal-responsive.flows.spec.ts` により実稼働スタック上で生成。**読み取り専用採取**：ポータルを開き、タブを切り替え、更新をクリックするのみで、見積の送信や変更は一切行いません。

- 前提条件：前二節と同一（ローカル Compose フルスタック、`supplier1` の Portal 関連付け済み、短期 JWT はプロセス内で発行し終了時に破棄）。
- 操作者：`supplier1`。
- 操作：**390×844（モバイル）** と **1024×768（タブレット）** のビューポートでそれぞれサプライヤーポータルを開き、「見積回答」タブに切り替える。
- 期待状態：タブが表示されクリック可能；「見積依頼を更新」と行ごとの見積操作が狭い幅で押し出されたり遮蔽されない；一覧はレスポンシブに列を縮約（モバイルは見積番号/テーマ/操作のみ保持）し、はみ出しがない。
- 実測結果：8 passed / 0 skipped（四言語 × 二ビューポート）。

| ビューポート | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 390×844 モバイル | ![ポータル見積一覧 モバイル（簡体字中国語）](images/zh-CN/srm-portal-quotation-mobile.png) | ![ポータル見積一覧 モバイル（英語）](images/en-US/srm-portal-quotation-mobile.png) | ![ポータル見積一覧 モバイル（日本語）](images/ja-JP/srm-portal-quotation-mobile.png) | ![ポータル見積一覧 モバイル（韓国語）](images/ko-KR/srm-portal-quotation-mobile.png) |
| 1024×768 タブレット | ![ポータル見積一覧 タブレット（簡体字中国語）](images/zh-CN/srm-portal-quotation-tablet.png) | ![ポータル見積一覧 タブレット（英語）](images/en-US/srm-portal-quotation-tablet.png) | ![ポータル見積一覧 タブレット（日本語）](images/ja-JP/srm-portal-quotation-tablet.png) | ![ポータル見積一覧 タブレット（韓国語）](images/ko-KR/srm-portal-quotation-tablet.png) |

採取時に一覧へ表示されていたのは環境に既存の歴史的な見積依頼行です（本バッチの見積データは帰属別にクリーンアップ済み）；画像は実際の一覧内容をそのまま保持しており、データを造って埋めてはいません。

## 10. テスト

SRM モジュールは以下のテストスイートをカバー：

- サプライヤーステートマシン：有効/無効な遷移
- 評価加重スコア計算の正確性（全て1=20、全て5=100）
- 評価自動等級マッピングの正確性（60/75/90 しきい値）
- リスク総合レベルは最高指標レベルを取得
- PII マスキング（銀行口座、連絡先電話/メール）
- 全6種の dataScope のリストと集計
- クロステナント読取/更新/削除は全て失敗
- tenant/scope 欠落時はフェイルクローズ
- `tenant_id + id + version` 並行更新競合
- ポータル入居冪等性（重複 credit_code や同一 userId は拒否）
- 期限切れ、無効化、クロステナント、並行超過使用の inviteToken は全て拒否
- SUPPLIER ロールは関連付けられたサプライヤーのデータのみ閲覧可能

テスト実行：

```bash
cd omni-backend && ./mvnw clean install -pl omni-srm -am
```
