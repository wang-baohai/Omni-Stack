<script setup lang="ts">
/** SRM 风险管理页面，提供风险供应商分页筛选和单供应商风险聚合维护。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
const listLoading = ref(false)
const riskSuppliers = ref<SupplierRiskSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ riskLevel?: RiskLevel }>({})
const riskLevels: RiskLevel[] = ['RED', 'YELLOW', 'GREEN']
const riskLevelLabel = computed<Record<RiskLevel, string>>(() => ({
  RED: t('srmRiskPage.riskHigh'),
  YELLOW: t('srmRiskPage.riskMedium'),
  GREEN: t('srmRiskPage.riskLow'),
}))

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

/** 打开综合评估弹窗，从当前指标数据初始化表单。 */
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

/** 提交综合评估：先逐个更新变化的指标，再创建综合评估记录。 */
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
    ElMessage.success(t('srmRiskMessages.assessmentCompleted'))
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
        <el-form-item :label="t('srmRiskPage.overallRiskLevel')">
          <el-select
            v-model="query.riskLevel"
            clearable
            :placeholder="t('srmRiskPage.allLevels')"
            style="width: 150px"
            @change="search"
          >
            <el-option v-for="level in riskLevels" :key="level" :label="riskLevelLabel[level]" :value="level" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="resetQuery">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="listLoading" :data="riskSuppliers" border stripe>
        <el-table-column prop="supplierName" :label="t('srmSupplierOverview.name')" min-width="200">
          <template #default="{ row }">
            {{ row.supplierName || t('srmRiskPage.supplierWithId', { id: row.supplierId }) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('srmRiskPage.overallLevel')" width="130" align="center">
          <template #default="{ row }"><RiskIndicator :level="row.overallLevel" /></template>
        </el-table-column>
        <el-table-column prop="redIndicatorCount" :label="t('srmRiskPage.redIndicatorCount')" width="120" align="center" />
        <el-table-column prop="assessmentTime" :label="t('srmRiskPage.latestAssessmentTime')" width="180" />
        <el-table-column :label="t('common.actions')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('srmRiskPage.detail') }}</el-button>
            <el-button v-permission="'srm:risk:assess'" link type="success" @click="openAssessmentFromList(row)">
              {{ t('srmRiskPage.assess') }}
            </el-button>
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
      :title="t('srmRiskPage.detailTitle', {
        name: selectedSupplier?.supplierName || t('srmRiskPage.supplierWithId', { id: selectedSupplier?.supplierId || '' }),
      })"
      size="820px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <el-descriptions v-if="latestAssessment" :column="2" border class="detail-section">
          <el-descriptions-item :label="t('srmRiskPage.currentOverallLevel')">
            <RiskIndicator :level="latestAssessment.overallLevel" />
          </el-descriptions-item>
          <el-descriptions-item :label="t('srmRiskPage.assessmentTime')">{{ latestAssessment.assessmentTime }}</el-descriptions-item>
          <el-descriptions-item v-if="latestAssessment.remark" :label="t('srmRiskPage.assessmentRemark')" :span="2">
            {{ latestAssessment.remark }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-else
          :title="t('srmRiskPage.noAssessment')"
          type="info"
          :closable="false"
          class="detail-section"
        />

        <div class="section-header">
          <h3>{{ t('srmRiskPage.riskIndicators') }}</h3>
          <el-space>
            <el-select
              v-model="indicatorLevel"
              clearable
              :placeholder="t('srmRiskPage.allIndicatorLevels')"
              style="width: 150px"
            >
              <el-option v-for="level in riskLevels" :key="level" :label="riskLevelLabel[level]" :value="level" />
            </el-select>
            <el-button v-permission="'srm:risk:assess'" type="success" @click="openAssessmentDialog">
              {{ t('srmRiskPage.createAssessment') }}
            </el-button>
          </el-space>
        </div>
        <el-table :data="filteredIndicators" border stripe>
          <el-table-column :label="t('srmSupplierOverview.indicatorType')" min-width="140">
            <template #default="{ row }">{{ row.indicatorTypeName || row.indicatorType }}</template>
          </el-table-column>
          <el-table-column prop="indicatorValue" :label="t('srmSupplierOverview.indicatorValue')" min-width="120" />
          <el-table-column :label="t('srmRiskPage.score')" width="80" align="center">
            <template #default="{ row }">{{ row.score ?? '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('srmSupplierOverview.riskLevel')" width="110">
            <template #default="{ row }"><RiskIndicator :level="row.riskLevel" /></template>
          </el-table-column>
          <el-table-column prop="assessmentTime" :label="t('srmRiskPage.assessmentTime')" width="170" />
          <el-table-column prop="remark" :label="t('procurementGoodsReceiptForm.remark')" min-width="150" show-overflow-tooltip />
        </el-table>

        <div class="history-toggle">
          <el-button type="info" plain @click="toggleAssessmentHistory">
            {{ showAssessmentHistory ? t('srmRiskPage.collapseHistory') : t('srmRiskPage.loadHistory') }}
          </el-button>
        </div>
        <el-table v-if="showAssessmentHistory" :data="assessments" border stripe>
          <el-table-column :label="t('srmRiskPage.overallLevel')" width="120">
            <template #default="{ row }"><RiskIndicator :level="row.overallLevel" /></template>
          </el-table-column>
          <el-table-column prop="assessmentTime" :label="t('srmRiskPage.assessmentTime')" width="180" />
          <el-table-column prop="remark" :label="t('procurementGoodsReceiptForm.remark')" min-width="200" />
        </el-table>
      </div>
    </el-drawer>

    <el-dialog
      v-model="assessmentDialogVisible"
      :title="t('srmRiskPage.createAssessment')"
      width="800px"
      destroy-on-close
    >
      <div style="margin-bottom: 16px; color: #909399; font-size: 13px">
        {{ t('srmRiskPage.assessmentHint') }}
      </div>
      <el-table :data="assessmentItems" border size="small" style="margin-bottom: 16px">
        <el-table-column :label="t('srmSupplierOverview.indicatorType')" min-width="110">
          <template #default="{ row }">{{ row.indicatorTypeName }}</template>
        </el-table-column>
        <el-table-column :label="t('srmRiskPage.scoringCriterion')" min-width="200">
          <template #default="{ row }">
            <el-select
              v-if="!row.autoCalc && row.criteria.length > 0"
              v-model="row.criterionId"
              size="small"
              :placeholder="t('srmRiskPage.pleaseSelect')"
              style="width: 100%"
              @change="onCriterionChange(row)"
            >
              <el-option
                v-for="c in row.criteria"
                :key="c.id"
                :label="t('srmRiskPage.criterionWithScore', { label: c.criterionLabel, score: c.score })"
                :value="c.id"
              />
            </el-select>
            <span v-else style="color: #909399; font-size: 12px">
              {{ row.indicatorValue || t('srmRiskPage.autoCalculated') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('srmRiskPage.score')" width="70" align="center">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('srmSupplierOverview.riskLevel')" width="110" align="center">
          <template #default="{ row }"><RiskIndicator :level="row.riskLevel" /></template>
        </el-table-column>
        <el-table-column :label="t('procurementGoodsReceiptForm.remark')" min-width="150">
          <template #default="{ row }">
            <el-input v-if="!row.autoCalc" v-model="row.remark" size="small" maxlength="500" />
            <span v-else style="color: #909399; font-size: 12px">
              {{ row.remark || t('srmRiskPage.autoCalculated') }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 16px">
        <span style="font-weight: bold">{{ t('srmRiskPage.totalScore', { score: computedTotalScore }) }}</span>
        <span style="font-weight: bold">{{ t('srmRiskPage.overallPreview') }}</span>
        <RiskIndicator :level="computedOverallLevel" />
      </div>
      <el-form label-width="80px">
        <el-form-item :label="t('srmRiskPage.assessmentRemark')">
          <el-input
            v-model="assessmentRemark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            :placeholder="t('srmRiskPage.assessmentRemarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assessmentDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="'srm:risk:assess'" type="primary" :loading="assessmentSubmitting" @click="submitAssessment">
          {{ t('srmRiskPage.submitAssessment') }}
        </el-button>
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
