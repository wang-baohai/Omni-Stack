<script setup lang="ts">
/** 采购概览页面，展示待处理数量、订单状态和按币种隔离的支出分析。 */
import { computed, onMounted, ref } from 'vue'
import {
  getProcurementOverviewSummary,
  getProcurementSpendAnalysis,
  type ProcurementOverviewSummary,
  type ProcurementSpendDimension,
  type ProcurementSpendItem,
} from '@/api/procurement-overview'

const loading = ref(false)
const summary = ref<ProcurementOverviewSummary>()
const spendLoading = ref(false)
const spendDimension = ref<ProcurementSpendDimension>('CATEGORY')
const spendRows = ref<ProcurementSpendItem[]>([])

const dimensionOptions: Array<{
  value: ProcurementSpendDimension
  label: string
}> = [
  { value: 'CATEGORY', label: '物料品类' },
  { value: 'SUPPLIER', label: '供应商' },
  { value: 'DEPARTMENT', label: '负责部门' },
]

const orderStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SENT: '已发送',
  CONFIRMED: '已确认',
  PARTIAL_RECEIVED: '部分收货',
  RECEIVED: '已收齐',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const activeOrderCount = computed(() =>
  (summary.value?.purchaseOrderStatusCounts || [])
    .filter((item) => ['SENT', 'CONFIRMED', 'PARTIAL_RECEIVED'].includes(item.status))
    .reduce((total, item) => total + item.count, 0),
)

async function loadSummary() {
  loading.value = true
  try {
    const response = await getProcurementOverviewSummary()
    summary.value = response.data.data
  } finally {
    loading.value = false
  }
}

async function loadSpend() {
  spendLoading.value = true
  try {
    const response = await getProcurementSpendAnalysis(spendDimension.value, 20)
    spendRows.value = response.data.data
  } finally {
    spendLoading.value = false
  }
}

async function refresh() {
  await Promise.all([loadSummary(), loadSpend()])
}

function dimensionLabel(dimension: ProcurementSpendDimension) {
  return dimensionOptions.find((item) => item.value === dimension)?.label || dimension
}

onMounted(refresh)
</script>

<template>
  <div v-loading="loading" class="procurement-overview-page">
    <div class="page-heading">
      <div>
        <h2>采购概览</h2>
        <p>统计严格沿用当前用户的采购数据范围，金额按币种分别汇总。</p>
      </div>
      <el-button type="primary" plain @click="refresh">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="metric-card metric-warning">
          <div class="metric-label">审批中的请购</div>
          <div class="metric-value">{{ summary?.pendingApprovalRequisitionCount ?? 0 }}</div>
          <div class="metric-hint">等待 Workflow 审批完成</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="metric-card metric-primary">
          <div class="metric-label">等待供应商报价</div>
          <div class="metric-value">{{ summary?.waitingQuotationRfqCount ?? 0 }}</div>
          <div class="metric-hint">仍在报价截止时间内</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="metric-card metric-success">
          <div class="metric-label">执行中的订单</div>
          <div class="metric-value">{{ activeOrderCount }}</div>
          <div class="metric-hint">已发送、已确认或部分收货</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="metric-card metric-danger">
          <div class="metric-label">待确认收货草稿</div>
          <div class="metric-value">{{ summary?.draftGoodsReceiptCount ?? 0 }}</div>
          <div class="metric-hint">确认后才占用订单数量</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>采购订单状态</span>
              <span class="card-note">当前数据范围</span>
            </div>
          </template>
          <div class="status-grid">
            <div
              v-for="item in summary?.purchaseOrderStatusCounts || []"
              :key="item.status"
              class="status-item"
            >
              <span>{{ orderStatusLabels[item.status] || item.status }}</span>
              <strong>{{ item.count }}</strong>
            </div>
          </div>
          <el-empty
            v-if="!summary?.purchaseOrderStatusCounts.length"
            description="暂无采购订单"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>已确认采购承诺</span>
              <span class="card-note">按币种隔离</span>
            </div>
          </template>
          <div
            v-for="item in summary?.committedAmountsByCurrency || []"
            :key="item.currencyCode"
            class="currency-item"
          >
            <span>{{ item.currencyCode }}</span>
            <strong>{{ item.amount }}</strong>
          </div>
          <el-empty
            v-if="!summary?.committedAmountsByCurrency.length"
            description="暂无已确认采购金额"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>支出分析</span>
          <el-segmented
            v-model="spendDimension"
            :options="dimensionOptions"
            @change="loadSpend"
          />
        </div>
      </template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="`当前按${dimensionLabel(spendDimension)}聚合；不同币种不会相加。`"
      />
      <el-table v-loading="spendLoading" :data="spendRows" stripe class="spend-table">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="dimensionName" :label="dimensionLabel(spendDimension)" min-width="220" />
        <el-table-column prop="dimensionKey" label="编码 / ID" min-width="150" />
        <el-table-column prop="currencyCode" label="币种" width="90" />
        <el-table-column prop="amount" label="已确认承诺金额" min-width="180" align="right" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.procurement-overview-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading,
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h2 {
  margin: 0 0 6px;
}

.page-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.metric-card {
  height: 150px;
  border-top: 3px solid var(--el-border-color);
}

.metric-warning {
  border-top-color: var(--el-color-warning);
}

.metric-primary {
  border-top-color: var(--el-color-primary);
}

.metric-success {
  border-top-color: var(--el-color-success);
}

.metric-danger {
  border-top-color: var(--el-color-danger);
}

.metric-label,
.metric-hint,
.card-note {
  color: var(--el-text-color-secondary);
}

.metric-value {
  margin: 8px 0;
  font-size: 34px;
  font-weight: 700;
}

.metric-hint,
.card-note {
  font-size: 12px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
}

.status-item,
.currency-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.status-item strong {
  font-size: 22px;
}

.currency-item + .currency-item {
  margin-top: 10px;
}

.currency-item strong {
  font-size: 20px;
}

.spend-table {
  margin-top: 16px;
}
</style>
