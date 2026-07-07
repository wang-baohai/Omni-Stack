<script setup lang="ts">
/**
 * 流程模型管理页面。
 * 支持模型的创建、草稿编辑、校验、发布、版本管理。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDictOptions } from '@/composables/useDictOptions'
import {
  listModels,
  deleteModel,
  validateModel,
  publishModel,
  type ProcessModel,
} from '@/api/workflow-model'
import CreateModelDialog from './CreateModelDialog.vue'
import VersionHistoryDialog from './VersionHistoryDialog.vue'
import ValidateResultDialog from './ValidateResultDialog.vue'
import ModelDesigner from '@/components/workflow/ModelDesigner.vue'

const { t } = useI18n()

// ===== 字典选项 =====
const { options: categoryOptions } = useDictOptions('workflow_category')

/** 根据字典值获取分类标签 */
function categoryLabel(value: string | null) {
  if (!value) return ''
  return categoryOptions.value.find(o => o.value === value)?.label || value
}

// ===== 列表 =====
const list = ref<ProcessModel[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const search = reactive({ keyword: '', category: '' })

async function loadList() {
  loading.value = true
  try {
    const res = await listModels({
      keyword: search.keyword || undefined,
      category: search.category || undefined,
      page: page.value,
      size: size.value,
    })
    const data = res.data.data
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handlePageChange(p: number) {
  page.value = p
  loadList()
}

// ===== 创建模型 =====
const createDialogVisible = ref(false)

function handleCreate() {
  createDialogVisible.value = true
}

function handleCreateSuccess() {
  createDialogVisible.value = false
  loadList()
}

// ===== 设计器 =====
const designerVisible = ref(false)
const designerModelId = ref<number>(0)

function handleDesign(model: ProcessModel) {
  designerModelId.value = model.id
  designerVisible.value = true
}

function handleDesignerSaved() {
  loadList()
}

// ===== 校验 =====
async function handleValidate(model: ProcessModel) {
  try {
    const res = await validateModel(model.id)
    validateResult.value = res.data.data
    validateDialogVisible.value = true
  } catch {
    ElMessage.error('校验请求失败')
  }
}

const validateResult = ref<any>(null)
const validateDialogVisible = ref(false)

// ===== 发布 =====
async function handlePublish(model: ProcessModel) {
  try {
    await ElMessageBox.confirm(
      `确认发布模型「${model.modelName}」？发布后将生成新版本并部署到流程引擎。`,
      '发布确认',
      { type: 'warning' },
    )
    const res = await publishModel(model.id)
    ElMessage.success(`发布成功！业务版本: v${res.data.data.businessVersion}`)
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '发布失败')
    }
  }
}

// ===== 版本历史 =====
const versionDialogVisible = ref(false)
const versionModelId = ref<number>(0)

function handleVersions(model: ProcessModel) {
  versionModelId.value = model.id
  versionDialogVisible.value = true
}

// ===== 删除 =====
async function handleDelete(model: ProcessModel) {
  try {
    await ElMessageBox.confirm(
      `确认删除模型「${model.modelName}」？删除后不可恢复。`,
      '删除确认',
      { type: 'warning' },
    )
    await deleteModel(model.id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

// ===== 状态标签 =====
function statusLabel(status: number) {
  return status === 1 ? '正常' : '已归档'
}

function statusType(status: number): string {
  return status === 1 ? 'success' : 'info'
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="model-list-container">
    <!-- 搜索栏 -->
    <el-form :inline="true" @submit.prevent="handleSearch">
      <el-form-item>
        <el-input
          v-model="search.keyword"
          placeholder="模型名称 / 标识"
          clearable
          @clear="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="search.category"
          placeholder="流程分类"
          clearable
          style="width: 160px"
          @clear="handleSearch"
          @change="handleSearch"
        >
          <el-option
            v-for="opt in categoryOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">
          {{ t('common.search') }}
        </el-button>
        <el-button @click="search.keyword = ''; search.category = ''; handleSearch()">
          {{ t('common.reset') }}
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 操作栏 -->
    <div class="toolbar">
      <el-button type="primary" v-permission="'workflow:model:create'" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        {{ t('common.create') }}
      </el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="list" v-loading="loading" stripe border>
      <el-table-column prop="modelName" label="模型名称" min-width="180" />
      <el-table-column prop="modelKey" label="模型标识" width="200" />
      <el-table-column label="分类" width="120">
        <template #default="{ row }">
          {{ categoryLabel(row.category) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateBy" label="更新人" width="120" />
      <el-table-column prop="updateTime" label="更新时间" width="180" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleDesign(row)">
            设计
          </el-button>
          <el-button
            link type="primary" size="small"
            v-permission="'workflow:model:validate'"
            @click="handleValidate(row)"
          >
            校验
          </el-button>
          <el-button
            link type="warning" size="small"
            v-permission="'workflow:model:publish'"
            @click="handlePublish(row)"
          >
            发布
          </el-button>
          <el-button link type="primary" size="small" @click="handleVersions(row)">
            版本
          </el-button>
          <el-button
            link type="danger" size="small"
            v-permission="'workflow:model:delete'"
            @click="handleDelete(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      class="pagination"
      @current-change="handlePageChange"
    />

    <!-- 对话框 -->
    <CreateModelDialog
      v-model:visible="createDialogVisible"
      @success="handleCreateSuccess"
    />
    <VersionHistoryDialog
      v-model:visible="versionDialogVisible"
      :model-id="versionModelId"
    />
    <ValidateResultDialog
      v-model:visible="validateDialogVisible"
      :result="validateResult"
    />
    <ModelDesigner
      v-model:visible="designerVisible"
      :model-id="designerModelId"
      @saved="handleDesignerSaved"
    />
  </div>
</template>

<style scoped>
.model-list-container {
  padding: 20px;
}
.toolbar {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
