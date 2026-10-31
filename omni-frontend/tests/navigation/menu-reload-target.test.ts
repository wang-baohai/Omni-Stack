import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createMemoryHistory, createRouter } from 'vue-router'
import { resolveMenuReloadTarget } from '../../src/utils/navigation.ts'

/** 使用实际 Router 重现一次菜单首次装载后的重新匹配，不用假解析器模拟地址。 */
test('冷启动重新匹配保留查询数组、空值、编码和章节锚点，且不追加历史', async () => {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/workspace/overview', component: {} }] })
  let menusLoaded = false
  router.beforeEach(to => {
    if (!menusLoaded) { menusLoaded = true; return resolveMenuReloadTarget(to) }
    return true
  })
  const target = '/workspace/overview?status=RUNNING&tag=a&tag=b&blank&text=%E5%AE%A1%E6%89%B9#approvals'
  await router.push(target)
  assert.equal(router.currentRoute.value.fullPath, target)
  assert.deepEqual(router.currentRoute.value.query, { status: 'RUNNING', tag: ['a', 'b'], blank: null, text: '审批' })
  assert.equal(router.currentRoute.value.hash, '#approvals')
  assert.deepEqual(resolveMenuReloadTarget(router.currentRoute.value), {
    path: '/workspace/overview', query: router.currentRoute.value.query, hash: '#approvals', replace: true,
  })
})

test('实际菜单加载守卫使用同一重新匹配入口，不把 fullPath 塞进对象 path', () => {
  const source = readFileSync(new URL('../../src/router/index.ts', import.meta.url), 'utf8')
  assert.match(source, /return resolveMenuReloadTarget\(to\)/)
  assert.doesNotMatch(source, /path: to\.fullPath/)
})
