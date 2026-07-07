<script setup lang="ts">
/**
 * 校验结果对话框。
 */
import { useI18n } from 'vue-i18n'
import type { ValidateResult } from '@/api/workflow-model'

const { t } = useI18n()

defineProps<{
  visible: boolean
  result: ValidateResult | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="校验结果"
    width="600px"
    @close="handleClose"
  >
    <div v-if="result">
      <el-result
        :icon="result.valid ? 'success' : 'error'"
        :title="result.valid ? '校验通过' : '校验未通过'"
      />

      <div v-if="result.errors.length > 0" class="result-section">
        <h4 class="section-title error-title">错误 ({{ result.errors.length }})</h4>
        <ul class="result-list">
          <li v-for="(error, idx) in result.errors" :key="idx" class="error-item">
            <el-icon color="var(--el-color-danger)"><CircleCloseFilled /></el-icon>
            {{ error }}
          </li>
        </ul>
      </div>

      <div v-if="result.warnings.length > 0" class="result-section">
        <h4 class="section-title warning-title">警告 ({{ result.warnings.length }})</h4>
        <ul class="result-list">
          <li v-for="(warning, idx) in result.warnings" :key="idx" class="warning-item">
            <el-icon color="var(--el-color-warning)"><WarningFilled /></el-icon>
            {{ warning }}
          </li>
        </ul>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">{{ t('common.back') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.result-section {
  margin-top: 16px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}
.error-title { color: var(--el-color-danger); }
.warning-title { color: var(--el-color-warning); }
.result-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.result-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.result-list li:last-child {
  border-bottom: none;
}
</style>
