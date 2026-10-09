<script setup lang="ts">
/** 资产概览页面，按管理 DataScope 展示状态、原值与多维分布。 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getAssetDistribution,
  getAssetOverviewSummary,
  type AssetDistributionDimension,
  type AssetDistributionItem,
  type AssetOverviewSummary,
} from '@/api/asset-overview'

const { t } = useI18n()
const loading = ref(false)
const summary = ref<AssetOverviewSummary>()
const distribution = ref<AssetDistributionItem[]>([])
const dimension = ref<AssetDistributionDimension>('STATUS')
const dimensionOptions = computed<Array<{ label: string; value: AssetDistributionDimension }>>(() => [
  { label: t('assetOverviewPage.status'), value: 'STATUS' },
  { label: t('assetOverviewPage.category'), value: 'CATEGORY' },
  { label: t('assetOverviewPage.department'), value: 'DEPARTMENT' },
  { label: t('assetOverviewPage.location'), value: 'LOCATION' },
])

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
      <el-card shadow="never"><el-statistic :title="t('assetOverviewPage.total')" :value="summary?.totalCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic :title="t('assetOverviewPage.inStock')" :value="summary?.inStockCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic :title="t('assetOverviewPage.inUse')" :value="summary?.inUseCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic :title="t('assetOverviewPage.allocated')" :value="summary?.allocatedCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic :title="t('assetOverviewPage.maintenance')" :value="summary?.maintenanceCount || 0" /></el-card>
      <el-card shadow="never"><el-statistic :title="t('assetOverviewPage.transfer')" :value="summary?.transferCount || 0" /></el-card>
      <el-card shadow="never">
        <el-statistic :title="t('assetOverviewPage.disposalPending')" :value="summary?.disposalPendingCount || 0" />
      </el-card>
      <el-card shadow="never"><el-statistic :title="t('assetOverviewPage.terminal')" :value="summary?.terminalCount || 0" /></el-card>
    </div>

    <el-card shadow="never">
      <template #header><span>{{ t('assetOverviewPage.originalValue') }}</span></template>
      <el-empty v-if="!summary?.amountsByCurrency.length" :description="t('assetOverviewPage.noAmount')" />
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
          <span>{{ t('assetOverviewPage.distribution') }}</span>
          <el-segmented v-model="dimension" :options="dimensionOptions" @change="load" />
        </div>
      </template>
      <el-table :data="distribution" stripe>
        <el-table-column prop="dimensionName" :label="t('assetOverviewPage.dimensionItem')" min-width="200" />
        <el-table-column prop="count" :label="t('assetOverviewPage.assetCount')" min-width="120" align="right" />
        <el-table-column prop="currencyCode" :label="t('assetOverviewPage.currency')" min-width="100">
          <template #default="{ row }">{{ row.currencyCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="amount" :label="t('assetOverviewPage.assetValue')" min-width="160" align="right">
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
