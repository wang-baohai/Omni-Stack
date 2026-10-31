/**
 * @module navigation/application-navigation.test
 * @description `application-navigation.ts` 纯导航转换/装饰函数的行为定向测试（node:test，不依赖浏览器、路由单例、Pinia 或 i18n 实例）。
 *
 * 特意置于 omni-frontend/tests/navigation/ 下（不在 src/），以便：
 * - 与既有 tests/wp012、tests/workflow、tests/base 一同被 `test:unit` 发现；
 * - 用 node --experimental-strip-types 运行：视图可解析性经 resolver 参数注入固定集合，
 *   无需 import router/index.ts（其 createWebHistory 在模块加载即依赖浏览器环境）。
 *
 * 断言针对「输出结构与取值」而非「实现函数被调用」：归属跟随目录祖先、真实 routePath 由
 * permissionCode 派生、默认叶子不合成 overview、四类排除分门别类、稳定排序、同名不同域不合并、
 * 纯函数确定且无内部缓存（失权即换树结果随之变化）、绝不就地修改输入树。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/application-navigation.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  buildApplicationNavigation,
  decorateNavigation,
} from '../../src/utils/application-navigation.ts'
import type { MenuNode } from '../../src/api/menu.ts'

/** 构造完整 MenuNode：仅覆盖调用方读取的字段，其余给中性默认值。 */
function node(
  fields: Partial<MenuNode> & Pick<MenuNode, 'id' | 'permissionCode' | 'type'>,
): MenuNode {
  return {
    parentId: 0,
    permissionName: fields.permissionName ?? fields.permissionCode,
    path: '',
    depth: 0,
    sort: 0,
    status: 1,
    children: [],
    ...fields,
  }
}

/** 固定可解析集合：命中即返回组件（true），与生产 resolveViewComponent!==undefined 语义一致。 */
function resolverFor(codes: readonly string[]): (permissionCode: string) => boolean {
  const set = new Set(codes)
  return (permissionCode) => set.has(permissionCode)
}

/** 深拷贝（用于断言输入未被就地修改）。 */
function clone<T>(value: T): T {
  return structuredClone(value)
}

test('空树：不产出模块、页面与任何排除分类', () => {
  const result = buildApplicationNavigation([], () => true)

  assert.deepEqual(result.modules, [])
  assert.deepEqual(result.allPages, [])
  assert.deepEqual(result.exclusions, {
    noComponent: [],
    portalHidden: [],
    emptyDirectory: [],
    ignoredType: [],
  })
})

test('混合目录：归属跟随目录祖先、routePath 由 permissionCode 派生、稳定排序逐层生效', () => {
  const tree: MenuNode[] = [
    node({
      id: 10,
      permissionCode: 'monitor',
      type: 'DIRECTORY',
      sort: 2,
      children: [
        node({
          id: 11,
          permissionCode: 'monitor:log',
          type: 'DIRECTORY',
          sort: 1,
          children: [
            node({ id: 12, permissionCode: 'base:operlog', type: 'MENU', sort: 5 }),
            node({ id: 13, permissionCode: 'base:auditlog', type: 'MENU', sort: 3 }),
          ],
        }),
      ],
    }),
    node({
      id: 20,
      permissionCode: 'system',
      type: 'DIRECTORY',
      sort: 1,
      children: [node({ id: 21, permissionCode: 'system:user', type: 'MENU', sort: 1 })],
    }),
  ]
  const resolver = resolverFor(['base:operlog', 'base:auditlog', 'system:user'])
  const result = buildApplicationNavigation(tree, resolver, {
    titleMap: { monitor: 'common.monitorManagement', 'base:operlog': 'common.operLogs' },
  })

  // 顶层按 sort 稳定排序：system(1) 先于 monitor(2)
  assert.deepEqual(result.modules.map((m) => m.permissionCode), ['system', 'monitor'])

  const monitor = result.modules.find((m) => m.permissionCode === 'monitor')
  assert.ok(monitor)
  assert.equal(monitor?.leafCount, 2)
  // 分组内页面按 sort 稳定排序：auditlog(3) 先于 operlog(5)
  const group = monitor?.groups[0]
  assert.equal(group?.permissionCode, 'monitor:log')
  assert.deepEqual(group?.pages.map((p) => p.permissionCode), ['base:auditlog', 'base:operlog'])

  const operlog = result.allPages.find((p) => p.permissionCode === 'base:operlog')
  assert.ok(operlog)
  assert.equal(operlog?.appId, 10)
  assert.equal(operlog?.appCode, 'monitor')
  assert.equal(operlog?.routePath, '/admin/base/operlog')
  assert.deepEqual(operlog?.groupPath, ['monitor:log'])
  assert.equal(operlog?.titleKey, 'common.operLogs')

  const direct = result.allPages.find((p) => p.permissionCode === 'system:user')
  assert.ok(direct)
  assert.equal(direct?.appCode, 'system')
  assert.deepEqual(direct?.groupPath, [])
  // 未登记标题的叶子：titleKey 为 null，交由消费方回退 permissionName，绝不伪造
  assert.equal(direct?.titleKey, null)
  assert.equal(direct?.fallbackTitle, 'system:user')
})

