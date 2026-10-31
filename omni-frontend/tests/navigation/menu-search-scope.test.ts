/** @module navigation/menu-search-scope 工作台全局与业务模块搜索的真实纯函数边界回归。 */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { filterNavigationSearchPages, type NavigationEntry } from '../../src/utils/application-navigation.ts'

function page(id: number, appId: number, routePath: string | null): NavigationEntry {
  return { id, appId, kind: 'page', permissionCode: `app${appId}:page${id}`, appCode: `app${appId}`,
    routePath, enterable: true, titleKey: null, fallbackTitle: `page${id}`, icon: null, wikiPath: null, sort: id }
}

const entries = Object.freeze([
  Object.freeze(page(1, 42, '/admin/shared/first')),
  Object.freeze(page(2, 84, '/admin/shared/second')),
])

test('工作台搜索保留输入中全部已授权叶子，不追加其他应用或缓存页', () => {
  assert.deepEqual(filterNavigationSearchPages(entries, undefined).map(row => row.id), [1, 2])
})

test('业务搜索按稳定应用编号隔离，不能因相同URL前缀带出其他应用', () => {
  assert.deepEqual(filterNavigationSearchPages(entries, 42).map(row => row.id), [1])
  assert.deepEqual(filterNavigationSearchPages(entries, 84).map(row => row.id), [2])
})

test('归属缺失或非法时拒绝所有菜单，不能回退到工作台全局结果', () => {
  for (const appId of [null, 0, -1, Number.NaN, Number.POSITIVE_INFINITY, 42.5]) {
    assert.deepEqual(filterNavigationSearchPages(entries, appId), [])
  }
})

test('目录、不可进入和没有真实路由的条目都不成为搜索候选', () => {
  const blocked: NavigationEntry[] = [
    { ...entries[0], kind: 'module' }, { ...entries[0], enterable: false },
    { ...entries[0], routePath: null }, { ...entries[0], routePath: '' },
  ]
  assert.deepEqual(filterNavigationSearchPages(blocked, undefined), [])
  assert.deepEqual(filterNavigationSearchPages(blocked, 42), [])
})

test('失权或身份切换立即按新集合计算，不保留旧菜单', () => {
  assert.deepEqual(filterNavigationSearchPages(entries, 42).map(row => row.id), [1])
  assert.deepEqual(filterNavigationSearchPages([entries[1]], 42), [])
  assert.deepEqual(filterNavigationSearchPages([], undefined), [])
})

test('筛选不改变源集合，调用方调整结果数组不会修改其他导航消费者', () => {
  const before = JSON.stringify(entries), result = filterNavigationSearchPages(entries, undefined)
  result.pop()
  assert.equal(JSON.stringify(entries), before)
  assert.equal(entries.length, 2)
})
