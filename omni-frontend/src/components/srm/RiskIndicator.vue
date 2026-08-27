<script setup lang="ts">
import type { RiskLevel } from '@/api/srm-risk'
import { useI18n } from 'vue-i18n'

defineProps<{ level?: RiskLevel | string }>()

const { t } = useI18n()

const levelLabels: Record<string, string> = {
  RED: 'srmRiskCommon.high',
  YELLOW: 'srmRiskCommon.medium',
  GREEN: 'srmRiskCommon.low',
}
</script>

<template>
  <div class="risk-indicator">
    <span :class="['risk-indicator__dot', `risk-indicator__dot--${level?.toLowerCase()}`]" />
    <span class="risk-indicator__label">{{ level ? (levelLabels[level] ? t(levelLabels[level]) : level) : '' }}</span>
  </div>
</template>

<style scoped>
.risk-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.risk-indicator__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.risk-indicator__dot--red { background: var(--el-color-danger); }
.risk-indicator__dot--yellow { background: var(--el-color-warning); }
.risk-indicator__dot--green { background: var(--el-color-success); }
.risk-indicator__label {
  font-size: 13px;
}
</style>
