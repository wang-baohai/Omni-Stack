<script setup lang="ts">
/** 资产概览页面，按管理 DataScope 展示状态、原值与多维分布。 */
import { onMounted, ref } from 'vue'
import {
  getAssetDistribution,
  getAssetOverviewSummary,
  type AssetDistributionDimension,
  type AssetDistributionItem,
  type AssetOverviewSummary,
} from '@/api/asset-overview'

const loading = ref(false)
const summary = ref<AssetOverviewSummary>()
const distribution = ref<AssetDistributionItem[]>([])
const dimension = ref<AssetDistributionDimension>('STATUS')
const dimensionOptions: Array<{ label: string; value: AssetDistributionDimension }> = [
  { label: '状态', value: 'STATUS' },
  { label: '品类', value: 'CATEGORY' },
  { label: '部门', value: 'DEPARTMENT' },
  { label: '位置', value: 'LOCATION' },
]

async function load() {
  loading.value = true
  try {
    const [summaryResponse, distributionResponse] = await Promise.all([
      getAssetOverviewSummary(),
      getAssetDistribution(dimension.value),
    ])
    summary.value = summaryResponse.data.data
    distribution.value = distributionResponse.data.data
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="asset-overview-page">
    <div class="stat-grid">
      <el-card shadow="never"><el-statistic title="资产总数" :value="summary?.totalCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic title="在库" :value="summary?.inStockCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic title="使用中" :value="summary?.inUseCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic title="待领用" :value="summary?.allocatedCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic title="维修中" :value="summary?.maintenanceCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic title="调拨中" :value="summary?.transferCount || 0" /></el-card>
      <el-card shadow="never">
        <el-statistic title="待处置" :value="summary?.disposalPendingCount || 0" />
      </el-card>
      <el-card shadow="never"><el-statistic title="已终结" :value="summary?.terminalCount || 0" /></el-card>
    </div>

    <el-card shadow="never">
      <template #header><span>资产原值（按币种独立统计）</span></template>
      <el-empty v-if="!summary?.amountsByCurrency.length" description="暂无金额数据" />
      <div v-else class="amount-grid">
        <div v-for="item in summary.amountsByCurrency" :key="item.currencyCode" class="amount-item">
          <span>{{ item.currencyCode }}</span>
          <strong>{{ item.amount }}</strong>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>资产分布</span>
          <el-segmented v-model="dimension" :options="dimensionOptions" @change="load" />
        </div>
      </template>
      <el-table :data="distribution" stripe>
        <el-table-column prop="dimensionName" label="维度项" min-width="200" />
        <el-table-column prop="count" label="资产数量" min-width="120" align="right" />
        <el-table-column prop="currencyCode" label="币种" min-width="100">
          <template #default="{ row }">{{ row.currencyCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="资产原值" min-width="160" align="right">
          <template #default="{ row }">{{ row.amount || '—' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.asset-overview-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.amount-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.amount-item {
  display: flex;
  min-width: 220px;
  justify-content: space-between;
  padding: 14px 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