test('无组件的 MENU 被分类登记进 noComponent 且不进入可点击页面', () => {
  const tree: MenuNode[] = [
    node({
      id: 30,
      permissionCode: 'crm',
      type: 'DIRECTORY',
      children: [
        node({ id: 31, permissionCode: 'crm:lead', type: 'MENU' }),
        node({ id: 32, permissionCode: 'crm:ghost', type: 'MENU' }),
      ],
    }),
  ]
  const result = buildApplicationNavigation(tree, resolverFor(['crm:lead']))

  assert.deepEqual(result.exclusions.noComponent, ['crm:ghost'])
  assert.deepEqual(result.allPages.map((p) => p.permissionCode), ['crm:lead'])
})

test('内部控制台不可见门户项优先于可解析性被登记进 portalHidden', () => {
  const tree: MenuNode[] = [
    node({
      id: 40,
      permissionCode: 'srm',
      type: 'DIRECTORY',
      children: [node({ id: 41, permissionCode: 'srm:portal:profile', type: 'MENU' })],
    }),
  ]
  // 即便门户项在 resolver 命中（视图存在），仍按前缀排除且不进入页面集合
  const result = buildApplicationNavigation(tree, () => true)

  assert.deepEqual(result.exclusions.portalHidden, ['srm:portal:profile'])
  assert.deepEqual(result.allPages, [])
})

test('非 DIRECTORY/MENU 类型节点被登记进 ignoredType 且标注类型', () => {
  const tree: MenuNode[] = [
    node({
      id: 50,
      permissionCode: 'system',
      type: 'DIRECTORY',
      children: [node({ id: 51, permissionCode: 'system:user:add', type: 'BUTTON' })],
    }),
  ]
  const result = buildApplicationNavigation(tree, () => true)

  assert.deepEqual(result.exclusions.ignoredType, ['system:user:add:BUTTON'])
})

test('排除后无有效叶子的目录登记空目录且不生成可点空卡片（缺模块）', () => {
  const tree: MenuNode[] = [
    node({
      id: 60,
      permissionCode: 'empty',
      type: 'DIRECTORY',
      children: [node({ id: 61, permissionCode: 'empty:orphan', type: 'MENU' })],
    }),
  ]
  const result = buildApplicationNavigation(tree, () => false)

  assert.deepEqual(result.modules, [])
  assert.ok(result.exclusions.noComponent.includes('empty:orphan'))
  assert.ok(result.exclusions.emptyDirectory.includes('empty'))
})

test('同名不同域：两个模块下的相同展示名各自保留，绝不按名称跨模块合并', () => {
  const tree: MenuNode[] = [
    node({
      id: 70,
      permissionCode: 'crm',
      type: 'DIRECTORY',
      children: [node({ id: 71, permissionCode: 'crm:contact', type: 'MENU', permissionName: '联系人' })],
    }),
    node({
      id: 80,
      permissionCode: 'srm',
      type: 'DIRECTORY',
      sort: 1,
      children: [node({ id: 81, permissionCode: 'srm:contact', type: 'MENU', permissionName: '联系人' })],
    }),
  ]
  const result = buildApplicationNavigation(tree, resolverFor(['crm:contact', 'srm:contact']))

  assert.equal(result.allPages.length, 2)
  const names = result.allPages.map((p) => p.fallbackTitle)
  assert.deepEqual(names, ['联系人', '联系人'])
  assert.deepEqual(result.allPages.map((p) => p.appCode).sort(), ['crm', 'srm'])
  assert.notEqual(
    result.allPages[0]?.routePath,
    result.allPages[1]?.routePath,
  )
})

