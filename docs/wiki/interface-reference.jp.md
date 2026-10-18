# 画面参考と過去の画像

基準主幹 fc165e3 から保持した過去の画面画像 68 ファイルを掲載し、各記事で四言語のディレクトリを並べています。今回の業務実行や現在の画面の受入れを証明するものではありません。過去の last_success_at 登録は画像ハッシュやコンテナイメージと結び付いておらず、実際の撮影時刻・環境を確定できません。現在のガイドで自分の記録を準備し、過去の業務 ID が存在すると仮定しません。言語別ディレクトリでも画像内の全文が翻訳済みとは限りません。

<!-- omni:modules:workflow -->
## workflow

| ページ | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| プロセス定義（publish） | ![プロセス定義（簡体字中国語）](../images/zh-CN/workflow-definitions.png) | ![プロセス定義（英語）](../images/en-US/workflow-definitions.png) | ![プロセス定義（日本語）](../images/ja-JP/workflow-definitions.png) | ![プロセス定義（韓国語）](../images/ko-KR/workflow-definitions.png) |
| プロセスインスタンス追跡（instance-tracking） | ![プロセスインスタンス（簡体字中国語）](../images/zh-CN/workflow-instances.png) | ![プロセスインスタンス（英語）](../images/en-US/workflow-instances.png) | ![プロセスインスタンス（日本語）](../images/ja-JP/workflow-instances.png) | ![プロセスインスタンス（韓国語）](../images/ko-KR/workflow-instances.png) |
| 統計ダッシュボード（サマリービュー） | ![統計ダッシュボード（簡体字中国語）](../images/zh-CN/workflow-stats.png) | ![統計ダッシュボード（英語）](../images/en-US/workflow-stats.png) | ![統計ダッシュボード（日本語）](../images/ja-JP/workflow-stats.png) | ![統計ダッシュボード（韓国語）](../images/ko-KR/workflow-stats.png) |
### 読み取り専用詳細オーバーレイ（4言語）
| オーバーレイ | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| プロセス進捗（instance-tracking） | ![プロセス進捗（簡体字中国語）](../images/zh-CN/workflow-instance-progress.png) | ![プロセス進捗（英語）](../images/en-US/workflow-instance-progress.png) | ![プロセス進捗（日本語）](../images/ja-JP/workflow-instance-progress.png) | ![プロセス進捗（韓国語）](../images/ko-KR/workflow-instance-progress.png) |
| 承認記録（approval） | ![承認記録（簡体字中国語）](../images/zh-CN/workflow-instance-approval-records.png) | ![承認記録（英語）](../images/en-US/workflow-instance-approval-records.png) | ![承認記録（日本語）](../images/ja-JP/workflow-instance-approval-records.png) | ![承認記録（韓国語）](../images/ko-KR/workflow-instance-approval-records.png) |
| バージョン履歴（publish） | ![バージョン履歴（簡体字中国語）](../images/zh-CN/workflow-model-versions.png) | ![バージョン履歴（英語）](../images/en-US/workflow-model-versions.png) | ![バージョン履歴（日本語）](../images/ja-JP/workflow-model-versions.png) | ![バージョン履歴（韓国語）](../images/ko-KR/workflow-model-versions.png) |

旧バージョン履歴画像は中国語で DRAFT の版 0、英・日・韓で PUBLISHED の版 2 を表示しています。同一記録の四言語比較や今回の公開成功の証拠ではありません。現在の設計・公開・承認操作は[ワークフローガイド](../guides/workflow-approval.jp.md)を参照します。

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## outbox

| ページ | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| メッセージ詳細オーバーレイ（message-status） | ![メッセージ詳細（簡体字中国語）](../images/zh-CN/monitor-mq-message-detail.png) | ![メッセージ詳細（英語）](../images/en-US/monitor-mq-message-detail.png) | ![メッセージ詳細（日本語）](../images/ja-JP/monitor-mq-message-detail.png) | ![メッセージ詳細（韓国語）](../images/ko-KR/monitor-mq-message-detail.png) |

