import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import vm from 'node:vm'
import type { ComputedRef } from 'vue'
import type { DictOption } from '../../src/api/dict.ts'
import type { ApiResponse } from '../../src/types/api.ts'

const require = createRequire(import.meta.url)
const ts = require('typescript') as typeof import('typescript')
const vue = require('vue') as typeof import('vue')

/** 仅存在于测试中的延迟 API 响应，不发送 HTTP 请求。 */
interface PendingResponse {
  tenant: number | null
  typeCode: string
  complete: () => void
}

/** 实际 composable 的公开返回结构。 */
interface DictOptionsState {
  options: ComputedRef<DictOption[]>
  loading: { value: boolean }
}

/** 从内存测试上下文识别租户，不生成或发送真实 JWT。 */
function tenantFromContext(token: string): number | null {
  const match = /^tenant-(\d+)-/.exec(token)
  return match ? Number(match[1]) : null
}

/**
 * 执行实际 TypeScript 源码和真实 Vue 响应式系统。
 * 仅替换 API、身份 Store 和 JWT 解码依赖，函数体不复制、不改写。
 */
async function createHarness(deferred = false) {
  const sourcePath = new URL('../../src/composables/useDictOptions.ts', import.meta.url)
  const code = ts.transpileModule(readFileSync(sourcePath, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const user = vue.reactive({ token: 'tenant-8-initial' })
  const requests: PendingResponse[] = []
  const context = vm.createContext({ Promise, setTimeout, clearTimeout })

  /** 向 VM 提供显式依赖替身和实际 Vue 导出。 */
  function synthetic(exports: Record<string, unknown>) {
    return new vm.SyntheticModule(Object.keys(exports), function () {
      for (const [name, value] of Object.entries(exports)) this.setExport(name, value)
    }, { context })
  }

  const modules: Record<string, vm.SyntheticModule> = {
    vue: synthetic(vue),
    '@/stores/user': synthetic({ useUserStore: () => user }),
    '@/utils/jwt': synthetic({ getTenantIdFromToken: tenantFromContext }),
    '@/api/dict': synthetic({
      getDictOptions(typeCode: string) {
        const tenant = tenantFromContext(user.token)
        const sequence = requests.length + 1
        const entry: PendingResponse = {
          tenant,
          typeCode,
          complete: () => { throw new Error('测试响应尚未初始化') },
        }
        const promise = new Promise<{ data: ApiResponse<DictOption[]> }>((resolve) => {
          entry.complete = () => resolve({
            data: {
              code: 200,
              message: 'success',
              data: [{ value: String(tenant), label: `controlled-${tenant}-request-${sequence}` }],
            },
          })
        })
        requests.push(entry)
        if (!deferred) entry.complete()
        return promise
      },
    }),
  }
  const source = new vm.SourceTextModule(code, { context, identifier: sourcePath.pathname })
  await source.link((specifier) => {
    assert.ok(modules[specifier], `未配置依赖替身: ${specifier}`)
    return modules[specifier]
  })
  await source.evaluate()
  const namespace = source.namespace as unknown as { useDictOptions: (typeCode: string) => DictOptionsState }
  let scope = vue.effectScope()

  return {
    user,
    requests,
    use: () => {
      if (!scope.active) scope = vue.effectScope()
      return scope.run(() => namespace.useDictOptions('controlled-type'))!
    },
    unmount: () => scope.stop(),
    flush: () => new Promise<void>((resolve) => setTimeout(resolve, 0)),
    close: () => {
      scope.stop()
      for (const request of requests) request.complete()
    },
  }
}

/** 同一租户多个消费者仍复用一个进行中的请求。 */
test('同租户并发消费者复用请求和响应', async (t) => {
  const h = await createHarness(true)
  t.after(h.close)
  const first = h.use()
  const second = h.use()
  assert.equal(h.requests.length, 1)
  h.requests[0].complete()
  await h.flush()
  assert.equal(first.options.value[0]?.value, '8')
  assert.equal(second.options.value[0]?.value, '8')
  assert.equal(first.loading.value, false)
  assert.equal(second.loading.value, false)
})

/** 新租户创建消费者时不能命中上一租户的选项。 */
test('新租户消费者不会复用旧租户缓存', async (t) => {
  const h = await createHarness()
  t.after(h.close)
  h.use()
  await h.flush()
  h.user.token = 'tenant-9-initial'
  const current = h.use()
  await h.flush()
  assert.equal(current.options.value[0]?.value, '9')
  assert.equal(h.requests.length, 2)
})

/** 已挂载消费者也必须立即隐藏旧值并为新租户重新加载。 */
test('已存在消费者跟随租户切换重新加载', async (t) => {
  const h = await createHarness()
  t.after(h.close)
  const current = h.use()
  await h.flush()
  h.user.token = 'tenant-9-initial'
  assert.equal(current.options.value.length, 0)
  await h.flush()
  assert.equal(current.options.value[0]?.value, '9')
  assert.equal(h.requests.length, 2)
})

/** 上一租户晚到的响应不能污染当前视图或删除当前在途请求。 */
test('旧租户晚到响应不会污染新租户请求', async (t) => {
  const h = await createHarness(true)
  t.after(h.close)
  const current = h.use()
  h.user.token = 'tenant-9-initial'
  const second = h.use()
  assert.equal(h.requests.length, 2)
  h.requests[0].complete()
  await h.flush()
  assert.equal(current.options.value.length, 0)
  assert.equal(second.options.value.length, 0)
  assert.equal(second.loading.value, true)
  h.requests[1].complete()
  await h.flush()
  assert.equal(current.options.value[0]?.value, '9')
  assert.equal(second.options.value[0]?.value, '9')
})

/** 退出后不继续展示缓存，也不使用匿名上下文请求字典。 */
test('退出立即隐藏选项并清除会话缓存', async (t) => {
  const h = await createHarness()
  t.after(h.close)
  const current = h.use()
  await h.flush()
  h.user.token = ''
  assert.equal(current.options.value.length, 0)
  h.use()
  await h.flush()
  assert.equal(h.requests.length, 1)
  h.user.token = 'tenant-8-next-login'
  await h.flush()
  assert.equal(current.options.value[0]?.value, '8')
  assert.equal(h.requests.length, 2)
})

/** 退出后的晚到响应不能重新填充已清除的会话缓存。 */
test('退出后的晚到响应不会恢复旧缓存', async (t) => {
  const h = await createHarness(true)
  t.after(h.close)
  const current = h.use()
  h.user.token = ''
  h.requests[0].complete()
  await h.flush()
  assert.equal(current.options.value.length, 0)
  h.user.token = 'tenant-8-next-login'
  const next = h.use()
  assert.equal(h.requests.length, 2)
  assert.equal(next.options.value.length, 0)
  h.requests[1].complete()
  await h.flush()
  assert.equal(next.options.value[0]?.value, '8')
})

/** Token 更新时旧响应不能覆盖新身份上下文的在途请求。 */
test('同租户身份更新仍隔离旧在途响应', async (t) => {
  const h = await createHarness(true)
  t.after(h.close)
  const current = h.use()
  h.user.token = 'tenant-8-refreshed'
  assert.equal(h.requests.length, 2)
  h.requests[0].complete()
  await h.flush()
  assert.equal(current.options.value.length, 0)
  h.requests[1].complete()
  await h.flush()
  assert.equal(current.options.value[0]?.label, 'controlled-8-request-2')
})

/** 最后一个消费者销毁后，退出仍应清除模块级会话缓存。 */
test('消费者全部销毁后退出也清除会话缓存', async (t) => {
  const h = await createHarness()
  t.after(h.close)
  h.use()
  await h.flush()
  h.unmount()
  h.user.token = ''
  h.user.token = 'tenant-8-next-login'
  const current = h.use()
  await h.flush()
  assert.equal(h.requests.length, 2)
  assert.equal(current.options.value[0]?.label, 'controlled-8-request-2')
})
