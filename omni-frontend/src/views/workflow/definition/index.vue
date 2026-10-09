<script setup lang="ts">
/**
 * 流程定义管理页面。
 * 展示已部署到 Flowable 引擎的流程定义列表，支持挂起/激活/删除操作。
 * 新建流程请前往「流程模型」页面，通过草稿→校验→发布流程完成。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDictOptions } from '@/composables/useDictOptions'
import {
  listProcessDefinitions,
  suspendProcessDefinition,
  activateProcessDefinition,
  deleteDeployment,
  startProcess,
  type ProcessDefinition,
} from '@/api/workflow'
import { listUsers, type SysUser } from '@/api/user'

const { t } = useI18n()

// ===== 字典选项 =====
const { options: categoryOptions } = useDictOptions('workflow_category')

/** 根据字典值获取分类标签 */
function categoryLabel(value: string | null) {
  if (!value) return ''
  return categoryOptions.value.find(o => o.value === value)?.label || value
}

// ===== 列表 =====
const list = ref<ProcessDefinition[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const search = reactive({ name: '', category: '' })

async function loadList() {
  loading.value = true
  try {
    const res = await listProcessDefinitions({
      name: search.name || undefined,
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

function handleReset() {
  search.name = ''
  search.category = ''
  handleSearch()
}

// ===== 操作 =====
async function handleSuspend(row: ProcessDefinition) {
  await ElMessageBox.confirm(t('workflow.suspendConfirmMessage'), t('common.notice'), {
    type: 'warning',
  })
  await suspendProcessDefinition(row.id)
  ElMessage.success(t('common.success'))
  loadList()
}

async function handleActivate(row: ProcessDefinition) {
  await activateProcessDefinition(row.id)
  ElMessage.success(t('common.success'))
  loadList()
}

async function handleDelete(row: ProcessDefinition) {
  await ElMessageBox.confirm(t('workflow.deleteDeploymentConfirmMessage'), t('common.warning'), {
    type: 'warning',
    confirmButtonClass: 'el-button--danger',
  })
  await deleteDeployment(row.deploymentId)
  ElMessage.success(t('common.success'))
  loadList()
}

onMounted(() => loadList())

// ===== 模拟发起流程 =====
const startDialogVisible = ref(false)
const startForm = reactive({ key: '', title: '', simulateUserId: undefined as number | undefined })
const startLoading = ref(false)
const userList = ref<SysUser[]>([])
const usersLoaded = ref(false)

async function loadUsers() {
  if (usersLoaded.value) return
  try {
    const res = await listUsers(1, 200)
    userList.value = res.data.data.records.filter(u => u.status === 1)
    usersLoaded.value = true
  } catch {
    // 加载失败不阻断弹窗
  }
}

function openStartDialog(row: ProcessDefinition) {
  startForm.key = row.key
  startForm.title = row.name
  startForm.simulateUserId = undefined
  startDialogVisible.value = true
  loadUsers()
}

/** 用户下拉变更，同步 ID */
function onUserChange(id: number) {
  startForm.simulateUserId = id
}

async function handleStart() {
  if (!startForm.title.trim()) {
    ElMessage.warning(t('workflow.titleRequired'))
    return
  }
  if (!startForm.simulateUserId) {
    ElMessage.warning(t('workflow.simulatedInitiatorRequired'))
    return
  }
  startLoading.value = true
  try {
    const selectedUser = userList.value.find(u => u.id === startForm.simulateUserId)
    await startProcess({
      processKey: startForm.key,
      title: startForm.title,
      simulateUserId: startForm.simulateUserId,
      simulateUserName: selectedUser?.nickname || selectedUser?.username,
    })
    ElMessage.success(t('workflow.simulatedStartSuccess'))
    startDialogVisible.value = false
  } finally {
    startLoading.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-form :inline="true" @submit.prevent="handleSearch">
      <el-form-item :label="t('workflow.processName')">
        <el-input v-model="search.name" :placeholder="t('workflow.processName')" clearable />
      </el-form-item>
      <el-form-item :label="t('workflow.category')">
        <el-select
          v-model="search.category"
          :placeholder="t('workflow.category')"
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
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-text type="info" size="small">
        {{ t('workflow.createDefinitionHint') }}
      </el-text>
    </div>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="name" :label="t('workflow.processName')" min-width="180" />
      <el-table-column prop="key" label="Key" width="150" />
      <el-table-column :label="t('workflow.category')" width="120">
        <template #default="{ row }">
          {{ categoryLabel(row.category) }}
        </template>
      </el-table-column>
      <el-table-column prop="version" :label="t('workflow.version')" width="80" align="center" />
      <el-table-column :label="t('common.status')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.suspended ? 'danger' : 'success'" size="small">
            {{ row.suspended ? t('workflow.suspended') : t('workflow.active') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.suspended"
            v-permission="'workflow:instance:start'"
            size="small"
            type="primary"
            @click="openStartDialog(row)"
          >
            {{ t('workflow.simulatedStart') }}
          </el-button>
          <el-button
            v-if="!row.suspended"
            v-permission="'workflow:definition:update'"
            size="small"
            type="warning"
            @click="handleSuspend(row)"
          >
            {{ t('workflow.suspend') }}
          </el-button>
          <el-button
            v-else
            v-permission="'workflow:definition:update'"
            size="small"
            type="success"
            @click="handleActivate(row)"
          >
            {{ t('workflow.activate') }}
          </el-button>
          <el-button v-permission="'workflow:definition:delete'" size="small" type="danger" @click="handleDelete(row)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[5, 10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>
    <!-- 模拟发起流程弹窗 -->
    <el-dialog v-model="startDialogVisible" :title="t('workflow.simulatedStartTitle')" width="420px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item :label="t('workflow.processKey')">
          <el-input :model-value="startForm.key" disabled />
        </el-form-item>
        <el-form-item :label="t('workflow.title')">
          <el-input v-model="startForm.title" :placeholder="t('workflow.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('workflow.simulatedInitiator')">
          <el-select
            v-model="startForm.simulateUserId"
            :placeholder="t('workflow.simulatedInitiatorPlaceholder')"
            filterable
            style="width: 100%"
            @change="onUserChange"
          >
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="`${user.nickname}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="startLoading" @click="handleStart">{{ t('workflow.confirmStart') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}
.toolbar {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
