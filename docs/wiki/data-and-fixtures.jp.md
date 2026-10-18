# 基本シード、教材データ、テスト fixture

基準：主幹 `fc165e3`。[初回起動](../guides/quick-start.jp.md)を完了し、Compose project、テナント、記録を確認します。本ページは既存データのリセットを許可しません。

## 1. データの出所

| 出所 | 責務 | 使用時 |
|---|---|---|
| `database/changelog/` | 不変の構造・必要データ移行 | fresh と更新、migrator が実行 |
| `scripts/sql/seed/` | 冪等な基本シード | migrator が参照、手動で重複実行しない |
| `database/seed/manifest.yaml` | ハッシュ、自然キー、検証条件 | ユーザーデータが空でなくても基本データを検証 |
| `database/seed/history/` | 過去の固定シード | 既存 changeSet の再実行、自由に編集しない |
| `scripts/fixtures/`、test classpath | 範囲を限定した識別情報・テストデータ | 確認済みローカル検証、本番初期化ではない |
| 教材で作る記録 | 実際の業務デモ | UI・正式 API で作成し、返された ID を使う |

管理者、開発テナント、メニュー、承認モデルは基本能力です。承認済み申請、見積、注文、資産は業務記録です。シードは過去の `PR-1-1` や `RFQ-1-1` を保証しません。

## 2. fresh、再実行、既存データの取り込み

fresh は分離された空のデータベース環境です。Compose は migrator の正常終了後に依存アプリを起動します。再起動は更新の再実行であり、失敗を隠すために volume を削除しません。

既存データの取り込みには `database/adoption/` の固定基準と実際のバックアップ証拠が必要です。未確認の DB を移行済みにしません。失敗時はログと版を保持し、前方修正を行います。実行済み changeSet は変更しません。

基本検証は正式なシードの自然キーを使い、ユーザーが追加した記録を固定件数に含めません。権限・基本データ変更ではソース、SHA-256、検証条件を更新し、不足を隠すために期待値を変えません。

## 3. 教材データの作成

1. 購買・資産には supply-chain、営業には crm を選びます。
2. テナント、管理者、申請者、承認者、仕入先を記録します。仕入先には SUPPLIER と有効なポータル関連が必要です。ユーザー名だけで権限があると判断しません。
3. デモ名を `WIKI-<実行固有ID>` で始め、実際の個人・銀行・企業情報を使いません。
4. [SRM](../guides/srm-flow.jp.md)、[購買](../guides/procurement-flow.jp.md)、[資産](../guides/asset-flow.jp.md)の順に正式操作を行い、ID、状態、版を記録します。
5. 同じ要求の再試行は requestId を再使用し、新しい操作には新しい requestId を使います。不明な応答の後は副作用を確認します。
6. 主な業務の成功を確認する前に、非表示、拒否、取消、重複要求を確認します。後片付けは本実行のキーだけを、許可された撤回・終了・削除操作で行います。

物理削除できない記録は取消・終端状態のまま保持します。教材ツールは汎用 reset ではありません。共有テナントを上書きしたり、DB・volume を一括削除したりしません。

<!-- omni:modules:workflow -->
### 教材用承認者の組織スコープ

ロールと主組織を割り当てるだけでは承認候補者になりません。現在のユーザーロール API は `sys_user_role` を更新しますが、候補者解決は別途 `sys_user_role_scope` を参照します。管理画面にはこの関連付けの保守機能がありません。まず候補者プレビューを確認してください。ロールの選択だけでは承認可能な状態を証明できません。

ホストへ MySQL ポートを公開しない独立した教材環境に限り、[組織スコープ fixture](../../scripts/fixtures/run-workflow-scope-fixture.ps1)で明示した `wiki{run}_name` アカウントの既存ロールと主組織を `SAME_UNIT` で関連付けられます。ユーザー作成、ロール付与、競合の上書きは行わず、標準 `omni-stack` は拒否します。有効なユーザー、ロール、主組織を先に準備します。ホスト PowerShell は起動用で、DB クライアントと SQL は対象 Docker コンテナ内で実行します。

```powershell
$env:E2E_MUTATIONS = 'true'
$evidence = Join-Path ([IO.Path]::GetTempPath()) ('omni-scope-' + [guid]::NewGuid())
./scripts/fixtures/run-workflow-scope-fixture.ps1 -ProjectName 'omni-wiki-acceptance-yourrun' -Usernames 'wikiyourrun_manager','wikiyourrun_compliance' -OutputDirectory $evidence
```

プロジェクト名とアカウントを実際の値に置き換えます。検証記録には SQL ハッシュ、変更前後の関連付け、コンテナ識別情報を記録します。再確認には新しい出力先と `-VerifyOnly` を使います。競合したら検証記録を残して教材設定を修正し、DB はリセットしません。

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
候補者資格と承認完了 API の権限は別条件です。現在の SRM の三つの承認ロールのシードには `workflow:approval:complete` がありません。ロール管理で教材用の最小ロールを作り、`workflow`、`workflow:instance`、`workflow:instance:list`、`workflow:task:todo`、`workflow:approval:complete` を付与し、元の業務ロールも残します。再ログインまたは短期 fixture トークンの再発行後、その本人の候補タスクを処理します。権限不足を隠すためにスーパー管理者が代理承認してはいけません。
<!-- omni:modules:end -->

## 4. 管理された識別情報と CAPTCHA

実際のログインはサービスが生成した CAPTCHA を人が入力します。成功・失敗どちらでも captchaKey を原子的に消費するため、再試行前に更新します。

ログイン後 E2E では `omni-auth/src/test/java/.../E2eTokenFixture.java` の短期テスト識別情報を使えます。確認済み DB のロールと現在の Redis 鍵を使い、本番の裏口、パスワード読み取り、CAPTCHA 解読を行いません。`scripts/fixtures/run-token-fixture.ps1` から実行し、出力はリポジトリ外に置いて使用後削除します。

過去 fixture のテナント、UUID、資源保護条件を保持します。入力不足では拒否します。標準スタックへの置換は分離検証の成功ではありません。[fixture 規則](../../scripts/fixtures/README.md)を参照します。

## 5. 結果の判断

| 現象 | 確認 |
|---|---|
| 基本カタログ、権限、モデル不足 | migrator とシード条件、業務状態を直接直さない |
| 概覧が空 | 空業務環境では正常、教材記録を作る |
| 入荷後に資産なし | PASS、assetManaged、正の整数数量、MQ と Inbox |
| デモが見えない | テナント、ロール、owner/current user 範囲 |
| 再実行で重複 | requestId、イベント ID、出所単位番号、前の副作用 |
| 図とデータが異なる | 本実行の ID・状態を使い、過去図を現在の証拠にしない |

バックアップと復元は[運用](../guides/operations-upgrade.jp.md)を参照します。機密情報を除去した結論と必要画像だけをコミットし、token、認証状態、DB 出力、原ログは含めません。
