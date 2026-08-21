<script setup lang="ts">
/**
 * 中间事件（IntermediateThrowEvent）属性面板。
 * V1 版本中仅作为流程节点标记，无需额外配置。
 */
import type { BpmnElement } from '@/types/bpmn'

defineProps<{
  element: BpmnElement
}>()
</script>

<template>
  <div class="event-panel">
    <div class="section-title">中间事件</div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
    >
      <template #title>
        中间事件在 V1 版本中仅作为流程节点标记，后续可扩展信号/消息触发能力。
      </template>
    </el-alert>

    <el-form label-width="80px" size="small" class="event-form">
      <el-form-item label="名称">
        <el-input
          :model-value="element?.businessObject?.name || 'Intermediate Event'"
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
