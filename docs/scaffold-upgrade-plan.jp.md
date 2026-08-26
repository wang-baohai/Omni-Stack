# Omni-Stack 高効率スキャフォールド 次期アップグレード計画

> 翻訳状態：中国語原文を基にした技術翻訳ドラフトです。公開前に人手によるレビューが必要です。
> 原文基準：`docs/scaffold-upgrade-plan.md`（2026-08-17 提案版）。

## 1. アップグレード目標

次期は業務画面を増やすことではなく、現在の企業向け基盤を再利用可能な開発スキャフォールドへ進化させます。サービス／CRUD 生成、プロジェクトプリセット、共通セキュリティ、業務担当者向け管理 UI、4 言語ドキュメント、運用品質と可観測性を標準装備し、再利用成熟度 92～95/100 を目標とします。

## 2. ロードマップ

| ID | 優先度 | 項目 | 完了条件 |
|---|---|---|---|
| R-01 | P0 | 購買申請承認ルール UI | 担当者がモデルバージョン ID や区間表記を理解せず、ルール作成・プレビュー・検証を完了できる。 |
| R-02 | P0 | `create-service` CLI | 1 コマンドでビルド可能なサービス、設定、テスト、Docker/Gateway、文書ひな型を生成する。 |
| R-03 | P0 | 共通業務 Starter | テナント、事前認証、DataScope、内部 API 認証、XSS、監査を宣言的に導入できる。 |
| R-04 | P1 | フルスタック CRUD ジェネレーター | 標準マスタ機能を半日以内に生成・調整・検収できる。 |
| R-05 | P1 | プロジェクトプリセット | `core`、`workflow`、`crm`、`supply-chain`、`full` と依存表・保守手順を提供する。 |
| R-06 | P1 | 軽量開発モード | 単一モジュール開発でフルコンテナ群を起動する必要がない。 |
| R-07 | P1 | 可観測性テンプレート | メトリクス、構造化ログ、トレース、Dashboard、アラート、SLO 例を標準化する。 |
| R-08 | P1 | フロントエンド型／lint 改善 | `npm run lint` を error 0、warning 0 とし CI 必須ゲートにする。 |
| R-09 | P0 | 4 言語文書と全フロー画像 | 中・英・日・韓で同一範囲の説明と再現可能な操作画像を提供する。 |
| R-10 | P0 | 納品時クリーンアップ | 最終成果物のみを残し、人手管理 SQL は最終の冪等 seed のみにする。 |

## 3. R-01：購買申請承認ルール

「承認ルーティング」を「購買申請承認ルール」に改称し、説明、マッチテスター、ルール一覧、3 ステップウィザード、カバレッジ警告の順に配置します。品目カテゴリ、下限を含む金額、上限を含まない金額、理解しやすい承認ステップ、公開済みフロー名／版、状態を表示し、コード、数値モデル ID、優先度は詳細情報へ移します。

テスターと実際の申請送信は、サーバー上の同一評価ロジックと同一結果モデルを使います。カテゴリ不正、区間重複／欠落、未公開フロー、既定ルール不足、権限不足を検証し、既存プロセスは開始時の版を保持します。作成、更新、有効化／停止、削除はすべて監査対象です。

境界値、個別カテゴリ優先、異常状態、プレビューと実申請の一致、および desktop／tablet／390×844 mobile を受入試験に含めます。

## 4. R-05：プリセットと保守説明

機械可読の単一カタログを事実源とします。利用者向け文書は選択方法、生成内容、初回起動、追加／削除、資源要件、障害対応を説明します。保守者向け文書は schema、依存閉包、モジュール変更、互換性、廃止、検証、rollback、独自プリセット作成を説明します。依存表には backend、frontend route/menu、権限、設定、Compose、port、message、database を含めます。

CI はカタログ、CLI 選択肢、README、4 言語、golden output の整合性を検証します。保守者はジェネレーターのソースを読まずに独自プリセットを追加できなければなりません。

## 5. R-09：文書、README、操作画像

中国語を事実源とし、`*.en.md`、`*.jp.md`、`*.kr.md` を同期します。Quick Start、認証／RBAC、System／Security、Workflow、Scheduling、CRM、SRM、Procurement、Asset、MQ／運用、二次開発を必須文書群とします。

4 つの README は、現行版、モジュール、構成、起動モード、health check、文書ナビゲーション、検証済み品質、production 境界、generator、preset を同じ意味で説明し、平文の初期 password を掲載しません。

専用 Playwright 文書スイートは、公開／認証、システム管理、Workflow、Scheduling、CRM、SRM、Procurement、Asset、MQ／monitoring、権限、異常状態の全フローを記録します。manifest には role、route、language、viewport、fixture、操作、期待結果、mask を保存します。標準は 1440×900、重要フォームと Supplier Portal は 390×844 も対象です。Secret、CAPTCHA 解答、JWT、個人情報、本番データは撮影しません。

初心者向け手順は、目的、操作 role、前提、番号付き画像、期待結果、障害対応、上下流関係、API／permission／設計文書参照の共通構成にします。

## 6. R-10：必須の最終クリーンアップ

実装、試験、文書、画像が完成した後だけ実施します。先に保持リストと削除候補を作り、source、Compose、CI、test、docs の参照を検査します。再利用価値がある処理は正式 CLI／test／screenshot tool／module code に移してから一時実装を削除します。

不要な一時 SQL、Python、JavaScript/TypeScript、Shell、PowerShell、Batch、debug、backup、export、cache、中間成果物を削除します。DB 構造と更新は正式 migration 管理へ移行済みであることが前提です。人手管理 SQL は最終の冪等 seed のみとし、復元元は Git 履歴です。

参照切れ scan、fresh／upgrade DB、preset 生成、full build／E2E、cleanup report、独立レビュー可能な削除 commit を受入条件とします。

## 7. フェーズと依存関係

1. 基準を凍結し DB version 管理を確立する。
2. 承認ルール UI を提供し lint debt を解消する。
3. 共通 Starter を抽出する。
4. metadata 駆動の service／CRUD generator を作る。
5. preset と lightweight mode を提供する。
6. observability を提供する。
7. 4 言語 UI／docs／screenshots と初心者検証を完了する。
8. 最終クリーンアップと release 受入を行う。

DB version 管理は generator、preset、破壊的 cleanup より先、Starter は生成 template より先、最終 screenshot は UI と fixture の安定後です。

## 8. 品質ゲート

各フェーズで JDK 25 Maven reactor、frontend lint/build、tenant／DataScope 分離、permission／internal call security、XSS、DB fresh／upgrade／冪等／復旧、browser E2E、rollback 証拠を維持します。CI はシナリオを列挙するだけでなく実行しなければなりません。

## 9. 工数と予算

見積りは 131～199 人日、7.05M～11.65M token、運用目標約 9.5M、計画上限約 12M です。1 名で 26～40 週、full-stack 2 名で 16～24 週、backend・frontend/UX・platform/test・docs/localization を並行できれば 10～16 週を想定します。token は計画値であり、価格保証や固定 ChatGPT credits ではありません。

## 10. Definition of Done

R-01～R-10 の証拠、5 preset の clean generation、DB fresh／upgrade、現行規約に合う generator、共通 business code の lite/full、4 言語 UI／README／文書／画像の人手レビュー、初心者の独立操作、temporary artifact の撤去、既知 P0・重大 dependency vulnerability・secret leak・cross-tenant／authorization defect の不存在をすべて満たした時だけ完了とします。
