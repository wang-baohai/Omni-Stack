# フロントエンドパターンと規約

> 本書は現在のフロントエンド構成、共有クライアント状態、ルーティング、権限による表示、言語、ライフサイクルの制約を管理します。実際の動作はソースと設定が決定します。新規コードの規約は、既存の全行を検証済みという意味ではありません。
> 通信形式とエラー分類は [API 契約](api-contract.jp.md)、業務不変条件は各ドメインの事実・設計文書で管理します。導入、機能追加、操作、復旧は [Wiki](wiki/index.jp.md)、[開発ガイド](guides/scaffold-development.jp.md)、[Docker デプロイ](docker-deployment.jp.md)を参照してください。

---

## 目次

- [1. 技術基準](#1-技術基準)
- [2. ディレクトリ構成](#2-ディレクトリ構成)
- [3. API レイヤー (src/api/)](#3-api-レイヤー-srcapi)
- [4. 状態管理 (src/stores/)](#4-状態管理-srcstores)
- [5. ルーティング (src/router/)](#5-ルーティング-srcrouter)
- [6. コンポーネント設計パターン](#6-コンポーネント設計パターン)
- [7. コンポーネント規約](#7-コンポーネント規約)
- [8. 命名規約 (TypeScript)](#8-命名規約-typescript)
- [9. コードフォーマット (TypeScript / Vue)](#9-コードフォーマット-typescript--vue)
- [10. UI フレームワーク](#10-ui-フレームワーク)
- [11. 国際化（i18n）](#11-国際化i18n)
- [12. 権限とアクセス制御](#12-権限とアクセス制御)
- [13. ビジネスモジュール構造パターン](#13-ビジネスモジュール構造パターン)
- [14. Composables パターン](#14-composables-パターン)
- [15. ビルドと静的配信の境界](#15-ビルドと静的配信の境界)
- [16. ビルドとツール](#16-ビルドとツール)
- [17. トラブルシューティングガイド](#17-トラブルシューティングガイド)

---

## 1. 技術基準

Vue 3 Composition API、TypeScript、Pinia、Vue Router、Element Plus、vue-i18n、Vite を使用します。宣言範囲は [package.json](../omni-frontend/package.json)、解決済みバージョンは [package-lock.json](../omni-frontend/package-lock.json)が管理し、範囲の下限をインストール済みバージョンとみなしません。

アプリは `<script setup lang="ts">` と Pinia setup store を使います。共有コンポーネントは [plugins/element-plus.ts](../omni-frontend/src/plugins/element-plus.ts)で明示登録し、Element Plus locale をアプリ言語に連動させます。起動、HMR、バンドル、本番性能の測定には環境を明記し、依存バージョンだけで固定性能や UI 全体の検証完了を主張しません。

---

## 2. ディレクトリ構成

次は責務と代表的な入口であり、完全なファイルツリーではありません。全業務モジュールは現在のソースとモジュール定義に従って構成します。

| ディレクトリ・入口 | 現在の責務 |
|---|---|
| [main.ts](../omni-frontend/src/main.ts)、[App.vue](../omni-frontend/src/App.vue) | Pinia/Router/UI/i18n/ディレクティブ/テーマの起動、ルート出口、コンポーネント言語 |
| `src/api/` | 共有 request.ts と user.ts、dict.ts、`crm-*`、`srm-*`、`procurement-*`、`asset-*` などのサブドメイン |
| `src/stores/` | user.ts、app.ts、permission.ts |
| [router/index.ts](../omni-frontend/src/router/index.ts) | 静的・動的ルート、ビュー・アイコン対応、セッション回収、ガード |
| `src/views/`、`src/components/`、`src/layout/` | 画面、共有コンポーネント、管理レイアウト。OAuth クライアントは system/oauth2 ではなく views/oauth2-client |
| `src/composables/` | 辞書状態と BPMN ライフサイクル。拡張モジュールは通常の関数も公開可能 |
| `src/utils/`、`src/constants/` | JWT デコード、エラー・遷移先・アクセス方針、PKCE、メニュー文言。PKCE は認可コードフロー用 |
| `src/i18n/`、`src/locales/` | 言語インスタンスと zh-CN.ts、en-US.ts、ja-JP.ts、ko-KR.ts |
| [types/api.ts](../omni-frontend/src/types/api.ts)、`src/styles/` | 唯一の共有レスポンス型とグローバルスタイル |

---

## 3. API レイヤー (`src/api/`)

[request.ts](../omni-frontend/src/api/request.ts) の共有 Axios は VITE_API_BASE_URL（既定 /api）、timeout 15000ms、withCredentials=true を使用します。userStore.token から Bearer と X-Tenant-Id を取得します。クライアントのデコードは JWT 署名を検証せず、信頼済み ID は Gateway が決定します。

成功時は AxiosResponse 全体を返し、業務 data を自動展開しません。

| 階層 | 現在の例 |
|---|---|
| HTTP レスポンス | `AxiosResponse<ApiResponse<PageResult<SysUser>>>` |
| `response.data` | `R<T>` に対応する ApiResponse |
| `response.data.data` | 業務 PageResult |

現在の [user.ts](../omni-frontend/src/api/user.ts) は SysUser/listUsers/getUser を使用し、戻り型は Axios の型引数から推論します。共有 ApiResponse/PageResult は `@/types/api` からだけ取得し、DTO・null の意味は API 契約に従います。

[error-display.ts](../omni-frontend/src/utils/error-display.ts) と [errors.ts](../omni-frontend/src/utils/errors.ts) がエラーを分類・翻訳します。401 は必ずしも期限切れではなく、認証情報用のエンドポイントはセッションと入力を保持します。再認証は未ログイン・期限切れの該当分岐だけで行い、403 ではセッションを消しません。再認証は先に共有状態を回収し、ダイアログ終了後に遷移します。同時ダイアログと明示的ログアウトの応答はフラグで抑制します。

リモート・Axios の技術 message は診断用で、表示には displayMessage/getErrorMessage を使用します。ただし Axios でないローカル Error の message は getErrorMessage が保持するため、全ローカル例外が翻訳済みとは言えません。OAuth2 標準 token 取得などは別の fetch・プロトコル応答を使い、この R クライアント規則を適用しません。

---

## 4. 状態管理 (`src/stores/`)

現在の三つの setup store の責務です。

| Store | 状態・永続化 |
|---|---|
| [user](../omni-frontend/src/stores/user.ts) | token と username を永続化。isLoggedIn は token の有無だけ、isLoggingOut は明示的終了 |
| [app](../omni-frontend/src/stores/app.ts) | sidebarCollapsed/theme、omni-theme の既定は dark。初回側欄は768px条件で折りたたみ、側欄状態は永続化しない |
| [permission](../omni-frontend/src/stores/permission.ts) | permissions/menuTree/menusLoaded/menuLoadState/menuLoadError、メモリ状態と共有メニュー Promise |

nickname/avatar は現在の userStore の項目ではありません。omni-lang は i18n が管理し、appStore に属しません。token だけを消すログアウト例は完全なセッション回収ではありません。

Router の `clearAuthenticatedSession(explicit)` は user.logout、permission.reset、clearDynamicRoutes を順に呼びます。user.logout 自体は token/username のみを消去し、辞書キャッシュは別途 ID 変更を監視します。テーマ helper による HTML dark クラス操作は appStore が明示的に所有する DOM 副作用です。通常の業務 store が自由にページ DOM を操作する方式ではありません。

現在のメニュー要求には辞書キャッシュの Token・世代による応答ガードがありません。reset は状態・Promise 参照を消すだけで送信済み要求を取り消しません。辞書の回帰検証で全共有状態の同等な隔離を証明することはできません。

---

## 5. ルーティング (`src/router/`)

Router は `createWebHistory(import.meta.env.BASE_URL)` を使用します。静的とは事前定義であり、公開ではありません。`requiresAuth === false` だけが公開分岐で、未設定も保護分岐です。

| 静的パス | 現在の処理 |
|---|---|
| `/`、`/login`、`/register` | 公開。token がある Login は Home へ |
| `/portal-login`、`/portal-register` | 公開。token がある PortalLogin は supplier-portal へ |
| `/callback`、`/device`、`/device/verify`、`/consent` | 公開ルート。画面・プロトコル固有の検証は残る |
| `/supplier-portal` | token と portalAccess 方針が必要 |
| `/menu-load-error` | token が必要。メニュー再試行 |
| `/admin`、`/admin/dashboard` | 管理レイアウト・既定子画面。管理アクセス方針を適用 |
| `/workspace`、`/workspace/overview` | 保護対象。ルートは統合コックピットへ移り、旧承認・ジョブルートは5.1の同一画面内パネルへ移る |
| `/admin/:pathMatch(.*)*`、`/:pathMatch(.*)*` | それぞれ403/404画面。前者は保護、後者は公開 |

保護ルートで token がなければ safeAppRedirect でアプリ内復帰先を保持し、Login/PortalLogin を選びます。token があれば毎回 initFromToken を行います。メニュー未完了時は loadMenus を待って登録・replace 再遷移し、失敗は MenuLoadError に進みます。残りの分岐は登録を補い、管理・サプライヤーポータル・資産本人の方針を適用します。token の存在やデコードは署名の有効性・期限を証明せず、サーバー要求には引き続き認証が必要です。

動的登録はビューが見つかる MENU だけを扱い、srm:portal: 子項目を除外して children を再帰処理します。権限コードのコロンを斜線にして admin 子パス、ハイフンにしてルート名を作ります。`system:user → /admin/system/user`、`base:dict → /admin/base/dict` であり、/admin/user・/admin/dict ではありません。MenuNode.path は権限ツリーの階層を表す保存パスで、フロントエンド URL ではありません。

`import.meta.glob('../views/**/index.vue')` で遅延ビューを取得します。viewOverrides は system:oauth2→views/oauth2-client/index.vue、base:operlog→views/monitor/oper-log/index.vue の二件です。動的 meta は title/icon/requiresAuth/permissionCode を持ちますが型は任意項目で、全静的記録が全部を指定しているわけではありません。登録名で重複を防ぎ、終了時に削除します。

メニュー失敗でも menusLoaded=true になるため、idle/loading/loaded/failed も確認します。retryLoadMenus はリセットして再取得します。復旧操作は Wiki に記載します。

---

### 5.1 ホーム、ワークスペース、アプリの移動境界

公式 CLI は catalog が解決したモジュールの依存閉包を `OMNI_FRONTEND_MODULES` でフロントエンドのビルド引数 `VITE_OMNI_MODULES` に渡します。ソースを小さいプリセットでビルドすると、ビュー解決とクライアント側の表示権限をその選択範囲に限定します。workflow がなければプロセス統計を要求せず承認パネルも表示しません。SRM がなければサプライヤーの入口も提供しません。この引数を指定しない直接ビルドは従来の全体動作を保ち、生成プロジェクトは実際に残るファイルにも制約されます。この絞り込みは JWT、DB のロール、サーバー認可を変更しません。実装は [deployment-modules.ts](../omni-frontend/src/utils/deployment-modules.ts)です。

ホームのワークスペース入口は同一オリジンの通常リンクで、新しいタブを `noopener noreferrer` 付きで開きます。既存セッションを共有し、URL に token を入れません。ワークスペースにホーム/Wiki への戻り口はありません。

概要、承認、マイジョブは `/workspace/overview` で既存一覧・フォーム・API を再利用します。旧 `/workspace/approvals`、`/workspace/jobs` は query を保ち、`#approvals`、`#my-tasks` へ移ります。三画面を別々には維持しません。

ワークスペースのアプリと検索は権限があり実ビューを持つ全メニューが対象で、業務入口は新しいタブを開きます。業務レイアウトに全体アプリボタンやパネルはなく、サイドバーは現在のモジュールに集中します。検索は安定した根 ID `route.meta.appId` で入れるページを絞り、選択時も対象 appId を再確認して同じタブで移動します。無効 ID は候補なしで、URL 接頭辞から推測しません。

非公開エラーページもホーム・前の画面への回復ボタンを持ちません。不明なモジュールや権限エラーでダッシュボードを代替メニューにしません。ブラウザの戻る、タブ切替、アドレス入力は制限せず、画面の境界でサーバー認可を代用しません。初回メニュー読込後は `resolveMenuReloadTarget()` で path、query、hash を個別に渡し、path に fullPath を入れず戻り先引数とアンカーを保ちます。

実装は [ConsoleHeader](../omni-frontend/src/components/ConsoleHeader.vue)、[ワークスペース](../omni-frontend/src/views/workspace/index.vue)、[移動用ツール](../omni-frontend/src/utils/navigation.ts)です。

---

## 6. コンポーネント設計パターン

画面ではリアクティブデータ、読込・フォーム状態、操作、テンプレートを分けます。API・型はドメインから取得し、再利用ロジックは Composables、業務コンポーネントは画面と共に整理できます。実装は[ユーザー画面](../omni-frontend/src/views/system/user/index.vue)を参照し、import 不足や省略記号を含む別の「実行可能」画面を維持しません。

loading は finally で解除し、送信前にフォーム検証を待ち、ボタン権限とサーバー検証を守ります。新規操作ではユーザー取消と実際の失敗を区別し、広い catch で全失敗を取消と扱いません。既存処理の範囲はソースが定義します。

親子通信は defineProps/defineEmits、共有クライアント状態は Pinia、UI 拡張は slots を使います。型推論は実行時の本文検証の代わりにはなりません。

---

## 7. コンポーネント規約

新規 SFC は `<script setup lang="ts">` → `<template>` → `<style scoped>` の順です。画面は通常 kebab-case ディレクトリの index.vue、共有コンポーネントは LoginForm.vue/LanguageSelector.vue のような UpperCamelCase ファイルも使います。画面の規則を全コンポーネント名に強制しません。

Props・イベントは型付き defineProps/defineEmits を使い、スタイルの範囲を明確にし、共通スタイルは src/styles に置きます。第三者ライブラリの上書きも範囲を限定します。日時・金額・共有応答型は [API 契約](api-contract.jp.md)と [AGENTS](../AGENTS.md)に従い、競合する形式を作りません。

---

## 8. 命名規約 (TypeScript)

| 対象 | 新規コードの規約 |
|---|---|
| 変数・関数 | lowerCamelCase |
| interface・型・コンポーネント | UpperCamelCase |
| 画面ディレクトリ | kebab-case + index.vue |
| Store・ドメインファイル | 現在の入口に合わせ、新規は kebab-case を優先 |
| Store・状態 Composable | use 接頭辞 |
| 定数・CSS クラス | UPPER_SNAKE_CASE / kebab-case |

myJob.ts/systemJob.ts などの既存入口は残っており、一括改名済みという意味ではありません。composables 内の通常 helper は Ref の返却や Hook 名を強制されません。新規コードコメントは AGENTS に従って中国語で記述します。

---

## 9. コードフォーマット (TypeScript / Vue)

[eslint.config.mjs](../omni-frontend/eslint.config.mjs) は二空白、セミコロンなし、単引用符、複数行末尾コンマ、SFC block-order を検査します。lint は `--max-warnings 0` のため warn もコマンド失敗になります。Node スクリプトの globals/no-console 例外は src 規則を変えません。

[tsconfig.json](../omni-frontend/tsconfig.json) は strict、noUnusedLocals/noUnusedParameters、noFallthroughCasesInSwitch、bundler resolution、noEmit と @/*→src/* を設定します。これらはバックエンド権限や全既存命名・書式の監査を証明しません。

---

## 10. UI フレームワーク

main が呼ぶ [installElementPlus](../omni-frontend/src/plugins/element-plus.ts) は列挙したコンポーネント、アイコン、loading 指令だけを登録し、全アイコンライブラリは登録しません。Globe は Connection の別名です。iconMap と実際の登録集合を同時に管理します。

main は基本・暗色変数 CSS を読み込み、initTheme/setTheme が document.documentElement の dark クラスを適用します。既定値・永続化は appStore の責務であり、CSS の読込だけでテーマは切り替わりません。検証文言を言語変更に追随させる場合はリアクティブな規則と t() を使い、各画面の実装を参照します。

---

## 11. 国際化（i18n）

[i18n/index.ts](../omni-frontend/src/i18n/index.ts) は legacy=false と `zh-CN/en-US/ja-JP/ko-KR`、保存キー omni-lang を使います。無効・未設定値は zh-CN、欠落キーは en-US にフォールバックします。コンポーネント外では同じインスタンスの translate() を使います。

[LanguageSelector](../omni-frontend/src/components/LanguageSelector.vue) は locale と storeLang の両方を更新し、後者は保存と HTML lang の設定を行います。[App.vue](../omni-frontend/src/App.vue) は ElConfigProvider の言語を対応付け、未知の対応は enUS に戻します。localStorage だけの書き換えではリアクティブ言語は変わりません。

新規文言は四つの locale で同じキー構造を維持し、メニューは menuI18nMap も更新します。API・プロトコル識別子を翻訳したり、特定言語のソース例に別言語の本文を入れたりしません。ファイル存在・フォールバックは意味や UI の検証の代わりにはなりません。

Router は遷移時にメニュー対応または docTitle で document.title を更新します。同一画面の言語変更直後の再計算は保証しません。キー一致、ハードコード監査、文書 AI_REVIEW、実ブラウザーの確認は別の検査です。

---

## 12. 権限とアクセス制御

permissions は JWT scope の配列を前提とし、roles は roles から取得します。ローカルのデコードは署名も claims の構造も検証しません。この配列の前提で hasPermission は文字列の完全一致による所属判定を行い、SUPER_ADMIN やロール名に自動でワイルドカード権限を与えません。

メニューツリーは `GET /api/auth/menus` から取得し、DIRECTORY/MENU はグループ・ルート、path は権限ツリーの階層を表す保存パスです。クライアント表示はバックエンド認可の証明ではありません。v-permission は mounted/updated で display:none または空 display を設定し、DOM を削除しません。空のバインドは安全な拒否手段ではありません。

ルートには [access.ts](../omni-frontend/src/utils/access.ts) の管理・サプライヤー・資産本人方針も適用します。安全境界はバックエンドの認証、機能権限・行の所有、テナント・データ範囲であり、画面パスやボタンでは代用できません。現実装の制約は各ドメイン文書で確認します。

XSS 設定 API、エラー、テナントの契約は [API 契約](api-contract.jp.md)で管理します。X-Tenant-Id を送っても任意の ID が信頼されるわけではなく、Gateway が検証済み ID で上書きします。終了はメニュー非表示だけでなく、セッション全体を回収します。

---

## 13. ビジネスモジュール構造パターン

画面、API、任意 Store、四言語 locale を CRM/SRM/Procurement/Asset とプラットフォーム機能ごとに整理します。共有応答型は src/types/api.ts だけで定義します。

router の iconMap、constants/menu.ts の menuI18nMap、sys_permission/sys_role_permission、指令、エンドポイント権限を一致させ、シード変更時は manifest 摘要と検証を更新します。接続手順は[開発ガイド](guides/scaffold-development.jp.md)、現在の例は[辞書画面](../omni-frontend/src/views/base/dict/index.vue)と [API](../omni-frontend/src/api/dict.ts)に一本化します。

---

## 14. Composables パターン

状態を持つ Composable は機能ごとに名前付き Ref、ComputedRef、操作関数を返す。通常の補助関数はそのインターフェースに従った値を返す。副作用の所有者を明確にし、コンポーネント資源はそのスコープで回収する。共有モジュールキャッシュは独自に認証文脈を監視する。

### 設計原則

| 原則 | 制約 |
| --- | --- |
| 単一責務 | 機能単位で整理し、共有 ApiResponse/PageResult を重複定義しない |
| リアクティブな戻り値 | リアクティブ状態を公開する場合は refs/computed を返し、分割代入後も更新を保つ。テンプレートは Vue の規則で展開する |
| 文脈の所有 | キャッシュと処理中要求はテナントと認証文脈を区別する |
| ライフサイクル | コンポーネント監視はスコープ終了時に停止。モジュール監視は独立所有し、文脈置換・HMR 時に停止 |

### 代表的な Composable：useDictOptions

```typescript
import { useDictOptions } from '@/composables/useDictOptions'

const { options, loading } = useDictOptions('workflow_category')
```

`useDictOptions(typeCode)` の戻り値は `{ options, loading }`。options は `ComputedRef<DictOption[]>`、loading は `Ref<boolean>`。認証済みの `getDictOptions()` を呼び、公開 `reload` メソッドや `listDictDataByType()` 呼び出しはない。

キャッシュキーはテナント ID と typeCode。同一テナント・同一 Token の処理中要求を共有する。テナント変更で選択肢の参照先が切り替わり、Token 変更で旧要求は無効になる。同一テナントの取得済みキャッシュは再利用できる。旧応答は要求が有効かつ Token 不変の場合のみ反映し、その終了処理は新しい処理中要求を削除しない。

モジュールは利用側から独立した認証監視スコープを所有する。最後の利用側が破棄されても、ログアウト時にはキャッシュを消去し旧応答を拒否する。デコードしたテナント値が falsy の場合は選択肢要求を送らない。このクライアント判定は JWT の署名や claims の構造を検証しない。アンマウントだけでは共有キャッシュは失効せず、バックエンド Redis の更新もクライアントメモリの更新とは別である。

テンプレートでは loading の Ref 自動展開値を直接バインドし、スクリプトでは `.value` を使用する。ソース入口は契約と実 Composable/Vue の 8 回帰ケースを含む。操作手順は Wiki に記載する。

[dict.ts](../omni-frontend/src/api/dict.ts) / [useDictOptions.ts](../omni-frontend/src/composables/useDictOptions.ts) / [dict-options.test.ts](../omni-frontend/tests/base/dict-options.test.ts)

### 既存の Composables

| ソース | 責務 |
| --- | --- |
| [useDictOptions.ts](../omni-frontend/src/composables/useDictOptions.ts) | テナント選択肢キャッシュ・文脈監視・読み込み状態 |
| [useBpmnModeler.ts](../omni-frontend/src/composables/useBpmnModeler.ts) | bpmn-js 初期化・インポート／エクスポート・onBeforeUnmount による破棄 |
| [useBpmnExtension.ts](../omni-frontend/src/composables/useBpmnExtension.ts) | readAssignment/writeAssignment などの BPMN 拡張読み書き関数 |

[Flow 9](core-flows.jp.md)

この隔離は useDictOptions の実装であり、permissionStore のメニュー要求には一般化しません。useBpmnModeler はコンテナー準備後に初期化し、import/export、unmount 時の keydown 削除・destroy を行います。useBpmnExtension は通常の拡張関数と moddle 定義を公開します。辞書要求の状態を共有せず、8 回帰で全非同期取消、readonly 動作、ブラウザーのライフサイクルを認証するものでもありません。

---

## 15. ビルドと静的配信の境界

フロントエンドイメージはアプリの構築後、同じリポジトリの Wiki を順番に構築し、同じ3000ポートの `/wiki/` で公開静的ページを配信します。公開の `/` はログインなしで本文を表示し、認証状態はアカウント領域だけを変えます。ワークスペースと業務ルートの認証ガードは維持します。Wiki ファイルがなければアプリのホームへ戻さず404を返します。同一オリジンでの埋め込みを許可するのは Wiki 文書だけで、起動スクリプトにはレスポンスの nonce を使用します。ログインと業務パスの既存の安全方針は維持します。

[フロントエンド Dockerfile](../docker/frontend/Dockerfile) は Node 22 で構築し、Nginx 1.28 で dist を3000から配信します。現在の [Nginx](../docker/frontend/nginx.conf) は /api/、/oauth2/、/.well-known/ を Docker DNS で omni-gateway:8080 に転送し、index.html の history フォールバックを提供します。

HTML は no-cache/no-store、assets は一年の immutable キャッシュです。add_header を持つ location は安全ヘッダーを明示再定義します。これは設定の事実であり、本段階でサービス起動やプロキシ・キャッシュの動作を確認済みという意味ではありません。操作・永続化は [Docker デプロイ](docker-deployment.jp.md)と[クイックスタート](guides/quick-start.jp.md)に記載します。

---

## 16. ビルドとツール

package.json は build=`vue-tsc -b && vite build`、lint=`eslint . --max-warnings 0`、単体/E2E/スクリーンショット/言語監査を定義します。Vite は es2020、dist、750KB警告、bpmn-vendor/element-plus/vue-vendor 分割を使用し、開発転送先 `http://localhost:8102` は Vite プロセスのネットワークを意味します。

アプリ、ビルド、テスト、ブラウザー検証はローカル Docker で行い、正確なコマンドは [AGENTS](../AGENTS.md)に従います。ロックファイルと npm ci を使用し、ソース変更は影響する build/lint/tests、E2E は TypeScript も検証します。文書変更だけで不変の高価な検証を反復せず、再利用には摘要・コミット等価性が必要です。生成プリセットは独自の検査を行います。

---

## 17. トラブルシューティングガイド

[トラブルシューティング](guides/troubleshooting.jp.md)が起動・ネットワーク・ビルド・ログイン・メニュー・言語の操作と復旧、[ソースデバッグ](wiki/source-debugging.jp.md)が呼出関係とブレークポイントを管理します。

実際の HTTP/R.code、trace、エラーの取得元を保持し、ID、メニュー状態・ビュー対応、権限、翻訳を区別します。標準ポート変更、共有キャッシュ削除、品質検査無効化で失敗を隠しません。ソース読解、テスト、静的設定、ブラウザー、実業務の証拠は分けて記録します。
