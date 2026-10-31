/** 运行实际审批规则组件，防止后端诊断串泄露成跨语言界面提示。 */
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import { computed, defineComponent, reactive, ref } from 'vue'
import { createI18n } from 'vue-i18n'
import type { ApprovalNode, CategoryCoverage, MatchPreview, ProcurementApprovalRoute } from '../../src/api/procurement-approval-route.ts'

const require = createRequire(import.meta.url)
const ts = require('typescript') as typeof import('typescript')
const { parse, compileScript } = require('@vue/compiler-sfc') as typeof import('@vue/compiler-sfc')
const localeContext = vm.createContext({}), localeModules = new Map<string, vm.SourceTextModule>()
function localeModule(name: string): vm.SourceTextModule {
  const previous = localeModules.get(name)
  if (previous) return previous
  assert.ok(['zh-CN', 'en-US', 'ja-JP', 'ko-KR'].includes(name))
  const source = readFileSync(new URL(`../../src/locales/${name}.ts`, import.meta.url), 'utf8')
  const module = new vm.SourceTextModule(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText, { context: localeContext, identifier: name })
  localeModules.set(name, module); return module
}
const messages: Record<string, Record<string, unknown>> = {}
for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
  const module = localeModule(locale)
  if (module.status === 'unlinked') await module.link(specifier => { assert.match(specifier, /^\.\/[a-z]{2}-[A-Z]{2}$/); return localeModule(specifier.slice(2)) })
  if (module.status === 'linked') await module.evaluate()
  messages[locale] = (module.namespace as unknown as { default: Record<string, unknown> }).default
}
const literalKeys = new Set<string>()
function collectLiteralKeys(directory: URL) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = new URL(entry.name + (entry.isDirectory() ? '/' : ''), directory)
    if (entry.isDirectory()) collectLiteralKeys(path)
    else if (/\.(vue|ts)$/.test(entry.name)) {
      for (const match of readFileSync(path, 'utf8').matchAll(/\bt\(\s*'([\w.-]+)'/g)) literalKeys.add(match[1]!)
    }
  }
}
collectLiteralKeys(new URL('../../src/views/procurement/', import.meta.url))
collectLiteralKeys(new URL('../../src/components/procurement/', import.meta.url))
type Translate = (key: string, values?: Record<string, string | number>) => string

