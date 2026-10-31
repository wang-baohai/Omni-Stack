/**
 * @module navigation/console-header.test
 * @description WP-04.S01 共享顶栏的定向结构测试（node:test，不依赖浏览器、Vue 运行时或 Pinia）。
 *
 * 断言对象是「同一份全局入口」这一完成判据本身：
 * - 模块布局与工作台布局引用同一个 ConsoleHeader，且不再各自渲染顶栏本体；
 * - 布局不复制用户/主题/登出逻辑，账户与主题仍走现有 store；
 * - 仅工作台提供全局应用按钮，侧栏折叠仍归模块布局自己的按钮；
 * - 菜单搜索只在同一份派生页面集合内本地过滤，不额外发请求、不放大权限；
 * - 两种控制台均无首页/Wiki返回入口，业务搜索按真实应用归属拒绝跨模块候选；
 * - 窄屏只收敛用户名与文字标签，不隐藏搜索/语言/主题/账户；
 * - 五个新增词条在四语里齐全且都嵌在 common 下（不新增顶层键）。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/console-header.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

const header = read('../../src/components/ConsoleHeader.vue')
const moduleLayout = read('../../src/layout/index.vue')
const workspaceLayout = read('../../src/views/workspace/layout.vue')

/** 取 `<style>` 区块，用于断言只存在于渲染层的响应式规则。 */
function styleBlock(source: string): string {
  const start = source.indexOf('<style')
  const end = source.indexOf('</style>')
  assert.notEqual(start, -1, '组件应包含样式区块')
  return source.slice(start, end)
}

test('模块布局与工作台布局共用同一份 ConsoleHeader，且不再各自渲染顶栏本体', () => {
  for (const layout of [moduleLayout, workspaceLayout]) {
    assert.match(layout, /import ConsoleHeader from '@\/components\/ConsoleHeader\.vue'/)
    assert.doesNotMatch(layout, /<header class="header/)
  }
  assert.match(header, /<header class="header console-header"/)
})

test('布局不再复制一份用户、主题与登出逻辑', () => {
  for (const layout of [moduleLayout, workspaceLayout]) {
    assert.doesNotMatch(layout, /useUserStore|clearAuthenticatedSession|setTheme|LanguageSelector/)
    assert.doesNotMatch(layout, /<el-dropdown/)
  }
  assert.match(header, /import \{ useAppStore \} from '@\/stores\/app'/)
  assert.match(header, /import \{ useUserStore \} from '@\/stores\/user'/)
  assert.match(header, /import \{ clearAuthenticatedSession \} from '@\/router'/)
  assert.match(header, /appStore\.setTheme\(appStore\.theme === 'dark' \? 'light' : 'dark'\)/)
})

test('工作台全局应用按钮只控制抽屉，模块不提供该按钮，侧栏折叠仍归布局', () => {
  const appsButton = header.slice(header.indexOf('console-header__apps'), header.indexOf('</el-button>'))
  assert.match(appsButton, /@click="toggleAppDrawer"/)
  assert.match(appsButton, /:aria-expanded="appDrawerOpen"/)
  assert.doesNotMatch(appsButton, /toggleSidebar/)
  assert.doesNotMatch(header, /toggleSidebar/)
  assert.match(header, /<el-button\s+v-if="!props\.moduleOnly"[\s\S]*?class="console-header__apps"/)
  assert.match(header, /<ApplicationDrawer v-if="!props\.moduleOnly"/)
  assert.match(moduleLayout, /class="collapse-btn" @click="appStore\.toggleSidebar"/)
})

test('菜单搜索复用同一份导航派生结果，只做本地过滤且不发起额外请求', () => {
  assert.match(header, /import \{ useApplicationNavigation \} from '@\/composables\/useApplicationNavigation'/)
  assert.match(header, /const \{ modules, pages, menuLoadState \} = useApplicationNavigation\(\)/)
  const block = header.slice(header.indexOf('const searchOptions'), header.indexOf('const selectedMenu'))
  assert.match(block, /for \(const page of searchPages\.value\)/)
  assert.match(header, /filterNavigationSearchPages\(pages\.value, props\.moduleOnly \? currentAppId\.value : undefined\)/)
  assert.match(block, /page\.enterable \|\| routePath === null/)
  assert.match(block, /seen\.has\(routePath\)|seen\.add\(routePath\)/)
  assert.doesNotMatch(block, /fetch|axios|request\(|@\/api/)
  assert.doesNotMatch(header, /from '@\/api|fetch\(|axios/)
})

test('搜索的空态文案跟随菜单加载状态，失败与无结果不复用同一句话', () => {
  const block = header.slice(header.indexOf('const searchNoDataText'), header.indexOf('function handleMenuSelect'))
  assert.match(block, /menuLoadState\.value === 'loading'[\s\S]*common\.loading/)
  assert.match(block, /menuLoadState\.value === 'failed'[\s\S]*request\.menuLoadFailed/)
  assert.match(block, /common\.menuNoMatch/)
})

test('控制台不渲染返回首页、工作台或公共Wiki的链接', () => {
  assert.doesNotMatch(header, /wikiPath|<router-link|to="\/wiki|to="\/workspace"/)
  assert.doesNotMatch(workspaceLayout, /goToHome|common\.home|router\.push|<router-link/)
})

test('窄屏只收敛用户名与文字标签，不隐藏搜索、语言、主题与账户', () => {
  const styles = styleBlock(header)
  const hiddenSelectors = [...styles.matchAll(/([^{}\n]+)\{\s*\n?\s*display: none/g)]
    .map((match) => match[1].trim())
    .sort()
  assert.deepEqual(hiddenSelectors, ['.console-header__apps-label', '.console-header__username'])
  assert.match(styles, /@media \(max-width: 768px\)[\s\S]*\.header-right \{\s*\n\s*flex-wrap: wrap/)
  assert.match(styles, /z-index: 1002/)
})

test('五个新增词条在四语齐全且仍嵌在 common 下，不新增顶层键', () => {
  const keys = ['applications', 'menuSearch', 'menuSearchPlaceholder', 'menuNoMatch', 'wiki']
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
