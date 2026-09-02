<script setup lang="ts">
/** 请购表单共享组件——新建 / 编辑请购对话框，含明细行动态增删。 */
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
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
const rules = computed<FormRules>(() => ({
  title: [
    { required: true, message: t('procurementRequisitionFormMessages.titleRequired'), trigger: 'blur' },
    { max: 200, message: t('procurementRequisitionFormMessages.titleLength'), trigger: 'blur' },
  ],
}))
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
    ElMessage.warning(t('procurementRequisitionFormMessages.minimumLine'))
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
    ElMessage.warning(t('procurementRequisitionFormMessages.sameCategoryRequired'))
  }
}

const decimalPattern = /^\d{1,13}(?:\.\d{1,6})?$/
function validateLines() {
  if (!form.lines.length) return t('procurementRequisitionFormMessages.minimumLine')
  const materialIds = new Set<number>()
  let categoryCode = ''
  for (const [index, line] of form.lines.entries()) {
    if (!line.materialId) return t('procurementRequisitionFormMessages.selectMaterial', { index: index + 1 })
    if (materialIds.has(line.materialId)) return t('procurementRequisitionFormMessages.duplicateMaterial', { index: index + 1 })
    materialIds.add(line.materialId)
    const material = findMaterial(line.materialId)
    if (!material || material.status !== 'ACTIVE') {
      return t('procurementRequisitionFormMessages.inactiveMaterial', { index: index + 1 })
    }
    if (categoryCode && material.categoryCode !== categoryCode) {
      return t('procurementRequisitionFormMessages.sameCategoryRequired')
    }
    categoryCode = material.categoryCode
    if (!decimalPattern.test(line.quantity) || !/[1-9]/.test(line.quantity)) {
      return t('procurementRequisitionFormMessages.invalidQuantity', { index: index + 1 })
    }
    if (!decimalPattern.test(line.estimatedUnitPrice)) {
      return t('procurementRequisitionFormMessages.invalidPrice', { index: index + 1 })
    }
    if (line.remark.length > 500) {
      return t('procurementRequisitionFormMessages.remarkLength', { index: index + 1 })
    }
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
  ElMessage.success(t('common.save'))
  dialogVisible.value = false
  emit('saved')
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :title="editing ? t('procurementRequisitionPage.editTitle') : t('procurementRequisitionPage.createTitle')"
    width="980px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item :label="t('procurementRequisitionFormMessages.titleLabel')" prop="title">
        <el-input v-model="form.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item :label="t('procurementRequisitionPage.reason')">
        <el-input v-model="form.reason" type="textarea" :rows="2" maxlength="1000" show-word-limit />
      </el-form-item>
      <el-form-item :label="t('procurementRfqCompare.categoryLabel')">
        <el-tag v-if="selectedCategoryCode">{{ selectedCategoryCode }}</el-tag>
        <span v-else class="form-tip">{{ t('procurementRequisitionFormMessages.categoryTip') }}</span>
      </el-form-item>
      <el-form-item :label="t('procurementRequisitionPage.linesTitle')">
        <div class="line-editor">
          <el-table :data="form.lines" border>
            <el-table-column :label="t('procurementRfqCompare.materialLabel')" min-width="260">
              <template #default="{ row }">
                <el-select
                  v-model="row.materialId"
                  filterable
                  remote
                  :remote-method="loadMaterialOptions"
                  :loading="materialLoading"
                  :placeholder="t('procurementRequisitionFormMessages.materialPlaceholder')"
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
            <el-table-column :label="t('procurementRequisitionPage.quantity')" min-width="145">
              <template #default="{ row }">
                <el-input v-model="row.quantity" maxlength="20" :placeholder="t('procurementRequisitionFormMessages.decimalText')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('procurementRequisitionPage.estimatedUnitPrice')" min-width="145">
              <template #default="{ row }">
                <el-input v-model="row.estimatedUnitPrice" maxlength="20" :placeholder="t('procurementRequisitionFormMessages.decimalText')" />
              </template>
            </el-table-column>
            <el-table-column :label="t('procurementGoodsReceiptForm.remark')" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.remark" maxlength="500" />
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeLine($index)">{{ t('procurementRequisitionFormMessages.remove') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button class="add-line" plain type="primary" @click="addLine">
            {{ t('procurementRequisitionFormMessages.addLine') }}
          </el-button>
          <div class="form-tip">{{ t('procurementRequisitionFormMessages.serverCalculationTip') }}</div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="save">{{ t('procurementRfqPage.saveDraft') }}</el-button>
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