/** 编译实际SFC/setup或composable；只隔离网络和确认框，不复制显示分支算法。 */
async function load(relative: string, t: Translate, props: object = {}, overrides: Record<string, Record<string, unknown>> = {}) {
  const filename = '../../src/views/procurement/approval-route/' + relative
  const source = readFileSync(new URL(filename, import.meta.url), 'utf8')
  const script = relative.endsWith('.vue') ? compileScript(parse(source, { filename }).descriptor, { id: 'procurement-approval-hints-regression' }).content : source
  const context = vm.createContext({}), module = new vm.SourceTextModule(ts.transpileModule(script, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText, { context })
  const unexpected = async () => { throw new Error('本单元测试禁止未指定的网络/写操作') }
  const mocks: Record<string, Record<string, unknown>> = {
    vue: { computed, defineComponent, reactive, ref },
    'vue-i18n': { useI18n: () => ({ t }) },
    'element-plus': { ElMessage: { warning: unexpected }, ElMessageBox: { confirm: unexpected } },
    './ApprovalFlowPreview.vue': { default: {} },
    '@/api/procurement-approval-route': Object.fromEntries(['previewApprovalRouteMatch', 'createProcurementApprovalRoute', 'deleteProcurementApprovalRoute', 'getApprovalRouteCoverage', 'getApprovalRouteImpact', 'listApprovalWorkflowOptions', 'listProcurementApprovalRoutes', 'updateProcurementApprovalRoute'].map(name => [name, unexpected])),
    '@/api/procurement-material': { listProcurementCategories: unexpected },
  }
  for (const [specifier, values] of Object.entries(overrides)) mocks[specifier] = { ...mocks[specifier], ...values }
  await module.link(specifier => {
    const values = mocks[specifier]; assert.ok(values, `未知依赖${specifier}必须显式处理`)
    return new vm.SyntheticModule(Object.keys(values), function () { for (const [name, value] of Object.entries(values)) this.setExport(name, value) }, { context })
  })
  await module.evaluate()
  if (!relative.endsWith('.vue')) return module.namespace as unknown as Record<string, unknown>
  return (module.namespace as unknown as { default: { setup: (props: object, context: object) => Record<string, unknown> } }).default.setup(props, { expose: () => undefined })
}

for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
  const i18n = createI18n({ legacy: false, locale, messages })
  const t: Translate = (key, values = {}) => String(i18n.global.t(key, values))
  const label = (key: string, values?: Record<string, string | number>) => t('procurementApprovalRules.' + key, values)
  test(`${locale} 当前采购页面和共享表单的固定文案键均存在于本语言`, () => {
    assert.ok(literalKeys.size > 100)
    const missing = [...literalKeys].filter(key => !i18n.global.te(key, locale))
    assert.deepEqual(missing, [], '四语键相同不能掩盖四语一起缺少实际页面文案')
    for (const status of ['INVITED', 'QUOTED', 'AWARDED', 'REJECTED', 'CANCELLED', 'EXPIRED']) {
      assert.ok(i18n.global.te('portal.quotation.invitationStates.' + status, locale))
    }
    assert.ok(!/BigDecimal|浮点|floating.point|浮動小数点|부동소수점/.test(t('procurementRequisitionFormMessages.serverCalculationTip')))
  })
  test(`${locale} 真实试算组件按结构化结果区分精确、默认、断档、冲突和不可验证`, async () => {
    const setup = await load('components/RuleMatchTester.vue', t)
    const describe = setup.resultDescription as (preview: Partial<MatchPreview>) => string
    for (const [outcome, defaultRule, key] of [['MATCHED', false, 'matchExactHint'], ['MATCHED', true, 'matchDefaultHint'], ['NO_MATCH', false, 'matchNoRuleHint'], ['AMBIGUOUS', false, 'matchConflictHint'], ['WORKFLOW_UNAVAILABLE', false, 'matchUnavailableHint']] as const) {
      const text = describe({ outcome, defaultRule, actionMessage: '不应上屏的中文诊断<test-secret>' })
      assert.equal(text, label(key)); assert.ok(!text.includes('test-secret')); assert.ok(!text.includes('procurementApprovalRules.'))
    }
  })
  test(`${locale} 真实节点组件保留角色编码和模型名称但不使用服务器说明串`, async () => {
    const node: ApprovalNode = { id: 'approval', name: '客户自定义节点名', type: 'APPROVAL', roleCode: 'PROCUREMENT_MANAGER', approvalMode: 'ANY', description: '后端中文技术摘要' }
    const setup = await load('components/ApprovalFlowPreview.vue', t, { graph: { nodes: [node] } })
    const describe = setup.nodeDescription as (node: ApprovalNode) => string
    assert.equal(describe(node), label('role') + '：PROCUREMENT_MANAGER')
    assert.equal(describe({ ...node, roleCode: undefined }), label('configuredApproval'))
    assert.equal(describe({ ...node, type: 'GATEWAY' }), label('flowBranchHint'))
    assert.equal((setup.businessNodes as { value: ApprovalNode[] }).value[0]?.name, '客户自定义节点名')
    assert.equal(node.description, '后端中文技术摘要')
  })
  test(`${locale} 覆盖风险保留半开金额、无上限和三种风险而不解析中文issues`, async () => {
    const setup = await load('components/RuleCoverageAlert.vue', t)
    const describe = setup.issueMessages as (category: CategoryCoverage) => string[]
    const category: CategoryCoverage = { categoryCode: 'TEST', categoryName: '自定义品类', complete: false, issues: ['后端中文诊断'], segments: [
      { minAmount: '0.0000', maxAmount: '100.0000', outcome: 'GAP', source: 'NONE', routeIds: [], workflowAvailability: 'NOT_APPLICABLE' },
      { minAmount: '100.0000', maxAmount: '200.0000', outcome: 'AMBIGUOUS', source: 'EXACT', routeIds: [1, 2], workflowAvailability: 'CONFLICT' },
      { minAmount: '200.0000', maxAmount: null, outcome: 'COVERED', source: 'DEFAULT', routeIds: [3], workflowAvailability: 'NOT_CURRENT' },
    ] }
    assert.deepEqual(Array.from(describe(category)), [label('coverageGapRange', { min: '0.0000', max: '100.0000' }), label('coverageConflictRange', { min: '100.0000', max: '200.0000' }), label('coverageInvalidRange', { min: '200.0000', max: '∞' })])
    assert.deepEqual(Array.from(describe({ ...category, segments: [] })), [label('coverageRisk')])
    assert.equal(category.issues[0], '后端中文诊断')
  })
  test(`${locale} 真实停用和删除确认使用结构化影响数，取消确认不会调用写接口`, async () => {
    const calls: { message: string; type: string }[] = []; let writes = 0, gaps = 2, conflicts = 1
    const module = await load('composables/useApprovalRules.ts', t, {}, {
      'element-plus': { ElMessageBox: { confirm: async (message: string, _title: string, options: { type: string }) => { calls.push({ message, type: options.type }); throw new Error('用户取消') } } },
      '@/api/procurement-approval-route': { getApprovalRouteImpact: async () => ({ data: { data: { gapSegmentCount: gaps, ambiguousSegmentCount: conflicts, actionMessage: '不得显示的后端中文' } } }),
        deleteProcurementApprovalRoute: async () => { writes += 1 }, updateProcurementApprovalRoute: async () => { writes += 1 } },
    })
    const state = (module.useApprovalRules as (t: Translate) => { remove: (row: ProcurementApprovalRoute) => Promise<void>; toggleStatus: (row: ProcurementApprovalRoute) => Promise<void> })(t)
    const row = { id: 7, routeName: '自定义规则名', status: 'ACTIVE', version: 4 } as ProcurementApprovalRoute
    await state.remove(row); assert.ok(calls[0]?.message.includes(label('impactRiskHint', { gaps: 2, conflicts: 1 }))); assert.equal(calls[0]?.type, 'warning')
    gaps = 0; conflicts = 2; await state.toggleStatus(row); assert.ok(calls[1]?.message.includes(label('impactRiskHint', { gaps: 0, conflicts: 2 }))); assert.equal(calls[1]?.type, 'warning')
    conflicts = 0; await state.remove(row); assert.ok(calls[2]?.message.includes(label('impactSafeHint'))); assert.equal(calls[2]?.type, 'info')
    assert.equal(writes, 0); assert.ok(calls.every(call => !call.message.includes('不得显示的后端中文')))
  })
}