test('默认入口指向真实叶子，绝不合成 /overview；并列 sort 取最小 id', () => {
  const tree: MenuNode[] = [
    node({
      id: 90,
      permissionCode: 'monitor',
      type: 'DIRECTORY',
      children: [
        node({ id: 92, permissionCode: 'base:operlog', type: 'MENU', sort: 3 }),
        node({ id: 91, permissionCode: 'base:auditlog', type: 'MENU', sort: 3 }),
      ],
    }),
  ]
  const decorated = decorateNavigation(
    buildApplicationNavigation(tree, resolverFor(['base:operlog', 'base:auditlog'])),
  )

  assert.equal(decorated.modules.length, 1)
  const monitorEntry = decorated.modules[0]
  // sort 并列（都=3）→ 取 id 最小者 91 → base:auditlog
  assert.equal(monitorEntry?.permissionCode, 'monitor')
  assert.equal(monitorEntry?.routePath, '/admin/base/auditlog')
  assert.equal(monitorEntry?.enterable, true)
  assert.ok(!decorated.modules.some((m) => m.routePath?.endsWith('/overview')))
})

test('装饰器：图标按注入解析、未登记返回 null；无 wikiResolver 时 wikiPath 为 null 不伪造入口', () => {
  const tree: MenuNode[] = [
    node({
      id: 100,
      permissionCode: 'monitor',
      type: 'DIRECTORY',
      children: [
        node({ id: 101, permissionCode: 'base:operlog', type: 'MENU' }),
        node({ id: 102, permissionCode: 'base:mqmessage', type: 'MENU' }),
      ],
    }),
  ]
  const result = buildApplicationNavigation(tree, resolverFor(['base:operlog', 'base:mqmessage']))
  const decorated = decorateNavigation(result, {
    iconResolver: (code) => (code === 'base:operlog' ? 'Tickets' : null),
  })

  const operlog = decorated.pages.find((p) => p.permissionCode === 'base:operlog')
  const mq = decorated.pages.find((p) => p.permissionCode === 'base:mqmessage')
  assert.equal(operlog?.icon, 'Tickets')
  assert.equal(mq?.icon, null)
  // 未提供 wikiResolver：不得合成假 Wiki 入口
  assert.equal(operlog?.wikiPath, null)
  for (const entry of [...decorated.pages, ...decorated.modules]) {
    assert.equal(entry.wikiPath, null)
  }
})

test('纯函数确定性且无内部缓存：同输入两次结果深相等；换树（失权）结果即时随之变化且不累加', () => {
  const tree: MenuNode[] = [
    node({
      id: 110,
      permissionCode: 'monitor',
      type: 'DIRECTORY',
      children: [node({ id: 111, permissionCode: 'base:operlog', type: 'MENU' })],
    }),
    node({
      id: 120,
      permissionCode: 'system',
      type: 'DIRECTORY',
      sort: 1,
      children: [node({ id: 121, permissionCode: 'system:user', type: 'MENU' })],
    }),
  ]
  const resolver = resolverFor(['base:operlog', 'system:user'])

  const first = buildApplicationNavigation(tree, resolver)
  const second = buildApplicationNavigation(tree, resolver)
  // 确定性 + 无跨调用累加（排除表不会在两次调用间增长）
  assert.deepEqual(first, second)
  assert.equal(first.exclusions.noComponent.length, second.exclusions.noComponent.length)

  // 模拟失权：仅移除 system 分支后重新派生，集合即时变化、不复用旧账号结果
  const afterLoss = buildApplicationNavigation(tree.filter((n) => n.permissionCode !== 'system'), resolver)
  assert.deepEqual(afterLoss.modules.map((m) => m.permissionCode), ['monitor'])
  assert.deepEqual(afterLoss.allPages.map((p) => p.permissionCode), ['base:operlog'])
})

test('绝不就地修改输入树：转换前后深相等', () => {
  const tree: MenuNode[] = [
    node({
      id: 130,
      permissionCode: 'monitor',
      type: 'DIRECTORY',
      sort: 5,
      children: [
        node({ id: 132, permissionCode: 'base:operlog', type: 'MENU', sort: 2 }),
        node({ id: 131, permissionCode: 'base:auditlog', type: 'MENU', sort: 1 }),
      ],
    }),
  ]
  const snapshot = clone(tree)

  buildApplicationNavigation(tree, resolverFor(['base:operlog', 'base:auditlog']))

  assert.deepEqual(tree, snapshot)
})
