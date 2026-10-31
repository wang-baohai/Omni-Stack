# 5. データ範囲：同じ会社の誰がどの記録を見るか

テナントが会社を分けても、社内には分担があります。李さんは自分の顧客、王さんは部門を担当し、下位部門まで必要な役割もあります。

これをデータ範囲で表します。操作資格と可視記録の両方を満たします。複数ロールがあっても、毎回自由に最も広いものを使えるわけではありません。

## 六つの値の意味

| 値 | 最初の理解 |
|---|---|
| SELF | 本人が担当する記録 |
| DEPT | 主組織の担当記録 |
| DEPT_AND_BELOW | 主組織と条件を満たす下位組織 |
| CUSTOM | ロールで設定した組織 |
| TENANT | テナント内で本人・組織による追加縮小をしない |
| ALL | 範囲層では行条件を追加せず、横断可否は API とテナント規則次第 |

SELF の列は業務次第です。担当者、申請者、固定された現在の資産利用者などがあり、任意の user_id 列ではありません。

ALL と TENANT が同じく追加条件なしでも、全 API で同義とはいえません。

## 操作資格と範囲を組み合わせる

common-service の @ServiceDataScope メソッドは、現在の利用者、テナント、permissionCode で Auth に範囲を問い合わせます。返却された身元の一致を検証し、実行中に保持します。

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java" -->
```java
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        try {
            InternalDataScopeDTO scope = dataScopeResolver.resolve(identity, annotation.permissionCode());
            validate(scope, identity, annotation.permissionCode());
            ServiceDataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            ServiceDataScopeContext.clear();
        }
```

信頼できる身元を取得し、範囲を解決・検証してから元の業務を実行し、最後に片付けます。連続した中心部分であり、全メソッド・全サービスが同じ注釈を使う主張ではありません。

permissionCode 付きの解決は、その完全な権限を実際に与える有効ロールだけを考慮します。Auth の通常要求でのロール範囲の合成とは別です。

たとえば広い利用者管理ロールがあっても、顧客操作の範囲を自動で広げません。そのロールが顧客操作を許可するかを先に確認します。

<!-- omni:modules:crm -->
## 顧客記録の分岐を読む

CRM は登録した業務表に対象を限定し、現在の操作範囲を取得します。条件を選ぶ連続原文です。

<!-- omni:source path="omni-backend/omni-crm/src/main/java/com/omni/crm/security/CrmDataPermissionHandler.java" -->
```java
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        ServiceDataScopeContext.ScopeInfo info = ServiceDataScopeContext.get();
        if (info == null || info.effectiveScope() == null) {
            return deny(alias);
        }
        return switch (info.effectiveScope()) {
            case "ALL", "TENANT" -> null;
            case "SELF" -> equals(alias, "owner_user_id", info.userId());
            case "DEPT" -> equals(alias, "owner_unit_id", info.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> in(alias, "owner_unit_id", info.accessibleUnitIds());
            default -> deny(alias);
        };
```

有効な範囲がなければ一致しない条件にします。SELF は owner_user_id、DEPT は owner_unit_id、下位・独自範囲は可視組織集合を使います。ALL/TENANT は owner 条件を加えませんが、独立したテナントプラグインが会社条件を保持します。

営業員は「本人担当かつ A 社の顧客」、管理者は「部門範囲かつ A 社の顧客」を見ます。担当条件を広げても会社条件まで消しません。

ソース上の境界の説明で、全組合せを現在環境で再実測したとはしていません。
<!-- omni:modules:end -->

## 子表へ担当者条件を足せばよいわけではない

明細に owner_user_id がない場合もあります。供給者の連絡先、評価明細、購買行が親から可視性を継承するなら、その関係で確認します。

親をフォルダー、明細をページと考えると、そのページを読めるかはフォルダーの権限で決まります。存在しない担当者欄を各ページへ要求できません。

完全なリポジトリでは対応が異なります。

| 対象 | 範囲の基準 |
|---|---|
| CRM の登録済み顧客等 | 各自の担当者と組織の列 |
| SRM の供給者・評価の子リソース | 供給者・評価関係から継承。存在しない owner 列を足さない |
| 購買申請 | 申請者と申請組織 |
| 見積依頼、発注、受入と明細 | 担当者・組織、明細は親から継承 |
| 資産管理、自分の資産、受取・返却 | 管理は owner、本人操作は固定 current_user_id、子記録は資産から継承 |

完全版の分担を示す表です。生成プロジェクトは選んだプリセットだけの能力を持ち、未収録モジュールのメニューやデータは期待できません。

## 組織ツリーにもテナントが必要な理由

番号が大きい全組織が下位ではありません。現在のテナント内の関係とパスで展開します。

DEPT は有効な主組織、DEPT_AND_BELOW は対象の子孫、CUSTOM はロールで設定した根から展開します。Auth の CUSTOM の根と子孫に対する有効状態検査は完全に同じではありません。子孫クエリーの status=1 だけで、全根も同じ検査済みと推測できません。

SAME_UNIT/UNIT_AND_BELOW はロール割当が同じ組織だけか下位にも有効かを示し、承認担当者の検索にも関係します。本人・部門・テナントのクエリー範囲とは別設定です。

## 例外と失敗境界

オンライン利用者管理は Redis を読み、利用者 DB で会社・組織を確認します。現在 ALL はテナント横断を許し、他の範囲は先に会社を制限します。ドメイン一覧まで同じ横断が可能という証明にはなりません。

範囲不足時も異なります。CRM 等の登録表は一致を拒否し、Auth の処理器はコンテキスト不足・未知の範囲・未登録表で条件を追加せず、API とクエリーの他の検査に依存します。全経路が自動で失敗時閉鎖になるわけではありません。

範囲解決の失敗、操作権限不足、業務状態の不適合は別の問題です。ヘッダー偽装、管理者共有、条件削除で回避せず、止まった条件を調べます。

次は[キャッシュとバックグラウンド](tenancy-background.jp.md)で、HTTP 外を補います。
