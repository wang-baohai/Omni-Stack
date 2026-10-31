/**
 * @module navigation/module-sidebar.test
 * WP-04.S03 模块侧栏与面包屑的定向结构测试（node:test，不依赖浏览器、Vue 运行时或 Pinia）。
 *
 * 断言对象是「侧栏只接收当前模块的菜单」这一完成判据本身：
 * - 模块归属只按 route.meta.appId 与菜单根稳定 ID 匹配，不用权限码前缀猜目录；
 * - 侧栏可见性、标题、图标与路径取自与卡片/抽屉/搜索同源的派生页面集合，未派生即不渲染；
 * - 工作台壳层不保留整棵管理侧栏；模块中立落点只保留自己的单个入口；
 * - 选中态与面包屑都由真实路由计算，深链刷新可定位，模块切换重建菜单；
 * - 折叠是布局自己的独立控件且可被键盘操作，应用抽屉按钮不复用它；
 * - 两个新增词条在四语里齐全且都嵌在 common 下（不新增顶层键）。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/module-sidebar.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

const layout = read('../../src/layout/index.vue')
const sidebar = read('../../src/components/SidebarMenu.vue')
const workspaceLayout = read('../../src/views/workspace/layout.vue')
const header = read('../../src/components/ConsoleHeader.vue')

/** 取源码里两个标记之间的片段，用于把断言限定在同一个 computed 内。 */
function between(source: string, start: string, end: string, label: string): string {
  const from = source.indexOf(start)
  const to = source.indexOf(end, from)
  assert.ok(from > -1 && to > from, `源码缺少${label}片段`)
  return source.slice(from, to)
}

