# SRM 管理画面とサプライヤーポータルの完全フロー

SRM は招待、Portal 入居、審査、ライフサイクル、評価、リスク、見積回答を扱います。内部ユーザーと Portal ユーザーは別の役割とデータ境界を持ちます。

## 1. 招待と入居

管理者が招待を作成し、サプライヤーが `/portal-register` で認証アカウントを登録します。Portal で招待トークン、一意のクライアント要求 ID、企業情報を送信すると、SRM がサプライヤーを作成し、Saga が Auth に `SUPPLIER` ロールを要求します。

`inviteToken` と `requestId` は必須で、再試行は同じ ID を使います。Portal ユーザー ID を内部 owner 列へ書きません。

## 2. 審査とライフサイクル

```text
REGISTERING → PENDING_REVIEW → APPROVED
                     ↘ REJECTED → PENDING_REVIEW
APPROVED ↔ SUSPENDED
APPROVED ↔ BLACKLISTED
APPROVED/SUSPENDED → ELIMINATED
```

調達で選択できるのは承認済みだけです。却下は再提出、停止は再開、ブラックリストは専用権限が必要で、淘汰は終端です。SRM コーディネーターが送信、取下げ、取消、開始再試行と Workflow 状態を整合させます。

## 3. 企業情報と子リソース

連絡先、資格、銀行口座を含みます。Portal は有効な関連先だけ、内部ユーザーはサプライヤー集約を通じたデータ範囲で参照します。

## 4. 評価

管理者が期間と評価項目を作成し、待機、実施中、完了へ進みます。完了結果は権限のある Portal に表示します。点数、重み、必須項目はバックエンドで検証します。

## 5. リスク

指標種別と基準から GREEN、YELLOW、RED を算出します。ルール変更時は再計算または履歴ルールバージョンを明示します。

## 6. 見積回答

Procurement が RFQ を送ると、招待された有効サプライヤーが Portal で回答します。信頼性メッセージで Procurement へ渡し、イベント ID で冪等処理します。他社回答は見えず、締切・取消・完了後は送信できません。

## 7. Saga 復旧

Auth ロール割当と SRM 入居はサービスをまたぎます。失敗は診断可能な再試行状態へ入り、完了済みリモート処理を単純ロールバックしません。要求 ID、サプライヤー ID、メッセージ ID、Trace ID を関連付けます。

[SRM 文書](../srm.jp.md)と [SRM 設計](../design/srm-design.md)を参照してください。

