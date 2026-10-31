/**
 * @module navigation/workspace-overview.test
 * @description WP-05.S01 工作台概览的定向结构测试（node:test，不依赖浏览器、Vue 运行时或 Pinia）。
 *
 * 断言对象是 S01 完成判据本身：
 * - 账户、待办摘要、应用目录、最近访问、收藏五张卡片齐备，且用同一套栅格排布；
 * - 账户信息只取当前登录状态与 JWT 角色，未登录令牌不解角色，也不出现本项目不存在的会员等级或云资源指标；
 * - 应用目录与抽屉共用同一份统一导航派生结果，入口沿用 WP-04.S04 锁定的同源真实 href 新标签方案，
 *   不可解析时退化为禁用项，绝不合成死链；
 * - 待办与个人任务嵌入同一驾驶舱，以同页锚点定位，统计仍由共享 Store 取数；
 * - 最近访问与收藏在 S03 接入身份作用域偏好后只渲染「记录 ID ∩ 当前可见集合」，页面本身不读写存储也不自建记录源
 *   （存储与隔离语义由 workspace-preferences.test.ts 在行为层取证）；访问记录的写入仍属 S04；
 * - 加载中/失败/无可见应用三种状态各自独立，失败态带重试入口；
 * - 无数据账号仍有合理空态，空态文案由页面给定，列表组件不猜语义；
 * - 概览词条在四语里齐全（都嵌在 workspace.overview 下，不新增顶层键，因此 modules.yaml 无需变更）；
 * - 窄屏收成一列并靠折行避免横向溢出。
 *
 * 2026-10-25 用户追加要求统一驾驶舱；旧双子页入口断言由同页实际操作取代，
 * 已授权目录、身份隔离、偏好、失败语义等原有断言继续保留。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/workspace-overview.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

const overview = read('../../src/views/workspace/index.vue')
const entryList = read('../../src/components/workspace/WorkspaceEntryList.vue')
const drawer = read('../../src/components/ApplicationDrawer.vue')
const workspaceLayout = read('../../src/views/workspace/layout.vue')
const zhCN = read('../../src/locales/zh-CN.ts')
const enUS = read('../../src/locales/en-US.ts')
const jaJP = read('../../src/locales/ja-JP.ts')
const koKR = read('../../src/locales/ko-KR.ts')

/** 取 `<script setup>` 区块，用于断言只在逻辑层成立的约束。 */
function scriptBlock(source: string): string {
  const start = source.indexOf('<script setup')
  const end = source.indexOf('</script>')
  assert.notEqual(start, -1, '组件应包含 script setup 区块')
  return source.slice(start, end)
}

/** 取去掉注释的 `<style>` 区块，避免否定断言命中自家说明文字。 */
function styleBlock(source: string): string {
  const start = source.indexOf('<style')
  const end = source.indexOf('</style>')
  assert.notEqual(start, -1, '组件应包含样式区块')
  return source.slice(start, end).replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, '')
}

/** 取 workspace.overview 区块文本，用于逐语言核对键集合。 */
function overviewBlock(localeSource: string): string {
  const start = localeSource.indexOf('    overview: {', localeSource.indexOf('  workspace: {'))
  assert.notEqual(start, -1, 'workspace 下应存在 overview 区块')
  const end = localeSource.indexOf('\n    },', start)
  assert.notEqual(end, -1, 'overview 区块应正确闭合')
  return localeSource.slice(start, end)
}

const OVERVIEW_KEYS = [
  'account',
  'roles',
  'apps',
  'recent',
  'favorites',
  'todo',
  'noRecent',
  'noFavorites',
]

