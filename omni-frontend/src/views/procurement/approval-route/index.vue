<script setup lang="ts">
/** 请购审批路由页面，按品类和金额区间绑定已发布流程模型版本。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createProcurementApprovalRoute,
  deleteProcurementApprovalRoute,
  listProcurementApprovalRoutes,
  updateProcurementApprovalRoute,
  type ProcurementApprovalRoute,
  type ProcurementApprovalRouteStatus,
} from '@/api/procurement-approval-route'
import {
  listProcurementCategories,
  type ProcurementMaterialCategory,
} from '@/api/procurement-material'
import { listModels, type ProcessModel } from '@/api/workflow-model'
import { usePermissionStore } from '@/stores/permission'

const permissionStore = usePermissionStore()
const loading = ref(false)
const rows = ref<ProcurementApprovalRoute[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  keyword: string
  categoryCode: string
  status?: ProcurementApprovalRouteStatus
}>({ keyword: '', categoryCode: '' })
const categories = ref<ProcurementMaterialCategory[]>([])
const workflowModels = ref<ProcessModel[]>([])

const categoryOptions = computed(() => {
  const options = [{ value: '*', label: '*（默认路由）' }]
  for (const parent of categories.value) {
    options.push({ value: parent.categoryCode, label: `${parent.categoryName}（${parent.categoryCode}）` })
    for (const child of parent.children || []) {
      options.push({
        value: child.categoryCode,
        label: `${parent.categoryName} / ${child.categoryName}（${child.categoryCode}）`,
      })
    }
  }
  return options
})

const publishedModels = computed(() => workflowModels.value.filter(
  (model) => model.currentPublishedVersionId != null,
))

function categoryLabel(categoryCode: string) {
  return categoryOptions.value.find((item) => item.value === categoryCode)?.label || categoryCode
}

function modelLabel(modelVersionId: number) {
  const model = publishedModels.value.find((item) => item.currentPublishedVersionId === modelVersionId)
  return model ? `${model.modelName}（${model.modelKey}）` : `版本 #${modelVersionId}`
}

async function loadRows() {
  loading.value = true
  try {
    const response = await listProcurementApprovalRoutes({
      keyword: query.keyword || undefined,
      categoryCode: query.categoryCode || undefined,
      status: query.status,
      page: currentPage.value,
      size: pageSize.value,
    })
    rows.value = response.data.data.records
    total.value = response.data.data.total
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const categoryResponse = await listProcurementCategories()
  categories.value = categoryResponse.data.data
  if (permissionStore.hasPermission('workflow:model:list')) {
    const modelResponse = await listModels({ page: 1, size: 100 })
    workflowModels.value = modelResponse.data.data.records
  }
}

function search() {
  currentPage.value = 1
  loadRows()
}

function resetQuery() {
  Object.assign(query, { keyword: '', categoryCode: '', status: undefined })
  search()
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editingRoute = ref<ProcurementApprovalRoute>()
const form = reactive<{
  routeCode: string
  categoryCode: string
  minAmount: string
  maxAmount: string
  modelVersionId?: number
  priority: number
  status: ProcurementApprovalRouteStatus
  version?: number
}>({
  routeCode: '',
  categoryCode: '*',
  minAmount: '0.0000',
  maxAmount: '',
  modelVersionId: undefined,
  priority: 0,
  status: 'ACTIVE',
})

const decimalPattern = /^\d{1,15}(?:\.\d{1,4})?$/
function compareDecimalStrings(left: string, right: string) {
  const [leftIntegerRaw, leftFractionRaw = ''] = left.split('.')
  const [rightIntegerRaw, rightFractionRaw = ''] = right.split('.')
  const leftInteger = leftIntegerRaw.replace(/^0+(?=\d)/, '')
  const rightInteger = rightIntegerRaw.replace(/^0+(?=\d)/, '')
  if (leftInteger.length !== rightInteger.length) return leftInteger.length - rightInteger.length
  const integerComparison = leftInteger.localeCompare(rightInteger)
  if (integerComparison !== 0) return integerComparison
  return leftFractionRaw.padEnd(4, '0').localeCompare(rightFractionRaw.padEnd(4, '0'))
}
function validateAmount(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!decimalPattern.test(value)) callback(new Error('请输入非负金额，最多 15 位整数和 4 位小数'))
  else callback()
}
function validateMaxAmount(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback()
    return
  }
  if (!decimalPattern.test(value)) {
    callback(new Error('请输入非负金额，最多 15 位整数和 4 位小数'))
    return
  }
  if (decimalPattern.test(form.minAmount) && compareDecimalStrings(value, form.minAmount) <= 0) {
    callback(new Error('金额上界必须大于下界'))
    return
  }
  callback()
}
const rules: FormRules = {
  routeCode: [
    { required: true, message: '请输入路由编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9][A-Za-z0-9_.-]*$/, message: '路由编码格式不正确', trigger: 'blur' },
  ],
  categoryCode: [{ required: true, message: '请选择物料品类', trigger: 'change' }],
  minAmount: [{ required: true, validator: validateAmount, trigger: 'blur' }],
  maxAmount: [{ validator: validateMaxAmount, trigger: 'blur' }],
  modelVersionId: [{ required: true, message: '请选择或输入已发布模型版本 ID', trigger: 'change' }],
}

function openCreate() {
  editingRoute.value = undefined
  Object.assign(form, {
    routeCode: '',
    categoryCode: '*',
    minAmount: '0.0000',
    maxAmount: '',
    modelVersionId: undefined,
    priority: 0,
    status: 'ACTIVE',
    version: undefined,
  })
  dialogVisible.value = true
}

function openEdit(row: ProcurementApprovalRoute) {
  editingRoute.value = row
  Object.assign(form, {
    routeCode: row.routeCode,
    categoryCode: row.categoryCode,
    minAmount: row.minAmount,
    maxAmount: row.maxAmount || '',
    modelVersionId: row.modelVersionId,
    priority: row.priority,
    status: row.status,
    version: row.version,
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.modelVersionId) return
  const common = {
    categoryCode: form.categoryCode,
    minAmount: form.minAmount,
    maxAmount: form.maxAmount || undefined,
    modelVersionId: form.modelVersionId,
    priority: form.priority,
    status: form.status,
  }
  if (editingRoute.value) {
    await updateProcurementApprovalRoute(editingRoute.value.id, {
      ...common,
      version: form.version ?? editingRoute.value.version,
    })
  } else {
    await createProcurementApprovalRoute({
      ...common,
      routeCode: form.routeCode.trim(),
    })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadRows()
}

async function remove(row: ProcurementApprovalRoute) {
  try {
    await ElMessageBox.confirm(`确认删除审批路由“${row.routeCode}”？`, '删除确认', {
      type: 'warning',
    })
    await deleteProcurementApprovalRoute(row.id, row.version)
    ElMessage.success('删除成功')
    loadRows()
  } catch {
    // 用户取消时保持当前页面状态。
  }
}

onMounted(async () => {
  await Promise.all([loadRows(), loadOptions()])
})
</script>

<template>
  <div class="approval-route-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="提交请购时先匹配精确品类，再回退到 * 默认路由；金额区间左闭右开，活动区间不得重叠。"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>请购审批路由</span>
          <el-button
            v-permission="'procurement:approval-route:create'"
            type="primary"
            @click="openCreate"
          >
            新建路由
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="路由或品类编码" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="品类">
          <el-select v-model="query.categoryCode" clearable filterable placeholder="全部" style="width: 230px">
            <el-option
              v-for="option in categoryOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="routeCode" label="路由编码" min-width="150" />
        <el-table-column label="品类" min-width="220">
          <template #default="{ row }">{{ categoryLabel(row.categoryCode) }}</template>
        </el-table-column>
        <el-table-column label="金额区间" min-width="210">
          <template #default="{ row }">
            [{{ row.minAmount }}, {{ row.maxAmount || '∞' }})
          </template>
        </el-table-column>
        <el-table-column label="流程模型版本" min-width="220">
          <template #default="{ row }">{{ modelLabel(row.modelVersionId) }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'procurement:approval-route:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'procurement:approval-route:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[5, 10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadRows"
        @size-change="search"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingRoute ? '编辑审批路由' : '新建审批路由'"
      width="620px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="路由编码" prop="routeCode">
          <el-input v-model="form.routeCode" :disabled="Boolean(editingRoute)" maxlength="64" />
        </el-form-item>
        <el-form-item label="物料品类" prop="categoryCode">
          <el-select v-model="form.categoryCode" filterable style="width: 100%">
            <el-option
              v-for="option in categoryOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="金额下界" prop="minAmount">
          <el-input v-model="form.minAmount" maxlength="20" placeholder="包含，例如 0.0000" />
        </el-form-item>
        <el-form-item label="金额上界" prop="maxAmount">
          <el-input v-model="form.maxAmount" maxlength="20" placeholder="不包含；留空表示无上限" />
        </el-form-item>
        <el-form-item label="流程模型版本" prop="modelVersionId">
          <el-select
            v-if="publishedModels.length"
            v-model="form.modelVersionId"
            filterable
            style="width: 100%"
            placeholder="选择已发布模型"
          >
            <el-option
              v-for="model in publishedModels"
              :key="model.currentPublishedVersionId!"
              :label="`${model.modelName}（${model.modelKey}） · 版本 ID ${model.currentPublishedVersionId}`"
              :value="model.currentPublishedVersionId!"
            />
          </el-select>
          <el-input-number
            v-else
            v-model="form.modelVersionId"
            :min="1"
            :controls="false"
            style="width: 100%"
            placeholder="请输入已发布模型版本 ID"
          />
        </el-form-item>
        <el-form-item label="列表排序">
          <el-input-number v-model="form.priority" :min="0" :max="999999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">启用</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.approval-route-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
