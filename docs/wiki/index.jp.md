# Omni-Stack 学習・保守 Wiki

対象基準：主幹 `fc165e3` とその文書整理ブランチ。Java/Vue の基礎を知り、本プロジェクトを初めて使う読者を想定します。中国語を内容の基準とし、[简体中文](index.md)、[English](index.en.md)、[한국어](index.kr.md) と同期します。実際の翻訳レビュー状況（HUMAN_REVIEW と所有者が承認した AI_REVIEW を区別）は[文書マニフェスト](../docs-manifest.yaml)に記録します。

## 1. ゼロから始める

最初の四段階を順番に完了してから、役割に応じた業務を学びます。各段階の期待結果はローカル Docker で確認します。

| 段階 | 読む・実行する | 期待結果 |
|---|---|---|
| 1 | [コード取得、構成の理解、初回起動](../guides/quick-start.jp.md) | migrator が正常終了し、選択サービスが正常でフロントエンドに接続できる |
| 2 | [ログインとテナント](../guides/authentication.jp.md) | 実際の CAPTCHA で、意図したテナントとユーザーとしてログイン |
| 3 | [ロール、メニュー、ボタン、データ範囲](../guides/permissions.jp.md) | 管理者と従業員の権限が異なり、不正な要求は拒否される |
| 4 | [シード、教材データ、テスト fixture](data-and-fixtures.jp.md) | 空環境、基本シード、業務デモを区別できる |
| 5 | [プリセット選択](../preset-quick-selection.jp.md)と[プロジェクト生成](../guides/scaffold-development.jp.md) | 一部起動と削減済みプロジェクト生成の違いを説明できる |

## 2. 役割に沿って業務を学ぶ

| 役割・目的 | 学習パス | 前提 |
|---|---|---|
| プラットフォーム管理者 | [システム、セキュリティ、監査](../guides/system-security-audit.jp.md) | core、テナント管理者ロール |
| 従業員・承認者 | [Workflow](../guides/workflow-approval.jp.md)、[個人・システムタスク](../guides/scheduling.jp.md) | workflow、候補者割当とタスク所有権 |
| 営業 | [CRM リードから商談へ](../guides/crm-flow.jp.md) | crm、営業ロールとデータ範囲 |
| 仕入先管理者・仕入先 | [SRM 登録とポータル](../guides/srm-flow.jp.md) | supply-chain、招待、SUPPLIER ロール、有効な関連 |
| 申請者・購買担当・仕入先 | [購買申請から入荷へ](../guides/procurement-flow.jp.md) | supply-chain、承認モデル、有効な仕入先、購買権限 |
| 資産管理者・利用者・承認者 | [資産作成、割当、移管、処分](../guides/asset-flow.jp.md) | supply-chain、合格した資産対象入荷、承認モデル |
| 運用担当 | [運用、バックアップ、更新](../guides/operations-upgrade.jp.md)、[問題解決](../guides/troubleshooting.jp.md) | 確認済みのローカル環境とバックアップ範囲 |

初期シードに過去の画面の注文や承認があるとは限りません。画像内の業務番号は例です。自分の操作が返した ID を使います。SQL で状態を書き換えて承認、見積、入荷を飛ばしてはいけません。

## 3. ソースと拡張

- [ソース読解とブレークポイント](source-debugging.jp.md)：要求から権限、トランザクション、イベント、テスト、失敗まで追跡します。
- [サービス・CRUD 生成](../guides/scaffold-development.jp.md)：dry-run の後に生成、接続、検証します。
- [依存マトリクス](../preset-dependency-matrix.jp.md)、[保守](../preset-maintenance.jp.md)、[カスタム](../custom-preset-tutorial.jp.md)、[更新](../preset-upgrade-guide.jp.md)：事実表は catalog から派生します。
- [構成](../architecture.jp.md)、[API 契約](../api-contract.jp.md)、[主要フロー](../core-flows.jp.md)：保守者向けの現在の技術的事実です。
- [バックエンド](../backend-patterns.jp.md)と[フロントエンド規約](../frontend-patterns.jp.md)：変更する前に関連規則を読みます。

## 4. 用語

| 用語 | 本プロジェクトでの意味 |
|---|---|
| preset | モジュール選択。catalog が依存関係の閉包を計算する |
| migrator | 起動前に構造移行、冪等シード、初期設定を行う一回限りのプロセス |
| tenant | 分離境界。ALL のデータ範囲でもテナントを越えない |
| RBAC / DataScope | 機能権限と行の可視範囲。両方を検証する |
| Gateway assertion | ゲートウェイが署名したユーザー情報。下流はクライアントの識別情報ヘッダーを信用しない |
| Outbox / Inbox | 同一トランザクションの送信待ちイベント／消費重複除去記録 |
| Saga | 分散手順と補償。サービス横断の ACID トランザクションではない |
| fixture | テナント、業務キー、後片付け範囲が明示されたテスト・教材データ |
| ADR | 理由、影響、置換関係を含む過去の意思決定 |
| Harness | 事実、制約、ツール、検証による実行ループ |

## 5. 文書サイトと保守

[独立文書サイト](site-maintenance.jp.md)で Docker ビルド、4173 ポート、ローカル検索、静的成果物、各ホストのソースリンクを説明します。本文の唯一のソースはリポジトリ Markdown です。言語ディレクトリはマニフェストから生成します。

現在の事実、設計、教材、ADR、検収履歴の責務は[リポジトリ規則](../repository-governance.md)にあります。過去の検収は当時の範囲だけを証明します。プレビュー成功は翻訳レビューや公開完了を意味しません。
