<script setup lang="ts">
/** 收货表单共享组件——新建收货草稿对话框，含 PO 选择与收货货物明细。 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createProcurementGoodsReceipt,
  type ProcurementGoodsReceiptQualityStatus,
} from '@/api/procurement-goods-receipt'
import {
  getProcurementPurchaseOrder,
  listProcurementPurchaseOrders,
  type ProcurementPurchaseOrderDetail,
  type ProcurementPurchaseOrderSummary,
} from '@/api/procurement-purchase-order'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const qualityStatusOptions: Array<{
  value: ProcurementGoodsReceiptQualityStatus
  label: string
  type: 'warning' | 'success' | 'danger'
}> = [
  { value: 'PASS', label: '合格', type: 'success' },
  { value: 'FAIL', label: '不合格', type: 'danger' },
  { value: 'PENDING', label: '待定', type: 'warning' },
]

const orderLoading = ref(false)
const orderOptions = ref<ProcurementPurchaseOrderSummary[]>([])

async function loadOrderOptions(keyword?: string) {
  orderLoading.value = true
  try {
    const statuses = ['CONFIRMED', 'PARTIAL_RECEIVED'] as const
    const responses = await Promise.all(
      statuses.map((status) =>
        listProcurementPurchaseOrders({
          keyword: keyword?.trim() || undefined,
          status,
          page: 1,
          size: 100,
        }),
      ),
    )
    const options = new Map<number, ProcurementPurchaseOrderSummary>()
    for (const response of responses) {
      for (const order of response.data.data.records) options.set(order.id, order)
    }
    orderOptions.value = [...options.values()]
  } finally {
    orderLoading.value = false
  }
}

interface EditableReceiptLine {
  selected: boolean
  poLineId: number
  materialCode: string
  materialName: string
  unit: string
  remainingQuantity: string
  receivedQuantity: string
  qualityStatus: ProcurementGoodsReceiptQualityStatus
  remark: string
}

const createFormRef = ref<FormInstance>()
const selectedOrder = ref<ProcurementPurchaseOrderDetail>()
const createForm = reactive<{
  poId?: number
  receiveTime: string
  remark: string
  lines: EditableReceiptLine[]
}>({ receiveTime: '', remark: '', lines: [] })
const createRules: FormRules = {
  poId: [{ required: true, message: '请选择采购订单', trigger: 'change' }],
  receiveTime: [{ required: true, message: '请选择收货时间', trigger: 'change' }],
}

function nowText() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    selectedOrder.value = undefined
    Object.assign(createForm, { poId: undefined, receiveTime: nowText(), remark: '', lines: [] })
    loadOrderOptions()
  },
)

function compareDecimal(left: string, right: string) {
  const normalize = (value: string) => {
    const [integer = '0', fraction = ''] = value.split('.')
    return {
      integer: integer.replace(/^0+(?=\d)/, ''),
      fraction: fraction.padEnd(6, '0'),
    }
  }
  const a = normalize(left)
  const b = normalize(right)
  if (a.integer.length !== b.integer.length) return a.integer.length - b.integer.length
  const integerCompare = a.integer.localeCompare(b.integer)
  return integerCompare || a.fraction.localeCompare(b.fraction)
}

async function onOrderChanged() {
  createForm.lines = []
  selectedOrder.value = undefined
  if (!createForm.poId) return
  const response = await getProcurementPurchaseOrder(createForm.poId)
  selectedOrder.value = response.data.data
  createForm.lines = response.data.data.lines
    .filter((line: { remainingQuantity: string }) => compareDecimal(line.remainingQuantity, '0') > 0)
    .map((line: { id: number; materialCode: string; materialName: string; unit: string; remainingQuantity: string }) => ({
      selected: true,
      poLineId: line.id,
      materialCode: line.materialCode,
      materialName: line.materialName,
      unit: line.unit,
      remainingQuantity: line.remainingQuantity,
      receivedQuantity: line.remainingQuantity,
      qualityStatus: 'PASS' as const,
      remark: '',
    }))
}

const decimalPattern = /^\d{1,13}(?:\.\d{1,6})?$/
function validateReceiptLines() {
  const selected = createForm.lines.filter((line) => line.selected)
  if (!selected.length) return '请至少选择一条待收货明细'
  for (const [index, line] of selected.entries()) {
    if (!decimalPattern.test(line.receivedQuantity) || compareDecimal(line.receivedQuantity, '0') <= 0) {
      return `第 ${index + 1} 条收货数量必须大于 0，且最多 6 位小数`
    }
    if (compareDecimal(line.receivedQuantity, line.remainingQuantity) > 0) {
      return `${line.materialName} 的本次收货数量不能超过待收数量 ${line.remainingQuantity}`
    }
    if (line.remark.length > 500) return `${line.materialName} 的备注不能超过 500 个字符`
  }
  return ''
}

async function createDraft() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid || !createForm.poId) return
  const lineError = validateReceiptLines()
  if (lineError) {
    ElMessage.warning(lineError)
    return
  }
  await createProcurementGoodsReceipt({
    poId: createForm.poId,
    receiveTime: createForm.receiveTime,
    remark: createForm.remark.trim() || undefined,
    lines: createForm.lines
      .filter((line) => line.selected)
      .map((line) => ({
        poLineId: line.poLineId,
        receivedQuantity: line.receivedQuantity,
        qualityStatus: line.qualityStatus,
        remark: line.remark.trim() || undefined,
      })),
  })
  ElMessage.success('收货草稿已创建，请核对后确认')
  dialogVisible.value = false
  emit('saved')
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="新建收货草稿" width="980px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
      <el-form-item label="采购订单" prop="poId">
        <el-select
          v-model="createForm.poId"
          filterable
          remote
          :remote-method="loadOrderOptions"
          :loading="orderLoading"
          placeholder="选择已确认且仍有待收数量的订单"
          style="width: 100%"
          @change="onOrderChanged"
        >
          <el-option
            v-for="item in orderOptions"
            :key="item.id"
            :label="`${item.poNo} · ${item.supplierNameSnapshot} · ${item.title}`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="收货时间" prop="receiveTime">
        <el-date-picker
          v-model="createForm.receiveTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="createForm.remark" type="textarea" maxlength="500" show-word-limit />
      </el-form-item>

      <el-table v-if="createForm.lines.length" :data="createForm.lines" border>
        <el-table-column label="收货" width="65" align="center">
          <template #default="{ row }"><el-checkbox v-model="row.selected" /></template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料编码" min-width="125" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="remainingQuantity" label="待收数量" min-width="105" align="right" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="本次数量" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.receivedQuantity" :disabled="!row.selected" />
          </template>
        </el-table-column>
        <el-table-column label="质检状态" min-width="130">
          <template #default="{ row }">
            <el-select v-model="row.qualityStatus" :disabled="!row.selected">
              <el-option
                v-for="option in qualityStatusOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="行备注" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.remark" :disabled="!row.selected" maxlength="500" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="createForm.poId" description="该订单没有可收货明细" />
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="createDraft">创建草稿</el-button>
    </template>
  </el-dialog>
</template>