test('概览五张卡片齐备，账户、待办、应用目录、最近访问与收藏共用同一套栅格', () => {
  for (const key of ['account', 'todo', 'apps', 'recent', 'favorites']) {
    assert.match(overview, new RegExp(`t\\('workspace\\.overview\\.${key}'\\)`), `缺少 ${key} 卡片标题`)
  }
  assert.match(overview, /class="ws-overview"/)
  assert.match(overview, /grid-template-columns: repeat\(12, minmax\(0, 1fr\)\)/)
  assert.equal(overview.match(/aria-labelledby="ws-overview-/g)?.length, 5, '每张卡片应有独立的可达标题')
})

test('账户信息只取当前身份：用户名与 JWT 角色来自真实状态，未登录令牌不解角色', () => {
  const body = scriptBlock(overview)
  assert.match(body, /import \{ useUserStore \} from '@\/stores\/user'/)
  assert.match(body, /import \{ getRolesFromToken \} from '@\/utils\/jwt'/)
  assert.match(body, /name: userStore\.username/)
  assert.match(body, /roles: userStore\.token === '' \? \[\] : getRolesFromToken\(userStore\.token\)/)
  assert.match(body, /account\.value\.name\.slice\(0, 1\)\.toUpperCase\(\)/)
  assert.match(overview, /v-if="account\.roles\.length > 0"/)
  // 判据：不存在的会员等级与云资源配额不得出现在概览页里。
  assert.doesNotMatch(overview, /membership|vipLevel|cloudQuota|storageUsed|plan:|tier/i)
})

test('应用目录与抽屉共用同一份派生导航，不另发菜单请求也不放大可见集合', () => {
  const body = scriptBlock(overview)
  // WP-05.S03 起页面还要用 pages 建稳定 ID 索引，因此解构多出 pages。
  assert.match(body, /const \{ modules, pages, pagesOfApp \} = useApplicationNavigation\(\)/)
  assert.match(body, /modules\.value\.map\(\(app\) =>/)
  assert.match(body, /meta: t\('common\.appMenuCount', \{ count: pagesOfApp\(app\.id\)\.length \}\)/)
  assert.match(body, /function titleOf\(titleKey: string \| null, fallback: string\): string \{/)
  assert.match(body, /titleKey !== null && te\(titleKey\) \? t\(titleKey\) : fallback/)
  // 标题解析与抽屉同一份映射，两边都不按权限码首段切分。
  assert.match(drawer, /titleKey !== null && te\(titleKey\) \? t\(titleKey\) : fallback/)
  assert.doesNotMatch(body, /permissionCode\.split\(|toRoutePath\(/)
  for (const line of overview.match(/^import .*from '@\/api\/.*$/gm) ?? []) {
    assert.ok(line.startsWith('import type'), `概览页不应运行时引入 API 模块：${line}`)
  }
  assert.doesNotMatch(body, /\bfetch\(|axios|XMLHttpRequest/)
})

test('模块入口沿用 S04 锁定的同源真实 href 新标签方案，不可解析时退化为禁用项而不是死链', () => {
  const body = scriptBlock(overview)
  assert.match(
    body,
    /const resolved = router\.resolve\(routePath\)\n\s*return resolved\.matched\.length > 0 \? resolved\.href : null/,
  )
  assert.match(body, /href: moduleEntryHref\(app\.routePath\)/)
  assert.match(body, /routePath: app\.routePath/)
  assert.match(entryList, /\{ href: item\.href, target: '_blank', rel: 'noopener noreferrer', title: props\.newTabTitle \}/)
  assert.match(entryList, /:is="item\.href === null \? 'button' : 'a'/)
  assert.match(entryList, /return item\.href === null && item\.routePath === null/)
  assert.match(entryList, /:disabled="entryDisabled\(item\)"/)
  // 不依赖异步开窗，也不为不可解析目标合成占位地址。
  assert.doesNotMatch(entryList, /window\.open\(|href="#"|to="\/"/)
  assert.match(overview, /:new-tab-title="t\('common\.openInNewTab'\)"/)
})

test('审批和任务在同一驾驶舱保留真实组件，入口以同页锚点定位', () => {
  assert.match(overview, /href="#approvals"/)
  assert.match(overview, /href="#my-tasks"/)
  assert.match(overview, /<component :is="WorkspaceApprovals" embedded @changed="refreshStats\('workflow'\)"/)
  assert.match(overview, /<WorkspaceJobs embedded @changed="refreshStats\('jobs'\)"/)
  assert.match(overview, /import\.meta\.glob\('\/src\/views\/workspace\/approvals\/index\.vue'\)/)
  assert.match(overview, /WorkspaceApprovals !== null && permissionStore\.permissions\.some/)
  assert.doesNotMatch(overview, /import .+ from ['"]@\/views\/workspace\/approvals/)
  assert.doesNotMatch(overview, /to="\/workspace\/(approvals|jobs)"/)
  const body = scriptBlock(overview)
  // 统计取数属 S02：本步既不引用统计接口，也不引用统计词条。
  assert.doesNotMatch(body, /getWorkspaceStats|api\/workflow|api\/myJob|workspace\.stats|statValue/)
  assert.doesNotMatch(overview, /<el-statistic/)
})

test('最近访问与收藏读同一份身份作用域记录，与可见集合求交后不留不可见项', () => {
  // WP-05.S03 接入偏好存储：本步判据从「保持写死空集合」改为「只渲染 store 记录与当前可见集合的交集」。
  assert.match(overview, /:empty-text="t\('workspace\.overview\.noRecent'\)"/)
  assert.match(overview, /:empty-text="t\('workspace\.overview\.noFavorites'\)"/)
  assert.match(overview, /:items="recentEntries"/)
  assert.match(overview, /:items="favoriteEntries"/)
  assert.doesNotMatch(overview, /:items="\[\]"/, '两张卡不得再写死空集合')
  const body = scriptBlock(overview)
  assert.match(body, /import \{ useWorkspaceStore \} from '@\/stores\/workspace'/)
  assert.match(body, /for \(const id of workspaceStore\.favoriteIds\)|entriesOf\(workspaceStore\.favoriteIds\)/)
  assert.match(body, /\.map\(\(id\) => visibleEntries\.value\.get\(id\)\)/)
  assert.match(body, /if \(!visibleEntries\.value\.has\(id\)\) return/)
  assert.equal(overview.match(/:favorite-ids="workspaceStore\.favoriteIds"/g)?.length, 3, '三张卡共用同一份收藏集合')
  // 页面不自行落盘也不自建记录源，存储语义只在 store 一处（由 workspace-preferences.test.ts 行为层取证）。
  assert.doesNotMatch(body, /localStorage|sessionStorage|defineStore/)
  assert.doesNotMatch(entryList, /localStorage|sessionStorage|watch\(/)
  // 空态由页面给定文案，组件自身不猜语义。
  assert.match(entryList, /<p v-else class="entry-list__empty">\{\{ props\.emptyText \}\}<\/p>/)
})

test('加载中、加载失败与无可见应用三种状态各自独立，失败态带重试入口', () => {
  const body = scriptBlock(overview)
  assert.match(body, /const isLoading = computed\(\(\) => menuLoadState\.value === 'idle' \|\| menuLoadState\.value === 'loading'\)/)
  assert.match(body, /const isFailed = computed\(\(\) => menuLoadState\.value === 'failed'\)/)
  assert.match(body, /permissionStore\.retryLoadMenus\(\)/)
  assert.match(overview, /v-if="isLoading" class="ws-card__status" role="status">\{\{ t\('common\.loading'\) \}\}/)
  assert.match(overview, /role="alert">\s*\n\s*<span>\{\{ t\('request\.menuLoadFailed'\) \}\}/)
  assert.match(overview, /@click="retryMenus"/)
  assert.match(overview, /:empty-text="t\('common\.noAccessibleApp'\)"/)
  // 空态与失败态不复用同一文案，避免把失败粉饰成无权限。
  assert.ok(overview.indexOf('request.menuLoadFailed') < overview.indexOf('common.noAccessibleApp'))
})

test('概览页挂在既有工作台壳层内，顶栏仍由共享 ConsoleHeader 提供一份', () => {
  assert.match(workspaceLayout, /<router-view \/>/)
  assert.match(workspaceLayout, /<ConsoleHeader>/)
  assert.doesNotMatch(overview, /ConsoleHeader|<el-header|<el-aside|SidebarMenu/)
})

test('概览词条四语齐全且都嵌在 workspace.overview 下，ja/ko 不靠继承掩盖未翻译', () => {
  for (const [name, source] of [['zh-CN', zhCN], ['en-US', enUS], ['ja-JP', jaJP], ['ko-KR', koKR]] as const) {
    const block = overviewBlock(source)
    for (const key of OVERVIEW_KEYS) {
      assert.match(block, new RegExp(`^\\s+${key}: `, 'm'), `${name} 缺少 workspace.overview.${key}`)
    }
  }
  // 不新增顶层键：因此 scaffold/catalog/modules.yaml 无需随之变更。
  for (const source of [zhCN, enUS, jaJP, koKR]) {
    assert.doesNotMatch(source, /^ {2}workspaceOverview:/m)
  }
  // ja/ko 逐键给出本地译文，而不是整块沿用 enUS。
  for (const source of [jaJP, koKR]) {
    const block = overviewBlock(source)
    for (const key of OVERVIEW_KEYS) {
      const line = block.match(new RegExp(`^\\s+${key}: '([^']*)'`, 'm'))?.[1] ?? ''
      assert.ok(line.length > 0, `缺少 ${key} 的本地译文`)
      assert.doesNotMatch(line, /[a-z] [a-z]/, `${key} 译文不应是英文句子：${line}`)
    }
  }
})

test('窄屏收成一列，长用户名与权限码靠折行避免横向溢出', () => {
  const style = styleBlock(overview)
  assert.match(style, /@media \(max-width: 768px\) \{\s*\n\s*\.ws-overview \{\s*\n\s*grid-template-columns: 1fr;/)
  assert.match(style, /\.ws-card \{[\s\S]*?min-width: 0;/)
  assert.match(style, /\.ws-account__name \{[\s\S]*?overflow-wrap: anywhere;/)
  const listStyle = styleBlock(entryList)
  assert.match(listStyle, /\.entry-list__item \{\s*\n\s*min-width: 0;/)
  assert.match(listStyle, /\.entry-list__meta \{[\s\S]*?overflow-wrap: anywhere;/)
  assert.match(listStyle, /\.entry-list__link\[disabled\]/)
})