test('模块归属只按 appId 匹配菜单根，不按权限码前缀猜目录', () => {
  assert.match(layout, /const currentAppId = computed<number \| null>\(\(\) => route\.meta\.appId \?\? null\)/)
  assert.match(layout, /permissionStore\.menuTree\.find\(\(node\) => node\.id === appId\)/)
  for (const source of [layout, sidebar]) {
    assert.doesNotMatch(source, /\.split\(':'\)/)
    assert.doesNotMatch(source, /permissionCode\.startsWith\(/)
    assert.doesNotMatch(source, /srm:portal:/)
  }
})

test('侧栏只把当前模块子树交给递归菜单，不再吃整棵菜单树', () => {
  assert.match(layout, /<SidebarMenu :nodes="moduleNodes" :visible-pages="visiblePages" \/>/)
  assert.doesNotMatch(layout, /:nodes="permissionStore\.menuTree"/)
  const block = between(layout, 'const moduleNodes', '/** 与卡片/抽屉/搜索同源', '模块子树')
  assert.match(block, /if \(root === null\) return \[\]/)
  assert.match(block, /root\.type === 'MENU' \? \[root\] : root\.children/)
})

test('侧栏项的可见性与路径取自同一份派生页面集合，未派生即不渲染', () => {
  assert.match(layout, /import \{ useApplicationNavigation \} from '@\/composables\/useApplicationNavigation'/)
  assert.match(header, /import \{ useApplicationNavigation \} from '@\/composables\/useApplicationNavigation'/)
  assert.match(sidebar, /import type \{ NavigationEntry \} from '@\/utils\/application-navigation'/)
  const block = between(layout, 'const visiblePages', '/** 模块或落点变化时重建', '派生页面集合')
  assert.match(block, /new Map\(pagesOfApp\(appId\)\.map\(\(page\) => \[page\.permissionCode, page\]\)\)/)
  assert.match(block, /if \(appId === null\) return new Map/)
  assert.match(sidebar, /visiblePages: ReadonlyMap<string, NavigationEntry>/)
  assert.match(sidebar, /v-else-if="node\.type === 'MENU' && visiblePages\.has\(node\.permissionCode\)"/)
  assert.match(sidebar, /:index="pageOf\(node\.permissionCode\)\?\.routePath \?\? ''"/)
  assert.doesNotMatch(sidebar, /\/admin\//)
})

test('目录只在子树含可进入叶子时渲染，空目录不留下不可点分组', () => {
  const block = between(sidebar, 'function hasVisibleLeaf', '/** 菜单项标题', '可见叶子判定')
  assert.match(block, /node\.type === 'MENU'\) return props\.visiblePages\.has\(node\.permissionCode\)/)
  assert.match(block, /node\.children\.some\(hasVisibleLeaf\)/)
  assert.match(sidebar, /v-if="node\.type === 'DIRECTORY' && hasVisibleLeaf\(node\)"/)
})

test('深链刷新靠真实路由选中，模块切换用菜单 key 重建而不残留上个模块', () => {
  assert.match(layout, /<el-menu :key="menuKey" :default-active="route\.path"/)
  assert.match(layout, /const menuKey = computed\(\(\) => `module-menu-\$\{currentAppId\.value \?\? 'landing'\}`\)/)
})

test('面包屑只读真实路由 meta 的祖先链，未登记不渲染、目录不合成死链', () => {
  assert.match(layout, /const breadcrumbs = computed<readonly RouteBreadcrumbItem\[\]>\(\(\) => route\.meta\.breadcrumb \?\? \[\]\)/)
  assert.match(layout, /<el-breadcrumb v-if="breadcrumbs\.length > 0"/)
  assert.match(layout, /<el-breadcrumb-item v-for="item in breadcrumbs" :key="item\.code">/)
  assert.match(layout, /item\.titleKey !== null && te\(item\.titleKey\) \? t\(item\.titleKey\) : item\.fallbackTitle/)
  const block = between(layout, '<el-breadcrumb v-if', '</el-breadcrumb>', '面包屑渲染')
  assert.doesNotMatch(block, /:to=/)
})

test('Element Plus 未全局注册面包屑，本布局局部引入而不改全局装配', () => {
  assert.match(layout, /import \{ ElBreadcrumb, ElBreadcrumbItem \} from 'element-plus'/)
  const plugin = read('../../src/plugins/element-plus.ts')
  assert.doesNotMatch(plugin, /ElBreadcrumb/)
})

test('管理首页仅保留自己的入口，错误页不继承兜底，折叠控件可键盘操作', () => {
  assert.match(layout, /<el-menu-item v-if="currentAppId === null && route\.name === 'Dashboard'" :index="DASHBOARD_PATH">/)
  assert.match(layout, /<el-button text :aria-label="collapseLabel" class="collapse-btn" @click="appStore\.toggleSidebar">/)
  assert.match(layout, /t\(appStore\.sidebarCollapsed \? 'common\.expandSidebar' : 'common\.collapseSidebar'\)/)
  assert.doesNotMatch(header, /toggleSidebar/)
})

test('工作台壳层仅有统一驾驶舱，没有业务侧栏或三个子页切换', () => {
  assert.doesNotMatch(workspaceLayout, /el-aside|SidebarMenu|permissionStore|menuTree/)
  assert.match(workspaceLayout, /import ConsoleHeader from '@\/components\/ConsoleHeader\.vue'/)
  assert.match(workspaceLayout, /<router-view \/>/)
  assert.doesNotMatch(workspaceLayout, /to="\/workspace\/(approvals|jobs)"|goToHome|to="\/"/)
})

test('两个新增词条在四语齐全且仍嵌在 common 下，不新增顶层键', () => {
  const keys = ['collapseSidebar', 'expandSidebar']
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
      const matches = [...commonBlock.matchAll(new RegExp(`\\b${key}: '([^']+)'`, 'g'))]
      assert.equal(matches.length, 1, `${file} 的 common.${key} 应恰好出现一次`)
      const value = matches[0][1]
      assert.notEqual(value.trim(), '', `${file} 的 common.${key} 为空`)
      const isCjk = /[぀-ヿ가-힯一-鿿]/.test(value)
      assert.equal(isCjk, cjk, `${file} 的 common.${key} 不是本地语言文案：${value}`)
    }
  }
})
