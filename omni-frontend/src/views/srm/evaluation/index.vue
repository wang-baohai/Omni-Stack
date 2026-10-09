<script setup lang="ts">
/** SRM 绩效评估页面，包含评估列表、新建评估打分表单和评估详情抽屉。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  createEvaluation,
  getDefaultEvaluationTemplate,
  getEvaluation,
  listEvaluations,
  type CreateEvaluationRequest,
  type DefaultEvaluationTemplate,
  type EvaluationItemInput,
  type EvaluationVO,
} from '@/api/srm-evaluation'
import { listSuppliers, type SrmSupplier } from '@/api/srm-supplier'
import EvaluationScorecard from '@/components/srm/EvaluationScorecard.vue'

const { t } = useI18n()
const loading = ref(false)
const tableData = ref<EvaluationVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ supplierId?: number }>({})

const defaultTemplate = ref<DefaultEvaluationTemplate | null>(null)
const templateLoading = ref(false)

// 新建评估
const createDialogVisible = ref(false)
const createForm = reactive<{
  supplierId: number | null
  evaluationPeriod: string
  items: EvaluationItemInput[]
}>({
  supplierId: null,
  evaluationPeriod: '',
  items: [],
})
const supplierOptions = ref<SrmSupplier[]>([])
const supplierLoading = ref(false)

// 详情抽屉
const detailDrawerVisible = ref(false)
const currentEvaluation = ref<EvaluationVO | null>(null)

/** 评估状态映射。 */
const evalStatusLabel = computed<Record<string, string>>(() => ({
  COMPLETED: t('srmEvaluationPage.statusCompleted'),
  PENDING: t('srmEvaluationPage.statusPending'),
  IN_PROGRESS: t('srmEvaluationPage.statusInProgress'),
}))

// 等级映射
type EvaluationTagType = '' | 'success' | 'warning' | 'danger'

const levelMap = computed<Record<string, { label: string; type: EvaluationTagType }>>(() => ({
  STRATEGIC: { label: t('srmEvaluationPage.levelStrategic'), type: 'success' },
  PREFERRED: { label: t('srmEvaluationPage.levelPreferred'), type: '' },
  QUALIFIED: { label: t('srmEvaluationPage.levelQualified'), type: 'warning' },
  ELIMINATED: { label: t('srmEvaluationPage.levelEliminated'), type: 'danger' },
}))

function levelLabel(score: number): string {
  if (score >= 90) return 'STRATEGIC'
  if (score >= 75) return 'PREFERRED'
  if (score >= 60) return 'QUALIFIED'
  return 'ELIMINATED'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listEvaluations({
      supplierId: query.supplierId || undefined,
      page: currentPage.value,
      size: pageSize.value,
    })
    if (res.data.code === 200) {
      tableData.value = res.data.data.records
      total.value = res.data.data.total
    }
  } finally {
    loading.value = false
  }
}

async function loadSuppliers() {
  supplierLoading.value = true
  try {
    const res = await listSuppliers({ status: 'APPROVED', page: 1, size: 100 })
    if (res.data.code === 200) {
      supplierOptions.value = res.data.data.records
    }
  } finally {
    supplierLoading.value = false
  }
}

async function loadDefaultTemplate() {
  templateLoading.value = true
  try {
    const response = await getDefaultEvaluationTemplate()
    defaultTemplate.value = response.data.data
  } finally {
    templateLoading.value = false
  }
}

async function openCreateDialog() {
  createForm.supplierId = null
  createForm.evaluationPeriod = ''
  await Promise.all([loadSuppliers(), loadDefaultTemplate()])
  createForm.items = (defaultTemplate.value?.dimensions || []).map((dim) => ({
    dimensionId: dim.id,
    score: 0,
    indicatorName: dim.indicatorName,
  }))
  createDialogVisible.value = true
}

function onScorecardUpdate(items: EvaluationItemInput[]) {
  createForm.items = items
}

async function submitCreate() {
  if (!createForm.supplierId) {
    ElMessage.warning(t('srmEvaluationMessages.supplierRequired'))
    return
  }
  if (!createForm.evaluationPeriod) {
    ElMessage.warning(t('srmEvaluationMessages.periodRequired'))
    return
  }
  if (!defaultTemplate.value || createForm.items.length === 0) {
    ElMessage.warning(t('srmEvaluationMessages.templateMissing'))
    return
  }
  const expectedDimensionIds = new Set(defaultTemplate.value.dimensions.map((item) => item.id))
  const submittedDimensionIds = new Set(createForm.items.map((item) => item.dimensionId))
  const hasInvalidScore = createForm.items.some((item) => (
    !Number.isInteger(item.score) || item.score < 1 || item.score > 5
  ))
  if (hasInvalidScore) {
    ElMessage.warning(t('srmEvaluationMessages.invalidScore'))
    return
  }
  if (createForm.items.length !== expectedDimensionIds.size
    || submittedDimensionIds.size !== expectedDimensionIds.size
    || [...submittedDimensionIds].some((id) => !expectedDimensionIds.has(id))) {
    ElMessage.warning(t('srmEvaluationMessages.dimensionsMismatch'))
    return
  }
  const request: CreateEvaluationRequest = {
    supplierId: createForm.supplierId,
    evaluationPeriod: createForm.evaluationPeriod,
    items: createForm.items,
  }
  await createEvaluation(request)
  ElMessage.success(t('srmEvaluationMessages.created'))
  createDialogVisible.value = false
  loadData()
}

