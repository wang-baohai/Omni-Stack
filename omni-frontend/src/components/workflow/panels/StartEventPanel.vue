<script setup lang="ts">
/**
 * 开始事件属性面板 — 仅展示基本信息。
 */
import type { BpmnElement } from '@/types/bpmn'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  element: BpmnElement
}>()
</script>

<template>
  <div class="event-panel">
    <div class="section-title">{{ t('workflow.nodeTypeStartEvent') }}</div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
    >
      <template #title>
        {{ t('workflow.startEventHint') }}
      </template>
    </el-alert>

    <el-form label-width="80px" size="small" class="event-form">
      <el-form-item :label="t('workflow.nodeName')">
        <el-input
          :model-value="element?.businessObject?.name || 'Start'"
          disabled
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.event-panel {
  margin-top: 4px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}
.event-form {
  margin-top: 12px;
}
</style>
