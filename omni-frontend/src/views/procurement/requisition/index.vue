<script setup lang="ts">
/** 采购请购页面，支持草稿明细维护、审批启动重试和状态跟踪。 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
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

const statusMap = computed<Record<
  ProcurementRequisitionStatus,
  { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }
>>(() => ({
  DRAFT: { label: t('procurementRequisitionPage.statusDraft'), type: 'info' },
  SUBMITTED: { label: t('procurementRequisitionPage.statusSubmitted'), type: 'primary' },
  APPROVING: { label: t('procurementRequisitionPage.statusApproving'), type: 'warning' },
  APPROVED: { label: t('procurementRequisitionPage.statusApproved'), type: 'success' },
  REJECTED: { label: t('procurementRequisitionPage.statusRejected'), type: 'danger' },
  CANCELLED: { label: t('procurementRequisitionPage.statusCancelled'), type: 'info' },
}))

function statusInfo(status: ProcurementRequisitionStatus) {
  return statusMap.value[status]
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
    await ElMessageBox.confirm(
      t('procurementRequisitionMessages.deleteConfirm', { no: row.requisitionNo }),
      t('procurementRequisitionMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteProcurementRequisition(row.id, row.version)
    ElMessage.success(t('procurementRequisitionMessages.deleteSuccess'))
    loadRows()
  } catch {
    // 用户取消或请求失败时保持当前页面。
  }
}

async function submit(row: ProcurementRequisitionSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementRequisitionMessages.submitConfirm', { no: row.requisitionNo }),
      t('procurementRequisitionMessages.submitTitle'),
    )
    await submitProcurementRequisition(row.id, row.version)
    ElMessage.success(t('procurementRequisitionMessages.workflowStarted'))
  } finally {
    await loadRows()
  }
}

async function retryStart(row: ProcurementRequisitionSummary) {
  try {
    await retryProcurementRequisitionStart(row.id, row.version)
    ElMessage.success(t('procurementRequisitionMessages.workflowRecovered'))
  } finally {
    await loadRows()
  }
}

async function cancel(row: ProcurementRequisitionSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementRequisitionMessages.cancelConfirm', { no: row.requisitionNo }),
      t('procurementRequisitionMessages.cancelTitle'),
      { type: 'warning' },
    )
    await cancelProcurementRequisition(row.id, row.version)
    ElMessage.success(t('procurementRequisitionMessages.cancelSuccess'))
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
      :title="t('procurementRequisitionPage.amountNotice')"
    />
    <el-alert
      v-if="hasAsyncSync"
      :type="syncTimedOut ? 'warning' : 'info'"
      :title="syncTimedOut ? t('procurementRequisitionPage.syncSlow') : t('procurementRequisitionPage.syncing')"
      :description="syncTimedOut
        ? t('procurementRequisitionPage.syncSlowDescription')
        : t('procurementRequisitionPage.syncDescription')"
      :closable="false"
      show-icon
    >
      <template #default>
        <el-button link type="primary" @click="loadRows()">
          {{ t('procurementRequisitionPage.refreshNow') }}
        </el-button>
      </template>
    </el-alert>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('procurementRequisitionPage.title') }}</span>
          <el-button
            v-permission="'procurement:requisition:create'"
            type="primary"
            @click="openCreate"
          >
            {{ t('procurementRequisitionPage.create') }}
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item :label="t('procurementRequisitionPage.keyword')">
          <el-input
            v-model="query.keyword"
            clearable
            :placeholder="t('procurementRequisitionPage.keywordPlaceholder')"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item :label="t('procurementRequisitionPage.status')">
          <el-select v-model="query.status" clearable :placeholder="t('procurementRequisitionPage.all')" style="width: 130px">
            <el-option
              v-for="(option, key) in statusMap"
              :key="key"
              :label="option.label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementRequisitionPage.categoryCode')">
          <el-input v-model="query.categoryCode" clearable placeholder="IT_DEVICE" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="resetQuery">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="requisitionNo" :label="t('procurementRequisitionPage.requisitionNo')" min-width="170" />
        <el-table-column prop="title" :label="t('procurementRequisitionPage.titleColumn')" min-width="210" show-overflow-tooltip />
        <el-table-column prop="primaryCategoryCode" :label="t('procurementRequisitionPage.category')" min-width="130" />
        <el-table-column :label="t('procurementRequisitionPage.estimatedAmount')" min-width="150" align="right">
          <template #default="{ row }">{{ row.totalAmount }} {{ row.currencyCode }}</template>
        </el-table-column>
        <el-table-column :label="t('procurementRequisitionPage.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('procurementRequisitionPage.workflowStart')" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.workflowStartStatus === 'FAILED'" type="danger">
              {{ t('procurementRequisitionPage.startFailed') }}
            </el-tag>
            <el-tag v-else-if="row.workflowStartStatus === 'PENDING'" type="warning">
              {{ t('procurementRequisitionPage.starting') }}
            </el-tag>
            <span v-else-if="row.workflowStartStatus === 'STARTED'">
              {{ t('procurementRequisitionPage.approvalAttempt', { count: row.approvalAttempt }) }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('procurementRequisitionPage.createTime')" min-width="170" />
        <el-table-column :label="t('common.actions')" min-width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              {{ t('procurementRequisitionPage.detail') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'REJECTED'"
              v-permission="'procurement:requisition:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:requisition:submit'"
              link
              type="success"
              @click="submit(row)"
            >
              {{ t('procurementRequisitionPage.submit') }}
            </el-button>
            <el-button
              v-if="row.status === 'SUBMITTED' && row.workflowStartStatus === 'FAILED'"
              v-permission="'procurement:requisition:submit'"
              link
              type="warning"
              @click="retryStart(row)"
            >
              {{ t('procurementRequisitionPage.retryStart') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || (row.status === 'SUBMITTED' && row.workflowStartStatus === 'FAILED')"
              v-permission="'procurement:requisition:cancel'"
              link
              type="warning"
              @click="cancel(row)"
            >
              {{ t('common.cancel') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:requisition:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              {{ t('common.delete') }}
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

    <el-drawer v-model="detailVisible" :title="t('procurementRequisitionPage.detailTitle')" size="760px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('procurementRequisitionPage.requisitionNo')">
              {{ detail.requisitionNo }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('procurementRequisitionPage.status')">
              {{ statusInfo(detail.status).label }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('procurementRequisitionPage.titleColumn')" :span="2">
              {{ detail.title }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('procurementRequisitionPage.category')">
              {{ detail.primaryCategoryCode }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('procurementRequisitionPage.estimatedAmount')">
              {{ detail.totalAmount }} {{ detail.currencyCode }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('procurementRequisitionPage.approvalAttemptLabel')">
              {{ detail.approvalAttempt }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('procurementRequisitionPage.processInstance')">
              {{ detail.processInstanceId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('procurementRequisitionPage.reason')" :span="2">
              {{ detail.reason || '—' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-table :data="detail.lines" class="detail-lines" border>
            <el-table-column prop="lineNo" label="#" width="55" />
            <el-table-column prop="materialCode" :label="t('procurementRequisitionPage.materialCode')" min-width="130" />
            <el-table-column prop="materialName" :label="t('procurementRequisitionPage.materialName')" min-width="160" />
            <el-table-column prop="quantity" :label="t('procurementRequisitionPage.quantity')" min-width="120" align="right" />
            <el-table-column prop="unit" :label="t('procurementRequisitionPage.unit')" width="75" />
            <el-table-column prop="estimatedUnitPrice" :label="t('procurementRequisitionPage.estimatedUnitPrice')" min-width="120" align="right" />
            <el-table-column prop="estimatedTotalPrice" :label="t('procurementRequisitionPage.lineAmount')" min-width="130" align="right" />
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
