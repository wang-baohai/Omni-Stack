<script setup lang="ts">
/** SRM 风险管理页面，提供风险供应商分页筛选和单供应商风险聚合维护。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createRiskAssessment,
  getSupplierRisk,
  listRiskSuppliers,
  updateRiskIndicator,
  type RiskAssessmentVO,
  type RiskCriterionOption,
  type RiskIndicatorVO,
  type RiskLevel,
  type SupplierRiskSummary,
} from '@/api/srm-risk'
import RiskIndicator from '@/components/srm/RiskIndicator.vue'

const listLoading = ref(false)
const riskSuppliers = ref<SupplierRiskSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ riskLevel?: RiskLevel }>({})
const riskLevels: RiskLevel[] = ['RED', 'YELLOW', 'GREEN']
const riskLevelLabel: Record<RiskLevel, string> = { RED: '高风险', YELLOW: '中风险', GREEN: '低风险' }

const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const selectedSupplier = ref<SupplierRiskSummary | null>(null)
const indicators = ref<RiskIndicatorVO[]>([])
const latestAssessment = ref<RiskAssessmentVO | null>(null)
const assessments = ref<RiskAssessmentVO[]>([])
const showAssessmentHistory = ref(false)
const indicatorLevel = ref<RiskLevel | ''>('')
const filteredIndicators = computed(() => (
  indicatorLevel.value
    ? indicators.value.filter((item) => item.riskLevel === indicatorLevel.value)
    : indicators.value
))

/** 综合评估弹窗表单数据。 */
interface AssessmentFormItem {
  id: number
  indicatorType: string
  indicatorTypeName: string
  indicatorValue: string
  riskLevel: RiskLevel
  criterionId: number | null
  score: number | null
  autoCalc: boolean
  remark: string
  version: number
  criteria: RiskCriterionOption[]
}

const assessmentDialogVisible = ref(false)
const assessmentSubmitting = ref(false)
const assessmentRemark = ref('')
const assessmentItems = ref<AssessmentFormItem[]>([])

/** 总分实时计算。 */
const computedTotalScore = computed(() =>
  assessmentItems.value.reduce((sum, item) => sum + (item.score || 0), 0),
)

/** 综合等级实时预览：取所有指标中最高等级。 */
const LEVEL_ORDER: Record<RiskLevel, number> = { GREEN: 0, YELLOW: 1, RED: 2 }
const computedOverallLevel = computed<RiskLevel>(() => {
  let highest: RiskLevel = 'GREEN'
  for (const item of assessmentItems.value) {
    if (LEVEL_ORDER[item.riskLevel] > LEVEL_ORDER[highest]) {
      highest = item.riskLevel
    }
  }
  return highest
})

async function loadRiskSuppliers() {
  listLoading.value = true
  try {
    const response = await listRiskSuppliers({
      riskLevel: query.riskLevel,
      page: currentPage.value,
      size: Math.min(pageSize.value, 100),
    })
    riskSuppliers.value = response.data.data.records
    total.value = response.data.data.total
  } finally {
    listLoading.value = false
  }
}

function search() {
  currentPage.value = 1
  loadRiskSuppliers()
}

function resetQuery() {
  query.riskLevel = undefined
  search()
}

async function loadSupplierDetail(includeHistory = showAssessmentHistory.value) {
  if (!selectedSupplier.value) return
  detailLoading.value = true
  try {
    const response = await getSupplierRisk(selectedSupplier.value.supplierId)
    indicators.value = response.data.data.indicators
    latestAssessment.value = response.data.data.latestAssessment
    assessments.value = includeHistory ? response.data.data.history : []
  } finally {
    detailLoading.value = false
  }
}

async function openDetail(row: SupplierRiskSummary) {
  selectedSupplier.value = row
  indicators.value = []
  latestAssessment.value = null
  assessments.value = []
  indicatorLevel.value = ''
  showAssessmentHistory.value = false
  detailDrawerVisible.value = true
  await loadSupplierDetail(false)
}

async function toggleAssessmentHistory() {
  showAssessmentHistory.value = !showAssessmentHistory.value
  assessments.value = []
  if (showAssessmentHistory.value) await loadSupplierDetail(true)
}

/**
 * 打开综合评估弹窗，从当前指标数据初始化表单。
 */
