<script setup lang="ts">
/**
 * 数据字典管理页面。
 * 左侧展示字典类型列表（可搜索/分页），右侧展示选中类型下的字典数据列表。
 * 支持字典类型和字典数据的增删改查，以及缓存刷新功能。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listDictTypes,
  createDictType,
  updateDictType,
  deleteDictType,
  toggleDictTypeStatus,
  listDictData,
  createDictData,
  updateDictData,
  deleteDictData,
  refreshDictCache,
  type DictType,
  type DictData,
} from '@/api/dict'

const { t } = useI18n()

// ===== 字典类型列表 =====
const typeList = ref<DictType[]>([])
const typeTotal = ref(0)
const typePage = ref(1)
const typeSize = ref(10)
const typeLoading = ref(false)
const typeSearch = reactive({ typeCode: '', typeName: '', status: undefined as number | undefined })

/** 当前选中的字典类型 */
const selectedType = ref<DictType | null>(null)

/** 字典数据列表 */
const dataList = ref<DictData[]>([])
const dataTotal = ref(0)
const dataPage = ref(1)
const dataSize = ref(10)
const dataLoading = ref(false)

// ===== 类型表单 =====
const typeFormVisible = ref(false)
const typeFormIsEdit = ref(false)
const typeFormId = ref<number | null>(null)
const typeForm = reactive({ typeCode: '', typeName: '', remark: '', sort: 0 })

// ===== 数据表单 =====
const dataFormVisible = ref(false)
const dataFormIsEdit = ref(false)
const dataFormId = ref<number | null>(null)
const dataForm = reactive({
  typeCode: '',
  dictValue: '',
  dictLabel: '',
  tagType: '',
  remark: '',
  sort: 0,
})

/** 标签样式选项 */
const tagTypeOptions = [
  { value: '', label: '-' },
  { value: 'success', label: 'Success' },
  { value: 'warning', label: 'Warning' },
  { value: 'danger', label: 'Danger' },
  { value: 'info', label: 'Info' },
  { value: 'primary', label: 'Primary' },
]

/** 加载字典类型列表 */
async function loadTypes() {
  typeLoading.value = true
  try {
    const res = await listDictTypes({
      typeCode: typeSearch.typeCode || undefined,
      typeName: typeSearch.typeName || undefined,
      status: typeSearch.status,
      page: typePage.value,
      size: typeSize.value,
    })
    typeList.value = res.data.data.records
    typeTotal.value = res.data.data.total
  } catch { /* 忽略错误 */ } finally {
    typeLoading.value = false
  }
}

/** 搜索字典类型 */
function handleTypeSearch() {
  typePage.value = 1
  loadTypes()
}

/** 重置搜索 */
function handleTypeReset() {
  typeSearch.typeCode = ''
  typeSearch.typeName = ''
  typeSearch.status = undefined
  handleTypeSearch()
}

/** 选择字典类型，加载右侧数据 */
function handleSelectType(row: DictType) {
  selectedType.value = row
  dataPage.value = 1
  loadDataList()
}

/** 加载字典数据列表 */
async function loadDataList() {
  if (!selectedType.value) return
  dataLoading.value = true
  try {
    const res = await listDictData({
      typeCode: selectedType.value.typeCode,
      page: dataPage.value,
      size: dataSize.value,
    })
    dataList.value = res.data.data.records
    dataTotal.value = res.data.data.total
  } catch { /* 忽略错误 */ } finally {
    dataLoading.value = false
  }
}

// ===== 字典类型 CRUD =====

/** 打开新建类型对话框 */
function openCreateTypeDialog() {
  typeFormIsEdit.value = false
  typeFormId.value = null
  typeForm.typeCode = ''
  typeForm.typeName = ''
  typeForm.remark = ''
  typeForm.sort = 0
  typeFormVisible.value = true
}

