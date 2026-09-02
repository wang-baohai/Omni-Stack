<script setup lang="ts">
/** 采购收货页面，支持分批收货、确认占量和待定质检转。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmProcurementGoodsReceipt,
  getProcurementGoodsReceipt,
  listProcurementGoodsReceipts,
  submitProcurementGoodsReceiptQualityResult,
  type ProcurementGoodsReceiptDetail,
  type ProcurementGoodsReceiptQualityStatus,
  type ProcurementGoodsReceiptStatus,
  type ProcurementGoodsReceiptSummary,
} from '@/api/procurement-goods-receipt'
import GoodsReceiptForm from '@/components/procurement/GoodsReceiptForm.vue'

const { t } = useI18n()
const loading = ref(false)
const rows = ref<ProcurementGoodsReceiptSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  keyword: string
  status?: ProcurementGoodsReceiptStatus
  receiveTimeRange: string[]
}>({ keyword: '', status: undefined, receiveTimeRange: [] })

const receiptStatusMap = computed<Record<ProcurementGoodsReceiptStatus, { label: string; type: 'info' | 'success' }>>(() => ({
  DRAFT: { label: t('procurementGoodsReceiptPage.statusDraft'), type: 'info' },
  CONFIRMED: { label: t('procurementGoodsReceiptPage.statusConfirmed'), type: 'success' },
}))
const qualityStatusMap = computed<Record<ProcurementGoodsReceiptQualityStatus, { label: string; type: 'warning' | 'success' | 'danger' }>>(() => ({
  PASS: { label: t('procurementGoodsReceiptForm.qualityPass'), type: 'success' },
  FAIL: { label: t('procurementGoodsReceiptForm.qualityFail'), type: 'danger' },
  PENDING: { label: t('procurementGoodsReceiptForm.qualityPending'), type: 'warning' },
}))

function receiptStatusInfo(status: ProcurementGoodsReceiptStatus) {
  return receiptStatusMap.value[status]
}

function qualityStatusInfo(status: ProcurementGoodsReceiptQualityStatus) {
  return qualityStatusMap.value[status]
}

async function loadRows() {
  loading.value = true
  try {
    const response = await listProcurementGoodsReceipts({
      keyword: query.keyword.trim() || undefined,
      status: query.status,
      receiveTimeFrom: query.receiveTimeRange[0],
      receiveTimeTo: query.receiveTimeRange[1],
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
  Object.assign(query, { keyword: '', status: undefined, receiveTimeRange: [] })
  search()
}

const createVisible = ref(false)

function openCreate() {
  createVisible.value = true
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProcurementGoodsReceiptDetail>()

async function loadDetail(id: number) {
  const response = await getProcurementGoodsReceipt(id)
  detail.value = response.data.data
  return response.data.data
}

async function openDetail(row: ProcurementGoodsReceiptSummary) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    await loadDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function confirmReceipt(row: ProcurementGoodsReceiptSummary) {
  try {
    await ElMessageBox.confirm(
      t('procurementGoodsReceiptMessages.confirmText', { no: row.grNo }),
      t('procurementGoodsReceiptMessages.confirmTitle'),
      { type: 'warning' },
    )
    await confirmProcurementGoodsReceipt(row.id, row.version)
    ElMessage.success(t('procurementGoodsReceiptMessages.confirmed'))
  } finally {
    await loadRows()
  }
}

interface QualityResultLine {
  selected: boolean
  goodsReceiptLineId: number
  materialName: string
  receivedQuantity: string
  qualityStatus: 'PASS' | 'FAIL'
}

const qualityVisible = ref(false)
const qualityReceipt = ref<ProcurementGoodsReceiptDetail>()
const qualityLines = ref<QualityResultLine[]>([])

async function openQualityResult(row: ProcurementGoodsReceiptSummary) {
  const receipt = await loadDetail(row.id)
  const pending = receipt.lines.filter((line) => line.qualityStatus === 'PENDING')
  if (!pending.length) {
    ElMessage.info(t('procurementGoodsReceiptMessages.noPendingQualityLines'))
    return
  }
  qualityReceipt.value = receipt
  qualityLines.value = pending.map((line) => ({
    selected: true,
    goodsReceiptLineId: line.id,
    materialName: line.materialName,
    receivedQuantity: line.receivedQuantity,
    qualityStatus: 'PASS',
  }))
  qualityVisible.value = true
}

async function submitQualityResult() {
  if (!qualityReceipt.value) return
  const selected = qualityLines.value.filter((line) => line.selected)
  if (!selected.length) {
    ElMessage.warning(t('procurementGoodsReceiptMessages.noSelectedQualityLines'))
    return
  }
  await submitProcurementGoodsReceiptQualityResult(
    qualityReceipt.value.id,
    qualityReceipt.value.version,
    selected.map((line) => ({
      goodsReceiptLineId: line.goodsReceiptLineId,
      qualityStatus: line.qualityStatus,
    })),
  )
  ElMessage.success(t('procurementGoodsReceiptMessages.qualitySubmitted'))
  qualityVisible.value = false
  await loadRows()
}

onMounted(loadRows)
</script>

<template>
  <div class="goods-receipt-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('procurementGoodsReceiptPage.notice')"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('procurementGoodsReceiptPage.title') }}</span>
          <el-button
            v-permission="'procurement:goods-receipt:create'"
            type="primary"
            @click="openCreate"
          >
            {{ t('procurementGoodsReceiptPage.create') }}
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item :label="t('procurementRfqPage.keyword')">
          <el-input
            v-model="query.keyword"
            clearable
            :placeholder="t('procurementGoodsReceiptPage.keywordPlaceholder')"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item :label="t('procurementRfqPage.status')">
          <el-select v-model="query.status" clearable :placeholder="t('procurementRfqPage.all')" style="width: 130px">
            <el-option
              v-for="(option, value) in receiptStatusMap"
              :key="value"
              :label="option.label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementGoodsReceiptForm.receiveTime')">
          <el-date-picker
            v-model="query.receiveTimeRange"
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
        <el-table-column prop="grNo" :label="t('procurementGoodsReceiptPage.receiptNo')" min-width="180" />
        <el-table-column prop="poNo" :label="t('procurementGoodsReceiptPage.poNo')" min-width="180" />
        <el-table-column prop="receiveTime" :label="t('procurementGoodsReceiptForm.receiveTime')" min-width="175" />
        <el-table-column prop="remark" :label="t('procurementGoodsReceiptForm.remark')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('procurementRfqPage.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="receiptStatusInfo(row.status).type">
              {{ receiptStatusInfo(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confirmedTime" :label="t('procurementGoodsReceiptPage.confirmedTime')" min-width="175">
          <template #default="{ row }">{{ row.confirmedTime || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" min-width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('srmRiskPage.detail') }}</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:goods-receipt:confirm'"
              link
              type="success"
              @click="confirmReceipt(row)"
            >
              {{ t('procurementGoodsReceiptPage.confirmReceipt') }}
            </el-button>
            <el-button
              v-if="row.status === 'CONFIRMED'"
              v-permission="'procurement:goods-receipt:confirm'"
              link
              type="warning"
              @click="openQualityResult(row)"
            >
              {{ t('procurementGoodsReceiptPage.qualityResult') }}
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

    <GoodsReceiptForm v-model="createVisible" @saved="loadRows" />

    <el-drawer v-model="detailVisible" :title="t('procurementGoodsReceiptPage.detailTitle')" size="72%">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="3" border>
          <el-descriptions-item :label="t('procurementGoodsReceiptPage.receiptNo')">{{ detail.grNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementGoodsReceiptForm.purchaseOrder')">{{ detail.poNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementRfqPage.status')">
            <el-tag :type="receiptStatusInfo(detail.status).type">
              {{ receiptStatusInfo(detail.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('procurementGoodsReceiptForm.receiveTime')">{{ detail.receiveTime }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementGoodsReceiptPage.confirmedTime')">{{ detail.confirmedTime || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementGoodsReceiptPage.receiverId')">{{ detail.receiverUserId }}</el-descriptions-item>
          <el-descriptions-item :label="t('procurementGoodsReceiptForm.remark')" :span="3">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail">
          <h3>{{ t('procurementGoodsReceiptPage.linesTitle') }}</h3>
          <el-table :data="detail.lines" border>
            <el-table-column prop="lineNo" :label="t('procurementPurchaseOrderTracker.lineNo')" width="70" />
            <el-table-column prop="materialCode" :label="t('procurementGoodsReceiptForm.materialCode')" min-width="130" />
            <el-table-column prop="materialName" :label="t('procurementGoodsReceiptForm.materialName')" min-width="180" />
            <el-table-column prop="orderedQuantity" :label="t('procurementPurchaseOrderTracker.orderQuantity')" min-width="110" align="right" />
            <el-table-column prop="receivedQuantity" :label="t('procurementGoodsReceiptForm.receivedQuantity')" min-width="110" align="right" />
            <el-table-column prop="unit" :label="t('procurementGoodsReceiptForm.unit')" width="80" />
            <el-table-column :label="t('procurementGoodsReceiptForm.qualityStatus')" min-width="100">
              <template #default="{ row }">
                <el-tag :type="qualityStatusInfo(row.qualityStatus).type">
                  {{ qualityStatusInfo(row.qualityStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('procurementMaterialPage.assetManaged')" width="90">
              <template #default="{ row }">{{ row.assetManaged ? t('srmResources.yes') : t('procurementMaterialPage.no') }}</template>
            </el-table-column>
            <el-table-column prop="remark" :label="t('procurementGoodsReceiptForm.remark')" min-width="150" show-overflow-tooltip />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="qualityVisible" :title="t('procurementGoodsReceiptPage.qualityTitle')" width="720px">
      <el-alert
        type="warning"
        :closable="false"
        :title="t('procurementGoodsReceiptPage.qualityNotice')"
      />
      <el-table :data="qualityLines" border class="quality-table">
        <el-table-column :label="t('procurementGoodsReceiptPage.process')" width="65" align="center">
          <template #default="{ row }"><el-checkbox v-model="row.selected" /></template>
        </el-table-column>
        <el-table-column prop="materialName" :label="t('procurementGoodsReceiptForm.materialName')" min-width="200" />
        <el-table-column prop="receivedQuantity" :label="t('procurementGoodsReceiptPage.receivedQuantity')" min-width="110" />
        <el-table-column :label="t('procurementGoodsReceiptPage.finalResult')" min-width="150">
          <template #default="{ row }">
            <el-radio-group v-model="row.qualityStatus" :disabled="!row.selected">
              <el-radio value="PASS">{{ t('procurementGoodsReceiptForm.qualityPass') }}</el-radio>
              <el-radio value="FAIL">{{ t('procurementGoodsReceiptForm.qualityFail') }}</el-radio>
            </el-radio-group>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="qualityVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitQualityResult">{{ t('procurementGoodsReceiptPage.submitResult') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.goods-receipt-page {
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

.quality-table {
  margin-top: 16px;
}

h3 {
  margin: 24px 0 12px;
}
</style>