async function viewDetail(row: EvaluationVO) {
  const res = await getEvaluation(row.id)
  currentEvaluation.value = res.data.data
  detailDrawerVisible.value = true
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadData()
}

onMounted(() => {
  loadData()
  loadSuppliers()
})
</script>

<template>
  <div class="srm-evaluation">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="mb-4">
      <el-form :inline="true" @submit.prevent="loadData">
        <el-form-item :label="t('srmSupplierOverview.name')">
          <el-select
            v-model="query.supplierId"
            clearable
            filterable
            :placeholder="t('srmEvaluationPage.allSuppliers')"
            @change="loadData"
          >
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">{{ t('common.search') }}</el-button>
          <el-button v-permission="'srm:evaluation:create'" type="success" @click="openCreateDialog">
            {{ t('srmEvaluationPage.create') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 评估列表 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="supplierName" :label="t('srmSupplierOverview.name')" min-width="150" />
        <el-table-column prop="evaluationPeriod" :label="t('srmEvaluationPage.period')" width="120" />
        <el-table-column prop="totalScore" :label="t('srmSupplierOverview.totalScore')" width="80" align="center">
          <template #default="{ row }">
            <span class="score-text">{{ row.totalScore }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('srmEvaluationPage.level')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="levelMap[levelLabel(row.totalScore)]?.type || ''">
              {{ levelMap[levelLabel(row.totalScore)]?.label || levelLabel(row.totalScore) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="evaluationTime" :label="t('srmRiskPage.assessmentTime')" width="170" />
        <el-table-column :label="t('srmSupplierOverview.status')" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ evalStatusLabel[row.status as string] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">{{ t('srmRiskPage.detail') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt-4"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 新建评估对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      :title="t('srmEvaluationPage.create')"
      width="640px"
      destroy-on-close
    >
      <el-form v-loading="templateLoading" label-width="80px">
        <el-alert
          v-if="defaultTemplate"
          :title="t('srmEvaluationPage.currentTemplate', { name: defaultTemplate.name })"
          type="info"
          :closable="false"
          class="mb-4"
        />
        <el-form-item :label="t('srmSupplierOverview.name')" required>
          <el-select
            v-model="createForm.supplierId"
            filterable
            :placeholder="t('srmEvaluationMessages.supplierRequired')"
            :loading="supplierLoading"
            style="width: 100%"
          >
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('srmEvaluationPage.period')" required>
          <el-input
            v-model="createForm.evaluationPeriod"
            maxlength="50"
            show-word-limit
            placeholder="2026-Q3"
          />
        </el-form-item>
        <el-form-item :label="t('srmRiskPage.score')">
          <EvaluationScorecard
            v-if="defaultTemplate"
            :dimensions="defaultTemplate.dimensions"
            @update:items="onScorecardUpdate"
          />
          <el-empty v-else :description="t('srmEvaluationMessages.templateMissing')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="'srm:evaluation:create'" type="primary" :disabled="!defaultTemplate" @click="submitCreate">
          {{ t('srmRiskPage.submitAssessment') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 评估详情抽屉 -->
    <el-drawer v-model="detailDrawerVisible" :title="t('srmEvaluationPage.detailTitle')" size="520px">
      <template v-if="currentEvaluation">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('srmSupplierOverview.name')">{{ currentEvaluation.supplierName }}</el-descriptions-item>
          <el-descriptions-item :label="t('srmEvaluationPage.period')">{{ currentEvaluation.evaluationPeriod }}</el-descriptions-item>
          <el-descriptions-item :label="t('srmSupplierOverview.totalScore')">
            <span class="score-text-lg">{{ currentEvaluation.totalScore }}</span>
            <el-tag :type="levelMap[levelLabel(currentEvaluation.totalScore)]?.type || ''" class="ml-2">
              {{ levelMap[levelLabel(currentEvaluation.totalScore)]?.label || levelLabel(currentEvaluation.totalScore) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('srmRiskPage.assessmentTime')">{{ currentEvaluation.evaluationTime }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="currentEvaluation.items" class="mt-4" stripe>
          <el-table-column prop="indicatorName" :label="t('srmEvaluationPage.indicator')" />
          <el-table-column prop="score" :label="t('srmRiskPage.score')" width="60" align="center">
            <template #default="{ row }">{{ row.score }}/5</template>
          </el-table-column>
          <el-table-column prop="weight" :label="t('srmEvaluationPage.weight')" width="70" align="center">
            <template #default="{ row }">{{ row.weight }}%</template>
          </el-table-column>
          <el-table-column prop="remark" :label="t('procurementGoodsReceiptForm.remark')" min-width="100" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.srm-evaluation {
  padding: 0;
}
.mb-4 {
  margin-bottom: 16px;
}
.mt-4 {
  margin-top: 16px;
}
.ml-2 {
  margin-left: 8px;
}
.score-text {
  font-weight: bold;
  font-size: 15px;
  color: var(--el-color-primary);
}
.score-text-lg {
  font-weight: bold;
  font-size: 20px;
  color: var(--el-color-primary);
}
</style>