/** 打开编辑类型对话框 */
function openEditTypeDialog(row: DictType) {
  typeFormIsEdit.value = true
  typeFormId.value = row.id
  typeForm.typeCode = row.typeCode
  typeForm.typeName = row.typeName
  typeForm.remark = row.remark || ''
  typeForm.sort = row.sort
  typeFormVisible.value = true
}

/** 提交类型表单 */
async function submitTypeForm() {
  try {
    if (typeFormIsEdit.value && typeFormId.value !== null) {
      await updateDictType(typeFormId.value, {
        typeName: typeForm.typeName,
        remark: typeForm.remark,
        sort: typeForm.sort,
      })
    } else {
      await createDictType({
        typeCode: typeForm.typeCode,
        typeName: typeForm.typeName,
        remark: typeForm.remark || undefined,
        sort: typeForm.sort,
      })
    }
    ElMessage.success(t('common.success'))
    typeFormVisible.value = false
    loadTypes()
    // 如果编辑的是当前选中的类型，刷新右侧
    if (typeFormIsEdit.value && selectedType.value?.id === typeFormId.value) {
      selectedType.value = typeList.value.find((t) => t.id === typeFormId.value) || selectedType.value
    }
  } catch { /* 错误已由拦截器处理 */ }
}

/** 删除字典类型 */
async function handleDeleteType(row: DictType) {
  try {
    await ElMessageBox.confirm(t('dict.confirmDeleteType'), t('common.confirm'), {
      type: 'warning',
    })
    await deleteDictType(row.id)
    ElMessage.success(t('common.success'))
    loadTypes()
    if (selectedType.value?.id === row.id) {
      selectedType.value = null
      dataList.value = []
      dataTotal.value = 0
    }
  } catch { /* 用户取消或错误已处理 */ }
}

/** 切换类型状态 */
async function handleToggleTypeStatus(row: DictType) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await toggleDictTypeStatus(row.id, newStatus)
    row.status = newStatus
    ElMessage.success(t('common.success'))
  } catch { /* 错误已处理 */ }
}

// ===== 字典数据 CRUD =====

/** 打开新建数据对话框 */
function openCreateDataDialog() {
  if (!selectedType.value) {
    ElMessage.warning(t('dict.selectTypeHint'))
    return
  }
  dataFormIsEdit.value = false
  dataFormId.value = null
  dataForm.typeCode = selectedType.value.typeCode
  dataForm.dictValue = ''
  dataForm.dictLabel = ''
  dataForm.tagType = ''
  dataForm.remark = ''
  dataForm.sort = 0
  dataFormVisible.value = true
}

/** 打开编辑数据对话框 */
function openEditDataDialog(row: DictData) {
  dataFormIsEdit.value = true
  dataFormId.value = row.id
  dataForm.typeCode = row.typeCode
  dataForm.dictValue = row.dictValue
  dataForm.dictLabel = row.dictLabel
  dataForm.tagType = row.tagType || ''
  dataForm.remark = row.remark || ''
  dataForm.sort = row.sort
  dataFormVisible.value = true
}

/** 提交数据表单 */
async function submitDataForm() {
  try {
    if (dataFormIsEdit.value && dataFormId.value !== null) {
      await updateDictData(dataFormId.value, {
        dictValue: dataForm.dictValue,
        dictLabel: dataForm.dictLabel,
        tagType: dataForm.tagType || undefined,
        remark: dataForm.remark,
        sort: dataForm.sort,
      })
    } else {
      await createDictData({
        typeCode: dataForm.typeCode,
        dictValue: dataForm.dictValue,
        dictLabel: dataForm.dictLabel,
        tagType: dataForm.tagType || undefined,
        remark: dataForm.remark || undefined,
        sort: dataForm.sort,
      })
    }
    ElMessage.success(t('common.success'))
    dataFormVisible.value = false
    loadDataList()
  } catch { /* 错误已处理 */ }
}

