<script setup lang="ts">
/**
 * 任务类型管理页面。
 * 提供任务类型的增删改查和参数模板编辑功能。
 */
/** @see api/userJobType */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listUserJobTypes, createUserJobType, updateUserJobType, deleteUserJobType, toggleUserJobTypeStatus,
  type UserJobType,
} from '@/api/userJobType'
import type { PageResult } from '@/types/api'
import SchemaFieldEditor from '@/components/SchemaFieldEditor.vue'

const { t } = useI18n()

/** 表格数据 */
const tableData = ref<UserJobType[]>([])
/** 总记录数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页大小 */
const pageSize = ref(10)
/** 加载状态 */
const loading = ref(false)

/** 搜索条件：类型编码 */
const searchTypeCode = ref('')
/** 搜索条件：类型名称 */
const searchTypeName = ref('')
/** 搜索条件：状态 */
const searchStatus = ref<number | undefined>(undefined)

/** 表单对话框可见状态 */
const formDialogVisible = ref(false)
/** 是否编辑模式 */
const isEdit = ref(false)
/** 当前编辑的 ID */
const editingId = ref<number | null>(null)
/** 表单数据 */
const form = reactive({
  typeCode: '',
  typeName: '',
  description: '',
  paramTemplate: '' as string | null,
})

/**
 * 加载任务类型列表数据。
 */
async function loadData() {
  loading.value = true
  try {
    const res = await listUserJobTypes({
      typeCode: searchTypeCode.value || undefined,
      typeName: searchTypeName.value || undefined,
      status: searchStatus.value,
      page: currentPage.value,
      size: pageSize.value,
    })
    const data = res.data.data as PageResult<UserJobType>
    tableData.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/**
 * 处理分页切换。
 *
 * @param page - 新页码
 */
function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/**
 * 搜索（重置页码后加载）。
 */
function handleSearch() {
  currentPage.value = 1
  loadData()
}

/**
 * 重置搜索条件。
 */
function handleReset() {
  searchTypeCode.value = ''
  searchTypeName.value = ''
  searchStatus.value = undefined
  handleSearch()
}

/**
 * 打开创建对话框（清空表单）。
 */
function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, { typeCode: '', typeName: '', description: '', paramTemplate: '' })
  formDialogVisible.value = true
}

/**
 * 打开编辑对话框（填充表单数据）。
 *
 * @param row - 当前行数据
 */
function openEditDialog(row: UserJobType) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    typeCode: row.typeCode,
    typeName: row.typeName,
    description: row.description || '',
    paramTemplate: row.paramTemplate || '',
  })
  formDialogVisible.value = true
}

/**
 * 保存表单（创建或更新）。
 */
async function saveForm() {
  if (isEdit.value && editingId.value) {
    await updateUserJobType(editingId.value, {
      typeName: form.typeName,
      description: form.description,
      paramTemplate: form.paramTemplate,
    })
  } else {
    await createUserJobType(form)
  }
  ElMessage.success(t('common.success'))
  formDialogVisible.value = false
  loadData()
}

/**
 * 删除任务类型（确认后执行）。
 *
 * @param row - 当前行数据
 */
async function handleDelete(row: UserJobType) {
  try {
    await ElMessageBox.confirm(t('userJobType.confirmDelete'), { type: 'warning' })
    await deleteUserJobType(row.id)
    ElMessage.success(t('common.success'))
    loadData()
  } catch { /* cancelled */ }
}

/**
 * 切换任务类型状态。
 *
 * @param row - 当前行数据
 */
async function handleToggleStatus(row: UserJobType) {
  const newStatus = row.status === 1 ? 0 : 1
  await toggleUserJobTypeStatus(row.id, newStatus)
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('common.userJobTypes') }}</span>
          <el-button v-permission="'job:user-job-type:create'" type="primary" @click="openCreateDialog">
            {{ t('common.create') }}
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form inline style="margin-bottom:16px">
        <el-form-item :label="t('userJobType.typeCode')">
          <el-input v-model="searchTypeCode" clearable />
        </el-form-item>
        <el-form-item :label="t('userJobType.typeName')">
          <el-input v-model="searchTypeName" clearable />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="searchStatus" clearable style="width:120px">
            <el-option :label="t('common.enabled')" :value="1" />
            <el-option :label="t('common.disabled')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table v-loading="loading" :data="tableData" stripe border>
        <el-table-column prop="typeCode" :label="t('userJobType.typeCode')" width="180" />
        <el-table-column prop="typeName" :label="t('userJobType.typeName')" width="180" />
        <el-table-column prop="description" :label="t('userJobType.description')" show-overflow-tooltip min-width="200" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-switch v-permission="'job:user-job-type:update'"
                       :model-value="row.status === 1"
                       @change="handleToggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'job:user-job-type:update'" size="small" @click="openEditDialog(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-permission="'job:user-job-type:delete'" size="small" type="danger" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页控件 -->
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination"
                     :page-sizes="[5, 10, 20, 50, 100]" :total="total"
                     layout="total, sizes, prev, pager, next"
                     @current-change="handlePageChange"
                     @size-change="currentPage = 1; handlePageChange(1)" />
    </el-card>

    <!-- 表单对话框 -->
    <el-dialog v-model="formDialogVisible"
               :title="isEdit ? t('userJobType.editType') : t('userJobType.createType')"
               width="800px">
      <el-form :model="form" label-width="120px">
        <el-form-item :label="t('userJobType.typeCode')">
          <el-input v-model="form.typeCode" :disabled="isEdit" />
        </el-form-item>
        <el-form-item :label="t('userJobType.typeName')">
          <el-input v-model="form.typeName" />
        </el-form-item>
        <el-form-item :label="t('userJobType.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('userJobType.paramTemplate')">
          <SchemaFieldEditor v-model="form.paramTemplate" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
