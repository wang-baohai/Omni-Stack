<script setup lang="ts">
/** 询价比价与定标共享组件——供应商报价横向对比 + 定标表单。 */
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  awardProcurementRfq,
  getProcurementRfqComparison,
  type ProcurementRfqQuotation,
  type ProcurementRfqSummary,
} from '@/api/procurement-rfq'

const props = defineProps<{
  modelValue: boolean
  rfqRow?: ProcurementRfqSummary
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  awarded: []
}>()

const { t } = useI18n()
const comparisonVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const comparisonLoading = ref(false)
const quotations = ref<ProcurementRfqQuotation[]>([])
const awardFormRef = ref<FormInstance>()
const awardForm = reactive<{
  quotationId?: number
  title: string
  expectedDeliveryDate: string
  deliveryAddress: string
  contactName: string
  contactPhone: string
}>({
  title: '',
  expectedDeliveryDate: '',
  deliveryAddress: '',
  contactName: '',
  contactPhone: '',
})
const awardRules = computed<FormRules>(() => ({
  quotationId: [{ required: true, message: t('procurementRfqCompareMessages.quotationRequired'), trigger: 'change' }],
  title: [
    { required: true, message: t('procurementRfqCompareMessages.titleRequired'), trigger: 'blur' },
    { max: 200, message: t('procurementRfqCompareMessages.titleLength'), trigger: 'blur' },
  ],
  deliveryAddress: [
    { required: true, message: t('procurementRfqCompareMessages.addressRequired'), trigger: 'blur' },
    { max: 500, message: t('procurementRfqCompareMessages.addressLength'), trigger: 'blur' },
  ],
  contactName: [{ required: true, message: t('procurementRfqCompareMessages.contactRequired'), trigger: 'blur' }],
  contactPhone: [{ required: true, message: t('procurementRfqCompareMessages.phoneRequired'), trigger: 'blur' }],
}))
const selectedQuotation = computed(() =>
  quotations.value.find((quotation) => quotation.id === awardForm.quotationId),
)

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    const row = props.rfqRow
    quotations.value = []
    Object.assign(awardForm, {
      quotationId: undefined,
      title: row?.title || '',
      expectedDeliveryDate: '',
      deliveryAddress: '',
      contactName: '',
      contactPhone: '',
    })
    if (row) {
      comparisonLoading.value = true
      getProcurementRfqComparison(row.id)
        .then((response) => {
          quotations.value = response.data.data
        })
        .finally(() => {
          comparisonLoading.value = false
        })
    }
  },
)

async function award() {
  const valid = await awardFormRef.value?.validate().catch(() => false)
  const rfq = props.rfqRow
  const quotation = selectedQuotation.value
  if (!valid || !rfq || !quotation) return
  await ElMessageBox.confirm(
    t('procurementRfqCompareMessages.awardConfirm', {
      supplier: quotation.supplierNameSnapshot,
      amount: quotation.totalAmount,
      currency: quotation.currencyCode,
    }),
    t('procurementRfqCompareMessages.awardTitle'),
    { type: 'warning' },
  )
  const response = await awardProcurementRfq(rfq.id, {
    rfqVersion: rfq.version,
    quotationId: quotation.id,
    quotationVersion: quotation.version,
    title: awardForm.title.trim(),
    expectedDeliveryDate: awardForm.expectedDeliveryDate || undefined,
    deliveryAddress: awardForm.deliveryAddress.trim(),
    contactName: awardForm.contactName.trim(),
    contactPhone: awardForm.contactPhone.trim(),
  })
  ElMessage.success(t('procurementRfqCompareMessages.awardSuccess', {
    no: response.data.data.purchaseOrder.poNo,
  }))
  comparisonVisible.value = false
  emit('awarded')
}
</script>

