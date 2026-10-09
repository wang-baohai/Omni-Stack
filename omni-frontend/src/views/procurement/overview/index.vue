<script setup lang="ts">
/** 采购概览页面，展示待处理数量、订单状态和按币种隔离的支出分析。 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getProcurementOverviewSummary,
  getProcurementSpendAnalysis,
  type ProcurementOverviewSummary,
  type ProcurementSpendDimension,
  type ProcurementSpendItem,
} from '@/api/procurement-overview'

const { t } = useI18n()
const loading = ref(false)
const summary = ref<ProcurementOverviewSummary>()
const spendLoading = ref(false)
const spendDimension = ref<ProcurementSpendDimension>('CATEGORY')
const spendRows = ref<ProcurementSpendItem[]>([])

const dimensionOptions = computed<Array<{
  value: ProcurementSpendDimension
  label: string
}>>(() => [
  { value: 'CATEGORY', label: t('procurementOverviewPage.category') },
  { value: 'SUPPLIER', label: t('procurementOverviewPage.supplier') },
  { value: 'DEPARTMENT', label: t('procurementOverviewPage.department') },
])

const orderStatusLabels = computed<Record<string, string>>(() => ({
  DRAFT: t('procurementOverviewPage.statusDraft'),
  SENT: t('procurementOverviewPage.statusSent'),
  CONFIRMED: t('procurementOverviewPage.statusConfirmed'),
  PARTIAL_RECEIVED: t('procurementOverviewPage.statusPartialReceived'),
  RECEIVED: t('procurementOverviewPage.statusReceived'),
  CLOSED: t('procurementOverviewPage.statusClosed'),
  CANCELLED: t('procurementOverviewPage.statusCancelled'),
}))

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
  return dimensionOptions.value.find((item) => item.value === dimension)?.label || dimension
}

onMounted(refresh)
</script>

<template>
  <div v-loading="loading" class="procurement-overview-page">
    <div class="page-heading">
      <div>
        <h2>{{ t('procurementOverviewPage.title') }}</h2>
        <p>{{ t('procurementOverviewPage.description') }}</p>
      </div>
      <el-button type="primary" plain @click="refresh">{{ t('common.refresh') }}</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="metric-card metric-warning">
          <div class="metric-label">{{ t('procurementOverviewPage.pendingRequisitions') }}</div>
          <div class="metric-value">{{ summary?.pendingApprovalRequisitionCount ?? 0 }}</div>
          <div class="metric-hint">{{ t('procurementOverviewPage.pendingRequisitionsHint') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="metric-card metric-primary">
          <div class="metric-label">{{ t('procurementOverviewPage.waitingQuotations') }}</div>
          <div class="metric-value">{{ summary?.waitingQuotationRfqCount ?? 0 }}</div>
          <div class="metric-hint">{{ t('procurementOverviewPage.waitingQuotationsHint') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="metric-card metric-success">
          <div class="metric-label">{{ t('procurementOverviewPage.activeOrders') }}</div>
          <div class="metric-value">{{ activeOrderCount }}</div>
          <div class="metric-hint">{{ t('procurementOverviewPage.activeOrdersHint') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="metric-card metric-danger">
          <div class="metric-label">{{ t('procurementOverviewPage.draftReceipts') }}</div>
          <div class="metric-value">{{ summary?.draftGoodsReceiptCount ?? 0 }}</div>
          <div class="metric-hint">{{ t('procurementOverviewPage.draftReceiptsHint') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ t('procurementOverviewPage.orderStatus') }}</span>
              <span class="card-note">{{ t('procurementOverviewPage.currentDataScope') }}</span>
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
            :description="t('procurementOverviewPage.noOrders')"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ t('procurementOverviewPage.committedPurchases') }}</span>
              <span class="card-note">{{ t('procurementOverviewPage.currencySeparated') }}</span>
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
            :description="t('procurementOverviewPage.noCommittedAmount')"
          />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('procurementOverviewPage.spendAnalysis') }}</span>
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
        :title="t('procurementOverviewPage.spendAggregation', { dimension: dimensionLabel(spendDimension) })"
      />
      <el-table v-loading="spendLoading" :data="spendRows" stripe class="spend-table">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="dimensionName" :label="dimensionLabel(spendDimension)" min-width="220" />
        <el-table-column prop="dimensionKey" :label="t('procurementOverviewPage.codeOrId')" min-width="150" />
        <el-table-column prop="currencyCode" :label="t('procurementOverviewPage.currency')" width="90" />
        <el-table-column prop="amount" :label="t('procurementOverviewPage.committedAmount')" min-width="180" align="right" />
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
