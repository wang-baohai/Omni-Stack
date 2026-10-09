<script setup lang="ts">
/** 采购订单页面，跟踪询价定标后订单的发送、确认和收货进度。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  cancelProcurementPurchaseOrder,
  confirmProcurementPurchaseOrder,
  deleteProcurementPurchaseOrder,
  getProcurementPurchaseOrder,
  listProcurementPurchaseOrders,
  sendProcurementPurchaseOrder,
  updateProcurementPurchaseOrder,
  type ProcurementPurchaseOrderDetail,
  type ProcurementPurchaseOrderStatus,
  type ProcurementPurchaseOrderSummary,
} from '@/api/procurement-purchase-order'
import PurchaseOrderTracker from '@/components/procurement/PurchaseOrderTracker.vue'

const { t } = useI18n()
const loading = ref(false)
const rows = ref<ProcurementPurchaseOrderSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  keyword: string
  status?: ProcurementPurchaseOrderStatus
  expectedDeliveryRange: string[]
}>({ keyword: '', status: undefined, expectedDeliveryRange: [] })

const statusMap = computed<Record<
  ProcurementPurchaseOrderStatus,
  { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }
>>(() => ({
  DRAFT: { label: t('procurementPurchaseOrderTracker.statusDraft'), type: 'info' },
  SENT: { label: t('procurementPurchaseOrderTracker.statusSent'), type: 'primary' },
  CONFIRMED: { label: t('procurementPurchaseOrderTracker.statusConfirmed'), type: 'success' },
  PARTIAL_RECEIVED: { label: t('procurementPurchaseOrderTracker.statusPartialReceived'), type: 'warning' },
  RECEIVED: { label: t('procurementPurchaseOrderTracker.statusReceived'), type: 'success' },
  CLOSED: { label: t('procurementPurchaseOrderTracker.statusClosed'), type: 'info' },
  CANCELLED: { label: t('procurementPurchaseOrderTracker.statusCancelled'), type: 'danger' },
}))

function statusInfo(status: ProcurementPurchaseOrderStatus) {
  return statusMap.value[status]
}

async function loadRows() {
  loading.value = true
  try {
    const response = await listProcurementPurchaseOrders({
      keyword: query.keyword.trim() || undefined,
      status: query.status,
      expectedDeliveryFrom: query.expectedDeliveryRange[0],
      expectedDeliveryTo: query.expectedDeliveryRange[1],
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
  Object.assign(query, { keyword: '', status: undefined, expectedDeliveryRange: [] })
  search()
}

const editVisible = ref(false)
const formRef = ref<FormInstance>()
const editing = ref<ProcurementPurchaseOrderDetail>()
const form = reactive({
  title: '',
  expectedDeliveryDate: '',
  deliveryAddress: '',
  contactName: '',
  contactPhone: '',
  version: 0,
})
const rules = computed<FormRules>(() => ({
  title: [
    { required: true, message: t('procurementPurchaseOrderMessages.titleRequired'), trigger: 'blur' },
    { max: 200, message: t('procurementPurchaseOrderMessages.titleLength'), trigger: 'blur' },
  ],
  deliveryAddress: [
    { required: true, message: t('procurementPurchaseOrderMessages.addressRequired'), trigger: 'blur' },
    { max: 500, message: t('procurementPurchaseOrderMessages.addressLength'), trigger: 'blur' },
  ],
  contactName: [
    { required: true, message: t('procurementPurchaseOrderMessages.contactRequired'), trigger: 'blur' },
    { max: 100, message: t('procurementPurchaseOrderMessages.contactLength'), trigger: 'blur' },
  ],
  contactPhone: [
    { required: true, message: t('procurementPurchaseOrderMessages.phoneRequired'), trigger: 'blur' },
    { max: 50, message: t('procurementPurchaseOrderMessages.phoneLength'), trigger: 'blur' },
  ],
}))

async function openEdit(row: ProcurementPurchaseOrderSummary) {
  const response = await getProcurementPurchaseOrder(row.id)
  editing.value = response.data.data
  Object.assign(form, {
    title: response.data.data.title,
    expectedDeliveryDate: response.data.data.expectedDeliveryDate || '',
    deliveryAddress: response.data.data.deliveryAddress,
    contactName: response.data.data.contactName,
    contactPhone: response.data.data.contactPhone,
    version: response.data.data.version,
  })
  editVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !editing.value) return
  await updateProcurementPurchaseOrder(editing.value.id, {
    version: form.version,
    title: form.title.trim(),
    expectedDeliveryDate: form.expectedDeliveryDate || undefined,
    deliveryAddress: form.deliveryAddress.trim(),
    contactName: form.contactName.trim(),
    contactPhone: form.contactPhone.trim(),
  })
  ElMessage.success(t('procurementPurchaseOrderMessages.updateSuccess'))
  editVisible.value = false
  await loadRows()
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProcurementPurchaseOrderDetail>()

async function openDetail(row: ProcurementPurchaseOrderSummary) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getProcurementPurchaseOrder(row.id)
    detail.value = response.data.data
  } finally {
    detailLoading.value = false
  }
}

async function remove(row: ProcurementPurchaseOrderSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementPurchaseOrderMessages.deleteConfirm', { no: row.poNo }),
      t('procurementPurchaseOrderMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteProcurementPurchaseOrder(row.id, row.version)
    ElMessage.success(t('procurementPurchaseOrderMessages.deleteSuccess'))
    await loadRows()
  } catch {
    // 用户取消或请求失败时保留当前列表。
  }
}

async function send(row: ProcurementPurchaseOrderSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementPurchaseOrderMessages.sendConfirm', { no: row.poNo }),
      t('procurementPurchaseOrderMessages.sendTitle'),
      { type: 'warning' },
    )
    await sendProcurementPurchaseOrder(row.id, row.version)
    ElMessage.success(t('procurementPurchaseOrderMessages.sendSuccess'))
  } finally {
    await loadRows()
  }
}

async function confirmOrder(row: ProcurementPurchaseOrderSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementPurchaseOrderMessages.confirmText', { no: row.poNo }),
      t('procurementPurchaseOrderMessages.confirmTitle'),
    )
    await confirmProcurementPurchaseOrder(row.id, row.version)
    ElMessage.success(t('procurementPurchaseOrderTracker.statusConfirmed'))
  } finally {
    await loadRows()
  }
}

async function cancelOrder(row: ProcurementPurchaseOrderSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementPurchaseOrderMessages.cancelConfirm', { no: row.poNo }),
      t('procurementPurchaseOrderMessages.cancelTitle'),
      { type: 'warning' },
    )
    await cancelProcurementPurchaseOrder(row.id, row.version)
    ElMessage.success(t('procurementPurchaseOrderMessages.cancelSuccess'))
  } finally {
    await loadRows()
  }
}

onMounted(loadRows)
</script>

<template>
  <div class="purchase-order-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('procurementPurchaseOrderPage.notice')"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('procurementPurchaseOrderPage.title') }}</span>
          <span class="header-tip">{{ t('procurementPurchaseOrderPage.headerTip') }}</span>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item :label="t('procurementPurchaseOrderPage.keyword')">
          <el-input
            v-model="query.keyword"
            clearable
            :placeholder="t('procurementPurchaseOrderPage.keywordPlaceholder')"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item :label="t('procurementPurchaseOrderPage.status')">
          <el-select v-model="query.status" clearable :placeholder="t('procurementPurchaseOrderPage.all')" style="width: 150px">
            <el-option
              v-for="(option, key) in statusMap"
              :key="key"
              :label="option.label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementPurchaseOrderTracker.expectedDelivery')">
          <el-date-picker
            v-model="query.expectedDeliveryRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            :start-placeholder="t('procurementPurchaseOrderPage.startDate')"
            :end-placeholder="t('procurementPurchaseOrderPage.endDate')"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="resetQuery">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="poNo" :label="t('procurementPurchaseOrderTracker.orderNo')" min-width="180" />
        <el-table-column prop="title" :label="t('procurementPurchaseOrderTracker.orderTitle')" min-width="200" show-overflow-tooltip />
        <el-table-column
          prop="supplierNameSnapshot"
          :label="t('procurementPurchaseOrderTracker.supplier')"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column :label="t('procurementPurchaseOrderTracker.orderAmount')" min-width="150" align="right">
          <template #default="{ row }">{{ row.totalAmount }} {{ row.currencyCode }}</template>
        </el-table-column>
        <el-table-column prop="expectedDeliveryDate" :label="t('procurementPurchaseOrderTracker.expectedDelivery')" min-width="120">
          <template #default="{ row }">{{ row.expectedDeliveryDate || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('procurementPurchaseOrderPage.status')" min-width="120">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactPhoneMasked" :label="t('procurementPurchaseOrderTracker.contact')" min-width="130">
          <template #default="{ row }">{{ row.contactPhoneMasked || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" min-width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              {{ t('procurementRequisitionPage.detail') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:purchase-order:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              {{ t('procurementPurchaseOrderPage.editDelivery') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:purchase-order:send'"
              link
              type="success"
              @click="send(row)"
            >
              {{ t('procurementPurchaseOrderPage.send') }}
            </el-button>
            <el-button
              v-if="row.status === 'SENT'"
              v-permission="'procurement:purchase-order:confirm'"
              link
              type="success"
              @click="confirmOrder(row)"
            >
              {{ t('common.confirm') }}
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:purchase-order:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              {{ t('common.delete') }}
            </el-button>
            <el-button
              v-if="['DRAFT', 'SENT', 'CONFIRMED'].includes(row.status)"
              v-permission="'procurement:purchase-order:cancel'"
              link
              type="danger"
              @click="cancelOrder(row)"
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

    <el-dialog v-model="editVisible" :title="t('procurementPurchaseOrderPage.editDeliveryTitle')" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="t('procurementPurchaseOrderTracker.orderTitle')" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('procurementPurchaseOrderTracker.expectedDelivery')">
          <el-date-picker
            v-model="form.expectedDeliveryDate"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="t('procurementPurchaseOrderPage.optional')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('procurementPurchaseOrderTracker.deliveryAddress')" prop="deliveryAddress">
          <el-input v-model="form.deliveryAddress" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item :label="t('procurementPurchaseOrderTracker.contact')" prop="contactName">
          <el-input v-model="form.contactName" maxlength="100" />
        </el-form-item>
        <el-form-item :label="t('procurementPurchaseOrderPage.contactPhone')" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="t('procurementPurchaseOrderPage.detailTitle')" size="76%">
      <div v-loading="detailLoading">
        <PurchaseOrderTracker :order-detail="detail" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.purchase-order-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
