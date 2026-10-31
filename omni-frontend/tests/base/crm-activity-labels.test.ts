/** CRM 默认界面枚举切换语言，同时保留租户自定义字典内容。 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import { computed } from 'vue'
import { createI18n } from 'vue-i18n'
import { localizeCrmActivityOptions } from '../../src/utils/crm-activity-labels.ts'

const require = createRequire(import.meta.url)
const ts = require('typescript') as typeof import('typescript')
const context = vm.createContext({ Object, Array, String, Number, Boolean, Symbol, JSON, Reflect, Promise, TypeError })
const modules = new Map<string, vm.SourceTextModule>()

/** 按真实继承关系加载语言模块，兼容Vite源码省略扩展名的导入，不改写模块正文。 */
function localeModule(name: string): vm.SourceTextModule {
  const cached = modules.get(name)
  if (cached) return cached
  const source = readFileSync(new URL(`../../src/locales/${name}.ts`, import.meta.url), 'utf8')
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const mod = new vm.SourceTextModule(code, { context, identifier: name })
  modules.set(name, mod)
  return mod
}

const messages: Record<string, Record<string, unknown>> = {}
for (const name of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
  const mod = localeModule(name)
  await mod.link((specifier) => {
    assert.match(specifier, /^\.\/[a-z]{2}-[A-Z]{2}$/)
    return localeModule(specifier.slice(2))
  })
  await mod.evaluate()
  messages[name] = (mod.namespace as unknown as { default: Record<string, unknown> }).default
}

for (const [locale, completed, customer, call] of [
  ['zh-CN', '已完成', '客户', '电话'],
  ['en-US', 'Completed', 'Customer', 'Call'],
  ['ja-JP', '完了', '顧客', '電話'],
  ['ko-KR', '완료', '고객', '전화'],
]) {
  test(`${locale} 显示实际语言资源中的状态、对象及活动类型`, () => {
    const i18n = createI18n({ legacy: false, locale, messages })
    const t = (key: string) => String(i18n.global.t(key))
    assert.equal(localizeCrmActivityOptions([{ value: 'COMPLETED', label: '已完成' }], 'crm_activity_status', t)[0]?.label, completed)
    assert.equal(localizeCrmActivityOptions([{ value: 'CUSTOMER', label: '客户' }], 'crm_root_type', t)[0]?.label, customer)
    assert.equal(localizeCrmActivityOptions([{ value: 'CALL', label: '电话' }], 'crm_activity_type', t)[0]?.label, call)
  })
}

test('保留自定义标签、未知代码、顺序和值，不修改共享字典缓存', () => {
  const input = Object.freeze([
    Object.freeze({ value: 'CALL', label: '专属客户回访' }),
    Object.freeze({ value: 'CUSTOM', label: '业务自定义活动' }),
    Object.freeze({ value: 'VISIT', label: '拜访' }),
  ])
  const i18n = createI18n({ legacy: false, locale: 'en-US', messages })
  const result = localizeCrmActivityOptions(input, 'crm_activity_type', (key) => String(i18n.global.t(key)))
  assert.deepEqual(result, [
    { value: 'CALL', label: '专属客户回访' },
    { value: 'CUSTOM', label: '业务自定义活动' },
    { value: 'VISIT', label: 'Visit' },
  ])
  assert.equal(input[2]?.label, '拜访')
})

test('语言切换立即重算显示，不重新请求或改写租户选项', () => {
  const i18n = createI18n({ legacy: false, locale: 'en-US', messages })
  const input = [{ value: 'PLANNED', label: '计划中' }]
  const display = computed(() => localizeCrmActivityOptions(input, 'crm_activity_status', (key) => String(i18n.global.t(key))))
  assert.equal(display.value[0]?.label, 'Planned')
  i18n.global.locale.value = 'ko-KR'
  assert.equal(display.value[0]?.label, '계획 중')
  assert.equal(input[0]?.label, '计划中')
})
