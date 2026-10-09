<script setup lang="ts">
/** 持续展示审批覆盖风险，避免只有提交失败时才发现配置断档。 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CoverageReport } from '@/api/procurement-approval-route'

const props = defineProps<{
  report?: CoverageReport
  loading?: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

const { t } = useI18n()
const riskyCategories = computed(() => props.report?.categories.filter(
  (category) => !category.complete,
) ?? [])
const healthy = computed(() => Boolean(props.report)
  && riskyCategories.value.length === 0
  && !props.report?.allRulesInactive
  && props.report?.workflowAvailability === 'AVAILABLE')
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="coverage-card">
    <div class="coverage-heading">
      <div>
        <strong>
          {{ healthy
            ? t('procurementApprovalRules.coverageHealthy')
            : t('procurementApprovalRules.coverageRisk') }}
        </strong>
        <div v-if="report" class="coverage-summary">
          <span v-if="report.workflowAvailability === 'UNAVAILABLE'">
            {{ t('procurementApprovalRules.coverageUnavailable') }}
          </span>
          <span v-if="report.allRulesInactive">
            {{ t('procurementApprovalRules.allRulesInactive') }}
          </span>
          <span v-if="report.noDefaultRule">
            {{ t('procurementApprovalRules.noDefaultRule') }}
          </span>
          <span v-if="report.invalidModelRouteIds.length">
            {{ t('procurementApprovalRules.invalidModels', { count: report.invalidModelRouteIds.length }) }}
          </span>
        </div>
      </div>
      <el-button :loading="loading" @click="emit('refresh')">
        {{ t('procurementApprovalRules.refresh') }}
      </el-button>
    </div>
    <el-alert
      v-if="healthy"
      type="success"
      :closable="false"
      show-icon
      :title="t('procurementApprovalRules.coverageHealthy')"
    />
    <el-collapse v-else-if="riskyCategories.length" class="risk-list">
      <el-collapse-item
        v-for="category in riskyCategories"
        :key="category.categoryCode"
        :title="`${category.categoryName}（${category.categoryCode}）`"
        :name="category.categoryCode"
      >
        <ul>
          <li v-for="issue in category.issues" :key="issue">{{ issue }}</li>
        </ul>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<style scoped>
.coverage-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.coverage-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 6px;
  color: var(--el-color-warning-dark-2);
  font-size: 13px;
}

.risk-list {
  margin-top: 12px;
}

.risk-list ul {
  margin: 0;
  padding-left: 20px;
}
</style>
