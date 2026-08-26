<script setup lang="ts">
/** SRM 供应商概览页面，包含统计卡片和风险看板。 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getSrmOverviewSummary, getRiskDashboard, type SrmOverviewSummary, type RiskDashboard } from '@/api/srm-overview'
import RiskDashboardComponent from '@/components/srm/RiskDashboard.vue'

const { t } = useI18n()
const loading = ref(false)
const summary = ref<SrmOverviewSummary | null>(null)
const riskDashboard = ref<RiskDashboard>({ redCount: 0, yellowCount: 0, greenCount: 0, topRiskSuppliers: [] })

const statusCards = computed(() => [
  { key: 'approvedCount', label: t('srmOverviewPage.approved'), color: 'success' },
  { key: 'pendingReviewCount', label: t('srmOverviewPage.pendingReview'), color: 'warning' },
  { key: 'suspendedCount', label: t('srmOverviewPage.suspended'), color: 'info' },
  { key: 'blacklistedCount', label: t('srmOverviewPage.blacklisted'), color: 'danger' },
  { key: 'eliminatedCount', label: t('srmOverviewPage.eliminated'), color: 'danger' },
] as const)

const levelCards = computed(() => [
  { key: 'strategicCount', label: t('srmOverviewPage.strategic'), color: 'success' },
  { key: 'preferredCount', label: t('srmOverviewPage.preferred'), color: '' },
  { key: 'qualifiedCount', label: t('srmOverviewPage.qualified'), color: 'warning' },
] as const)

async function loadData() {
  loading.value = true
  try {
    const [summaryRes, riskRes] = await Promise.all([
      getSrmOverviewSummary(),
      getRiskDashboard(),
    ])
    if (summaryRes.data.code === 200) summary.value = summaryRes.data.data
    if (riskRes.data.code === 200) riskDashboard.value = riskRes.data.data
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading" class="srm-overview">
    <!-- 供应商总数 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :span="24">
        <el-card shadow="never">
          <div class="overview-total">
            {{ t('srmOverviewPage.totalSuppliers') }}：<span class="overview-total__count">{{ summary?.totalSuppliers ?? '-' }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 状态统计卡片 -->
    <el-row :gutter="16" class="mb-4">
      <el-col v-for="card in statusCards" :key="card.key" :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__count">{{ summary?.[card.key] ?? 0 }}</div>
          <div class="stat-card__label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 等级统计卡片 -->
    <el-row :gutter="16" class="mb-4">
      <el-col v-for="card in levelCards" :key="card.key" :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card__count">{{ summary?.[card.key] ?? 0 }}</div>
          <div class="stat-card__label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 风险看板 -->
    <el-card shadow="never">
      <template #header>{{ t('srmOverviewPage.riskDashboard') }}</template>
      <RiskDashboardComponent :dashboard="riskDashboard" />
    </el-card>
  </div>
</template>

<style scoped>
.mb-4 { margin-bottom: 16px; }
.overview-total {
  text-align: center;
  font-size: 16px;
}
.overview-total__count {
  font-size: 28px;
  font-weight: bold;
  color: var(--el-color-primary);
}
.stat-card {
  text-align: center;
}
.stat-card__count {
  font-size: 28px;
  font-weight: bold;
  color: var(--el-color-primary);
}
.stat-card__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
