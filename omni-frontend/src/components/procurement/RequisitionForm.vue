<script setup lang="ts">
/** 请购表单共享组件——新建 / 编辑请购对话框，含明细行动态增删。 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createProcurementRequisition,
  getProcurementRequisition,
  updateProcurementRequisition,
  type ProcurementRequisitionDetail,
  type ProcurementRequisitionSummary,
} from '@/api/procurement-requisition'
import {
  listProcurementMaterials,
  type ProcurementMaterial,
} from '@/api/procurement-material'

const props = defineProps<{
  modelValue: boolean
  editData?: ProcurementRequisitionSummary
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const materialLoading = ref(false)
const materialOptions = ref<ProcurementMaterial[]>([])

async function loadMaterialOptions(keyword?: string) {
  materialLoading.value = true
  try {
    const response = await listProcurementMaterials({
      keyword: keyword || undefined,
      status: 'ACTIVE',
      page: 1,
      size: 100,
    })
    const existing = new Map(materialOptions.value.map((item) => [item.id, item]))
    for (const item of response.data.data.records) existing.set(item.id, item)
    materialOptions.value = [...existing.values()]
  } finally {
    materialLoading.value = false
  }
}

function findMaterial(id?: number) {
  return materialOptions.value.find((item) => item.id === id)
}

interface EditableLine {
  key: number
  materialId?: number
  quantity: string
  estimatedUnitPrice: string
  remark: string
}

let nextLineKey = 1
function emptyLine(): EditableLine {
  return {
    key: nextLineKey++,
    materialId: undefined,
    quantity: '1.000000',
    estimatedUnitPrice: '0.000000',
    remark: '',
  }
}

const formRef = ref<FormInstance>()
const editing = ref<ProcurementRequisitionDetail>()
const form = reactive<{
  title: string
  reason: string
  version?: number
  lines: EditableLine[]
}>({ title: '', reason: '', lines: [emptyLine()] })
const rules: FormRules = {
  title: [
    { required: true, message: '请输入请购标题', trigger: 'blur' },
    { max: 200, message: '请购标题不能超过 200 个字符', trigger: 'blur' },
  ],
}
const selectedCategoryCode = computed(() => {
  const codes = form.lines
    .map((line) => findMaterial(line.materialId)?.categoryCode)
    .filter((value): value is string => Boolean(value))
  return codes[0] || ''
})

function addMaterialSnapshot(detail: ProcurementRequisitionDetail) {
  const existing = new Set(materialOptions.value.map((item) => item.id))
  for (const line of detail.lines) {
    if (existing.has(line.materialId)) continue
    materialOptions.value.push({
      id: line.materialId,
      categoryId: 0,
      categoryCode: line.categoryCode,
      categoryName: line.categoryCode,
      materialCode: line.materialCode,
      materialName: line.materialName,
      specification: null,
      unit: line.unit,
      assetManaged: false,
      status: 'INACTIVE',
      version: 0,
      createTime: '',
      updateTime: '',
    })
  }
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    if (props.editData) {
      const response = await getProcurementRequisition(props.editData.id)
      editing.value = response.data.data
      await loadMaterialOptions()
      addMaterialSnapshot(response.data.data)
      Object.assign(form, {
        title: response.data.data.title,
        reason: response.data.data.reason || '',
        version: response.data.data.version,
        lines: response.data.data.lines.map((line) => ({
          key: nextLineKey++,
          materialId: line.materialId,
          quantity: line.quantity,
          estimatedUnitPrice: line.estimatedUnitPrice,
          remark: line.remark || '',
        })),
      })
    } else {
      editing.value = undefined
      Object.assign(form, { title: '', reason: '', version: undefined, lines: [emptyLine()] })
      await loadMaterialOptions()
    }
  },
)

function addLine() {
  form.lines.push(emptyLine())
}

function removeLine(index: number) {
  if (form.lines.length === 1) {
    ElMessage.warning('请购至少需要一条明细')
    return
  }
  form.lines.splice(index, 1)
}

function onMaterialChanged(line: EditableLine) {
  const material = findMaterial(line.materialId)
  if (!material) return
  const otherCategory = form.lines
    .filter((item) => item.key !== line.key)
    .map((item) => findMaterial(item.materialId)?.categoryCode)
    .find(Boolean)
  if (otherCategory && otherCategory !== material.categoryCode) {
    line.materialId = undefined
    ElMessage.warning('MVP 要求一张请购单的所有物料属于同一品类')
  }
}

const decimalPattern = /^\d{1,13}(?:\.\d{1,6})?$/
function validateLines() {
  if (!form.lines.length) return '请购至少需要一条明细'
  const materialIds = new Set<number>()
  let categoryCode = ''
  for (const [index, line] of form.lines.entries()) {
    if (!line.materialId) return `第 ${index + 1} 行请选择物料`
    if (materialIds.has(line.materialId)) return `第 ${index + 1} 行物料重复`
    materialIds.add(line.materialId)
    const material = findMaterial(line.materialId)
    if (!material || material.status !== 'ACTIVE') return `第 ${index + 1} 行物料已停用，请重新选择`
    if (categoryCode && material.categoryCode !== categoryCode) return '所有物料必须属于同一品类'
    categoryCode = material.categoryCode
    if (!decimalPattern.test(line.quantity) || !/[1-9]/.test(line.quantity)) {
      return `第 ${index + 1} 行数量必须大于 0，且最多 6 位小数`
    }
    if (!decimalPattern.test(line.estimatedUnitPrice)) {
      return `第 ${index + 1} 行预估单价格式不正确，最多 6 位小数`
    }
    if (line.remark.length > 500) return `第 ${index + 1} 行备注不能超过 500 个字符`
  }
  return ''
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const lineError = validateLines()
  if (lineError) {
    ElMessage.warning(lineError)
    return
  }
  const request = {
    title: form.title.trim(),
    reason: form.reason.trim() || undefined,
    lines: form.lines.map((line) => ({
      materialId: line.materialId!,
      quantity: line.quantity,
      estimatedUnitPrice: line.estimatedUnitPrice,
      remark: line.remark.trim() || undefined,
    })),
  }
  if (editing.value) {
    await updateProcurementRequisition(editing.value.id, {
      ...request,
      version: form.version ?? editing.value.version,
    })
  } else {
    await createProcurementRequisition(request)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  emit('saved')
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="editing ? '编辑请购' : '新建请购'"
    width="980px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="请购标题" prop="title">
        <el-input v-model="form.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="请购原因">
        <el-input v-model="form.reason" type="textarea" :rows="2" maxlength="1000" show-word-limit />
      </el-form-item>
      <el-form-item label="物料品类">
        <el-tag v-if="selectedCategoryCode">{{ selectedCategoryCode }}</el-tag>
        <span v-else class="form-tip">选择首条物料后自动确定；一张请购仅支持一个品类</span>
      </el-form-item>
      <el-form-item label="请购明细">
        <div class="line-editor">
          <el-table :data="form.lines" border>
            <el-table-column label="物料" min-width="260">
              <template #default="{ row }">
                <el-select
                  v-model="row.materialId"
                  filterable
                  remote
                  :remote-method="loadMaterialOptions"
                  :loading="materialLoading"
                  placeholder="搜索物料编码或名称"
                  style="width: 100%"
                  @change="onMaterialChanged(row)"
                >
                  <el-option
                    v-for="material in materialOptions"
                    :key="material.id"
                    :label="`${material.materialCode} · ${material.materialName} · ${material.categoryCode}`"
                    :value="material.id"
                    :disabled="material.status !== 'ACTIVE'"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="145">
              <template #default="{ row }">
                <el-input v-model="row.quantity" maxlength="20" placeholder="十进制字符串" />
              </template>
            </el-table-column>
            <el-table-column label="预估单价" min-width="145">
              <template #default="{ row }">
                <el-input v-model="row.estimatedUnitPrice" maxlength="20" placeholder="十进制字符串" />
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.remark" maxlength="500" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeLine($index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button class="add-line" plain type="primary" @click="addLine">添加明细</el-button>
          <div class="form-tip">行金额和总金额只由服务端使用 BigDecimal 计算，页面不使用浮点数推导。</div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="save">保存草稿</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.line-editor {
  width: 100%;
}

.add-line {
  margin-top: 12px;
}

.form-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
