# トラブルシューティング手引き

最初に失敗層を特定し、Trace ID、業務 ID、ログを関連付けます。繰り返し再起動や DB 直接変更で根因を隠しません。

## 1. 起動

Maven 不整合は JDK 25 と `./mvnw`、Nacos 待ちは health/8848/9848/認証、migrator は新 changeSet と環境、MySQL は対象 Compose と接続 URL、未選択依存の再試行は CLI plan/profile を確認します。

## 2. 認証

新しい CAPTCHA Key とテナントを確認します。リダイレクトループはトークン期限、時刻、Gateway ID、ソーシャルは URI/PKCE/state/client、Portal 403 はロール、関連、サプライヤー状態を確認します。

## 3. メニューと権限

JWT authorities、`/api/auth/menus`、seed、ロール関係を確認し、変更後は再ログインします。禁止された書き込みを直接呼んで 403 を確認し、`v-permission` とバックエンドコードを比較します。

## 4. データ範囲

Gateway 経由、テナント、ユーザー、組織、ロール、ドメイン表/列、子集約継承、Interceptor 順序を確認します。存在しない owner 列を追加しません。

## 5. Workflow

BPMN と候補者設定を検証し、開始失敗は業務 ID と予約記録、候補者なしは role/anchor/scope、会签は `MI_END` と計数変数を確認します。

## 6. XXL-JOB

システムジョブは二重注釈、個人処理器は Bean 名=`typeCode`、登録失敗は DB 行削除、即時実行のログなしは Admin、実行器、処理器、ログ保存を確認します。

## 7. メッセージ

Outbox → Broker → Inbox をメッセージ ID、topic/key、producer/consumer trace で追跡します。下流冪等性確認後のみデッドレターを再送します。relay の全テナント走査は設計で、外部照会はテナントで絞ります。

## 8. フロントエンド

空白画面はメニュー、chunk、401/403/404、動的フォームは Schema、言語は `omni-lang`、日時/金額は現在 locale を確認します。`--max-warnings 0` を弱めず修正します。

## 9. サポート情報

版/commit、preset、Compose project、時刻、Trace/業務 ID、マスク済み応答・ログ、再現、期待/実際を提供します。パスワード、CAPTCHA、JWT、内部トークン、秘密鍵、未マスク個人情報を提供しません。

