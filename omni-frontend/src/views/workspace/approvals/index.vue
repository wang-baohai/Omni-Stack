<script setup lang="ts">
/**
 * 工作台审批子页。
 * 归属 workflow 模块：待我审批 / 我发起的 / 我已办的三个标签页 + 审批弹窗与提交。
 * 业务审批详情（采购请购 / 资产调拨 / 资产处置）按模块归属隔离，经 import.meta.glob 动态装载，
 * 核心页不静态导入 procurement/asset，以便被裁剪预设移除对应模块时不残留缺失模块的导入入口。
 */
import { ref, reactive, computed, shallowRef, defineAsyncComponent, onMounted, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElNotification } from 'element-plus'
import { useDictOptions } from '@/composables/useDictOptions'
import {
  listTodoTasks, listMyInitiated, listMyCompleted,
  completeApproval, getTaskFormData, getWorkspaceStats,
  type TodoTask, type ProcessInstanceExt, type WorkspaceStats,
} from '@/api/workflow'

const { t } = useI18n()
const props = defineProps<{ embedded?: boolean }>()
const emit = defineEmits<{ changed: [] }>()

// ─── 流程分类字典映射 ───
const { options: categoryOptions } = useDictOptions('workflow_category')
function categoryLabel(value: string | null) {
  if (!value) return '-'
  return categoryOptions.value.find(o => o.value === value)?.label || value
}

// ─── 可选业务审批视图：按模块归属动态解析 ───
const businessViewModules = import.meta.glob('/src/views/**/ApprovalView.vue')
const businessViewPaths: Record<string, string> = {
  PROCUREMENT_REQUISITION: '/src/views/procurement/requisition/ApprovalView.vue',
  ASSET_TRANSFER: '/src/views/asset/transfer/ApprovalView.vue',
  ASSET_DISPOSAL: '/src/views/asset/disposal/ApprovalView.vue',
}

// ─── 工作流标签页状态 ───

const activeTab = ref('todo')
const wfStats = ref<WorkspaceStats | null>(null)

// 待我审批
const todoList = ref<TodoTask[]>([])
const todoTotal = ref(0)
const todoPage = ref(1)
const todoPageSize = ref(10)
const todoLoading = ref(false)

// 我发起的
const initiatedList = ref<ProcessInstanceExt[]>([])
const initiatedTotal = ref(0)
const initiatedPage = ref(1)
const initiatedPageSize = ref(10)
const initiatedLoading = ref(false)
const initiatedStatus = ref<number | undefined>(undefined)

// 我已办的
const completedList = ref<ProcessInstanceExt[]>([])
const completedTotal = ref(0)
const completedPage = ref(1)
const completedPageSize = ref(10)
const completedLoading = ref(false)

// 审批对话框
const approvalVisible = ref(false)
const approvalTask = ref<TodoTask | null>(null)
const approvalForm = reactive({ approved: true, comment: '' })
const businessFormLoading = ref(false)
const businessFormError = ref('')
/** 当前任务解析出的业务审批详情组件（模块归属，动态装载）。 */
const businessView = shallowRef<Component | null>(null)
const businessViewLoaded = ref(false)
const businessTaskId = ref('')
const businessVariables = ref<Record<string, unknown>>({})
const hasBusinessApprovalView = computed(() => businessViewLoaded.value)
const approvalCanSubmit = computed(() => Boolean(
  approvalTask.value && !businessFormLoading.value && !businessFormError.value,
))

async function loadWfStats() {
  if (props.embedded) { emit('changed'); return }
  try {
    const res = await getWorkspaceStats()
    wfStats.value = res.data.data
  } catch { /* ignore */ }
}

async function loadTodoList() {
  todoLoading.value = true
  try {
    const res = await listTodoTasks({ page: todoPage.value, size: 10 })
    todoList.value = res.data.data.records
    todoTotal.value = res.data.data.total
  } finally { todoLoading.value = false }
}

async function loadInitiatedList() {
  initiatedLoading.value = true
  try {
    const res = await listMyInitiated({ status: initiatedStatus.value, page: initiatedPage.value, size: 10 })
    initiatedList.value = res.data.data.records
    initiatedTotal.value = res.data.data.total
  } finally { initiatedLoading.value = false }
}

