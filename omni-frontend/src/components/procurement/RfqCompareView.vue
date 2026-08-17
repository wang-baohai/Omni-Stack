<script setup lang="ts">
/** 询价比价与定标共享组件——供应商报价横向对比 + 定标表单。 */
import { computed, reactive, ref, watch } from 'vue'
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
const awardRules: FormRules = {
  quotationId: [{ required: true, message: '请选择中标报价', trigger: 'change' }],
  title: [
    { required: true, message: '请输入采购订单标题', trigger: 'blur' },
    { max: 200, message: '订单标题不能超过 200 个字符', trigger: 'blur' },
  ],
  deliveryAddress: [
    { required: true, message: '请输入收货地址', trigger: 'blur' },
    { max: 500, message: '收货地址不能超过 500 个字符', trigger: 'blur' },
  ],
  contactName: [{ required: true, message: '请输入收货联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
}
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
    `确认选择"${quotation.supplierNameSnapshot}"报价 ${quotation.totalAmount} ${quotation.currencyCode} 定标？定标后报价快照不可修改。`,
    '确认定标',
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
  ElMessage.success(`定标成功，已生成采购订单 ${response.data.data.purchaseOrder.poNo}`)
  comparisonVisible.value = false
  emit('awarded')
}
</script>

<template>
  <el-dialog v-model="comparisonVisible" title="供应商比价与定标" width="1080px" destroy-on-close>
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="列表只包含 SRM 当前有效报价；提交定标时服务端会重新拉取并核对报价版本与全部行快照。"
    />
    <el-table v-loading="comparisonLoading" :data="quotations" border class="comparison-table">
      <el-table-column label="选择" width="70" align="center">
        <template #default="{ row }">
          <el-radio v-model="awardForm.quotationId" :value="row.id">
            <span class="sr-only">选择</span>
          </el-radio>
        </template>
      </el-table-column>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-table :data="row.lines" size="small" border>
            <el-table-column prop="materialCode" label="物料编码" min-width="130" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="quantity" label="数量" min-width="100" align="right" />
            <el-table-column prop="unit" label="单位" width="70" />
            <el-table-column prop="unitPrice" label="单价" min-width="110" align="right" />
            <el-table-column prop="lineAmount" label="行金额" min-width="120" align="right" />
            <el-table-column prop="deliveryDays" label="交付天数" width="100" />
            <el-table-column prop="remark" label="供应商备注" min-width="150" />
          </el-table>
        </template>
      </el-table-column>
      <el-table-column prop="supplierNameSnapshot" label="供应商" min-width="190" />
      <el-table-column label="总报价" min-width="150" align="right">
        <template #default="{ row }">{{ row.totalAmount }} {{ row.currencyCode }}</template>
      </el-table-column>
      <el-table-column prop="quotationTime" label="报价时间" min-width="175" />
      <el-table-column prop="validUntil" label="有效期至" min-width="175" />
      <el-table-column prop="version" label="版本" width="80" />
    </el-table>
    <el-empty
      v-if="!comparisonLoading && !quotations.length"
      description="暂无当前有效报价，不能定标"
    />

    <el-form
      ref="awardFormRef"
      :model="awardForm"
      :rules="awardRules"
      label-width="110px"
      class="award-form"
    >
      <el-form-item label="中标报价" prop="quotationId">
        <span v-if="selectedQuotation">
          {{ selectedQuotation.supplierNameSnapshot }} · {{ selectedQuotation.totalAmount }}
          {{ selectedQuotation.currencyCode }} · v{{ selectedQuotation.version }}
        </span>
        <span v-else class="muted">请从上表选择</span>
      </el-form-item>
      <el-form-item label="订单标题" prop="title">
        <el-input v-model="awardForm.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="预计交付">
        <el-date-picker
          v-model="awardForm.expectedDeliveryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="留空则按报价最长交付天数计算"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="收货地址" prop="deliveryAddress">
        <el-input
          v-model="awardForm.deliveryAddress"
          type="textarea"
          :rows="2"
          maxlength="500"
        />
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input v-model="awardForm.contactName" maxlength="100" />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="awardForm.contactPhone" maxlength="50" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="comparisonVisible = false">取消</el-button>
      <el-button
        v-permission="'procurement:rfq:award'"
        type="primary"
        :disabled="!selectedQuotation"
        @click="award"
      >
        确认定标并生成订单
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
