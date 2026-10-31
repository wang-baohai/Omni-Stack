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

function resultDescription(preview: MatchPreview) {
  switch (preview.outcome) {
  case 'MATCHED':
    return t(preview.defaultRule
      ? 'procurementApprovalRules.matchDefaultHint'
      : 'procurementApprovalRules.matchExactHint')
  case 'NO_MATCH':
    return t('procurementApprovalRules.matchNoRuleHint')
  case 'AMBIGUOUS':
    return t('procurementApprovalRules.matchConflictHint')
  default:
    return t('procurementApprovalRules.matchUnavailableHint')
  }
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
        :description="resultDescription(result)"
      />
      <dl v-if="result.routeId" class="match-summary">
        <div>
          <dt>{{ t('procurementApprovalRules.ruleName') }}</dt>
          <dd>{{ result.routeName }}</dd>
        </div>
        <div>
          <dt>{{ t('procurementApprovalRules.category') }}</dt>
          <dd>
            {{ result.defaultRule
              ? t('procurementApprovalRules.defaultCategory')
              : result.effectiveCategoryCode }}
          </dd>
        </div>
        <div>
          <dt>{{ t('procurementApprovalRules.amountRange') }}</dt>
          <dd>{{ result.minAmount }} ≤ x &lt; {{ result.maxAmount || '∞' }}</dd>
        </div>
        <div>
          <dt>{{ t('procurementApprovalRules.approvalFlow') }}</dt>
          <dd>
            {{ result.modelName || t('procurementApprovalRules.unavailableFlow') }}
            <span v-if="result.modelVersion"> · {{ t('procurementApprovalRules.version', { version: result.modelVersion }) }}</span>
          </dd>
        </div>
      </dl>
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
  min-width: 0;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  margin-top: 4px;
}

.match-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.match-summary > div {
  min-width: 0;
  padding: 10px 12px;
}

.match-summary dt {
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.match-summary dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.match-result :deep(.el-alert__content) {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 768px) {
  .match-form {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .match-action :deep(.el-button) {
    width: 100%;
  }

  .match-summary {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
