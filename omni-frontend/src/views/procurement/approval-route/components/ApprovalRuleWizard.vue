<script setup lang="ts">
/** 三步业务向导：适用范围、审批流程、确认启用。 */
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import type {
  ApprovalWorkflowOption,
  CreateProcurementApprovalRouteRequest,
  ProcurementApprovalRoute,
  ProcurementApprovalRouteStatus,
  UpdateProcurementApprovalRouteRequest,
} from '@/api/procurement-approval-route'
import RuleAdvancedInfo from './RuleAdvancedInfo.vue'

const props = defineProps<{
  modelValue: boolean
  route?: ProcurementApprovalRoute
  categories: Array<{ value: string; label: string }>
  workflows: ApprovalWorkflowOption[]
  saving?: boolean
  workflowLoading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [payload: CreateProcurementApprovalRouteRequest | UpdateProcurementApprovalRouteRequest]
}>()

const { t } = useI18n()
const step = ref(0)
const formRef = ref<FormInstance>()
const form = reactive<{
  routeName: string
  categoryCode: string
  minAmount: string
  maxAmount: string
  modelVersionId?: number
  status: ProcurementApprovalRouteStatus
}>({
  routeName: '',
  categoryCode: '*',
  minAmount: '0.0000',
  maxAmount: '',
  modelVersionId: undefined,
  status: 'ACTIVE',
})
const amountPattern = /^\d{1,15}(?:\.\d{1,4})?$/

const selectedWorkflow = computed(() => props.workflows.find(
  (workflow) => workflow.modelVersionId === form.modelVersionId,
))

function compareAmounts(left: string, right: string) {
  const [leftIntegerRaw, leftFractionRaw = ''] = left.split('.')
  const [rightIntegerRaw, rightFractionRaw = ''] = right.split('.')
  const leftInteger = leftIntegerRaw.replace(/^0+(?=\d)/, '')
  const rightInteger = rightIntegerRaw.replace(/^0+(?=\d)/, '')
  if (leftInteger.length !== rightInteger.length) return leftInteger.length - rightInteger.length
  const integerComparison = leftInteger.localeCompare(rightInteger)
  return integerComparison || leftFractionRaw.padEnd(4, '0')
    .localeCompare(rightFractionRaw.padEnd(4, '0'))
}

function validateAmount(_rule: unknown, value: string, callback: (error?: Error) => void) {
  callback(amountPattern.test(value)
    ? undefined
    : new Error(t('procurementApprovalRules.amountInvalid')))
}

function validateMaxAmount(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback()
    return
  }
  if (!amountPattern.test(value)) {
    callback(new Error(t('procurementApprovalRules.amountInvalid')))
    return
  }
  callback(compareAmounts(value, form.minAmount) > 0
    ? undefined
    : new Error(t('procurementApprovalRules.maxAmountInvalid')))
}

const rules: FormRules = {
  routeName: [{ required: true, message: t('procurementApprovalRules.nameRequired'), trigger: 'blur' }],
  categoryCode: [{ required: true, message: t('procurementApprovalRules.categoryRequired'), trigger: 'change' }],
  minAmount: [{ required: true, validator: validateAmount, trigger: 'blur' }],
  maxAmount: [{ validator: validateMaxAmount, trigger: 'blur' }],
  modelVersionId: [{ required: true, message: t('procurementApprovalRules.flowRequired'), trigger: 'change' }],
}

watch(() => props.modelValue, (visible) => {
  if (!visible) return
  step.value = 0
  Object.assign(form, {
    routeName: props.route?.routeName ?? '',
    categoryCode: props.route?.categoryCode ?? '*',
    minAmount: props.route?.minAmount ?? '0.0000',
    maxAmount: props.route?.maxAmount ?? '',
    modelVersionId: props.route?.modelVersionId,
    status: props.route?.status ?? 'ACTIVE',
  })
  formRef.value?.clearValidate()
})

