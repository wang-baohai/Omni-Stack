<script setup lang="ts">
/** 采购收货页面，支持分批收货、确认占量和待定质检结转。 */
import { onMounted, reactive, ref } from 'vue'
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

const receiptStatusOptions = [
  { value: 'DRAFT' as const, label: '草稿', type: 'info' as const },
  { value: 'CONFIRMED' as const, label: '已确认', type: 'success' as const },
]
const receiptStatusMap = Object.fromEntries(
  receiptStatusOptions.map((item) => [item.value, item]),
) as Record<ProcurementGoodsReceiptStatus, (typeof receiptStatusOptions)[number]>
const qualityStatusOptions: Array<{
  value: ProcurementGoodsReceiptQualityStatus
  label: string
  type: 'warning' | 'success' | 'danger'
}> = [
  { value: 'PASS', label: '合格', type: 'success' },
  { value: 'FAIL', label: '不合格', type: 'danger' },
  { value: 'PENDING', label: '待定', type: 'warning' },
]
const qualityStatusMap = Object.fromEntries(
  qualityStatusOptions.map((item) => [item.value, item]),
) as Record<ProcurementGoodsReceiptQualityStatus, (typeof qualityStatusOptions)[number]>

function receiptStatusInfo(status: ProcurementGoodsReceiptStatus) {
  return receiptStatusMap[status]
}

function qualityStatusInfo(status: ProcurementGoodsReceiptQualityStatus) {
  return qualityStatusMap[status]
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

async function confirm(row: ProcurementGoodsReceiptSummary) {
  try {
    await ElMessageBox.confirm(
      `确认收货单“${row.grNo}”？确认后将占用订单待收数量且不可撤回。`,
      '确认收货',
      { type: 'warning' },
    )
    await confirmProcurementGoodsReceipt(row.id, row.version)
    ElMessage.success('收货单已确认')
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
    ElMessage.info('该收货单没有待定质检行')
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
    ElMessage.warning('请至少选择一条待定质检行')
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
  ElMessage.success('质检结果已提交')
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
      title="草稿不占用订单数量；确认时服务端会锁定订单并按所有已确认收货重新计算，防止并发超收。"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>采购收货</span>
          <el-button
            v-permission="'procurement:goods-receipt:create'"
            type="primary"
            @click="openCreate"
          >
            新建收货
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="收货单号或订单号"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option
              v-for="option in receiptStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="收货时间">
          <el-date-picker
            v-model="query.receiveTimeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="grNo" label="收货单号" min-width="180" />
        <el-table-column prop="poNo" label="采购订单号" min-width="180" />
        <el-table-column prop="receiveTime" label="收货时间" min-width="175" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="receiptStatusInfo(row.status).type">
              {{ receiptStatusInfo(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="confirmedTime" label="确认时间" min-width="175">
          <template #default="{ row }">{{ row.confirmedTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:goods-receipt:confirm'"
              link
              type="success"
              @click="confirm(row)"
            >
              确认收货
            </el-button>
            <el-button
              v-if="row.status === 'CONFIRMED'"
              v-permission="'procurement:goods-receipt:confirm'"
              link
              type="warning"
              @click="openQualityResult(row)"
            >
              质检结果
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

    <el-drawer v-model="detailVisible" title="收货详情" size="72%">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="3" border>
          <el-descriptions-item label="收货单号">{{ detail.grNo }}</el-descriptions-item>
          <el-descriptions-item label="采购订单">{{ detail.poNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="receiptStatusInfo(detail.status).type">
              {{ receiptStatusInfo(detail.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="收货时间">{{ detail.receiveTime }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ detail.confirmedTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="收货人 ID">{{ detail.receiverUserId }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail">
          <h3>收货明细</h3>
          <el-table :data="detail.lines" border>
            <el-table-column prop="lineNo" label="行号" width="70" />
            <el-table-column prop="materialCode" label="物料编码" min-width="130" />
            <el-table-column prop="materialName" label="物料名称" min-width="180" />
            <el-table-column prop="orderedQuantity" label="订单数量" min-width="110" align="right" />
            <el-table-column prop="receivedQuantity" label="本次数量" min-width="110" align="right" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column label="质检状态" min-width="100">
              <template #default="{ row }">
                <el-tag :type="qualityStatusInfo(row.qualityStatus).type">
                  {{ qualityStatusInfo(row.qualityStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="资产化" width="90">
              <template #default="{ row }">{{ row.assetManaged ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="qualityVisible" title="提交质检结果" width="720px">
      <el-alert
        type="warning"
        :closable="false"
        title="仅处理从 PENDING 结转的行；合格的资产化物料会发布新的资产候选事件。"
      />
      <el-table :data="qualityLines" border class="quality-table">
        <el-table-column label="处理" width="65" align="center">
          <template #default="{ row }"><el-checkbox v-model="row.selected" /></template>
        </el-table-column>
        <el-table-column prop="materialName" label="物料名称" min-width="200" />
        <el-table-column prop="receivedQuantity" label="收货数量" min-width="110" />
        <el-table-column label="最终结果" min-width="150">
          <template #default="{ row }">
            <el-radio-group v-model="row.qualityStatus" :disabled="!row.selected">
              <el-radio value="PASS">合格</el-radio>
              <el-radio value="FAIL">不合格</el-radio>
            </el-radio-group>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="qualityVisible = false">取消</el-button>
        <el-button type="primary" @click="submitQualityResult">提交结果</el-button>
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