旧 MQ 詳細画像では日本語・韓国語のタイトルとラベルが英語のままです。中国語は別の PENDING メッセージ、他言語は SENT を表示し、同一メッセージの四言語での配信を証明しません。本文の下部は画面外に切れており、全フィールドは自分の記録で確認します。現在のリース・CAS・再試行・消費側の冪等性は[MQ 事実文書](../mq-reliability.jp.md)を参照します。

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## srm

### サプライヤー見積フローのスクリーンショット（四言語）
| 項目 | 内容 |
|---|---|
| 環境 | 過去のスクリプトの既定フロントエンドは `127.0.0.1:3000` で、`omni-procurement` と `omni-srm` にアクセスしますが、旧画像の配備環境の証明ではありません。新たに撮影する場合はアプリケーションとブラウザーの両方をローカル Docker 内で動かし、`E2E_RUNTIME_MODE=external` でコンテナのフロントエンドに接続します。引数は[撮影設定](../../omni-frontend/playwright.docs.config.ts)を参照し、既定の webServer をホストで起動しません。 |
| データ前提 | 過去の準備スクリプトは `admin` と正式 API でカテゴリ・品目を作成 → `procurement-approval` のモデル版に紐づく承認ルールを作成 → 購買申請を提出して承認 → 見積依頼を作成して `send`（DRAFT→SENT、`supplier1` を招待）を実行します。言語ごとに別の見積依頼を使い、今回の記事確認ではこの書込みを再実行していません。 |
| 操作者 | 過去の準備スクリプトは `admin`、画面のアカウントは `supplier1`（`SUPPLIER` ロールと有効な `srm_supplier_portal_user` 関連）です。現在の一般ユーザーの最小権限一覧ではありません。ロール・データ範囲・候補者の条件は[SRM ガイド](../guides/srm-flow.jp.md)と[購買ガイド](../guides/procurement-flow.jp.md)を参照します。 |
| トークン | `E2eTokenFixture` は既存テナントユーザーと DB の権限から TTL 1200 秒のテスト JWT を発行し、パスワードログインや実際の CAPTCHA の代用にはなりません。リポジトリ外の一時ファイルは呼出し元が削除する必要がありますが、過去に削除されたことをこの記事では証明していません。トークンを文書・ログ・版管理へ残しません。 |
| 書込みスイッチ | テストトークンがないか `E2E_MUTATIONS=true` を明示していない場合、見積スイート全体をスキップします。expectOk のスイッチ検査はネットワーク応答を待った後なので、要求送信前の遮断ではありません。承認済みの隔離環境だけで実行し、今回の記事確認ではスイートを実行しません。 |
| ビューポート | 旧見積画像は 1440×900 で、ヘルパーは撮影時のアニメーションを無効にします。現在のヘルパーが時刻を固定するのは `display-fixtures` モードのみで、全旧画像がそのモードで撮影されたとは断定できません。 |
#### ステップ 1：招待一覧（未見積）
| zh-CN | en-US |
|---|---|
| ![サプライヤーポータル見積招待一覧（簡体字中国語）](../images/zh-CN/srm-portal-quotation-invitations.png) | ![サプライヤーポータル見積招待一覧（英語）](../images/en-US/srm-portal-quotation-invitations.png) |
| ja-JP | ko-KR |
|---|---|
| ![サプライヤーポータル見積招待一覧（日本語）](../images/ja-JP/srm-portal-quotation-invitations.png) | ![サプライヤーポータル見積招待一覧（韓国語）](../images/ko-KR/srm-portal-quotation-invitations.png) |
#### ステップ 2：見積フォーム（単価と有効期の入力）
| zh-CN | en-US |
|---|---|
| ![サプライヤーポータル見積フォーム（簡体字中国語）](../images/zh-CN/srm-portal-quotation-form.png) | ![サプライヤーポータル見積フォーム（英語）](../images/en-US/srm-portal-quotation-form.png) |
| ja-JP | ko-KR |
|---|---|
| ![サプライヤーポータル見積フォーム（日本語）](../images/ja-JP/srm-portal-quotation-form.png) | ![サプライヤーポータル見積フォーム（韓国語）](../images/ko-KR/srm-portal-quotation-form.png) |
#### ステップ 3：提出成功（QUOTED と見積総額）
| zh-CN | en-US |
|---|---|
| ![サプライヤーポータル見積提出成功（簡体字中国語）](../images/zh-CN/srm-portal-quotation-submitted.png) | ![サプライヤーポータル見積提出成功（英語）](../images/en-US/srm-portal-quotation-submitted.png) |
| ja-JP | ko-KR |
|---|---|
| ![サプライヤーポータル見積提出成功（日本語）](../images/ja-JP/srm-portal-quotation-submitted.png) | ![サプライヤーポータル見積提出成功（韓国語）](../images/ko-KR/srm-portal-quotation-submitted.png) |
### SRM 管理側ページのスクリーンショット（四言語）
| ページ | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| サプライヤー管理（lifecycle） | ![サプライヤー管理（簡体字中国語）](../images/zh-CN/srm-suppliers.png) | ![サプライヤー管理（英語）](../images/en-US/srm-suppliers.png) | ![サプライヤー管理（日本語）](../images/ja-JP/srm-suppliers.png) | ![サプライヤー管理（韓国語）](../images/ko-KR/srm-suppliers.png) |
| 業績評価（evaluation） | ![業績評価（簡体字中国語）](../images/zh-CN/srm-evaluations.png) | ![業績評価（英語）](../images/en-US/srm-evaluations.png) | ![業績評価（日本語）](../images/ja-JP/srm-evaluations.png) | ![業績評価（韓国語）](../images/ko-KR/srm-evaluations.png) |
| リスク管理（risk） | ![リスク管理（簡体字中国語）](../images/zh-CN/srm-risks.png) | ![リスク管理（英語）](../images/en-US/srm-risks.png) | ![リスク管理（日本語）](../images/ja-JP/srm-risks.png) | ![リスク管理（韓国語）](../images/ko-KR/srm-risks.png) |
| リスク指標設定（risk） | ![リスク指標設定（簡体字中国語）](../images/zh-CN/srm-risk-config.png) | ![リスク指標設定（英語）](../images/en-US/srm-risk-config.png) | ![リスク指標設定（日本語）](../images/ja-JP/srm-risk-config.png) | ![リスク指標設定（韓国語）](../images/ko-KR/srm-risk-config.png) |
| 招待管理（invite） | ![招待管理（簡体字中国語）](../images/zh-CN/srm-invites.png) | ![招待管理（英語）](../images/en-US/srm-invites.png) | ![招待管理（日本語）](../images/ja-JP/srm-invites.png) | ![招待管理（韓国語）](../images/ko-KR/srm-invites.png) |
### サプライヤーポータルのレスポンシブ安定性スクリーンショット（四言語）
| ビューポート | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 390×844 モバイル | ![ポータル見積一覧 モバイル（簡体字中国語）](../images/zh-CN/srm-portal-quotation-mobile.png) | ![ポータル見積一覧 モバイル（英語）](../images/en-US/srm-portal-quotation-mobile.png) | ![ポータル見積一覧 モバイル（日本語）](../images/ja-JP/srm-portal-quotation-mobile.png) | ![ポータル見積一覧 モバイル（韓国語）](../images/ko-KR/srm-portal-quotation-mobile.png) |
| 1024×768 タブレット | ![ポータル見積一覧 タブレット（簡体字中国語）](../images/zh-CN/srm-portal-quotation-tablet.png) | ![ポータル見積一覧 タブレット（英語）](../images/en-US/srm-portal-quotation-tablet.png) | ![ポータル見積一覧 タブレット（日本語）](../images/ja-JP/srm-portal-quotation-tablet.png) | ![ポータル見積一覧 タブレット（韓国語）](../images/ko-KR/srm-portal-quotation-tablet.png) |

二つの狭い画面はポータル一覧とタブの配置例であり、モバイルでの入力・提出・見積全体の成功を証明しません。旧見積フォームには背景の透け、日本語・韓国語の有効期限ラベルの折返し、中国語の単位「件」が残っています。欠落を隠す画像加工は行いません。

<!-- omni:modules:end -->

[今回の確認記録](../reference/evidence/interface-reference-wiki-review-2026-10-17.json)は 68 枚の基準画像のハッシュ確認と、原寸画像 12 枚の実際の確認を区別します。文書は実際の AI_REVIEW で受け入れ、人による文書レビューは必須ではありません。過去の画面翻訳の不足、現在の業務実行、最終サイトの受入れはそれぞれ別の確認事項として残します。
