/** 执行真实风险页面setup，核对预览使用租户阈值而非最高单项颜色。 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import { computed, defineComponent, reactive, ref } from 'vue'

const require = createRequire(import.meta.url)
const ts = require('typescript') as typeof import('typescript')
const { parse, compileScript } = require('@vue/compiler-sfc') as typeof import('@vue/compiler-sfc')
type Level = 'GREEN' | 'YELLOW' | 'RED'
type Threshold = { riskLevel: Level; minScore: number; maxScore: number }
type Item = { id: number; score: number | null; riskLevel: Level; criterionId: number | null;
  indicatorType: string; indicatorTypeName: string; autoCalc: number; version: number; remark: string;
  indicatorValue: string; criteria: { id: number; score: number; riskLevel: Level; criterionLabel: string }[] }
type PageState = {
  indicators: { value: Item[] }; assessmentItems: { value: Item[] }; assessmentDialogVisible: { value: boolean };
  computedTotalScore: { value: number }; computedOverallLevel: { value: Level };
  openAssessmentDialog: () => Promise<void> | void; onCriterionChange: (item: Item) => void;
}
const defaults: Threshold[] = [
  { riskLevel: 'GREEN', minScore: 5, maxScore: 8 },
  { riskLevel: 'YELLOW', minScore: 9, maxScore: 12 },
  { riskLevel: 'RED', minScore: 13, maxScore: 15 },
]

/** 编译并执行交付中的SFC；只替换网络、挂载和提示边界，不复制待测算法。 */
async function page(thresholds: Threshold[] = defaults, failLoad = false): Promise<PageState> {
  const source = readFileSync(new URL('../../src/views/srm/risk/index.vue', import.meta.url), 'utf8')
  const descriptor = parse(source, { filename: 'srm-risk.vue' }).descriptor
  const script = compileScript(descriptor, { id: 'srm-risk-preview-regression' }).content
  const code = ts.transpileModule(script, { compilerOptions: {
    module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022,
  } }).outputText
  const context = vm.createContext({ console })
  const module = new vm.SourceTextModule(code, { context })
  const mocks: Record<string, Record<string, unknown>> = {
    vue: { computed, defineComponent, reactive, ref, onMounted: () => undefined },
    'vue-i18n': { useI18n: () => ({ t: (key: string) => key }) },
    'element-plus': { ElMessage: { success: () => undefined } },
    '@/components/srm/RiskIndicator.vue': { default: {} },
    '@/api/srm-risk': { listRiskSuppliers: async () => ({ data: { data: { records: [], total: 0 } } }),
      getSupplierRisk: async () => { throw new Error('本测试不得访问其他业务接口') },
      createRiskAssessment: async () => { throw new Error('本测试不得提交业务状态') },
      updateRiskIndicator: async () => { throw new Error('本测试不得更新业务指标') } },
    '@/api/srm-risk-config': { getScoreThresholds: async () => {
      if (failLoad) throw new Error('threshold-fixture-unavailable')
      return { data: { data: thresholds } }
    } },
  }
  await module.link((specifier) => {
    const exports = mocks[specifier]; assert.ok(exports, `未知依赖${specifier}不能静默放过`)
    return new vm.SyntheticModule(Object.keys(exports), function () {
      for (const [name, value] of Object.entries(exports)) this.setExport(name, value)
    }, { context })
  })
  await module.evaluate()
  const component = (module.namespace as unknown as { default: { setup: (props: object, ctx: object) => PageState } }).default
  return component.setup({}, { expose: () => undefined })
}
function items(scores: number[]): Item[] {
  return scores.map((score, index) => ({ id: index + 1, score, riskLevel: score === 3 ? 'RED' : score === 2 ? 'YELLOW' : 'GREEN',
    criterionId: index + 1, indicatorType: `TYPE_${index}`, indicatorTypeName: `测试指标${index}`, autoCalc: index === 5 ? 1 : 0,
    version: 0, remark: '', indicatorValue: '', criteria: [
      { id: 10, score: 1, riskLevel: 'GREEN', criterionLabel: '一分标准' },
      { id: 30, score: 3, riskLevel: 'RED', criterionLabel: '三分标准' },
    ] }))
}

test('一项RED但总分8仍显示GREEN，与服务端真实反例一致', async () => {
  const state = await page(); state.indicators.value = items([3, 1, 1, 1, 1, 1]); await state.openAssessmentDialog()
  assert.equal(state.computedTotalScore.value, 8); assert.equal(state.computedOverallLevel.value, 'GREEN')
})
test('9/12/13/15边界和16超上界按当前阈值计算', async () => {
  const state = await page(); state.indicators.value = items([1, 1, 1, 1, 1, 1]); await state.openAssessmentDialog()
  for (const [scores, level] of [
    [[3, 2, 1, 1, 1, 1], 'YELLOW'], [[3, 3, 3, 1, 1, 1], 'YELLOW'],
    [[3, 3, 3, 2, 1, 1], 'RED'], [[3, 3, 3, 3, 2, 1], 'RED'], [[3, 3, 3, 3, 3, 1], 'RED'],
  ] as const) {
    state.assessmentItems.value = items([...scores]); assert.equal(state.computedOverallLevel.value, level)
  }
})
test('使用实际加载的租户阈值，不硬编码种子区间', async () => {
  const state = await page([{ riskLevel: 'GREEN', minScore: 0, maxScore: 10 },
    { riskLevel: 'YELLOW', minScore: 11, maxScore: 20 }, { riskLevel: 'RED', minScore: 21, maxScore: 30 }])
  state.indicators.value = items([3, 3, 3, 3, 3, 1]); await state.openAssessmentDialog()
  assert.equal(state.computedOverallLevel.value, 'YELLOW')
})
test('选择评分标准后真实响应式预览由6到8仍绿、到10转黄', async () => {
  const state = await page(); state.indicators.value = items([1, 1, 1, 1, 1, 1]); await state.openAssessmentDialog()
  assert.equal(state.computedOverallLevel.value, 'GREEN')
  state.assessmentItems.value[0]!.criterionId = 30; state.onCriterionChange(state.assessmentItems.value[0]!)
  assert.equal(state.computedTotalScore.value, 8); assert.equal(state.computedOverallLevel.value, 'GREEN')
  state.assessmentItems.value[1]!.criterionId = 30; state.onCriterionChange(state.assessmentItems.value[1]!)
  assert.equal(state.computedTotalScore.value, 10); assert.equal(state.computedOverallLevel.value, 'YELLOW')
})
test('阈值读取失败时不能打开带猜测等级的评估弹窗', async () => {
  const state = await page(defaults, true); state.indicators.value = items([3, 1, 1, 1, 1, 1])
  await assert.rejects(async () => state.openAssessmentDialog(), /threshold-fixture-unavailable/)
  assert.equal(state.assessmentDialogVisible.value, false)
})
test('真实空阈值表与服务端一致返回GREEN', async () => {
  const state = await page([]); state.indicators.value = items([3, 3, 3, 3, 3, 1]); await state.openAssessmentDialog()
  assert.equal(state.computedOverallLevel.value, 'GREEN')
})