async function loadCompletedList() {
  completedLoading.value = true
  try {
    const res = await listMyCompleted({ page: completedPage.value, size: 10 })
    completedList.value = res.data.data.records
    completedTotal.value = res.data.data.total
  } finally { completedLoading.value = false }
}

async function openApproval(task: TodoTask) {
  approvalTask.value = task
  approvalForm.approved = true
  approvalForm.comment = ''
  businessFormError.value = ''
  businessView.value = null
  businessViewLoaded.value = false
  businessTaskId.value = ''
  businessVariables.value = {}
  approvalVisible.value = true
  businessFormLoading.value = true
  try {
    const formResponse = await getTaskFormData(task.taskId)
    const variables = (formResponse.data.data.variables ?? {}) as Record<string, unknown>
    const businessType = typeof variables.businessType === 'string' ? variables.businessType : ''
    const viewPath = businessViewPaths[businessType]
    if (viewPath) {
      const loader = businessViewModules[viewPath]
      if (!loader) {
        // 业务类型已知但对应模块未安装：如实报错并保持提交禁用，不吞掉缺失分支
        throw new Error(t('workspaceApproval.formLoadFailed'))
      }
      businessTaskId.value = task.taskId
      businessVariables.value = variables
      businessView.value = defineAsyncComponent(
        loader as () => Promise<{ default: Component }>,
      )
      // businessFormLoading 保持为 true，直至子组件 emit loaded/failed 更新提交门
      return
    }
    // 无业务模块详情：仅通用审批表单
    businessFormLoading.value = false
  } catch (error) {
    businessFormError.value = error instanceof Error ? error.message : t('workspaceApproval.formLoadFailed')
    businessFormLoading.value = false
  }
}

/** 业务详情加载成功：允许提交并按有业务视图渲染宽度。 */
function onBusinessLoaded() {
  businessViewLoaded.value = true
  businessFormError.value = ''
  businessFormLoading.value = false
}

/** 业务详情加载失败：如实回写错误并禁用提交，不新增未支持行为。 */
function onBusinessFailed(message: string) {
  businessViewLoaded.value = false
  businessFormError.value = message || t('workspaceApproval.formLoadFailed')
  businessFormLoading.value = false
}

async function submitApproval() {
  if (!approvalTask.value || !approvalCanSubmit.value) {
    ElMessage.warning(t('workspaceApproval.formNotValidated'))
    return
  }
  await completeApproval(approvalTask.value.taskId, {
    approved: approvalForm.approved,
    comment: approvalForm.comment,
  })
  if (hasBusinessApprovalView.value) {
    ElNotification.success({
      title: t('workspaceApproval.processed'),
      message: t('workspaceApproval.syncing'),
      duration: 5000,
    })
  } else {
    ElMessage.success(t('common.success'))
  }
  approvalVisible.value = false
  loadTodoList()
  loadWfStats()
}

function handleTabChange(tab: string) {
  if (tab === 'todo') { loadTodoList(); loadWfStats() }
  else if (tab === 'initiated') loadInitiatedList()
  else if (tab === 'completed') loadCompletedList()
}

/** 状态标签映射 */
function instanceStatusLabel(status: number): string {
  if (status === 1) return t('workflow.pending')
  if (status === 2) return t('workflow.completed')
  return t('workflow.terminated')
}

function instanceStatusType(status: number): string {
  if (status === 1) return 'warning'
  if (status === 2) return 'success'
  return 'danger'
}

onMounted(async () => {
  await Promise.all([loadWfStats(), loadTodoList()])
})
</script>