function openAssessmentDialog() {
  assessmentItems.value = indicators.value.map((ind) => ({
    id: ind.id,
    indicatorType: ind.indicatorType,
    indicatorTypeName: ind.indicatorTypeName || ind.indicatorType,
    indicatorValue: ind.indicatorValue || '',
    riskLevel: ind.riskLevel,
    criterionId: ind.criterionId,
    score: ind.score,
    autoCalc: ind.autoCalc === 1,
    remark: ind.remark || '',
    version: ind.version,
    criteria: ind.criteria || [],
  }))
  assessmentRemark.value = ''
  assessmentDialogVisible.value = true
}

/** 选中评分标准后自动填充分数和风险等级。 */
function onCriterionChange(item: AssessmentFormItem) {
  const criterion = item.criteria.find(c => c.id === item.criterionId)
  if (criterion) {
    item.score = criterion.score
    item.riskLevel = criterion.riskLevel
    item.indicatorValue = criterion.criterionLabel
  }
}

/** 从列表直接打开评估弹窗：先加载该供应商的指标数据，再打开弹窗。 */
async function openAssessmentFromList(row: SupplierRiskSummary) {
  selectedSupplier.value = row
  indicatorLevel.value = ''
  showAssessmentHistory.value = false
  detailLoading.value = true
  try {
    const response = await getSupplierRisk(row.supplierId)
    indicators.value = response.data.data.indicators
    latestAssessment.value = response.data.data.latestAssessment
  } finally {
    detailLoading.value = false
  }
  openAssessmentDialog()
}

/** 提交综合评估：先逐个更新变化的指标（通过 criterionId），再创建综合评估记录。 */
async function submitAssessment() {
  if (!selectedSupplier.value) return
  assessmentSubmitting.value = true
  try {
    const originals = indicators.value
    for (const item of assessmentItems.value) {
      if (item.autoCalc) continue
      const original = originals.find((o) => o.id === item.id)
      const criterionChanged = item.criterionId !== original?.criterionId
      const remarkChanged = item.remark !== (original?.remark || '')
      if (criterionChanged || remarkChanged) {
        await updateRiskIndicator(item.id, {
          version: item.version,
          criterionId: item.criterionId || undefined,
          remark: item.remark || undefined,
        })
      }
    }
    await createRiskAssessment(selectedSupplier.value.supplierId, {
      remark: assessmentRemark.value || undefined,
    })
    ElMessage.success('综合风险评估已完成')
    assessmentDialogVisible.value = false
    await Promise.all([loadSupplierDetail(), loadRiskSuppliers()])
  } finally {
    assessmentSubmitting.value = false
  }
}

onMounted(loadRiskSuppliers)
</script>

