<script setup lang="ts">
import type { RiskLevel } from '@/api/srm-risk'

defineProps<{ level?: RiskLevel | string }>()

const levelLabels: Record<string, string> = {
  RED: '高风险',
  YELLOW: '中风险',
  GREEN: '低风险',
}
</script>

<template>
  <div class="risk-indicator">
    <span :class="['risk-indicator__dot', `risk-indicator__dot--${level?.toLowerCase()}`]" />
    <span class="risk-indicator__label">{{ level ? (levelLabels[level] || level) : '' }}</span>
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
