# プロジェクトプリセット保守ガイド

`scaffold/catalog/modules.yaml` が唯一の情報源で、`scaffold/presets/*.yaml` は入口モジュールだけを宣言します。依存表と README 表は `npm run docs:preset` で生成します。

## 管理対象

- catalog と `module.schema.json` / `preset.schema.json`
- 公式 `scaffold/presets/*.yaml`
- `tools/omni-cli/src/preset-generator.ts`
- `tools/omni-cli/scripts/preset-golden.mjs`
- 生成済みスナップショット `scaffold.lock`

## モジュール追加

1. コード、migration、冪等 seed、権限、Compose、Gateway、文書を完成させます。
2. 依存順に catalog へ追加し、`dependencies` は先に宣言済みの ID のみ参照します。
3. backend、frontend view/component/API/i18n、route、Compose、changelog/seed、provisioning、Nacos、port、MQ、XXL-JOB、docs、resource、compatibility/deprecation を実態どおり記述します。
4. `optionalModules` を実行時スイッチの代用にせず、競合を明示します。
5. catalog validation で不存在パス、Compose の孤立依存、ポート衝突、未管理モジュールを修正します。
6. 適切なプリセットに入口だけを追加し、推移依存を複製しません。
7. 裁断テスト、生成物 build、runtime smoke を追加します。

状態機械、DataScope、子リソース継承、Saga、冪等性は業務モジュールに残します。

## プリセット変更とゲート

~~~powershell
Set-Location tools/omni-cli
npm test
npm run test:preset-structure
npm run docs:preset
npm run docs:preset:check
~~~

修正は patch、互換機能追加は minor、破壊的境界変更は major を上げます。PR は `test:preset-smoke`、夜間/リリースは 5 種の `test:preset-golden`、runtime は各 preset の fresh・実起動・ログイン・メニュー・health・主要フローを検証します。

残留検査は Maven、Dockerfile、Compose、Gateway、view/component/API/i18n、権限、DB、MQ、専用文書を対象にします。

生成は同階層 staging で原子的に行われ、失敗時に削除されます。Schema → 依存/競合 → catalog resource → 裁断 → Maven/npm → Compose → fresh DB → runtime/E2E の順で調査します。管理対象出力を直接直さず、catalog・template・generator を修正して新しいディレクトリへ再生成してください。実行済み Liquibase changeSet は変更せず、前方修正を追加します。
