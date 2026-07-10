# フロントエンドパターンと規約

> 本文書は Omni-Stack フロントエンドの内部構成方法を定義します。すべてのフロントエンドコードはこれらのパターンに従う必要があります。  
> アーキテクチャの概要については [architecture.jp.md](architecture.jp.md) を参照してください。Docker デプロイメントの設定については [docker-deployment.jp.md](docker-deployment.jp.md) を参照してください。

---

## 目次

- [1. 技術選定の考え方](#1-技術選定の考え方)
- [2. ディレクトリ構成](#2-ディレクトリ構成)
- [3. API レイヤー](#3-api-レイヤー-srcapi)
- [4. 状態管理](#4-状態管理-srcstores)
- [5. ルーティング](#5-ルーティング-srcrouter)
- [6. コンポーネント設計パターン](#6-コンポーネント設計パターン)
- [7. コンポーネント規約](#7-コンポーネント規約)
- [8. 命名規約](#8-命名規約-typescript)
- [9. コードフォーマット](#9-コードフォーマット-typescript--vue)
- [10. UI フレームワーク](#10-ui-フレームワーク)
- [11. 国際化（i18n）](#11-国際化i18n)
- [12. 権限とアクセス制御](#12-権限とアクセス制御)
- [13. ビジネスモジュール構造パターン](#13-ビジネスモジュール構造パターン-business-module-structure-pattern)
- [14. Composables パターン](#14-composables-パターン)
- [15. Docker デプロイメントにおける Vite ビルド最適化](#15-docker-デプロイメントにおける-vite-ビルド最適化)
- [16. ビルドとツール](#16-ビルドとツール)
- [17. トラブルシューティングガイド](#17-トラブルシューティングガイド)

---

## 1. 技術選定の考え方

### Vue 3.5 + Composition API を選択した理由

| 検討事項 | 理由 |
|------|------|
| **TypeScript ネイティブサポート** | Vue 3.5 は `<script setup lang="ts">` に対して一流の型推論を提供し、`defineProps<T>()` はランタイム宣言が不要です |
| **Composition API** | ロジックの再利用は Composables を通じて実現され、Mixin の命名衝突や來源の不透明性を解消します |
| **Tree-shaking 対応** | API（`ref`、`computed`、`watch`）をオンデマンドでインポートでき、未使用の機能はバンドルされません |
| **パフォーマンス** | Proxy ベースのリアクティブシステムは Vue 2 の `Object.defineProperty` よりも高速で、`Map`/`Set` などのコレクション型もサポートします |
| **Teleport / Suspense** | 組み込みの `<Teleport>` と `<Suspense>` コンポーネントにより、モーダルや非同期コンポーネントの処理が簡素化されます |

### Vite 8 を選択した理由

| 検討事項 | 理由 |
|------|------|
| **ネイティブ ESM** | 開発サーバーはブラウザのネイティブ ES Module に基づいており、バンドルなしで起動できます。コールドスタート速度は Webpack の 30 秒以上から 2 秒未満に短縮されました |
| **HMR 速度** | モジュールレベルの HMR により、単一ファイルの変更時そのモジュールのみを更新し、チャンク全体は更新しません。ホットアップデートの遅延は 50ms 未満です |
| **Rollup 互換** | プロダクションビルドは Rollup に基づいており、成熟した tree-shaking とチャンク分割を備えています |
| **プラグインエコシステム** | `@vitejs/plugin-vue` 6.x は SFC コンパイラと HMR 統合を提供します |

### Vuex ではなく Pinia 3 を選択した理由

| 検討事項 | 理由 |
|------|------|
| **公式推奨** | Vue 3 から Vue 公式は Vuex の代わりに Pinia を推奨しており、Vuex はメンテナンスモードに入っています |
| **Composition API スタイル** | `defineStore('id', () => { ... })` は Vue 3 の `<script setup>` スタイルと一貫しています |
| **TypeScript サポート** | 完全な型推論を備え、`@Mutation` / `@Action` デコレータは不要です |
| **Mutation なし** | State + Getters + Actions のみで、データフローを簡素化します（state の直接変更または action 経由） |
| **軽量** | コアパッケージは 1KB 未満（gzipped）で、ボイラープレートコードはありません |
| **DevTools** | Vue DevTools と統合され、タイムトラベルデバッグと状態スナップショットをサポートします |

### Element Plus 2.x を選択した理由

| 検討事項 | 理由 |
|------|------|
| **Vue 3 ネイティブ** | Element UI の Vue 3 バージョンで、完全な Composition API サポートを備えています |
| **エンタープライズ向けコンポーネント** | テーブル（`el-table`）、フォーム（`el-form`）、ダイアログ（`el-dialog`）などのエンタープライズ向けコンポーネントが成熟しています |
| **国際化組み込み** | `vue-i18n` との統合をサポートし、コンポーネントに中日英の文言が組み込まれています |
| **ダークモード** | `theme-chalk/dark/css-vars.css` によりワンクリックでダークテーマに切り替えられます |
| **アイコンライブラリ** | `@element-plus/icons-vue` は 280 以上のベクターアイコンを提供します |

---

## 2. ディレクトリ構成

```
omni-frontend/
├── index.html              # Vite エントリ HTML
├── package.json
├── vite.config.ts          # Vite 設定（プロキシ、エイリアス、ビルド）
├── tsconfig.json
├── eslint.config.mjs       # ESLint flat config
└── src/
    ├── main.ts             # アプリケーション起動（Pinia、Router、Element Plus、I18n）
    ├── App.vue             # ルートコンポーネント（<router-view />）
    ├── api/                # API レイヤー（ビジネスドメインごとに 1 ファイル）
    │   ├── request.ts      # 共有 Axios インスタンス + インターセプタ
    │   ├── user.ts         # ユーザー API 関数
    │   ├── dict.ts         # ディクショナリ API（型 + データ CRUD）
    │   ├── auth.ts         # 認証 API（ログイン、登録、验证码、ソーシャルログイン、OAuth2）
    │   ├── menu.ts         # メニュー API（動的メニューツリー）
    │   ├── workflow.ts     # ワークフロー API（モデル、定義、インスタンス、承認）
    │   └── myJob.ts        # ユーザー定期タスク API
    ├── stores/             # Pinia 状態管理（ビジネスドメインごとに 1 ファイル）
    │   ├── user.ts         # ユーザー認証 Store（Token、ユーザー情報）
    │   ├── app.ts          # アプリケーション設定 Store（テーマ、サイドバー）
    │   └── permission.ts   # 権限 Store（権限コードリスト、動的メニューツリー）
    ├── router/
    │   └── index.ts        # ルート定義 + ナビゲーションガード + 動的ルート登録
    ├── views/              # ページコンポーネント（kebab-case ディレクトリ）
    │   ├── home/index.vue          # ホームページ（未ログインランディング / ログイン済みワークベンチ）
    │   ├── login/index.vue         # ログインページ
    │   ├── register/index.vue      # 登録ページ
    │   ├── callback/index.vue      # OAuth2 コールバックページ
    │   ├── dashboard/index.vue     # ダッシュボード
    │   ├── consent/index.vue       # OAuth2 認可確認ページ
    │   ├── device/                 # デバイス認可（Device Code Flow）
    │   │   ├── index.vue           # デバイスシミュレータ：QR コード表示 + Token ポーリング
    │   │   └── verify.vue          # ユーザー認可確認ページ
    │   ├── base/                   # 基礎データモジュール
    │   │   └── dict/index.vue      # ディクショナリ管理（master-detail レイアウト）
    │   ├── system/                 # システム管理モジュール
    │   │   ├── user/index.vue      # ユーザー管理
    │   │   ├── role/index.vue      # ロール管理
    │   │   ├── permission/index.vue # 権限管理
    │   │   ├── org/index.vue       # 組織管理
    │   │   ├── tenant/index.vue    # テナント管理
    │   │   ├── oauth2/index.vue    # OAuth2 クライアント管理
    │   │   ├── online/index.vue    # オンラインユーザー監視
    │   │   └── xssconfig/index.vue # XSS 防御設定
    │   ├── workflow/               # ワークフローモジュール
    │   │   ├── model/index.vue     # プロセスモデル管理 + BPMN デザイナー
    │   │   ├── definition/index.vue # プロセス定義管理
    │   │   ├── instance/index.vue  # プロセスインスタンス管理
    │   │   └── stats/index.vue     # プロセス統計
    │   └── monitor/                # 監視モジュール
    │       └── oper-log/index.vue  # 操作ログ
    ├── layout/
    │   └── index.vue       # アプリケーションシェルレイアウト（サイドバー、ヘッダー、コンテンツエリア）
    ├── components/         # 共有・再利用 UI コンポーネント
    │   ├── LoginForm.vue           # ログインフォーム（パスワード + SSO + サードパーティ OAuth2）
    │   ├── CronGenerator.vue       # Cron 式ジェネレータ
    │   ├── DynamicFormRenderer.vue # 動的フォームレンダラー
    │   └── workflow/               # ワークフロー専用コンポーネント
    │       ├── ModelDesigner.vue   # BPMN モデリングデザイナー
    │       └── panels/             # BPMN プロパティパネル
    ├── composables/        # Vue Composables（ロジック再利用）
    │   ├── useDictOptions.ts       # ディクショナリオプション読み込み
    │   ├── useBpmnModeler.ts       # BPMN Modeler ライフサイクル管理
    │   └── useBpmnExtension.ts     # BPMN 拡張要素の読み書き
    ├── constants/          # 定数定義
    │   └── menu.ts                 # メニュー i18n マッピングテーブル
    ├── directives/         # Vue カスタムディレクティブ
    │   └── permission.ts           # v-permission 権限ディレクティブ
    ├── utils/              # ユーティリティ関数
    │   ├── jwt.ts                  # JWT 解析（権限、テナント ID の抽出）
    │   └── pkce.ts                 # PKCE ユーティリティ（OAuth2 Device Code Flow）
    ├── i18n/
    │   └── index.ts        # vue-i18n インスタンス設定
    ├── locales/            # 翻訳ファイル
    │   ├── zh-CN.ts        # 中国語翻訳
    │   └── en-US.ts        # 英語翻訳
    ├── types/              # 共有 TypeScript 型
    │   └── api.ts          # ApiResponse<T>, PageResult<T>
    └── styles/
        └── index.scss      # グローバルスタイル（reset、レイアウト、テーマ）
```

---

## 3. API レイヤー (`src/api/`)

### 共有 Axios インスタンス

`request.ts` は単一の設定済み Axios インスタンスをエクスポートします：

- **baseURL**：`VITE_API_BASE_URL` 環境変数から読み取り、デフォルトは `/api`
- **timeout**：15000ms
- **withCredentials**：`true`（Cookie を含め、OAuth2 Session 管理をサポート）
- **リクエストインターセプタ**：`useUserStore()` から Token を読み取り、`Authorization: Bearer <token>` を注入します
- **レスポンスインターセプタ**：`code === 200` をチェックし、ビジネスエラーは `ElMessage.error` で通知、401 は期限切れダイアログを表示してログインページにリダイレクトします（redirect パラメータ付き）

### API 関数パターン

ビジネスドメインごとに 1 ファイル。関数命名規約：

| 操作 | プレフィックス | 例 |
|------|------|------|
| ID で検索 | `getXxx` | `getUserById(id)` |
| ページングリスト | `listXxx` | `listUsers(page, size)` |
| 作成 | `createXxx` | `createUser(data)` |
| 更新 | `updateXxx` | `updateUser(id, data)` |
| 削除 | `deleteXxx` | `deleteUser(id)` |

すべての関数は型付きパラメータと戻り値の型を持つ必要があり、`ApiResponse<T>` を使用します：

```typescript
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/auth/user/list?page=${page}&size=${size}`,
  )
}
```

### 型システム

- **共有型**（`ApiResponse<T>`、`PageResult<T>`）：`src/types/api.ts` に定義され、唯一の真実の情報源となります
- **ドメイン型**（`UserInfo`、`SysUser` など）：使用する API ファイル内に配置します（`src/api/user.ts`）
- 共有型の**重複定義は禁止**されています。常に `@/types/api` からインポートしてください
- 型のみのインポートには `import type { ... }` を使用してください

---

## 4. 状態管理 (`src/stores/`)

### Pinia Composition API スタイル

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const username = ref<string>('')

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
  }

  return { token, username, setToken, logout }
})
```

### ルール

- ビジネスドメインごとに 1 Store：`user.ts`、`app.ts`、`permission.ts`
- Store ファイル命名：kebab-case
- Store フック命名：`use` プレフィックス（`useUserStore`、`useAppStore`、`usePermissionStore`）
- Composition API スタイルを使用（`defineStore('id', () => { ... })`）
- 永続化には `localStorage` を使用。非永続化ステートには `ref()` を使用
- Store 内での DOM 直接操作は禁止

### 3 つのコア Store の責務

| Store | 責務 | 永続化 |
|-------|------|--------|
| `useUserStore` | Token 管理、ユーザー基本情報（username、nickname、avatar） | `localStorage`（token） |
| `useAppStore` | テーマ切り替え（light/dark）、サイドバー折りたたみ状態、言語設定 | `localStorage`（theme、lang） |
| `usePermissionStore` | 権限コードリスト（JWT から解析）、動的メニューツリー（バックエンドからロード）、`menusLoaded` フラグ | なし（ログインごとに再取得） |

---

## 5. ルーティング (`src/router/`)

### ルーティングアーキテクチャ

Omni-Stack は**静的ルート + 動的ルート**のハイブリッドアーキテクチャを採用しています：

```
静的ルート（公開ページ）
├── /            → Home（トップページランディング / ワークベンチ）
├── /login       → Login
├── /register    → Register
├── /callback    → OAuth2Callback
├── /device      → DeviceAuth
├── /device/verify → DeviceVerify
├── /consent     → OAuth2 認可確認
└── /admin       → Layout（管理バックエンドシェル）
    └── /admin/dashboard → Dashboard（静的子ルート）

動的ルート（権限ドリブン、ランタイム登録）
└── /admin/<feature> → 対応する views/<module>/<feature>/index.vue
    例：/admin/user → views/system/user/index.vue
       /admin/dict → views/base/dict/index.vue
```

### ナビゲーションガードフロー

```
router.beforeEach()
    ↓
to.meta.requiresAuth を判定
    ├── false → ログイン済みユーザーが /login にアクセス → /home にリダイレクト
    │           その他の公開ページ → 通過許可
    └── true  → userStore.token をチェック
                ├── Token なし → /login?redirect=<path> にリダイレクト
                └── Token あり → permissionStore.menusLoaded をチェック
                    ├── false → initFromToken() → loadMenus() → registerDynamicRoutes() → 再ナビゲーション
                    └── true  → registerDynamicRoutes()（冪等）→ 通過許可
```

### 規約ベースのビューコンポーネントマッピング

動的ルートは `import.meta.glob` を使用して `views/` ディレクトリ配下のすべての `index.vue` をスキャンし、permissionCode → ビューコンポーネントの自動マッピングを実現します：

```typescript
// router/index.ts
const viewModules = import.meta.glob('../views/**/index.vue')

// マッピングルール：permissionCode "system:user" → "views/system/user/index.vue"
function resolveViewComponent(permissionCode: string) {
  if (viewOverrides[permissionCode]) return viewModules[viewOverrides[permissionCode]]
  const modulePath = permissionCode.replace(/:/g, '/')
  return viewModules[`../views/${modulePath}/index.vue`]
}
```

**特殊マッピングオーバーライド**（`viewOverrides`）：

| permissionCode | 特殊マッピングパス | 理由 |
|----------------|------------|------|
| `system:oauth2` | `views/oauth2-client/index.vue` | OAuth2 コールバックページとの競合を回避するため |
| `base:operlog` | `views/monitor/oper-log/index.vue` | monitor ディレクトリに分類するため |

### ルール

- すべてのルートコンポーネントは遅延読み込みを使用：`() => import('@/views/xxx/index.vue')`
- `meta` フィールド：`title`（必須）、`icon`（任意）、`requiresAuth`（必須）、`permissionCode`（動的ルートは自動設定）
- 認証が必要なルート：明示的に `requiresAuth: true` を設定
- 公開ルート：明示的に `requiresAuth: false` を設定
- ログアウト時に `clearDynamicRoutes()` を呼び出してすべての動的ルートをクリアします

---

## 6. コンポーネント設計パターン

### ページコンポーネント（Views）

ページコンポーネントは標準的な**データ + 操作 + UI** の 3 層構造に従います：

```vue
<script setup lang="ts">
// 1. インポート（API、型、コンポーネント、i18n）
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, type SysUser } from '@/api/user'

const { t } = useI18n()

// 2. リアクティブステート
const tableData = ref<SysUser[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

// 3. フォームステート
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = { username: [{ required: true, ... }] }

// 4. ビジネス関数
async function loadData() { loading.value = true; try { ... } finally { loading.value = false } }
function handleCreate() { dialogVisible.value = true }
async function handleSubmit() { await formRef.value?.validate(); ... }
async function handleDelete(id: number) { await ElMessageBox.confirm(...); ... }
</script>
```

### 共有コンポーネント（Components）

- 汎用 UI コンポーネントは `src/components/` に配置します
- ビジネス固有のコンポーネントは対応する views と同じ場所に配置します
- コンポーネント使用時は UpperCamelCase（`<UserCard />`）、ファイルは kebab-case ディレクトリを使用

### コンポーネント通信パターン

| シナリオ | パターン | 例 |
|------|------|------|
| 親子通信 | `defineProps<T>()` + `defineEmits<T>()` | `<LoginForm @login-success="onLogin" />` |
| クロスコンポーネントステート | Pinia Store | `useUserStore().token` |
| ロジック再利用 | Composable | `useDictOptions('dict_type_code')` |
| スロット拡張 | `<slot>` + named slots | `<template #header>...</template>` |

---

## 7. コンポーネント規約

### SFC 要素の順序

```vue
<script setup lang="ts">
// Script は先頭
</script>

<template>
  <!-- Template は中間 -->
</template>

<style scoped lang="scss">
/* Styles は最後 */
</style>
```

### ルール

- すべてのコンポーネントは `<script setup lang="ts">` を使用
- Props：`defineProps<T>()` で TypeScript ジェネリクスを使用
- ディレクトリ：kebab-case ディレクトリ + `index.vue`（例：`views/login/index.vue`）
- スタイル：`<style scoped lang="scss">` でコンポーネントごとに分離
- グローバルスタイルは `src/styles/` に配置
- サードパーティコンポーネントのスタイルを上書きする場合を除き、`!important` の使用は禁止

---

## 8. 命名規約 (TypeScript)

| 種別 | スタイル | 例 |
|------|------|------|
| 変数 / 関数 | lowerCamelCase | `getUserById`、`userList` |
| インターフェース / 型 | UpperCamelCase | `ApiResponse`、`UserInfo` |
| コンポーネントファイル | kebab-case ディレクトリ + `index.vue` | `views/login/index.vue` |
| Store ファイル | kebab-case | `stores/user.ts` |
| Store フック | `use` プレフィックス | `useUserStore` |
| Composable | `use` プレフィックス | `useDictOptions` |
| 定数 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| CSS クラス名 | kebab-case | `sidebar-menu`、`login-container` |

---

## 9. コードフォーマット (TypeScript / Vue)

- インデント：半角スペース 2 つ
- セミコロンなし（ASI スタイル）
- シングルクォート。JSX 内ではダブルクォート
- 複数行構造では末尾カンマを保持
- 型のみのインポートには `import type { ... }` を使用
- `src/` 配下のモジュールのインポートには `@` パスエイリアスを使用
- Vue SFC の順序：`<script setup>` → `<template>` → `<style scoped>`

---

## 10. UI フレームワーク

- **Element Plus** を UI コンポーネントライブラリとして使用（フォーム、テーブル、カード、メニューなど）
- **@element-plus/icons-vue** でアイコンを提供（`main.ts` でグローバル登録）
- Element Plus の CSS 変数を使用してテーマをカスタマイズ（`--el-color-primary`、`--el-bg-color-page` など）
- フォームバリデーション：Element Plus の `FormRules` + `ref<FormInstance>()` パターン
- ダークモード：`theme-chalk/dark/css-vars.css` により自動切り替え

```typescript
// フォームバリデーションの標準パターン
const formRef = ref<FormInstance>()
const rules: FormRules = {
  username: [{ required: true, message: 'ユーザー名は必須です', trigger: 'blur' }],
}

async function handleSubmit() {
  await formRef.value?.validate()  // バリデーション通過後に続行
  // ... 送信ロジック
}
```

---

## 11. 国際化（i18n）

### 設定

`vue-i18n` 11.x をベースに、Composition API モード（`legacy: false`）を使用します：

```typescript
// i18n/index.ts
const i18n = createI18n({
  legacy: false,
  locale: getStoredLang(),        // localStorage から読み取り、デフォルトは 'zh-CN'
  fallbackLocale: 'en-US',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})
```

### 使用方法

```typescript
// コンポーネント内
const { t, locale } = useI18n()
const label = t('common.dashboard')

// 言語切り替え
locale.value = 'en-US'
storeLang('en-US')  // localStorage に永続化
```

### 翻訳ファイル構造

翻訳ファイルは `src/locales/` に配置され、モジュールごとに key を整理します：

```typescript
// zh-CN.ts
export default {
  common: {
    dashboard: '仪表盘',
    systemManagement: '系统管理',
    userManagement: '用户管理',
    // ...
  },
  login: {
    title: '登录',
    username: '用户名',
    // ...
  },
  // ...
}
```

### 新規モジュールの i18n チェックリスト

| 手順 | ファイル | 操作 |
|------|------|------|
| 1 | `src/locales/zh-CN.ts` | `common.<feature>Management` と機能レベルの翻訳 key を追加 |
| 2 | `src/locales/en-US.ts` | 対応する英語翻訳を追加 |
| 3 | `src/constants/menu.ts` → `menuI18nMap` | `permissionCode → i18n key` マッピングを追加 |

---

## 12. 権限とアクセス制御

### Permission Store (`src/stores/permission.ts`)

`usePermissionStore` は権限コードリストと動的メニューツリーを管理します：

```typescript
// JWT Token から権限コードを初期化
permissionStore.initFromToken()

// バックエンドから動的メニューをロード
await permissionStore.loadMenus()

// ユーザーが指定された権限を持っているか照会
permissionStore.hasPermission('system:user:create')
```

**データソース**：
- 権限コードリスト：JWT Token の `authorities` フィールド（ログイン時に書き込まれ、`getPermissionsFromToken()` で解析）
- 動的メニューツリー：`GET /api/auth/menus` を呼び出し（バックエンドがユーザー権限に基づいてフィルタリング済み）

### Dynamic Menu Routing

ログイン成功後、ルートガードは以下のフローを実行します：

```
JWT デコード → permissionStore.initFromToken() で権限コードを抽出
    → permissionStore.loadMenus() でバックエンドのフィルタ済みメニューツリーを取得
    → registerDynamicRoutes() でメニューツリーを走査し Vue Router ルートを追加
    → サイドバーで permissionStore.menuTree をレンダリング
```

メニューデータ構造（`MenuNode`）には `path`（フロントエンドルートパス）、`permissionCode`（権限コード）、`type`（DIRECTORY/MENU）などのフィールドが含まれます。

### Button-Level Permission (`v-permission`)

Vue カスタムディレクティブ `v-permission` を使用してボタンの表示・非表示を制御します：

```vue
<!-- system:user:create 権限を持つユーザーのみ表示 -->
<el-button v-permission="'system:user:create'" type="primary">
  新規追加
</el-button>
```

**実装の仕組み**：
- ディレクティブのマウント時に `usePermissionStore` から権限コードを照会
- 権限がない場合は `el.style.display = 'none'` を設定（`removeChild` ではなく、Vue のリアクティブ更新と互換性あり）
- `mounted` と `updated` の両方のフックでチェックを実行

**登録方法**（`src/directives/permission.ts`）：

```typescript
// main.ts で登録
import { setupPermissionDirective } from '@/directives/permission'
setupPermissionDirective(app)
```

### ルール

- 権限コードはバックエンドの `sys_permission` テーブルで定義されたものと一致する必要があり、フォーマットは `resource:action` です
- `v-permission` ディレクティブは UI レイヤーの表示・非表示制御のみに使用し、バックエンドの `@PreAuthorize` がセキュリティの境界です
- 動的ルートはナビゲーションガード内で `permissionStore.loadMenus()` の完了を待ってから登録する必要があります
- ログアウト時は必ず `permissionStore.reset()` を呼び出して権限状態をクリアしてください

### XSS 防御設定管理

XSS 防御管理ページ（`views/system/xssconfig/index.vue`）はグローバルスイッチとブラックリストルールの CRUD を提供します：

- **グローバルスイッチ**：`el-switch` コンポーネントで `PUT /api/auth/xss-config/toggle` を呼び出し
- **ルールリスト**：`el-table` ページングテーブルで、新規作成、編集、削除、個別の有効化/無効化をサポート
- **テナント分離**：API レイヤーで JWT Token から `tenant_id` を抽出し、`X-Tenant-Id` リクエストヘッダーで伝達
- **ルール種別**：HTML タグ / イベントハンドラ / 危険なプロトコル / カスタム正規表現

---

## 13. ビジネスモジュール構造パターン (Business Module Structure Pattern)

新規ビジネスモジュールを追加する際は、以下の規約に従ってディレクトリを構成し、ルートを登録し、国際化を設定します。

### 標準ディレクトリレイアウト

「基礎データモジュール → ディクショナリ管理機能」を例にします：

```
src/
├── views/
│   └── base/                  # モジュールグループディレクトリ
│       └── dict/
│           └── index.vue      # 機能ページ（SFC 順序: script → template → style）
├── api/
│   └── dict.ts               # API 関数 + TypeScript インターフェース（1 ファイル 1 機能ドメイン）
├── stores/
│   └── dict.ts               # （任意）複雑なクライアントサイドステートが必要な場合のみ作成
└── locales/
    ├── zh-CN.ts              # 中国語翻訳
    └── en-US.ts              # 英語翻訳
```

### 登録チェックリスト

新規ビジネスモジュールごとに以下の登録を完了する必要があります：

| 手順 | ファイル | 操作 |
|------|------|------|
| 1 | `src/router/index.ts` → `iconMap` | `'<module>:<feature>': '<IconName>'` を追加（例：`'base:dict': 'Collection'`） |
| 2 | `src/constants/menu.ts` → `menuI18nMap` | モジュールディレクトリとメニュー項目の i18n key マッピングを追加 |
| 3 | `src/locales/zh-CN.ts` + `en-US.ts` | `common.<module>Management`（ディレクトリ名）、`common.<feature>Management`（メニュー名）および機能レベルの翻訳 key を追加 |
| 4 | `scripts/sql/init-all.sql` → `sys_permission` | DIRECTORY（モジュールグループ）+ MENU（機能ページ）+ BUTTON/API（操作権限）のシードデータを追加 |
| 5 | `scripts/sql/init-all.sql` → `sys_role_permission` | SUPER_ADMIN ロールに新規権限ノードを割り当て |

### 参考実装：ディクショナリ管理モジュール

新規モジュールの参考として `base:dict`（ディクショナリ管理）を以下に示します：

- **View**: `views/base/dict/index.vue` — master-detail レイアウト（左側タイプリスト 10/24 cols、右側データリスト 14/24 cols）
- **API**: `api/dict.ts` — 11 個の型付き関数（`listDictTypes`、`getDictType`、`createDictType`、`updateDictType`、`deleteDictType`、`toggleDictTypeStatus`、`listDictData`、`createDictData`、`updateDictData`、`deleteDictData`、`refreshDictCache`）
- **Permission codes**: `dict:type:*`（4 個）+ `dict:data:*`（5 個）
- **UI patterns**: `v-permission` ボタン権限制御、`el-switch` ステータス切り替え、`el-pagination` ページング、`ElMessageBox.confirm` 削除確認、`X-Tenant-Id` マルチテナント分離

---

## 14. Composables パターン

Composables は Omni-Stack フロントエンドのロジック再利用のコア機能であり、従来の Mixin パターンを置き換えるものです。

### 設計原則

| 原則 | 説明 |
|------|------|
| **単一責務** | 1 つの Composable は 1 つの機能ドメインのみに焦点を当てます |
| **戻り値の規約** | `{ state, computed, actions }` オブジェクトを返し、分割代入で消費します |
| **リアクティブ** | 内部で `ref()` / `reactive()` を使用し、戻り値は自動的にリアクティブになります |
| **ライフサイクル安全性** | `onMounted` / `onUnmounted` で副作用を管理します |

### 代表的な Composable：useDictOptions

```typescript
// composables/useDictOptions.ts
export function useDictOptions(dictTypeCode: string) {
  const options = ref<DictOption[]>([])
  const loading = ref(false)

  async function loadOptions() {
    loading.value = true
    try {
      const { data: res } = await listDictDataByType(dictTypeCode)
      options.value = res.data.map(item => ({
        label: item.dictLabel,
        value: item.dictValue,
      }))
    } finally {
      loading.value = false
    }
  }

  onMounted(loadOptions)

  return { options, loading, reload: loadOptions }
}
```

**使用方法**：

```vue
<script setup lang="ts">
const { options: categoryOptions } = useDictOptions('workflow_category')
</script>

<template>
  <el-select v-model="form.category">
    <el-option v-for="opt in categoryOptions" :key="opt.value"
               :label="opt.label" :value="opt.value" />
  </el-select>
</template>
```

### 既存の Composables

| Composable | ファイル | 責務 |
|------------|------|------|
| `useDictOptions` | `composables/useDictOptions.ts` | ディクショナリオプションをロードし、リアクティブな `options` 配列を返します |
| `useBpmnModeler` | `composables/useBpmnModeler.ts` | bpmn-js Modeler の作成、破棄、インポート/エクスポートを管理します |
| `useBpmnExtension` | `composables/useBpmnExtension.ts` | BPMN extensionElements の読み書き操作を行います |

---

## 15. Docker デプロイメントにおける Vite ビルド最適化

### マルチステージビルド

フロントエンド Docker イメージは 2 段階ビルドを採用しています（詳細は `docker/frontend/Dockerfile` を参照）：

```dockerfile
# ステージ 1：ビルド
FROM node:22-alpine AS builder
WORKDIR /app
COPY omni-frontend/package*.json ./
RUN npm ci                      # 決定的インストール（バージョン固定）
COPY omni-frontend/ .
RUN npm run build               # vue-tsc 型チェック + Vite プロダクションビルド

# ステージ 2：実行（Nginx 静的サーバー）
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
```

### Vite ビルド設定

```typescript
// vite.config.ts
build: {
  target: 'es2020',            // ビルドターゲット：オプションチェーン、Null 合体演算子などのモダン構文をサポート
  outDir: 'dist',
  chunkSizeWarningLimit: 2000, // Element Plus のサイズが大きいため、閾値を適度に引き上げ
}
```

### プロダクションビルド最適化のポイント

| 最適化項目 | 説明 |
|--------|------|
| **ルート遅延読み込み** | すべての views コンポーネントは `() => import()` による動的インポートを使用し、オンデマンドローディングを実現 |
| **Element Plus オンデマンド** | `@vitejs/plugin-vue` により未使用コンポーネントを自動 tree-shake |
| **`import.meta.glob`** | ビューモジュールスキャンはコンパイル時に静的分析され、無関係なファイルはバンドルされません |
| **チャンク分割** | Vite/Rollup が自動的に vendor（vue、element-plus、axios）を独立したチャンクに分割 |
| **CSS 抽出** | プロダクションビルドは CSS を独立したファイルに自動抽出し、ブラウザキャッシュをサポート |

### Nginx 設定のポイント

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # SPA History モード：すべての非ファイルリクエストを index.html にフォールバック
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API リバースプロキシを Gateway（コンテナ内部ポート）に転送
    location /api/ {
        proxy_pass http://omni-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # OAuth2 エンドポイントプロキシ
    location /oauth2/ {
        proxy_pass http://omni-gateway:8080;
    }
}
```

---

## 16. ビルドとツール

| ツール | 用途 | 設定 |
|------|------|------|
| Vite 8 | 開発サーバー + バンドラー | `vite.config.ts` |
| TypeScript 5.9 | 型チェック | `tsconfig.json`（strict モード） |
| ESLint 9 | コードリント | `eslint.config.mjs`（flat config） |
| Sass | CSS プリプロセッサ | `<style lang="scss">` 経由 |
| vue-i18n 11 | 国際化 | `src/i18n/index.ts` |
| bpmn-js 18 | BPMN プロセスモデリング | `composables/useBpmnModeler.ts` |

### Vite プロキシ

開発サーバーは `/api`、`/oauth2`、`/.well-known` を `http://localhost:8102`（Gateway）にプロキシします：

```typescript
server: {
  port: 3000,
  host: true,
  proxy: {
    '/api': { target: 'http://localhost:8102', changeOrigin: true },
    '/oauth2': { target: 'http://localhost:8102', changeOrigin: true },
    '/.well-known': { target: 'http://localhost:8102', changeOrigin: true },
  },
},
```

### コマンド

```bash
npm install        # 依存関係のインストール
npm run dev        # 開発サーバー :3000
npm run build      # 型チェック + プロダクションビルド
npm run lint       # ESLint チェック
npm run preview    # プロダクションビルドのプレビュー
```

---

## 17. トラブルシューティングガイド

### 開発環境の問題

| 問題 | 考えられる原因 | 調査方法 |
|------|---------|---------|
| **Vite 起動エラー `EADDRINUSE`** | ポート 3000 が占有されている | `lsof -i :3000` または `netstat -ano \| findstr :3000` で占有プロセスを終了するか、`vite.config.ts` の `server.port` を変更 |
| **API リクエスト 404** | Gateway が起動していないかプロキシ設定エラー | Gateway が 8102 ポートで動作しているか確認。`vite.config.ts` の proxy target を確認 |
| **CORS エラー** | プロキシを経由せずバックエンドに直接リクエスト | リクエストが `/api` プレフィックスで Vite プロキシを経由しているか確認 |
| **Element Plus のスタイル欠落** | CSS がインポートされていない | `main.ts` に `import 'element-plus/dist/index.css'` が含まれているか確認 |
| **TypeScript 型エラー** | `node_modules` のキャッシュが古い | `rm -rf node_modules && npm install` を実行 |
| **HMR が機能しない** | ファイルシステムウォッチャーの制限 | Linux: `echo fs.inotify.max_user_watches=524288 \| sudo tee -a /etc/sysctl.conf` |

### ビルドの問題

| 問題 | 考えられる原因 | 調査方法 |
|------|---------|---------|
| **`vue-tsc` 型チェック失敗** | 型の不一致 | `npx vue-tsc --noEmit` を実行して具体的なファイルと行番号を特定 |
| **チャンクサイズが大きい** | 依存関係が tree-shake されていない | `npx vite build --mode analyze` を実行して依存関係のサイズを分析 |
| **Docker ビルドで `npm ci` が失敗** | ネットワークの問題または `package-lock.json` の非同期 | ローカルで `npm install` を再実行して新しい lock ファイルを生成するか、npm ミラーソースを設定 |

### ランタイムの問題

| 問題 | 考えられる原因 | 調査方法 |
|------|---------|---------|
| **ログイン後にホワイトスクリーン** | Token が無効またはメニューのロード失敗 | DevTools → Application → Local Storage で token を確認。Network タブで `/api/auth/menus` のレスポンスを確認 |
| **動的メニューが表示されない** | `permissionStore.loadMenus()` の失敗 | バックエンドの `/api/auth/menus` が正常に返されているか確認。`menusLoaded` 状態を確認 |
| **v-permission ボタンが常に非表示** | 権限コードの不一致 | `sys_permission` テーブルの `permission_code` とフロントエンドの `v-permission` の値を比較 |
| **OAuth2 コールバック 404** | redirect_uri の設定エラー | Auth サービスの OAuth2 クライアント設定で redirect_uri が実際のコールバック URL と一致しているか確認 |
| **i18n が key を表示して翻訳されない** | 翻訳 key の欠落 | `zh-CN.ts` / `en-US.ts` に対応する key が含まれているか確認。`menuI18nMap` のマッピングを確認 |
| **SPA ルート更新で 404** | Nginx にフォールバック設定がない | Nginx 設定に `try_files $uri $uri/ /index.html;` があるか確認 |
