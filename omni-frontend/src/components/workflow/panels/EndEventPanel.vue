<script setup lang="ts">
/**
 * 结束事件属性面板 — 仅展示基本信息。
 */

defineProps<{
  element: any
}>()
</script>

<template>
  <div class="event-panel">
    <div class="section-title">结束事件</div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
    >
      <template #title>
        结束事件是流程的终点，无需额外配置。流程执行到此节点时自动结束。
      </template>
    </el-alert>

    <el-form label-width="80px" size="small" class="event-form">
      <el-form-item label="名称">
        <el-input
          :model-value="element?.businessObject?.name || 'End'"
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
