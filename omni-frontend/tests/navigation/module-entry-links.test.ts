/**
 * @module navigation/module-entry-links.test
 * WP-04.S04 模块入口新标签与内部当前标签的定向结构测试（node:test，不依赖浏览器、Vue 运行时或 Pinia）。
 *
 * 断言对象是本步判据本身：
 * - 模块入口的 href 由 router.resolve 对真实路由派生（同源路径），并加 rel=noopener noreferrer，
 *   因此 Ctrl/中键/复制链接是浏览器原生行为，不依赖点击后异步 window.open；
 * - 入口目标只含路由路径，源码里不存在令牌、Cookie 或认证暂存值进入链接；
 * - 不可解析的模块入口退化为无跳转按钮，不合成 href="#" 之类死链；
 * - 用户追加后抽屉全部业务目标进入新标签，模块侧栏和本模块搜索仍留在当前标签；
 * - 业务标签和工作台均无返回首页/Wiki入口，业务页搜索只允许本应用；
 * - 新标签提示词条在四语里齐全且都嵌在 common 下（不新增顶层键）。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/module-entry-links.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

const drawer = read('../../src/components/ApplicationDrawer.vue')
const sidebar = read('../../src/components/SidebarMenu.vue')
const header = read('../../src/components/ConsoleHeader.vue')
const layout = read('../../src/layout/index.vue')
const workspaceLayout = read('../../src/views/workspace/layout.vue')

/** 取源码里两个标记之间的片段，用于把断言限定在同一个函数或模板块内。 */
function between(source: string, start: string, end: string, label: string): string {
  const from = source.indexOf(start)
  const to = source.indexOf(end, from)
  assert.ok(from > -1 && to > from, `源码缺少${label}片段`)
  return source.slice(from, to)
}

test('模块入口 href 只由真实路由解析派生，不可解析时不合成目标', () => {
  const block = between(drawer, 'function moduleEntryHref', '/** 分类与分组由派生集合', '模块入口派生')
  assert.match(block, /const resolved = router\.resolve\(routePath\)/)
  assert.match(block, /resolved\.matched\.length > 0 \? resolved\.href : null/)
  assert.match(drawer, /href: moduleEntryHref\(app\.routePath\)/)
})

test('模块入口渲染同源新标签链接并带 rel 与提示，交给浏览器原生行为', () => {
  const block = between(drawer, 'function moduleEntryAttrs', '/** 关闭后清空搜索词', '入口链接属性')
  assert.match(block, /\{ href, target: '_blank', rel: 'noopener noreferrer', title: t\('common\.openInNewTab'\) \}/)
  assert.match(block, /href === null[\s\S]*\{ type: 'button', disabled: 'disabled' \}/)
  assert.match(drawer, /:is="category\.href === null \? 'button' : 'a'"/)
  assert.match(drawer, /v-bind="moduleEntryAttrs\(category\.href\)"/)
  assert.match(drawer, /:is="hit\.href === null \? 'button' : 'a'"/)
})

test('原生应用入口不异步开窗，菜单搜索仅在同步用户操作中打开已解析目标', () => {
  for (const source of [drawer, sidebar, layout, workspaceLayout]) {
    assert.doesNotMatch(source, /window\.open\(/)
  }
  const search = between(header, 'function handleMenuSelect', '</script>', '同步菜单搜索')
  assert.doesNotMatch(search, /\bawait\b|setTimeout|\.then\(/)
  assert.match(search, /if \(resolved\.matched\.length === 0\) return/)
  assert.match(search, /window\.open\(resolved\.href, '_blank', 'noopener,noreferrer'\)/)
})

test('入口链接不携带令牌、Cookie 或认证暂存值', () => {
  assert.doesNotMatch(drawer, /token/i)
  assert.doesNotMatch(drawer, /cookie/i)
  assert.doesNotMatch(drawer, /authorization/i)
  assert.doesNotMatch(drawer, /localStorage/)
  assert.doesNotMatch(drawer, /[?&](token|access_token|captchaKey)=/)
  assert.doesNotMatch(drawer, /href="#"/)
})

test('追加要求：抽屉全部业务目标使用统一新标签属性，原工作台标签保留', () => {
  const blanks = [...drawer.matchAll(/_blank/g)].length
  assert.equal(blanks, 1, '抽屉新标签属性由单一构造入口维护')
  const block = between(drawer, 'function activateHit', '/** 模块入口链接属性', '命中分派')
  assert.match(block, /if \(hit\.href !== null\) closeDrawer\(\)/)
  assert.match(drawer, /v-bind="moduleEntryAttrs\(leaf\.href\)"/)
  assert.doesNotMatch(drawer, /router\.push\(/)
})

test('侧栏和面包屑仍在当前模块；业务搜索再次核对归属，工作台搜索才开新标签', () => {
  for (const source of [sidebar, layout, workspaceLayout]) {
    assert.doesNotMatch(source, /_blank/)
    assert.doesNotMatch(source, /window\.open\(/)
  }
  assert.match(layout, /<el-menu :key="menuKey" :default-active="route\.path" :collapse="appStore\.sidebarCollapsed" router>/)
  assert.match(header, /void router\.push\(hit\.routePath\)/)
  assert.match(header, /if \(props\.moduleOnly\) \{\s*if \(resolved\.meta\.appId !== currentAppId\.value\) return/)
  assert.match(sidebar, /:index="pageOf\(node\.permissionCode\)\?\.routePath \?\? ''"/)
})

test('工作台和业务标签均无首页或Wiki入口，业务页不挂载全局应用抽屉', () => {
  assert.match(layout, /<ConsoleHeader module-only>/)
  assert.doesNotMatch(layout, /router\.push\('\/'\)|common\.home|to="\/workspace"/)
  assert.doesNotMatch(header, /<router-link|wikiPath|to="\/workspace"/)
  assert.doesNotMatch(workspaceLayout, /goToHome|router\.push\('\/'\)|common\.home|<router-link/)
  assert.match(header, /<ApplicationDrawer v-if="!props\.moduleOnly"/)
  assert.match(header, /if \(props\.moduleOnly\) return\s*appDrawerOpen\.value/)
})

test('新标签提示词条在四语齐全且仍嵌在 common 下，不新增顶层键', () => {
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
    const matches = [...commonBlock.matchAll(/\bopenInNewTab: '([^']+)'/g)]
    assert.equal(matches.length, 1, `${file} 的 common.openInNewTab 应恰好出现一次`)
    const value = matches[0][1]
    assert.notEqual(value.trim(), '', `${file} 的 common.openInNewTab 为空`)
    const isCjk = /[぀-ヿ가-힯一-鿿]/.test(value)
    assert.equal(isCjk, cjk, `${file} 的 common.openInNewTab 不是本地语言文案：${value}`)
  }
})
