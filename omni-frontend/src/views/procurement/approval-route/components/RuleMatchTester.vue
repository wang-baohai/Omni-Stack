<script setup lang="ts">
/** 使用服务端真实解析器试算一笔请购，不在浏览器复制匹配算法。 */
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  previewApprovalRouteMatch,
  type MatchPreview,
} from '@/api/procurement-approval-route'
import ApprovalFlowPreview from './ApprovalFlowPreview.vue'

defineProps<{
  categories: Array<{ value: string; label: string }>
}>()

const { t } = useI18n()
const loading = ref(false)
const result = ref<MatchPreview>()
const form = reactive({ categoryCode: '', totalAmount: '' })
const amountPattern = /^\d{1,15}(?:\.\d{1,4})?$/

async function testMatch() {
  if (!form.categoryCode || !amountPattern.test(form.totalAmount)) {
    ElMessage.warning(t('procurementApprovalRules.matchInputRequired'))
    return
  }
  loading.value = true
  try {
    const response = await previewApprovalRouteMatch({
      categoryCode: form.categoryCode,
      totalAmount: form.totalAmount,
    })
    result.value = response.data.data
  } finally {
    loading.value = false
  }
}

function resultType(outcome: MatchPreview['outcome']) {
  if (outcome === 'MATCHED') return 'success'
  if (outcome === 'NO_MATCH') return 'error'
  return 'warning'
}

function resultTitle(outcome: MatchPreview['outcome']) {
  const keys: Record<MatchPreview['outcome'], string> = {
    MATCHED: 'matched',
    NO_MATCH: 'noMatch',
    AMBIGUOUS: 'ambiguous',
    WORKFLOW_UNAVAILABLE: 'workflowUnavailable',
  }
  return t(`procurementApprovalRules.${keys[outcome]}`)
}
</script>

<template>
  <el-card shadow="never" class="match-tester">
    <template #header>
      <strong>{{ t('procurementApprovalRules.matchTitle') }}</strong>
    </template>
    <el-form :model="form" label-position="top" class="match-form" @submit.prevent="testMatch">
      <el-form-item :label="t('procurementApprovalRules.category')">
        <el-select
          v-model="form.categoryCode"
          filterable
          :placeholder="t('procurementApprovalRules.allCategories')"
        >
          <el-option
            v-for="category in categories"
            :key="category.value"
            :label="category.label"
            :value="category.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('procurementApprovalRules.amount')">
        <el-input
          v-model="form.totalAmount"
          inputmode="decimal"
          :placeholder="t('procurementApprovalRules.amountPlaceholder')"
          @keyup.enter="testMatch"
        />
      </el-form-item>
      <el-form-item class="match-action">
        <el-button type="primary" :loading="loading" native-type="submit">
          {{ t('procurementApprovalRules.test') }}
        </el-button>
      </el-form-item>
    </el-form>

    <div v-if="result" class="match-result" aria-live="polite">
      <el-alert
        :type="resultType(result.outcome)"
        :closable="false"
        show-icon
        :title="resultTitle(result.outcome)"
        :description="result.actionMessage"
      />
      <el-descriptions v-if="result.routeId" :column="2" border>
        <el-descriptions-item :label="t('procurementApprovalRules.ruleName')">
          {{ result.routeName }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('procurementApprovalRules.category')">
          {{ result.defaultRule
            ? t('procurementApprovalRules.defaultCategory')
            : result.effectiveCategoryCode }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('procurementApprovalRules.amountRange')">
          {{ result.minAmount }} ≤ x &lt; {{ result.maxAmount || '∞' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('procurementApprovalRules.approvalFlow')">
          {{ result.modelName || t('procurementApprovalRules.unavailableFlow') }}
          <span v-if="result.modelVersion"> · {{ t('procurementApprovalRules.version', { version: result.modelVersion }) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <ApprovalFlowPreview :graph="result.approvalGraph" />
    </div>
  </el-card>
</template>

<style scoped>
.match-tester {
  overflow: visible;
}

.match-form {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.match-action {
  margin-bottom: 18px;
}

.match-result {
  display: grid;
  gap: 14px;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .match-form {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .match-action :deep(.el-button) {
    width: 100%;
  }

  .match-result :deep(.el-descriptions__body) {
    overflow-x: auto;
  }
}
</style>
