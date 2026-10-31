/**
 * @module navigation/application-drawer.test
 * @description WP-04.S02 应用抽屉的定向结构测试（node:test，不依赖浏览器、Vue 运行时或 Pinia）。
 *
 * 断言对象是「抽屉与顶栏/卡片/搜索共用同一份派生导航」这一完成判据本身：
 * - 抽屉本体只挂载在共享顶栏一次，布局不再各自挂载或保留 #drawer 插槽；
 * - 分类与分组全部来自 useApplicationNavigation 与同一份已授权菜单树，父级取真实 DIRECTORY 节点；
 * - 条目只指向已解析的真实路由，不合成 overview、不重拼路径、不依赖异步开窗；
 *   WP-04.S04 把模块入口改为同源真实 href 的新标签链接，因此本文件的模块入口断言按 S04 锁定方案更新，
 *   用户2026-10-24追加：抽屉内叶子也在新标签进入，只有模块自身侧栏保留当前标签路由；
 * - 搜索只做本地过滤，不发起额外请求，也不扩大可见集合；
 * - 加载/失败/无结果/无应用四种状态各有独立文案，失败态带重试入口；
 * - 遮罩、Esc、关闭按钮与 focus 归还齐备，窄屏不隐藏关键操作；
 * - 抽屉自身不持有记录源：收藏只按共享 store 的记录渲染，且与当前可见集合求交（WP-05.S03 接入偏好后修订本条判据），
 *   最近访问的写入仍属 WP-05.S04；
 * - 抽屉词条在四语里齐全且都嵌在 common 下（不新增顶层键）。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/application-drawer.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

const drawer = read('../../src/components/ApplicationDrawer.vue')
const header = read('../../src/components/ConsoleHeader.vue')
const moduleLayout = read('../../src/layout/index.vue')
const workspaceLayout = read('../../src/views/workspace/layout.vue')
const elementPlugins = read('../../src/plugins/element-plus.ts')

/** 取 `<script setup>` 区块，用于断言只在逻辑层成立的约束。 */
function scriptBlock(source: string): string {
  const start = source.indexOf('<script setup')
  const end = source.indexOf('</script>')
  assert.notEqual(start, -1, '组件应包含 script setup 区块')
  return source.slice(start, end)
}

/** 取 `<style>` 区块，用于断言只存在于渲染层的响应式规则。 */
function styleBlock(source: string): string {
  const start = source.indexOf('<style')
  const end = source.indexOf('</style>')
  assert.notEqual(start, -1, '组件应包含样式区块')
  return source.slice(start, end)
}

