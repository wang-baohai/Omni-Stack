<script setup lang="ts">
/** 采购询价页面，覆盖草稿维护、供应商邀请、发送及状态跟踪。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  cancelProcurementRfq,
  createProcurementRfq,
  deleteProcurementRfq,
  getProcurementRfq,
  listProcurementRfqs,
  listProcurementRfqSupplierOptions,
  sendProcurementRfq,
  updateProcurementRfq,
  type ProcurementRfqDetail,
  type ProcurementRfqStatus,
  type ProcurementRfqSummary,
  type ProcurementRfqSupplierOption,
} from '@/api/procurement-rfq'
import {
  listProcurementRequisitions,
  type ProcurementRequisitionSummary,
} from '@/api/procurement-requisition'
import RfqCompareView from '@/components/procurement/RfqCompareView.vue'

const { t } = useI18n()
const loading = ref(false)
const rows = ref<ProcurementRfqSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  keyword: string
  status?: ProcurementRfqStatus
  deadlineRange: string[]
}>({ keyword: '', status: undefined, deadlineRange: [] })

const statusMap = computed<Record<
  ProcurementRfqStatus,
  { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }
>>(() => ({
  DRAFT: { label: t('procurementRfqPage.statusDraft'), type: 'info' },
  SENT: { label: t('procurementRfqPage.statusSent'), type: 'primary' },
  CLOSED: { label: t('procurementRfqPage.statusClosed'), type: 'warning' },
  AWARDED: { label: t('procurementRfqPage.statusAwarded'), type: 'success' },
  CANCELLED: { label: t('procurementRfqPage.statusCancelled'), type: 'danger' },
}))

function statusInfo(status: ProcurementRfqStatus) {
  return statusMap.value[status]
}

async function loadRows() {
  loading.value = true
  try {
    const response = await listProcurementRfqs({
      keyword: query.keyword.trim() || undefined,
      status: query.status,
      deadlineFrom: query.deadlineRange[0],
      deadlineTo: query.deadlineRange[1],
      page: currentPage.value,
      size: pageSize.value,
    })
    rows.value = response.data.data.records
    total.value = response.data.data.total
  } finally {
    loading.value = false
  }
}

function search() {
  currentPage.value = 1
  loadRows()
}

function resetQuery() {
  Object.assign(query, { keyword: '', status: undefined, deadlineRange: [] })
  search()
}

const requisitionLoading = ref(false)
const requisitionOptions = ref<ProcurementRequisitionSummary[]>([])

async function loadRequisitionOptions(keyword?: string) {
  requisitionLoading.value = true
  try {
    const response = await listProcurementRequisitions({
      keyword: keyword?.trim() || undefined,
      status: 'APPROVED',
      page: 1,
      size: 100,
    })
    requisitionOptions.value = response.data.data.records
  } finally {
    requisitionLoading.value = false
  }
}

function selectedRequisition() {
  return requisitionOptions.value.find((item) => item.id === form.requisitionId)
}

const supplierLoading = ref(false)
const supplierOptions = ref<ProcurementRfqSupplierOption[]>([])

async function loadSupplierOptions(keyword?: string) {
  supplierLoading.value = true
  try {
    const response = await listProcurementRfqSupplierOptions({
      keyword: keyword?.trim() || undefined,
      categoryCode: selectedRequisition()?.primaryCategoryCode,
      limit: 100,
    })
    const existing = new Map(supplierOptions.value.map((item) => [item.id, item]))
    for (const item of response.data.data) existing.set(item.id, item)
    supplierOptions.value = [...existing.values()]
  } finally {
    supplierLoading.value = false
  }
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editing = ref<ProcurementRfqDetail>()
const form = reactive<{
  requisitionId?: number
  title: string
  quotationDeadline: string
  supplierIds: number[]
  version?: number
}>({ title: '', quotationDeadline: '', supplierIds: [] })
const rules = computed<FormRules>(() => ({
  requisitionId: [{ required: true, message: t('procurementRfqMessages.requisitionRequired'), trigger: 'change' }],
  title: [
    { required: true, message: t('procurementRfqMessages.titleRequired'), trigger: 'blur' },
    { max: 200, message: t('procurementRfqMessages.titleLength'), trigger: 'blur' },
  ],
  quotationDeadline: [{ required: true, message: t('procurementRfqMessages.deadlineRequired'), trigger: 'change' }],
  supplierIds: [{ required: true, type: 'array', min: 1, message: t('procurementRfqMessages.supplierRequired') }],
}))

async function openCreate() {
  editing.value = undefined
  supplierOptions.value = []
  Object.assign(form, {
    requisitionId: undefined,
    title: '',
    quotationDeadline: '',
    supplierIds: [],
    version: undefined,
  })
  dialogVisible.value = true
  await loadRequisitionOptions()
}

async function onRequisitionChanged() {
  const requisition = selectedRequisition()
  form.supplierIds = []
  supplierOptions.value = []
  if (requisition && !form.title.trim()) form.title = `${requisition.title}${t('procurementRfqPage.titleSuffix')}`
  if (requisition) await loadSupplierOptions()
}

function preserveSupplierSnapshots(detail: ProcurementRfqDetail) {
  const existing = new Map(supplierOptions.value.map((item) => [item.id, item]))
  for (const supplier of detail.suppliers) {
    if (!existing.has(supplier.supplierId)) {
      existing.set(supplier.supplierId, {
        id: supplier.supplierId,
        supplierNo: `#${supplier.supplierId}`,
        name: supplier.supplierName,
        levelCode: null,
        categoryCode: null,
      })
    }
  }
  supplierOptions.value = [...existing.values()]
}

async function openEdit(row: ProcurementRfqSummary) {
  const response = await getProcurementRfq(row.id)
  const detail = response.data.data
  editing.value = detail
  await loadRequisitionOptions()
  if (!requisitionOptions.value.some((item) => item.id === detail.requisitionId)) {
    requisitionOptions.value.push({
      id: detail.requisitionId,
      requisitionNo: `#${detail.requisitionId}`,
      title: detail.title,
      requesterUserId: 0,
      requesterUnitId: 0,
      primaryCategoryCode: detail.lines[0]?.categoryCode || '',
      totalAmount: '0',
      currencyCode: detail.currencyCode,
      status: 'APPROVED',
      workflowStartStatus: 'STARTED',
      approvalAttempt: 0,
      version: 0,
      createTime: '',
      updateTime: '',
    })
  }
  Object.assign(form, {
    requisitionId: detail.requisitionId,
    title: detail.title,
    quotationDeadline: detail.quotationDeadline,
    supplierIds: detail.suppliers.map((supplier) => supplier.supplierId),
    version: detail.version,
  })
  supplierOptions.value = []
  await loadSupplierOptions()
  preserveSupplierSnapshots(detail)
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.requisitionId) return
  const request = {
    title: form.title.trim(),
    quotationDeadline: form.quotationDeadline,
    supplierIds: [...new Set(form.supplierIds)],
  }
  if (editing.value) {
    await updateProcurementRfq(editing.value.id, {
      ...request,
      version: form.version ?? editing.value.version,
    })
  } else {
    await createProcurementRfq({ ...request, requisitionId: form.requisitionId })
  }
  ElMessage.success(t('procurementRfqMessages.saveSuccess'))
  dialogVisible.value = false
  await loadRows()
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProcurementRfqDetail>()

async function openDetail(row: ProcurementRfqSummary) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getProcurementRfq(row.id)
    detail.value = response.data.data
  } finally {
    detailLoading.value = false
  }
}

async function remove(row: ProcurementRfqSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementRfqMessages.deleteConfirm', { no: row.rfqNo }),
      t('procurementRfqMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteProcurementRfq(row.id, row.version)
    ElMessage.success(t('procurementRfqMessages.deleteSuccess'))
    await loadRows()
  } catch {
    // 用户取消或请求失败时保留当前列表。
  }
}

async function send(row: ProcurementRfqSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementRfqMessages.sendConfirm', { no: row.rfqNo }),
      t('procurementRfqMessages.sendTitle'),
      { type: 'warning' },
    )
    await sendProcurementRfq(row.id, row.version)
    ElMessage.success(t('procurementRfqMessages.sendSuccess'))
  } finally {
    await loadRows()
  }
}

const comparisonVisible = ref(false)
const comparisonRfq = ref<ProcurementRfqSummary>()

function openComparison(row: ProcurementRfqSummary) {
  comparisonRfq.value = row
  comparisonVisible.value = true
}

async function cancel(row: ProcurementRfqSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementRfqMessages.cancelConfirm', { no: row.rfqNo }),
      t('procurementRfqMessages.cancelTitle'),
      { type: 'warning' },
    )
    await cancelProcurementRfq(row.id, row.version)
    ElMessage.success(t('procurementRfqMessages.cancelSuccess'))
  } finally {
    await loadRows()
  }
}

onMounted(loadRows)
</script>

<template>
  <div class="rfq-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('procurementRfqPage.notice')"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('procurementRfqPage.title') }}</span>
          <el-button v-permission="'procurement:rfq:create'" type="primary" @click="openCreate">
            {{ t('procurementRfqPage.create') }}
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item :label="t('procurementRfqPage.keyword')">
          <el-input
            v-model="query.keyword"
            clearable
            :placeholder="t('procurementRfqPage.keywordPlaceholder')"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item :label="t('procurementRfqPage.status')">
          <el-select v-model="query.status" clearable :placeholder="t('procurementRfqPage.all')" style="width: 130px">
            <el-option
              v-for="(option, key) in statusMap"
              :key="key"
              :label="option.label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementRfqPage.deadlineRange')">
          <el-date-picker
            v-model="query.deadlineRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            :start-placeholder="t('procurementRfqPage.startTime')"
            :end-placeholder="t('procurementRfqPage.endTime')"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="resetQuery">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="rfqNo" :label="t('procurementRfqPage.rfqNo')" min-width="180" />
        <el-table-column prop="title" :label="t('procurementRfqPage.titleColumn')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="quotationDeadline" :label="t('procurementRfqPage.deadline')" min-width="175" />
        <el-table-column prop="currencyCode" :label="t('procurementOverviewPage.currency')" width="80" />
        <el-table-column :label="t('procurementRfqPage.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('procurementRequisitionPage.createTime')" min-width="175" />
        <el-table-column :label="t('common.actions')" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              {{ t('procurementRequisitionPage.detail') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:rfq:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:rfq:send'"
              link
              type="success"
              @click="send(row)"
            >
              {{ t('procurementPurchaseOrderPage.send') }}
            </el-button>
            <el-button
              v-if="row.status === 'SENT'"
              v-permission="'procurement:rfq:award'"
              link
              type="success"
              @click="openComparison(row)"
            >
              {{ t('procurementRfqPage.compareAward') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:rfq:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              {{ t('common.delete') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'SENT'"
              v-permission="'procurement:rfq:cancel'"
              link
              type="danger"
              @click="cancel(row)"
            >
              {{ t('common.cancel') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[5, 10, 20, 50, 100]"
        @current-change="loadRows"
        @size-change="search"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? t('procurementRfqPage.editTitle') : t('procurementRfqPage.createTitle')"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="t('procurementRfqPage.sourceRequisition')" prop="requisitionId">
          <el-select
            v-model="form.requisitionId"
            :disabled="Boolean(editing)"
            filterable
            remote
            :remote-method="loadRequisitionOptions"
            :loading="requisitionLoading"
            :placeholder="t('procurementRfqPage.requisitionPlaceholder')"
            style="width: 100%"
            @change="onRequisitionChanged"
          >
            <el-option
              v-for="item in requisitionOptions"
              :key="item.id"
              :label="`${item.requisitionNo} · ${item.title}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementRfqPage.titleField')" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('procurementRfqPage.quotationDeadline')" prop="quotationDeadline">
          <el-date-picker
            v-model="form.quotationDeadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="t('procurementRfqPage.futureTimePlaceholder')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('procurementRfqPage.invitedSuppliers')" prop="supplierIds">
          <el-select
            v-model="form.supplierIds"
            multiple
            filterable
            remote
            :remote-method="loadSupplierOptions"
            :loading="supplierLoading"
            :disabled="!form.requisitionId"
            :placeholder="t('procurementRfqPage.supplierPlaceholder')"
            style="width: 100%"
          >
            <el-option
              v-for="item in supplierOptions"
              :key="item.id"
              :label="`${item.supplierNo} · ${item.name}`"
              :value="item.id"
            >
              <span>{{ item.supplierNo }} · {{ item.name }}</span>
              <span class="option-extra">{{ item.categoryCode || t('procurementRfqPage.uncategorized') }}</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('procurementRfqPage.saveDraft') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="t('procurementRfqPage.detailTitle')" size="72%">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="3" border>
          <el-descriptions-item :label="t('procurementRfqPage.rfqNo')">{{ detail.rfqNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementRfqPage.status')">
            <el-tag :type="statusInfo(detail.status).type">
              {{ statusInfo(detail.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('procurementOverviewPage.currency')">{{ detail.currencyCode }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementRfqPage.titleColumn')" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementRfqPage.requisitionId')">{{ detail.requisitionId }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementRfqPage.deadline')">{{ detail.quotationDeadline }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementRfqPage.sentTime')">{{ detail.sentTime || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementRfqPage.version')">{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.awardedQuotationId" :label="t('procurementRfqCompare.winningQuotation')">
            #{{ detail.awardedQuotationId }} / v{{ detail.awardedQuotationVersion }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.awardedTime" :label="t('procurementRfqPage.awardedTime')">
            {{ detail.awardedTime }}
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="detail">
          <h3>{{ t('procurementRfqPage.linesTitle') }}</h3>
          <el-table :data="detail.lines" border>
            <el-table-column prop="lineNo" :label="t('procurementPurchaseOrderTracker.lineNo')" width="70" />
            <el-table-column prop="materialCode" :label="t('procurementRequisitionPage.materialCode')" min-width="140" />
            <el-table-column prop="materialName" :label="t('procurementRequisitionPage.materialName')" min-width="180" />
            <el-table-column prop="categoryCode" :label="t('procurementRequisitionPage.category')" min-width="120" />
            <el-table-column prop="quantity" :label="t('procurementRequisitionPage.quantity')" min-width="120" align="right" />
            <el-table-column prop="unit" :label="t('procurementRequisitionPage.unit')" width="90" />
            <el-table-column prop="remark" :label="t('procurementGoodsReceiptForm.remark')" min-width="160" show-overflow-tooltip />
          </el-table>

          <h3>{{ t('procurementRfqPage.suppliersTitle') }}</h3>
          <el-table :data="detail.suppliers" border>
            <el-table-column prop="supplierName" :label="t('procurementPurchaseOrderTracker.supplier')" min-width="200" />
            <el-table-column prop="status" :label="t('procurementRfqPage.invitationStatus')" width="110" />
            <el-table-column prop="invitedTime" :label="t('procurementRfqPage.invitedTime')" min-width="175">
              <template #default="{ row }">{{ row.invitedTime || '—' }}</template>
            </el-table-column>
            <el-table-column prop="quotationId" :label="t('procurementRfqPage.quotationId')" min-width="110">
              <template #default="{ row }">{{ row.quotationId || '—' }}</template>
            </el-table-column>
            <el-table-column prop="quotationVersion" :label="t('procurementRfqPage.quotationVersion')" width="100">
              <template #default="{ row }">{{ row.quotationVersion ?? '—' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <RfqCompareView v-model="comparisonVisible" :rfq-row="comparisonRfq" @awarded="loadRows" />
  </div>
</template>

<style scoped>
.rfq-page {
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

.option-extra {
  float: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

h3 {
  margin: 24px 0 12px;
}
</style>