async function next() {
  if (step.value === 0) {
    const valid = await formRef.value?.validateField([
      'routeName', 'categoryCode', 'minAmount', 'maxAmount',
    ]).then(() => true).catch(() => false)
    if (!valid) return
  }
  if (step.value === 1) {
    const valid = await formRef.value?.validateField('modelVersionId')
      .then(() => true).catch(() => false)
    if (!valid) return
  }
  step.value += 1
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid || !form.modelVersionId) return
  const base: CreateProcurementApprovalRouteRequest = {
    routeName: form.routeName.trim(),
    categoryCode: form.categoryCode,
    minAmount: form.minAmount,
    maxAmount: form.maxAmount || undefined,
    modelVersionId: form.modelVersionId,
    status: form.status,
  }
  emit('save', props.route
    ? { ...base, version: props.route.version, status: form.status }
    : base)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="route ? t('procurementApprovalRules.edit') : t('procurementApprovalRules.create')"
    width="min(760px, calc(100vw - 32px))"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-steps :active="step" align-center finish-status="success" class="wizard-steps">
      <el-step :title="t('procurementApprovalRules.wizardScope')" />
      <el-step :title="t('procurementApprovalRules.wizardFlow')" />
      <el-step :title="t('procurementApprovalRules.wizardConfirm')" />
    </el-steps>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div v-show="step === 0" class="wizard-panel">
        <el-form-item :label="t('procurementApprovalRules.ruleName')" prop="routeName">
          <el-input
            v-model="form.routeName"
            maxlength="100"
            show-word-limit
            :placeholder="t('procurementApprovalRules.namePlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('procurementApprovalRules.category')" prop="categoryCode">
          <el-select v-model="form.categoryCode" filterable>
            <el-option
              v-for="category in categories"
              :key="category.value"
              :label="category.label"
              :value="category.value"
            />
          </el-select>
        </el-form-item>
        <div class="amount-grid">
          <el-form-item :label="t('procurementApprovalRules.minAmount')" prop="minAmount">
            <el-input v-model="form.minAmount" inputmode="decimal" />
          </el-form-item>
          <el-form-item :label="t('procurementApprovalRules.maxAmount')" prop="maxAmount">
            <el-input
              v-model="form.maxAmount"
              inputmode="decimal"
              :placeholder="t('procurementApprovalRules.maxAmountPlaceholder')"
            />
          </el-form-item>
        </div>
      </div>

      <div v-show="step === 1" class="wizard-panel">
        <el-form-item :label="t('procurementApprovalRules.approvalFlow')" prop="modelVersionId">
          <el-select
            v-model="form.modelVersionId"
            filterable
            :loading="workflowLoading"
            :placeholder="t('procurementApprovalRules.flowPlaceholder')"
          >
            <el-option
              v-for="workflow in workflows"
              :key="workflow.modelVersionId"
              :value="workflow.modelVersionId"
              :label="`${workflow.modelName} · ${t('procurementApprovalRules.version', { version: workflow.version })}`"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="!workflowLoading && !workflows.length"
          type="warning"
          :closable="false"
          show-icon
          :title="t('procurementApprovalRules.noFlow')"
          :description="t('procurementApprovalRules.flowPermissionHint')"
        />
        <el-descriptions v-if="selectedWorkflow" :column="1" border>
          <el-descriptions-item :label="t('procurementApprovalRules.approvalFlow')">
            {{ selectedWorkflow.modelName }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('procurementApprovalRules.version', { version: '' })">
            {{ selectedWorkflow.version }} · {{ selectedWorkflow.publishTime }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div v-show="step === 2" class="wizard-panel">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('procurementApprovalRules.ruleName')">
            {{ form.routeName }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('procurementApprovalRules.category')">
            {{ categories.find((item) => item.value === form.categoryCode)?.label }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('procurementApprovalRules.amountRange')">
            {{ form.minAmount }} ≤ x &lt; {{ form.maxAmount || '∞' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('procurementApprovalRules.approvalFlow')">
            {{ selectedWorkflow?.modelName }}
          </el-descriptions-item>
        </el-descriptions>
        <el-form-item :label="t('procurementApprovalRules.status')" class="status-field">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">{{ t('procurementApprovalRules.active') }}</el-radio>
            <el-radio value="INACTIVE">{{ t('procurementApprovalRules.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <RuleAdvancedInfo v-if="route" :route="route" />
      </div>
    </el-form>

    <template #footer>
      <div class="wizard-footer">
        <el-button @click="emit('update:modelValue', false)">
          {{ t('procurementApprovalRules.cancel') }}
        </el-button>
        <div>
          <el-button v-if="step > 0" @click="step -= 1">
            {{ t('procurementApprovalRules.previous') }}
          </el-button>
          <el-button v-if="step < 2" type="primary" @click="next">
            {{ t('procurementApprovalRules.next') }}
          </el-button>
          <el-button v-else type="primary" :loading="saving" @click="save">
            {{ t('procurementApprovalRules.save') }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.wizard-steps {
  margin-bottom: 24px;
}

.wizard-panel {
  min-height: 270px;
}

.amount-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.status-field {
  margin-top: 18px;
}

.wizard-footer {
  display: flex;
  justify-content: space-between;
}

@media (max-width: 600px) {
  .amount-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .wizard-footer {
    gap: 8px;
  }
}
</style>