test('抽屉只挂载在共享顶栏一次，开合真值仍由顶栏持有', () => {
  assert.match(header, /import ApplicationDrawer from '@\/components\/ApplicationDrawer\.vue'/)
  assert.match(header, /<ApplicationDrawer v-if="!props\.moduleOnly" :open="appDrawerOpen" @close="closeAppDrawer" \/>/)
  assert.equal(header.match(/<ApplicationDrawer\b/g)?.length, 1, '仅工作台挂载唯一全局抽屉')
  assert.doesNotMatch(header, /name="drawer"/)
  for (const layout of [moduleLayout, workspaceLayout]) {
    assert.doesNotMatch(layout, /ApplicationDrawer/)
  }
  const body = scriptBlock(drawer)
  assert.match(body, /defineProps<\{ open: boolean \}>\(\)/)
  assert.match(body, /defineEmits<\{ \(event: 'close'\): void \}>\(\)/)
  assert.doesNotMatch(body, /appDrawerOpen|const open = ref\(/)
})

test('分类与分组复用统一导航派生结果，父级标题取真实菜单树节点', () => {
  const body = scriptBlock(drawer)
  assert.match(body, /const \{ modules, pages, menuLoadState \} = useApplicationNavigation\(\)/)
  assert.match(body, /for \(const app of modules\.value\)/)
  assert.match(body, /if \(page\.appId !== app\.id\) continue/)
  assert.match(body, /walk\(permissionStore\.menuTree, null\)/)
  assert.match(body, /directories\.value\.get\(page\.permissionCode\)\?\.parent/)
  assert.match(body, /if \(parent === null \|\| parent === appCode\) return null/)
  assert.match(body, /if \(direct\.length === 0 && groups\.length === 0\) continue/)
  assert.match(body, /menuI18nMap\[node\.permissionCode\] \?\? null/)
  assert.doesNotMatch(body, /permissionCode\.split\(|toRoutePath\(|routePath: '/)
  assert.doesNotMatch(body, /['"]\/admin\//)
})

test('追加要求：抽屉模块、具体菜单与收藏均为真实新标签链接，不替换工作台', () => {
  const body = scriptBlock(drawer)
  assert.match(body, /href: moduleEntryHref\(page\.routePath\)/)
  assert.match(body, /href: leaf\.href/)
  assert.doesNotMatch(body, /router\.push\(/)
  assert.match(drawer, /v-bind="moduleEntryAttrs\(category\.href\)"/)
  assert.match(drawer, /v-bind="moduleEntryAttrs\(hit\.href\)"/)
  assert.match(drawer, /@click="activateHit\(hit\)"/)
  assert.match(drawer, /v-bind="moduleEntryAttrs\(leaf\.href\)"/)
  assert.match(body, /href === null[\s\S]*disabled: 'disabled'/)
  // 弹窗拦截风险与异步开窗仍在禁止范围内；带括号只匹配真实调用，不匹配注释里的说明文字。
  assert.doesNotMatch(drawer, /window\.open\(|<router-link/)
})

test('搜索只在同一份可见集合内本地过滤，不请求额外服务也不放大权限', () => {
  const body = scriptBlock(drawer)
  const block = body.slice(body.indexOf('const searchHits'), body.indexOf('/** 加载与失败态'))
  assert.match(block, /if \(normalizedKeyword\.value === ''\) return \[\]/)
  assert.match(block, /for \(const category of categories\.value\)/)
  assert.match(block, /matches\(category\.title\) \|\| matches\(category\.app\.permissionCode\)/)
  assert.match(block, /matches\(leaf\.title\) \|\| matches\(leaf\.page\.permissionCode\)/)
  assert.doesNotMatch(block, /fetch|axios|request\(|@\/api|loadMenus|retryLoadMenus/)
  // 只允许类型导入：抽屉不直接依赖 API 层，也就不可能在搜索时另发请求。
  for (const line of drawer.match(/^import .*from '@\/api\/.*$/gm) ?? []) {
    assert.ok(line.startsWith('import type'), `抽屉不应运行时引入 API 模块：${line}`)
  }
  assert.doesNotMatch(drawer, /\bfetch\(|axios|XMLHttpRequest/)
})

test('加载、失败、无结果与无应用四种状态文案互不复用，失败态带重试入口', () => {
  const body = scriptBlock(drawer)
  assert.match(body, /const isLoading = computed\(\(\) => menuLoadState\.value === 'idle' \|\| menuLoadState\.value === 'loading'\)/)
  assert.match(body, /const isFailed = computed\(\(\) => menuLoadState\.value === 'failed'\)/)
  assert.match(body, /permissionStore\.retryLoadMenus\(\)/)
  assert.match(drawer, /v-if="isLoading" class="app-drawer__status" role="status">\{\{ t\('common\.loading'\)\s*\}\}/)
  assert.match(drawer, /role="alert">\s*\n\s*<span>\{\{ t\('request\.menuLoadFailed'\)\s*\}\}/)
  assert.match(drawer, /@click="retryMenus"/)
  assert.match(drawer, /<el-empty v-if="searchHits\.length === 0" :description="t\('common\.menuNoMatch'\)" \/>/)
  // WP-05.S03 起「无应用」空态排在收藏区块之后、同处一个 v-else 分支内，因此用 v-if 而非 v-else-if；四种文案仍互不复用。
  assert.match(drawer, /<el-empty v-if="categories\.length === 0" :description="t\('common\.noAccessibleApp'\)" \/>/)
})

test('遮罩、Esc、关闭按钮与焦点归还齐备，窄屏不隐藏关键操作', () => {
  assert.match(drawer, /:model-value="props\.open"/)
  assert.match(drawer, /:close-on-click-modal="true"/)
  assert.match(drawer, /:close-on-press-escape="true"/)
  assert.match(drawer, /:trap-focus="true"/)
  assert.match(drawer, /append-to-body/)
  assert.match(drawer, /size="min\(420px, 100%\)"/)
  assert.match(drawer, /@update:model-value="handleModelValue"/)
  assert.match(drawer, /<el-button @click="closeDrawer">\{\{ t\('common\.close'\)\s*\}\}<\/el-button>/)
  const body = scriptBlock(drawer)
  assert.match(body, /if \(value === false\) closeDrawer\(\)/)
  // WP-05.S03 起同一个 watch 还要按当前身份载入共享偏好，关闭时清搜索词的语义原样保留。
  assert.match(body, /if \(open\) workspaceStore\.loadPreferences\(\)\n\s*else keyword\.value = ''/)
  assert.match(header, /const appsTriggerRef = ref<ComponentPublicInstance \| null>\(null\)/)
  assert.match(header, /ref="appsTriggerRef"/)
  assert.match(header, /watch\(appDrawerOpen, \(open\) => \{[\s\S]*?if \(open\) return[\s\S]*?appsTriggerRef\.value\?\.\$el/)
  assert.doesNotMatch(styleBlock(drawer), /display: none/)
})

test('抽屉只用已全局注册的 Element Plus 组件与图标，不新增依赖或直接引入库', () => {
  assert.doesNotMatch(drawer, /from 'element-plus'|@element-plus\/icons/)
  for (const name of ['ElDrawer', 'ElInput', 'ElEmpty', 'ElButton', 'ElIcon', 'Refresh']) {
    assert.match(elementPlugins, new RegExp(`\\b${name}\\b`), `element-plus 插件未注册 ${name}`)
  }
})

test('抽屉的收藏只渲染当前可见目标，自身不落存储也不合成记录', () => {
  // WP-05.S03 已接入偏好记录：本步判据从「不出现收藏」改为「收藏只来自共享 store 且只渲染可见项」。
  const body = scriptBlock(drawer)
  assert.match(body, /const favoriteTargets = computed\(\(\) => \{/)
  assert.match(body, /if \(target !== undefined\) out\.push\(target\)/)
  assert.match(body, /if \(!targetsById\.value\.has\(id\)\) return/)
  assert.match(drawer, /v-for="target in favoriteTargets"/)
  // 抽屉不持有记录源：既不直接读写存储，也不自建 store，记录只在 stores/workspace 一处。
  assert.doesNotMatch(body, /localStorage|sessionStorage|defineStore/)
  assert.doesNotMatch(body, /recent|最近访问/)
})

test('抽屉词条在四语齐全且仍嵌在 common 下，不新增顶层键', () => {
  const keys = ['applications', 'menuSearch', 'menuSearchPlaceholder', 'menuNoMatch', 'wiki', 'noAccessibleApp']
  const locales = [
    { file: '../../src/locales/zh-CN.ts', cjk: true },
    { file: '../../src/locales/en-US.ts', cjk: false },
    { file: '../../src/locales/ja-JP.ts', cjk: true },
    { file: '../../src/locales/ko-KR.ts', cjk: true },
  ]
  for (const { file, cjk } of locales) {
    const source = read(file)
    const commonStart = source.indexOf('\n  common: {')
    const commonEnd = source.indexOf('\n  }', commonStart)
    assert.ok(commonStart > -1 && commonEnd > commonStart, `${file} 缺少 common 区块`)
    const commonBlock = source.slice(commonStart, commonEnd)
    for (const key of keys) {
      const value = new RegExp(`\\b${key}: '([^']+)'`).exec(commonBlock)?.[1]
      assert.ok(value, `${file} 缺少 common.${key}`)
      assert.notEqual(value.trim(), '', `${file} 的 common.${key} 为空`)
      const isCjk = /[぀-ヿ가-힯一-鿿]/.test(value)
      assert.equal(isCjk, cjk, `${file} 的 common.${key} 不是本地语言文案：${value}`)
    }
  }
})