<template>
  <div class="srm-risk">
    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="综合风险等级">
          <el-select v-model="query.riskLevel" clearable placeholder="全部等级" style="width: 150px" @change="search">
            <el-option v-for="level in riskLevels" :key="level" :label="riskLevelLabel[level]" :value="level" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="listLoading" :data="riskSuppliers" border stripe>
        <el-table-column prop="supplierName" label="供应商" min-width="200">
          <template #default="{ row }">{{ row.supplierName || `供应商 #${row.supplierId}` }}</template>
        </el-table-column>
        <el-table-column label="综合等级" width="130" align="center">
          <template #default="{ row }"><RiskIndicator :level="row.overallLevel" /></template>
        </el-table-column>
        <el-table-column prop="redIndicatorCount" label="红色指标数" width="120" align="center" />
        <el-table-column prop="assessmentTime" label="最近评估时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'srm:risk:assess'" link type="success" @click="openAssessmentFromList(row)">评估</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :total="total"
        :page-sizes="[5, 10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadRiskSuppliers"
        @size-change="currentPage = 1; loadRiskSuppliers()"
      />
    </el-card>

    <el-drawer
      v-model="detailDrawerVisible"
      :title="`${selectedSupplier?.supplierName || `供应商 #${selectedSupplier?.supplierId || ''}`} · 风险详情`"
      size="820px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <el-descriptions v-if="latestAssessment" :column="2" border class="detail-section">
          <el-descriptions-item label="当前综合等级"><RiskIndicator :level="latestAssessment.overallLevel" /></el-descriptions-item>
          <el-descriptions-item label="评估时间">{{ latestAssessment.assessmentTime }}</el-descriptions-item>
          <el-descriptions-item v-if="latestAssessment.remark" label="评估备注" :span="2">{{ latestAssessment.remark }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-else title="该供应商尚未进行综合风险评估" type="info" :closable="false" class="detail-section" />

        <div class="section-header">
          <h3>风险指标</h3>
          <el-space>
            <el-select v-model="indicatorLevel" clearable placeholder="全部指标等级" style="width: 150px">
              <el-option v-for="level in riskLevels" :key="level" :label="riskLevelLabel[level]" :value="level" />
            </el-select>
            <el-button v-permission="'srm:risk:assess'" type="success" @click="openAssessmentDialog">创建综合评估</el-button>
          </el-space>
        </div>
        <el-table :data="filteredIndicators" border stripe>
          <el-table-column label="指标类型" min-width="140">
            <template #default="{ row }">{{ row.indicatorTypeName || row.indicatorType }}</template>
          </el-table-column>
          <el-table-column prop="indicatorValue" label="指标值" min-width="120" />
          <el-table-column label="得分" width="80" align="center">
            <template #default="{ row }">{{ row.score ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="风险等级" width="110"><template #default="{ row }"><RiskIndicator :level="row.riskLevel" /></template></el-table-column>
          <el-table-column prop="assessmentTime" label="评估时间" width="170" />
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        </el-table>

        <div class="history-toggle">
          <el-button type="info" plain @click="toggleAssessmentHistory">
            {{ showAssessmentHistory ? '收起评估历史' : '加载评估历史' }}
          </el-button>
        </div>
        <el-table v-if="showAssessmentHistory" :data="assessments" border stripe>
          <el-table-column label="综合等级" width="120"><template #default="{ row }"><RiskIndicator :level="row.overallLevel" /></template></el-table-column>
          <el-table-column prop="assessmentTime" label="评估时间" width="180" />
          <el-table-column prop="remark" label="备注" min-width="200" />
        </el-table>
      </div>
    </el-drawer>

    <el-dialog v-model="assessmentDialogVisible" title="创建综合风险评估" width="800px" destroy-on-close>
      <div style="margin-bottom: 16px; color: #909399; font-size: 13px">从预定义评分标准中选择评估维度，系统将基于总分自动计算综合风险等级。自动计算类指标不可编辑。</div>
      <el-table :data="assessmentItems" border size="small" style="margin-bottom: 16px">
        <el-table-column label="指标类型" min-width="110">
          <template #default="{ row }">{{ row.indicatorTypeName }}</template>
        </el-table-column>
        <el-table-column label="评分标准" min-width="200">
          <template #default="{ row }">
            <el-select
              v-if="!row.autoCalc && row.criteria.length > 0"
              v-model="row.criterionId"
              size="small"
              placeholder="请选择"
              style="width: 100%"
              @change="onCriterionChange(row)"
            >
              <el-option
                v-for="c in row.criteria"
                :key="c.id"
                :label="`${c.criterionLabel}（${c.score}分）`"
                :value="c.id"
              />
            </el-select>
            <span v-else style="color: #909399; font-size: 12px">{{ row.indicatorValue || '自动计算' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="70" align="center">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="风险等级" width="110" align="center">
          <template #default="{ row }"><RiskIndicator :level="row.riskLevel" /></template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">
            <el-input v-if="!row.autoCalc" v-model="row.remark" size="small" maxlength="500" />
            <span v-else style="color: #909399; font-size: 12px">{{ row.remark || '自动计算' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 16px">
        <span style="font-weight: bold">总分：{{ computedTotalScore }}</span>
        <span style="font-weight: bold">综合风险等级预览：</span>
        <RiskIndicator :level="computedOverallLevel" />
      </div>
      <el-form label-width="80px">
        <el-form-item label="评估备注">
          <el-input v-model="assessmentRemark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="本次评估说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assessmentDialogVisible = false">取消</el-button>
        <el-button v-permission="'srm:risk:assess'" type="primary" :loading="assessmentSubmitting" @click="submitAssessment">提交评估</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-card { margin-bottom: 16px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
.detail-section { margin-bottom: 20px; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin: 16px 0 12px; }
.section-header h3 { margin: 0; }
.history-toggle { margin: 20px 0 12px; }
</style>