/** 删除字典数据 */
async function handleDeleteData(row: DictData) {
  try {
    await ElMessageBox.confirm(t('dict.confirmDeleteData'), t('common.confirm'), {
      type: 'warning',
    })
    await deleteDictData(row.id)
    ElMessage.success(t('common.success'))
    loadDataList()
  } catch { /* 用户取消或错误已处理 */ }
}

/** 刷新缓存 */
async function handleRefreshCache() {
  if (!selectedType.value) {
    ElMessage.warning(t('dict.selectTypeHint'))
    return
  }
  try {
    await refreshDictCache(selectedType.value.typeCode)
    ElMessage.success(t('dict.cacheRefreshed'))
  } catch { /* 错误已处理 */ }
}

onMounted(() => {
  loadTypes()
})
</script>

<template>
  <div class="dict-management">
    <el-row :gutter="16" class="dict-row">
      <!-- 左侧：字典类型列表 -->
      <el-col :span="10">
        <el-card shadow="never" class="type-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('dict.typeList') }}</span>
              <el-button
                v-permission="'dict:type:create'" type="primary" size="small"
                @click="openCreateTypeDialog">
                {{ t('dict.createType') }}
              </el-button>
            </div>
          </template>

          <!-- 搜索区 -->
          <el-form :inline="true" class="search-form" @submit.prevent="handleTypeSearch">
            <el-form-item>
              <el-input
                v-model="typeSearch.typeCode" :placeholder="t('dict.typeCode')"
                clearable size="small" style="width: 130px" />
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="typeSearch.typeName" :placeholder="t('dict.typeName')"
                clearable size="small" style="width: 130px" />
            </el-form-item>
            <el-form-item>
              <el-select
                v-model="typeSearch.status" :placeholder="t('common.status')"
                clearable size="small" style="width: 90px">
                <el-option :label="t('common.enabled')" :value="1" />
                <el-option :label="t('common.disabled')" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="small" @click="handleTypeSearch">{{ t('common.search') }}</el-button>
              <el-button size="small" @click="handleTypeReset">{{ t('common.reset') }}</el-button>
            </el-form-item>
          </el-form>

          <!-- 类型表格 -->
          <el-table
            v-loading="typeLoading" :data="typeList" highlight-current-row
            size="small" stripe @current-change="handleSelectType">
            <el-table-column prop="typeCode" :label="t('dict.typeCode')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="typeName" :label="t('dict.typeName')" min-width="100" show-overflow-tooltip />
            <el-table-column prop="sort" :label="t('common.sort')" width="60" align="center" />
            <el-table-column prop="status" :label="t('common.status')" width="80" align="center">
              <template #default="{ row }">
                <el-switch
                  v-permission="'dict:type:update'"
                  :model-value="row.status === 1"
                  :loading="row._statusLoading"
                  @change="handleToggleTypeStatus(row)"
                />
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="120" align="center">
              <template #default="{ row }">
                <el-button
                  v-permission="'dict:type:update'" link type="primary" size="small"
                  @click.stop="openEditTypeDialog(row)">
                  {{ t('common.edit') }}
                </el-button>
                <el-button
                  v-permission="'dict:type:delete'" link type="danger" size="small"
                  @click.stop="handleDeleteType(row)">
                  {{ t('common.delete') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-if="typeTotal > 0"
            class="type-pagination"
            :current-page="typePage"
            :page-size="typeSize"
            :total="typeTotal"
            layout="total, prev, pager, next"
            small
            @current-change="(p: number) => { typePage = p; loadTypes() }"
          />
        </el-card>
      </el-col>

      <!-- 右侧：字典数据列表 -->
      <el-col :span="14">
        <el-card shadow="never" class="data-card">
          <template #header>
            <div class="card-header">
              <span>
                {{ t('dict.dataList') }}
                <template v-if="selectedType">
                  — <strong>{{ selectedType.typeCode }}</strong> ({{ selectedType.typeName }})
                </template>
              </span>
              <div v-if="selectedType" class="data-actions">
                <el-button
                  v-permission="'dict:data:create'" type="primary" size="small"
                  @click="openCreateDataDialog">
                  {{ t('dict.createData') }}
                </el-button>
                <el-button
                  v-permission="'dict:data:refresh'" size="small"
                  @click="handleRefreshCache">
                  {{ t('dict.refreshCache') }}
                </el-button>
              </div>
            </div>
          </template>

          <!-- 空状态 -->
          <el-empty v-if="!selectedType" :description="t('dict.selectTypeHint')" />

          <!-- 数据表格 -->
          <template v-else>
            <el-table v-loading="dataLoading" :data="dataList" size="small" stripe>
              <el-table-column prop="dictValue" :label="t('dict.dictValue')" min-width="100" show-overflow-tooltip />
              <el-table-column prop="dictLabel" :label="t('dict.dictLabel')" min-width="100" show-overflow-tooltip />
              <el-table-column prop="tagType" :label="t('dict.tagType')" width="100" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.tagType" :type="row.tagType" size="small">
                    {{ row.tagType }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="sort" :label="t('common.sort')" width="60" align="center" />
              <el-table-column prop="status" :label="t('common.status')" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                    {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('common.actions')" width="120" align="center">
                <template #default="{ row }">
                  <el-button
                    v-permission="'dict:data:update'" link type="primary" size="small"
                    @click="openEditDataDialog(row)">
                    {{ t('common.edit') }}
                  </el-button>
                  <el-button
                    v-permission="'dict:data:delete'" link type="danger" size="small"
                    @click="handleDeleteData(row)">
                    {{ t('common.delete') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-pagination
              v-if="dataTotal > 0"
              class="data-pagination"
              :current-page="dataPage"
              :page-size="dataSize"
              :total="dataTotal"
              layout="total, prev, pager, next"
              small
              @current-change="(p: number) => { dataPage = p; loadDataList() }"
            />
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 字典类型表单对话框 -->
    <el-dialog
      v-model="typeFormVisible" :title="typeFormIsEdit ? t('dict.editType') : t('dict.createType')"
      width="500" destroy-on-close>
      <el-form :model="typeForm" label-width="100">
        <el-form-item :label="t('dict.typeCode')">
          <el-input v-model="typeForm.typeCode" :disabled="typeFormIsEdit" />
        </el-form-item>
        <el-form-item :label="t('dict.typeName')">
          <el-input v-model="typeForm.typeName" />
        </el-form-item>
        <el-form-item :label="t('dict.remark')">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('common.sort')">
          <el-input-number v-model="typeForm.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeFormVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitTypeForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据表单对话框 -->
    <el-dialog
      v-model="dataFormVisible" :title="dataFormIsEdit ? t('dict.editData') : t('dict.createData')"
      width="500" destroy-on-close>
      <el-form :model="dataForm" label-width="100">
        <el-form-item :label="t('dict.typeCode')">
          <el-input v-model="dataForm.typeCode" disabled />
        </el-form-item>
        <el-form-item :label="t('dict.dictValue')">
          <el-input v-model="dataForm.dictValue" />
        </el-form-item>
        <el-form-item :label="t('dict.dictLabel')">
          <el-input v-model="dataForm.dictLabel" />
        </el-form-item>
        <el-form-item :label="t('dict.tagType')">
          <el-select v-model="dataForm.tagType" style="width: 100%">
            <el-option
              v-for="opt in tagTypeOptions" :key="opt.value"
              :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('dict.remark')">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('common.sort')">
          <el-input-number v-model="dataForm.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataFormVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitDataForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dict-management {
  padding: 0;
}

.dict-row {
  min-height: calc(100vh - 160px);
}

.type-card,
.data-card {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.data-actions {
  display: flex;
  gap: 8px;
}

.search-form {
  margin-bottom: 12px;
}

.type-pagination,
.data-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
