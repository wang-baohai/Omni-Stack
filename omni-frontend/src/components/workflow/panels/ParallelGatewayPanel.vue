<script setup lang="ts">
/**
 * 并行网关（ParallelGateway）属性面板。
 * 并行网关自动汇聚所有入流，无需额外配置。仅展示基本信息。
 */
import type { BpmnElement } from '@/types/bpmn'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  element: BpmnElement
}>()
</script>

<template>
  <div class="gateway-panel">
    <div class="section-title">{{ t('workflow.nodeTypeParallelGateway') }}</div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
    >
      <template #title>
        {{ t('workflow.parallelGatewayHint') }}
      </template>
    </el-alert>

    <el-form label-width="80px" size="small" class="gateway-form">
      <el-form-item :label="t('workflow.nodeName')">
        <el-input
          :model-value="element?.businessObject?.name || 'Parallel Gateway'"
          disabled
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.gateway-panel {
  margin-top: 4px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}
.gateway-form {
  margin-top: 12px;
}
</style>
