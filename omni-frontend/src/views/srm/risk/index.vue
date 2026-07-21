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

const editDialogVisible = ref(false)
const editingIndicator = ref<RiskIndicatorVO | null>(null)
const editForm = reactive<{ version: number; indicatorValue: string; riskLevel: RiskLevel; remark: string }>({
  version: 0,
  indicatorValue: '',
  riskLevel: 'GREEN',
  remark: '',
})
const assessmentDialogVisible = ref(false)
const assessmentRemark = ref('')

const indicatorTypeLabel: Record<string, string> = {
  FINANCIAL: '财务风险',
  COMPLIANCE: '合规风险',
  SUPPLY: '供应风险',
  COOPERATION: '合作风险',
  QUALITY: '质量风险',
  CERTIFICATE: '资质证书风险',
}

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

function openEditDialog(row: RiskIndicatorVO) {
  if (row.indicatorType === 'CERTIFICATE') {
    ElMessage.warning('资质风险由资质有效期自动计算，不能手工修改')
    return
  }
  editingIndicator.value = row
  Object.assign(editForm, {
    version: row.version,
    indicatorValue: row.indicatorValue || '',
    riskLevel: row.riskLevel,
    remark: row.remark || '',
  })
  editDialogVisible.value = true
}

async function submitEdit() {
  if (!editingIndicator.value) return
  await updateRiskIndicator(editingIndicator.value.id, { ...editForm })
  ElMessage.success('风险指标更新成功')
  editDialogVisible.value = false
  await Promise.all([loadSupplierDetail(), loadRiskSuppliers()])
}

function openAssessmentDialog() {
  assessmentRemark.value = ''
  assessmentDialogVisible.value = true
}

async function submitAssessment() {
  if (!selectedSupplier.value) return
  await createRiskAssessment(selectedSupplier.value.supplierId, { remark: assessmentRemark.value || undefined })
  ElMessage.success('综合风险评估已完成')
  assessmentDialogVisible.value = false
  await Promise.all([loadSupplierDetail(), loadRiskSuppliers()])
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
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">风险详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
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
            <template #default="{ row }">{{ indicatorTypeLabel[row.indicatorType] || row.indicatorType }}</template>
          </el-table-column>
          <el-table-column prop="indicatorValue" label="指标值" min-width="120" />
          <el-table-column label="风险等级" width="110"><template #default="{ row }"><RiskIndicator :level="row.riskLevel" /></template></el-table-column>
          <el-table-column prop="assessmentTime" label="评估时间" width="170" />
          <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-tag v-if="row.indicatorType === 'CERTIFICATE'" type="info" size="small">自动</el-tag>
              <el-button v-else v-permission="'srm:risk:update'" link type="primary" @click="openEditDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
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

    <el-dialog v-model="editDialogVisible" title="编辑风险指标" width="480px">
      <el-form label-width="90px">
        <el-form-item label="指标类型">{{ indicatorTypeLabel[editingIndicator?.indicatorType || ''] || editingIndicator?.indicatorType }}</el-form-item>
        <el-form-item label="指标值"><el-input v-model="editForm.indicatorValue" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="editForm.riskLevel"><el-option v-for="level in riskLevels" :key="level" :label="riskLevelLabel[level]" :value="level" /></el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editDialogVisible = false">取消</el-button><el-button v-permission="'srm:risk:update'" type="primary" @click="submitEdit">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="assessmentDialogVisible" title="创建综合风险评估" width="480px">
      <el-form label-width="80px">
        <el-form-item label="备注"><el-input v-model="assessmentRemark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="评估备注（可选）" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="assessmentDialogVisible = false">取消</el-button><el-button v-permission="'srm:risk:assess'" type="primary" @click="submitAssessment">开始评估</el-button></template>
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