<template>
  <div class="approvals-page" :class="{ 'approvals-page--embedded': props.embedded }">
    <!-- 工作台标签页 -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- Tab 1: 待我审批 -->
      <el-tab-pane :label="props.embedded ? t('workflow.todo') : `${t('workflow.todo')} (${wfStats?.todoCount ?? '—'})`" name="todo">
        <el-table v-loading="todoLoading" :data="todoList" stripe border style="margin-top: 12px">
          <el-table-column prop="title" :label="t('workflow.title')" min-width="180" />
          <el-table-column prop="taskName" :label="t('workflow.processName')" width="150" />
          <el-table-column :label="t('workflow.category')" width="100">
            <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column prop="createTime" :label="t('workflow.startTime')" width="170" />
          <el-table-column :label="t('common.actions')" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openApproval(row)">
                {{ t('workflow.approve') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!todoLoading && todoList.length === 0" :description="t('workflow.noTodo')" />
        <el-pagination
          v-model:current-page="todoPage" v-model:page-size="todoPageSize" class="pagination"
          :page-sizes="[5, 10, 20, 50, 100]" :total="todoTotal"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTodoList"
          @size-change="todoPage = 1; loadTodoList()" />
      </el-tab-pane>

      <!-- Tab 2: 我发起的 -->
      <el-tab-pane :label="t('workflow.myInitiated')" name="initiated">
        <div style="margin: 12px 0">
          <el-select v-model="initiatedStatus" clearable :placeholder="t('common.status')" style="width: 140px" @change="loadInitiatedList">
            <el-option :label="t('workflow.pending')" :value="1" />
            <el-option :label="t('workflow.completed')" :value="2" />
            <el-option :label="t('workflow.terminated')" :value="0" />
          </el-select>
        </div>
        <el-table v-loading="initiatedLoading" :data="initiatedList" stripe border>
          <el-table-column prop="title" :label="t('workflow.title')" min-width="180" />
          <el-table-column prop="processKey" :label="t('workflow.processKey')" width="150" />
          <el-table-column :label="t('workflow.category')" width="100">
            <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="instanceStatusType(row.status)" size="small">
                {{ instanceStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" :label="t('workflow.startTime')" width="170" />
        </el-table>
        <el-empty v-if="!initiatedLoading && initiatedList.length === 0" :description="t('workflow.noInitiated')" />
        <el-pagination
          v-model:current-page="initiatedPage" v-model:page-size="initiatedPageSize" class="pagination"
          :page-sizes="[5, 10, 20, 50, 100]" :total="initiatedTotal"
          layout="total, sizes, prev, pager, next"
          @current-change="loadInitiatedList"
          @size-change="initiatedPage = 1; loadInitiatedList()" />
      </el-tab-pane>

      <!-- Tab 3: 我已办的 -->
      <el-tab-pane :label="t('workflow.myCompleted')" name="completed">
        <el-table v-loading="completedLoading" :data="completedList" stripe border style="margin-top: 12px">
          <el-table-column prop="title" :label="t('workflow.title')" min-width="180" />
          <el-table-column prop="processKey" :label="t('workflow.processKey')" width="150" />
          <el-table-column :label="t('workflow.category')" width="100">
            <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="instanceStatusType(row.status)" size="small">
                {{ instanceStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" :label="t('workflow.startTime')" width="170" />
        </el-table>
        <el-empty v-if="!completedLoading && completedList.length === 0" :description="t('workflow.noCompleted')" />
        <el-pagination
          v-model:current-page="completedPage" v-model:page-size="completedPageSize" class="pagination"
          :page-sizes="[5, 10, 20, 50, 100]" :total="completedTotal"
          layout="total, sizes, prev, pager, next"
          @current-change="loadCompletedList"
          @size-change="completedPage = 1; loadCompletedList()" />
      </el-tab-pane>
    </el-tabs>

    <!-- 审批对话框 -->
    <el-dialog
      v-model="approvalVisible"
      :title="approvalTask?.taskName || t('workflow.approve')"
      :width="hasBusinessApprovalView ? '860px' : '500px'"
    >
      <div v-loading="businessFormLoading">
        <el-alert
          v-if="businessFormError"
          type="error"
          :closable="false"
          show-icon
          :title="t('workspaceApproval.validationFailed', { error: businessFormError })"
        />
        <component
          :is="businessView"
          v-if="businessView"
          :task-id="businessTaskId"
          :variables="businessVariables"
          @loaded="onBusinessLoaded"
          @failed="onBusinessFailed"
        />
      </div>
      <el-form :model="approvalForm" label-width="100">
        <el-form-item :label="t('workflow.title')">
          <span>{{ approvalTask?.title }}</span>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-radio-group v-model="approvalForm.approved">
            <el-radio :value="true">{{ t('workflow.approve') }}</el-radio>
            <el-radio :value="false">{{ t('workflow.reject') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('workflow.comment')">
          <el-input v-model="approvalForm.comment" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approvalVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!approvalCanSubmit" @click="submitApproval">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.approvals-page {
  padding: var(--omni-space-xl);
  max-width: 1440px;
  margin: 0 auto;
  width: 100%;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.approvals-page--embedded { padding: 0; max-width: none; }
</style>
