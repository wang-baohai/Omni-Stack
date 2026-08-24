# プリセットプロジェクト アップグレードガイド

`scaffold.lock` は source、catalog、preset、module、template の各バージョンを記録します。新しい空ディレクトリへ再生成して手書き部分を移行する方式が標準で、破壊的なインプレース裁断はサポートしません。

## 事前確認

1. プロジェクト、DB、環境設定を commit または backup します。
2. 現在の lock を保存し、管理対象ファイルの手修正を検出します。
3. release note と catalog の `compatibility` / `deprecation` を確認します。
4. 公式 preset は `omni preset diff`、custom は解決済みクロージャを比較します。
5. DB の expand/migrate/contract とサービス間契約を整理します。

## 手順

新 CLI で validate・explain・dry-run を実行し、新しい空ディレクトリへ生成します。新旧 lock と生成物を比較し、古い Maven/Compose/Gateway/seed/catalog 登録で上書きしません。手書き domain code、test、秘密を含まない設定だけを移行し、管理領域の変更は declaration/template に戻します。

DB backup、fresh、正式 snapshot の clone-upgrade を行い、backend、frontend、Compose、残留、ログイン/メニュー/health、主要フロー、E2E を通します。1 リリース期間の互換 rollback を確保して切り替えます。

## ロールバックと完了

旧アプリは互換性のある拡張済み schema に戻します。未検証の down SQL は使わず、Liquibase の前方修正を追加します。未配備なら新ディレクトリを破棄するだけで元工程は変更されません。

lock と module/route/permission/DB/Compose が一致し、fresh と clone-upgrade が冪等、削除モジュールの残留がなく、tenant/role の主要フローと README・matrix・保守文書が同期した時点で完了です。
