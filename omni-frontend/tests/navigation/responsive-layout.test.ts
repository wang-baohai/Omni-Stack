/**
 * @module navigation/responsive-layout.test
 * @description WP-04.S05 桌面/窄屏布局与四语文案的定向结构测试（node:test，不依赖浏览器、Vue 运行时或 Pinia）。
 *
 * 断言对象是本步修复的样式契约本身，逐条对应实测到的回归根因：
 * - F1 全局 .header 固定 64px 高且不换行，769–1024px 一段会整页横向外溢 → 顶栏在该断点改为可换行 + 自适应高度；
 * - F2 布局通过默认插槽注入的工作台/模块子导航标题较长（日/韩），窄屏外溢 → 导航与右侧工具允许换行；
 * - F3 搜索结果行的模块名用 float:right 会溢出 112–220px 弹层并挤掉标题 → 改为 flex 两侧各自截断；
 * - F4 el-drawer 正文自身滚动，关闭按钮随列表滚出面板 → 页脚吸附底部保持可见；
 * - F5 搜索命中行的分组名不换行可顶破 420px 面板 → 标题与元数据各自 ellipsis；
 * - F6 日/韩失败文案过长会把重试按钮挤出面板 → 失败态允许换行。
 * 同时锁定未被破坏的既有契约：抽屉尺寸 min(420px, 100%)、遮罩/Esc 关闭与焦点陷阱、
 * 全局入口在 ≤600px 仍不隐藏（只省略账号名与应用按钮文字标签），以及布局文件不再各自复制头部断点。
 * 四语新增 common.appMenuCount 且带 {count} 占位符；真实浏览器视觉与截图结果按原计划归 WP-07，本文件不声称视觉通过。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/responsive-layout.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

const header = read('../../src/components/ConsoleHeader.vue')
const drawer = read('../../src/components/ApplicationDrawer.vue')
const moduleLayout = read('../../src/layout/index.vue')
const workspaceLayout = read('../../src/views/workspace/layout.vue')
const globalStyles = read('../../src/styles/index.scss')
const locales = {
  'zh-CN': read('../../src/locales/zh-CN.ts'),
  'en-US': read('../../src/locales/en-US.ts'),
  'ja-JP': read('../../src/locales/ja-JP.ts'),
  'ko-KR': read('../../src/locales/ko-KR.ts'),
}

/** 取 `<style>` 区块并去掉注释：注释里会描述被禁止的写法（如 float:right），不能参与断言。 */
function cssOf(component: string): string {
  const start = component.indexOf('<style')
  const end = component.indexOf('</style>')
  assert.notEqual(start, -1, '组件应包含样式区块')
  return component.slice(start, end).replace(/\/\*[\s\S]*?\*\//g, '')
}

const headerCss = cssOf(header)
const drawerCss = cssOf(drawer)

/** 取某个选择器在给定作用域内的第一条规则体（按花括号配对，支持嵌套）。 */
function ruleBody(scope: string, selector: string): string {
  const at = scope.indexOf(selector)
  assert.notEqual(at, -1, `缺少选择器：${selector}`)
  const open = scope.indexOf('{', at + selector.length - 1)
  let depth = 0
  for (let i = open; i < scope.length; i += 1) {
    if (scope[i] === '{') depth += 1
    else if (scope[i] === '}') {
      depth -= 1
      if (depth === 0) return scope.slice(open + 1, i)
    }
  }
  assert.fail(`选择器未闭合：${selector}`)
  return ''
}

/** 取 `@media (max-width: Npx)` 整块内容（含嵌套规则）。 */
function mediaBlock(scope: string, maxWidth: number): string {
  return ruleBody(scope, `@media (max-width: ${maxWidth}px)`)
}

/** 断言规则体包含指定声明，报错信息带上选择器便于定位。 */
function has(body: string, declaration: string, where: string): void {
  assert.ok(body.includes(declaration), `${where} 应包含 "${declaration}"`)
}

test('F1 顶栏在 1024px 以下改为可换行的自适应高度，消除整页横向溢出', () => {
  // 全局 .header 保持固定 64px（本步不改共享样式），由顶栏组件在断点内自身覆盖。
  has(ruleBody(globalStyles, '.header'), 'height: 64px', '全局 .header')
  const compact = mediaBlock(headerCss, 1024)
  const body = ruleBody(compact, '.console-header')
  has(body, 'flex-wrap: wrap', '1024 断点 .console-header')
  has(body, 'height: auto', '1024 断点 .console-header')
  has(body, 'min-height: 56px', '1024 断点 .console-header')
  assert.doesNotMatch(body, /overflow:\s*hidden/, '顶栏不得用 overflow:hidden 掩盖外溢')
  assert.doesNotMatch(ruleBody(headerCss, '.console-header'), /overflow:\s*hidden/, '基础 .console-header 不应裁剪内容')
})

test('F2 窄屏顶栏导航与右侧工具换行，全局入口彼此不挤掉', () => {
  const mobile = mediaBlock(headerCss, 768)
  const shell = ruleBody(mobile, '.console-header')
  has(shell, 'flex-wrap: wrap', '768 断点 .console-header')
  has(shell, 'height: auto', '768 断点 .console-header')
  has(ruleBody(shell, '.header-right'), 'flex-wrap: wrap', '768 断点 .header-right')
  // 用户追加移除首页/工作台返回导航；仍要求搜索和账户工具可换行、可操作。
  assert.doesNotMatch(header, /class="console-header__nav"|class="console-header__link"/)
  assert.doesNotMatch(ruleBody(shell, '.header-right'), /overflow:\s*hidden/)
})

test('全局入口在 ≤600px 仍保持可见，只省略账号名与应用按钮文字标签', () => {
  const tiny = mediaBlock(headerCss, 600)
  has(ruleBody(tiny, '.console-header__username'), 'display: none', '600 断点账号名')
  has(ruleBody(tiny, '.console-header__search'), 'width:', '600 断点搜索框')
  // 被隐藏的文本标签本身有无障碍替代：按钮仍带 aria-label，读屏可读出全局入口。
  assert.match(header, /class="console-header__apps"[\s\S]{0,200}:aria-label="t\('common\.applications'\)"/)
  assert.match(header, /:aria-label="t\('common\.menuSearch'\)"/)
  assert.match(header, /:title="t\('theme\.toggle'\)" :aria-label="t\('theme\.toggle'\)"/)
  for (const selector of ['.console-header__search']) {
    assert.doesNotMatch(ruleBody(headerCss, selector), /display:\s*none/, `${selector} 不应整体隐藏`)
  }
  // 精确匹配按钮选择器，不能把允许隐藏的 apps-label 误当成按钮本身。
  assert.doesNotMatch(headerCss, /\.console-header__apps\s*\{[^}]*display:\s*none/s)
})

test('F3 搜索结果行改用 flex 两侧截断，不再用 float:right 溢出弹层', () => {
  assert.doesNotMatch(headerCss, /float:\s*(right|left)/, '顶栏样式不应保留 float 排版')
  const row = ruleBody(headerCss, '.console-header__option')
  has(row, 'display: flex', '搜索结果行')
  has(row, 'min-width: 0', '搜索结果行')
  has(row, 'width: 100%', '搜索结果行')
  const label = ruleBody(headerCss, '.console-header__option-label')
  for (const d of ['min-width: 0', 'overflow: hidden', 'text-overflow: ellipsis', 'white-space: nowrap']) {
    has(label, d, '结果行标题')
  }
  const group = ruleBody(headerCss, '.console-header__option-group')
  for (const d of ['flex: none', 'max-width: 45%', 'margin-left: auto', 'text-overflow: ellipsis']) {
    has(group, d, '结果行模块名')
  }
  assert.match(header, /<span class="console-header__option">[\s\S]*__option-label[\s\S]*__option-group/)
})

test('F4 抽屉关闭按钮吸附面板底部，长列表滚动时仍可见可点', () => {
  const footer = ruleBody(drawerCss, '.app-drawer__footer')
  for (const d of ['position: sticky', 'bottom: 0', 'z-index: 1', 'display: flex', 'justify-content: flex-end', 'background:']) {
    has(footer, d, '抽屉页脚')
  }
  // 滚动交给 el-drawer 正文自身，不用 overflow:hidden 把页脚或按钮裁掉。
  assert.doesNotMatch(ruleBody(drawerCss, '.app-drawer__body'), /overflow:\s*hidden/, '抽屉正文不应裁剪溢出')
  assert.match(drawer, /<div class="app-drawer__footer">\s*<el-button @click="closeDrawer">/)
})

test('F5 抽屉条目两侧各自截断，长文案不顶破 420px 面板', () => {
  const title = ruleBody(drawerCss, '.app-drawer__link-title')
  for (const d of ['flex: 1 1 auto', 'min-width: 0', 'text-overflow: ellipsis', 'white-space: nowrap']) {
    has(title, d, '条目标题')
  }
  const meta = ruleBody(drawerCss, '.app-drawer__link-meta')
  for (const d of ['flex: 0 1 auto', 'min-width: 0', 'margin-left: auto', 'text-overflow: ellipsis']) {
    has(meta, d, '条目分组名')
  }
  has(ruleBody(drawerCss, '.app-drawer__app-count'), 'flex: none', '菜单数徽标')
  has(ruleBody(drawerCss, '.app-drawer__app'), 'display: flex', '模块入口行')
  // 面板宽度与滚动/遮罩契约保持不变（既有抽屉测试依赖同一 size 表达式）。
  assert.match(drawer, /size="min\(420px, 100%\)"/)
  assert.match(drawer, /:close-on-click-modal="true"/)
  assert.match(drawer, /:close-on-press-escape="true"/)
  assert.match(drawer, /:trap-focus="true"/)
})

test('F6 菜单加载失败态允许换行，重试入口不被长文案挤出面板', () => {
  const failed = ruleBody(drawerCss, '.app-drawer__status--failed')
  has(failed, 'flex-wrap: wrap', '失败态')
  has(failed, 'display: flex', '失败态')
  assert.match(drawer, /class="app-drawer__status app-drawer__status--failed" role="alert"/)
  assert.match(drawer, /@click="retryMenus"/)
})

test('布局文件不再各自复制头部断点，顶栏响应式规则单一来源', () => {
  for (const [name, source] of [
    ['src/layout/index.vue', moduleLayout],
    ['src/views/workspace/layout.vue', workspaceLayout],
  ] as const) {
    assert.doesNotMatch(source, /console-header/, `${name} 不应覆写顶栏内部样式`)
    assert.doesNotMatch(source, /max-width: 1024px/, `${name} 不应重复头部断点`)
  }
})

test('四语新增 common.appMenuCount 且带 count 占位符', () => {
  for (const [lang, source] of Object.entries(locales)) {
    const hits = source.match(/appMenuCount: '([^']*)'/g) ?? []
    assert.equal(hits.length, 1, `${lang} 应恰好声明一次 appMenuCount`)
    assert.match(hits[0], /\{count\}/, `${lang} 的 appMenuCount 需带 {count} 占位符`)
    // 词条嵌在 common 下（平台既有顶层键），不新增顶层命名空间。
    assert.doesNotMatch(source, /^ {2}appMenuCount:/m, `${lang} 的 appMenuCount 不应是顶层键`)
  }
  const call = drawer.match(/t\('common\.appMenuCount', \{ count: [^)]*\}\)/g) ?? []
  assert.equal(call.length, 1, '抽屉徽标应使用一次带 count 的本地化文案')
  assert.match(drawer, /:aria-label="t\('common\.appMenuCount', \{ count: category\.leaves\.length \}\)"/)
})
