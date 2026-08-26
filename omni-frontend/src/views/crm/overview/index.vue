<script setup lang="ts">
/** CRM 销售概览页面：汇总指标、商机漏斗和待跟进事项。 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getCrmFollowups, getCrmFunnel, getCrmOverviewSummary, type CrmOverviewSummary, type FollowupItem, type FunnelItem } from '@/api/crm-overview'

const { t, locale } = useI18n()
const loading = ref(false)
const summary = ref<CrmOverviewSummary | null>(null)
const funnel = ref<FunnelItem[]>([])
const followups = ref<{ overdue: FollowupItem[]; today: FollowupItem[] }>({ overdue: [], today: [] })

const maxFunnelAmount = computed(() => Math.max(...funnel.value.map((item) => item.amount), 1))

async function loadData() {
  loading.value = true
  try {
    const [summaryResponse, funnelResponse, followupResponse] = await Promise.all([
      getCrmOverviewSummary(),
      getCrmFunnel(),
      getCrmFollowups(),
    ])
    summary.value = summaryResponse.data.data
    funnel.value = funnelResponse.data.data
    followups.value = {
      overdue: followupResponse.data.data.filter((item) => item.overdue),
      today: followupResponse.data.data.filter((item) => !item.overdue),
    }
  } finally {
    loading.value = false
  }
}

function money(value = 0) {
  return new Intl.NumberFormat(locale.value, {
    style: 'currency',
    currency: summary.value?.currencyCode || 'CNY',
  }).format(value)
}

function percent(value = 0) {
  return `${Number(value).toFixed(1)}%`
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading" class="overview-page">
    <div class="page-heading">
      <div>
        <h2>{{ t('common.crmOverview') }}</h2>
        <p>{{ t('crmOverviewPage.description') }}</p>
      </div>
      <el-button @click="loadData"><el-icon><Refresh /></el-icon>{{ t('common.refresh') }}</el-button>
    </div>

    <el-row :gutter="16" class="metrics">
      <el-col :xs="12" :sm="8" :lg="4">
        <el-card shadow="never"><el-statistic :title="t('crmOverviewPage.newLeads')" :value="summary?.newLeadCount || 0" /></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :lg="4">
        <el-card shadow="never"><el-statistic :title="t('crmOverviewPage.qualifiedLeads')" :value="summary?.qualifiedLeadCount || 0" /></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :lg="4">
        <el-card shadow="never"><el-statistic :title="t('crmOverviewPage.convertedLeads')" :value="summary?.convertedLeadCount || 0" /></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :lg="4">
        <el-card shadow="never"><el-statistic :title="t('crmOverviewPage.openOpportunities')" :value="summary?.openOpportunityCount || 0" /></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :lg="4">
        <el-card shadow="never"><el-statistic :title="t('crmOverviewPage.todayFollowups')" :value="summary?.todayFollowupCount || 0" /></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :lg="4">
        <el-card shadow="never" class="danger-card"><el-statistic :title="t('crmOverviewPage.overdueFollowups')" :value="summary?.overdueFollowupCount || 0" /></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <template #header>
            <div class="section-heading">
              <strong>{{ t('crmOverviewPage.salesFunnel') }}</strong>
              <span>{{ t('crmOverviewPage.openAmount', { amount: money(summary?.openOpportunityAmount) }) }}</span>
            </div>
          </template>
          <el-empty v-if="funnel.length === 0" :description="t('crmOverviewPage.noFunnel')" />
          <div v-else class="funnel">
            <div v-for="item in funnel" :key="item.stageId" class="funnel-row">
              <div class="funnel-label">
                <strong>{{ item.stageName }}</strong>
                <span>{{ t('crmOverviewPage.funnelItem', { count: item.count, amount: money(item.amount), type: item.stageType }) }}</span>
              </div>
              <div class="funnel-track">
                <div class="funnel-bar" :style="{ width: `${Math.max(8, item.amount / maxFunnelAmount * 100)}%` }"></div>
              </div>
            </div>
          </div>
          <el-divider />
          <div class="rate-grid">
            <el-statistic :title="t('crmOverviewPage.leadConversionRate')" :value="percent(summary?.leadConversionRate)" />
            <el-statistic :title="t('crmOverviewPage.opportunityWinRate')" :value="percent(summary?.opportunityWinRate)" />
            <el-statistic :title="t('crmOverviewPage.wonAmount')" :value="money(summary?.wonOpportunityAmount)" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never">
          <template #header><strong>{{ t('crmOverviewPage.followupReminder') }}</strong></template>
          <el-tabs>
            <el-tab-pane :label="t('crmOverviewPage.overdueTab', { count: followups.overdue.length })">
              <el-empty v-if="followups.overdue.length === 0" :description="t('crmOverviewPage.noOverdue')" :image-size="70" />
              <div v-for="item in followups.overdue" :key="`${item.rootType}-${item.rootId}`" class="followup-item overdue">
                <strong>{{ item.name }}</strong><span>{{ item.number || `${item.rootType} #${item.rootId}` }}</span>
                <time>{{ item.nextFollowupTime }}</time>
              </div>
            </el-tab-pane>
            <el-tab-pane :label="t('crmOverviewPage.todayTab', { count: followups.today.length })">
              <el-empty v-if="followups.today.length === 0" :description="t('crmOverviewPage.noToday')" :image-size="70" />
              <div v-for="item in followups.today" :key="`${item.rootType}-${item.rootId}`" class="followup-item">
                <strong>{{ item.name }}</strong><span>{{ item.number || `${item.rootType} #${item.rootId}` }}</span>
                <time>{{ item.nextFollowupTime }}</time>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.overview-page { padding: 20px; }
.page-heading, .section-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.page-heading h2 { margin: 0; }
.page-heading p, .section-heading span { color: var(--el-text-color-secondary); }
.metrics, .content-row { margin-top: 16px; }
.metrics .el-col { margin-bottom: 16px; }
.danger-card :deep(.el-statistic__number) { color: var(--el-color-danger); }
.funnel { display: grid; gap: 18px; }
.funnel-label { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 6px; }
.funnel-label span { color: var(--el-text-color-secondary); font-size: 13px; }
.funnel-track { height: 12px; overflow: hidden; border-radius: 8px; background: var(--el-fill-color-light); }
.funnel-bar { height: 100%; min-width: 8%; border-radius: 8px; background: linear-gradient(90deg, var(--el-color-primary-light-3), var(--el-color-primary)); }
.rate-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; text-align: center; }
.followup-item { display: grid; grid-template-columns: 1fr auto; gap: 5px 10px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.followup-item span, .followup-item time { color: var(--el-text-color-secondary); font-size: 12px; }
.followup-item time { grid-column: 1 / -1; }
.followup-item.overdue time { color: var(--el-color-danger); }
@media (max-width: 768px) { .rate-grid { grid-template-columns: 1fr; } .content-row .el-col + .el-col { margin-top: 16px; } }
</style>
