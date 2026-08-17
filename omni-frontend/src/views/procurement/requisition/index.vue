<script setup lang="ts">
/** 采购请购页面，支持草稿明细维护、审批启动重试和状态追踪。 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelProcurementRequisition,
  deleteProcurementRequisition,
  getProcurementRequisition,
  listProcurementRequisitions,
  retryProcurementRequisitionStart,
  submitProcurementRequisition,
  type ProcurementRequisitionDetail,
  type ProcurementRequisitionStatus,
  type ProcurementRequisitionSummary,
} from '@/api/procurement-requisition'
import RequisitionForm from '@/components/procurement/RequisitionForm.vue'

const loading = ref(false)
const rows = ref<ProcurementRequisitionSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  keyword: string
  status?: ProcurementRequisitionStatus
  categoryCode: string
}>({ keyword: '', categoryCode: '' })
const syncStartedAt = ref<number>()
const syncTimedOut = ref(false)
let syncPollTimer: ReturnType<typeof setInterval> | undefined
const hasAsyncSync = computed(() => rows.value.some((row) => row.status === 'APPROVING'))

const statusOptions: Array<{
  value: ProcurementRequisitionStatus
  label: string
  type: 'info' | 'primary' | 'warning' | 'success' | 'danger'
}> = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'SUBMITTED', label: '已提交', type: 'primary' },
  { value: 'APPROVING', label: '审批中', type: 'warning' },
  { value: 'APPROVED', label: '已通过', type: 'success' },
  { value: 'REJECTED', label: '已驳回', type: 'danger' },
  { value: 'CANCELLED', label: '已取消', type: 'info' },
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  ProcurementRequisitionStatus,
  (typeof statusOptions)[number]
>

function statusInfo(status: ProcurementRequisitionStatus) {
  return statusMap[status]
}

async function loadRows(silent = false) {
  if (!silent) loading.value = true
  try {
    const response = await listProcurementRequisitions({
      keyword: query.keyword || undefined,
      status: query.status,
      categoryCode: query.categoryCode || undefined,
      page: currentPage.value,
      size: pageSize.value,
    })
    rows.value = response.data.data.records
    total.value = response.data.data.total
    if (rows.value.some((row) => row.status === 'APPROVING')) {
      syncStartedAt.value ??= Date.now()
      syncTimedOut.value = Date.now() - syncStartedAt.value >= 60_000
    } else {
      syncStartedAt.value = undefined
      syncTimedOut.value = false
    }
  } finally {
    if (!silent) loading.value = false
  }
}

function search() {
  currentPage.value = 1
  loadRows()
}

function resetQuery() {
  Object.assign(query, { keyword: '', status: undefined, categoryCode: '' })
  search()
}

const dialogVisible = ref(false)
const editRow = ref<ProcurementRequisitionSummary>()

function openCreate() {
  editRow.value = undefined
  dialogVisible.value = true
}

function openEdit(row: ProcurementRequisitionSummary) {
  editRow.value = row
  dialogVisible.value = true
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProcurementRequisitionDetail>()

async function openDetail(row: ProcurementRequisitionSummary) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getProcurementRequisition(row.id)
    detail.value = response.data.data
  } finally {
    detailLoading.value = false
  }
}

async function remove(row: ProcurementRequisitionSummary) {
  try {
    await ElMessageBox.confirm(`确认删除请购“${row.requisitionNo}”？`, '删除确认', {
      type: 'warning',
    })
    await deleteProcurementRequisition(row.id, row.version)
    ElMessage.success('删除成功')
    loadRows()
  } catch {
    // 用户取消或请求失败时保持当前页面。
  }
}

async function submit(row: ProcurementRequisitionSummary) {
  try {
    await ElMessageBox.confirm(`确认提交请购“${row.requisitionNo}”并启动审批？`, '提交审批')
    await submitProcurementRequisition(row.id, row.version)
    ElMessage.success('审批流程已启动')
  } finally {
    await loadRows()
  }
}

async function retryStart(row: ProcurementRequisitionSummary) {
  try {
    await retryProcurementRequisitionStart(row.id, row.version)
    ElMessage.success('审批流程已恢复')
  } finally {
    await loadRows()
  }
}

async function cancel(row: ProcurementRequisitionSummary) {
  try {
    await ElMessageBox.confirm(`确认取消请购“${row.requisitionNo}”？`, '取消确认', {
      type: 'warning',
    })
    await cancelProcurementRequisition(row.id, row.version)
    ElMessage.success('请购已取消')
  } finally {
    await loadRows()
  }
}

onMounted(() => {
  loadRows()
  syncPollTimer = setInterval(() => {
    if (hasAsyncSync.value && document.visibilityState === 'visible') loadRows(true)
  }, 5_000)
})

onUnmounted(() => {
  if (syncPollTimer) clearInterval(syncPollTimer)
})
</script>

<template>
  <div class="requisition-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="金额由服务端按数量与预估单价精确计算；提交时由品类和总金额自动选择审批流程。"
    />
    <el-alert
      v-if="hasAsyncSync"
      :type="syncTimedOut ? 'warning' : 'info'"
      :title="syncTimedOut ? '审批结果同步时间较长' : '审批状态同步中'"
      :description="syncTimedOut
        ? '业务状态仍在等待可靠消息回写。可继续自动等待，或手动刷新后联系管理员查看消息日志。'
        : 'Workflow 审批完成后由可靠消息异步回写业务状态，本页每 5 秒自动刷新。'"
      :closable="false"
      show-icon
    >
      <template #default><el-button link type="primary" @click="loadRows()">立即刷新</el-button></template>
    </el-alert>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>请购管理</span>
          <el-button
            v-permission="'procurement:requisition:create'"
            type="primary"
            @click="openCreate"
          >
            新建请购
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="请购单号或标题" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="品类编码">
          <el-input v-model="query.categoryCode" clearable placeholder="例如 IT_DEVICE" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="requisitionNo" label="请购单号" min-width="170" />
        <el-table-column prop="title" label="标题" min-width="210" show-overflow-tooltip />
        <el-table-column prop="primaryCategoryCode" label="品类" min-width="130" />
        <el-table-column label="预估金额" min-width="150" align="right">
          <template #default="{ row }">{{ row.totalAmount }} {{ row.currencyCode }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批启动" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.workflowStartStatus === 'FAILED'" type="danger">启动失败</el-tag>
            <el-tag v-else-if="row.workflowStartStatus === 'PENDING'" type="warning">启动中</el-tag>
            <span v-else-if="row.workflowStartStatus === 'STARTED'">第 {{ row.approvalAttempt }} 轮</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" min-width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
              v-permission="'procurement:requisition:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:requisition:submit'"
              link
              type="success"
              @click="submit(row)"
            >
              提交
            </el-button>
            <el-button
              v-if="row.status === 'SUBMITTED' && row.workflowStartStatus === 'FAILED'"
              v-permission="'procurement:requisition:submit'"
              link
              type="warning"
              @click="retryStart(row)"
            >
              重试启动
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || (row.status === 'SUBMITTED' && row.workflowStartStatus === 'FAILED')"
              v-permission="'procurement:requisition:cancel'"
              link
              type="warning"
              @click="cancel(row)"
            >
              取消
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:requisition:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[5, 10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="() => loadRows()"
        @size-change="search"
      />
    </el-card>

    <RequisitionForm v-model="dialogVisible" :edit-data="editRow" @saved="loadRows" />

    <el-drawer v-model="detailVisible" title="请购详情" size="760px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="请购单号">{{ detail.requisitionNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusInfo(detail.status).label }}</el-descriptions-item>
            <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="品类">{{ detail.primaryCategoryCode }}</el-descriptions-item>
            <el-descriptions-item label="预估金额">{{ detail.totalAmount }} {{ detail.currencyCode }}</el-descriptions-item>
            <el-descriptions-item label="审批轮次">{{ detail.approvalAttempt }}</el-descriptions-item>
            <el-descriptions-item label="流程实例">{{ detail.processInstanceId || '—' }}</el-descriptions-item>
            <el-descriptions-item label="申请原因" :span="2">{{ detail.reason || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="detail.lines" class="detail-lines" border>
            <el-table-column prop="lineNo" label="#" width="55" />
            <el-table-column prop="materialCode" label="物料编码" min-width="130" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="quantity" label="数量" min-width="120" align="right" />
            <el-table-column prop="unit" label="单位" width="75" />
            <el-table-column prop="estimatedUnitPrice" label="预估单价" min-width="120" align="right" />
            <el-table-column prop="estimatedTotalPrice" label="行金额" min-width="130" align="right" />
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.requisition-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-lines {
  margin-top: 18px;
}
</style>