<template>
  <el-dialog
    v-model="comparisonVisible"
    :title="t('procurementRfqCompare.title')"
    width="1080px"
    destroy-on-close
  >
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      :title="t('procurementRfqCompare.notice')"
    />
    <el-table v-loading="comparisonLoading" :data="quotations" border class="comparison-table">
      <el-table-column :label="t('procurementRfqCompare.select')" width="70" align="center">
        <template #default="{ row }">
          <el-radio v-model="awardForm.quotationId" :value="row.id">
            <span class="sr-only">{{ t('procurementRfqCompare.select') }}</span>
          </el-radio>
        </template>
      </el-table-column>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.lines" size="small" border>
            <el-table-column prop="materialCode" :label="t('procurementRequisitionPage.materialCode')" min-width="130" />
            <el-table-column prop="materialName" :label="t('procurementRequisitionPage.materialName')" min-width="160" />
            <el-table-column prop="quantity" :label="t('procurementRequisitionPage.quantity')" min-width="100" align="right" />
            <el-table-column prop="unit" :label="t('procurementRequisitionPage.unit')" width="70" />
            <el-table-column prop="unitPrice" :label="t('procurementPurchaseOrderTracker.unitPrice')" min-width="110" align="right" />
            <el-table-column prop="lineAmount" :label="t('procurementRequisitionPage.lineAmount')" min-width="120" align="right" />
            <el-table-column prop="deliveryDays" :label="t('procurementRfqCompare.deliveryDays')" width="100" />
            <el-table-column prop="remark" :label="t('procurementRfqCompare.supplierRemark')" min-width="150" />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="supplierNameSnapshot" :label="t('procurementPurchaseOrderTracker.supplier')" min-width="190" />
      <el-table-column :label="t('procurementRfqCompare.totalQuotation')" min-width="150" align="right">
        <template #default="{ row }">{{ row.totalAmount }} {{ row.currencyCode }}</template>
      </el-table-column>
      <el-table-column prop="quotationTime" :label="t('procurementRfqCompare.quotationTime')" min-width="175" />
      <el-table-column prop="validUntil" :label="t('procurementRfqCompare.validUntil')" min-width="175" />
      <el-table-column prop="version" :label="t('procurementApprovalRules.version', { version: '' }).replace(' ', '')" width="80">
        <template #default="{ row }">{{ row.version }}</template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!comparisonLoading && !quotations.length"
      :description="t('procurementRfqCompare.emptyQuotations')"
    />

    <el-form
      ref="awardFormRef"
      :model="awardForm"
      :rules="awardRules"
      label-width="110px"
      class="award-form"
    >
      <el-form-item :label="t('procurementRfqCompare.winningQuotation')" prop="quotationId">
        <span v-if="selectedQuotation">
          {{ selectedQuotation.supplierNameSnapshot }} · {{ selectedQuotation.totalAmount }}
          {{ selectedQuotation.currencyCode }} · v{{ selectedQuotation.version }}
        </span>
        <span v-else class="muted">{{ t('procurementRfqCompare.selectFromTable') }}</span>
      </el-form-item>
      <el-form-item :label="t('procurementPurchaseOrderTracker.orderTitle')" prop="title">
        <el-input v-model="awardForm.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item :label="t('procurementPurchaseOrderTracker.expectedDelivery')">
        <el-date-picker
          v-model="awardForm.expectedDeliveryDate"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="t('procurementRfqCompare.deliveryPlaceholder')"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item :label="t('procurementPurchaseOrderTracker.deliveryAddress')" prop="deliveryAddress">
        <el-input
          v-model="awardForm.deliveryAddress"
          type="textarea"
          :rows="2"
          maxlength="500"
        />
      </el-form-item>
      <el-form-item :label="t('procurementPurchaseOrderTracker.contact')" prop="contactName">
        <el-input v-model="awardForm.contactName" maxlength="100" />
      </el-form-item>
      <el-form-item :label="t('procurementPurchaseOrderPage.contactPhone')" prop="contactPhone">
        <el-input v-model="awardForm.contactPhone" maxlength="50" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="comparisonVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button
        v-permission="'procurement:rfq:award'"
        type="primary"
        :disabled="!selectedQuotation"
        @click="award"
      >
        {{ t('procurementRfqCompare.awardAndCreate') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.comparison-table {
  margin-top: 16px;
}

.award-form {
  margin-top: 24px;
}

.muted {
  color: var(--el-text-color-secondary);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
