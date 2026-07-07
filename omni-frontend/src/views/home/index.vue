<script setup lang="ts">
/**
 * 首页组件。
 * 未登录：展示产品介绍 Hero 落地页。
 * 已登录：展示用户工作台（统计卡片 + 任务列表 + 创建/编辑弹窗 + 执行日志弹窗）。
 */
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { usePermissionStore } from '@/stores/permission'
import { useDictOptions } from '@/composables/useDictOptions'
import { storeLang } from '@/i18n'
import {
  listMyJobs, createMyJob, updateMyJob, deleteMyJob,
  toggleMyJobStatus, triggerMyJob, getMyJobTypes,
  getMyJobStats, getMyJobLogs,
  type MyJobStats,
} from '@/api/myJob'
import type { UserJob, CreateUserJobRequest, UserJobLog } from '@/api/myJob'
import type { UserJobType } from '@/api/userJobType'
import type { PageResult } from '@/types/api'
import CronGenerator from '@/components/CronGenerator.vue'
import DynamicFormRenderer from '@/components/DynamicFormRenderer.vue'
import { CronExpressionParser } from 'cron-parser'
import {
  listTodoTasks, listMyInitiated, listMyCompleted,
  completeApproval, getWorkspaceStats,
  type TodoTask, type ProcessInstanceExt, type WorkspaceStats,
} from '@/api/workflow'

const { t, locale } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const permissionStore = usePermissionStore()

// ─── 流程分类字典映射 ───
const { options: categoryOptions } = useDictOptions('workflow_category')
function categoryLabel(value: string | null) {
  if (!value) return '-'
  return categoryOptions.value.find(o => o.value === value)?.label || value
}

// ─── 通用状态 ───

/** 管理员权限：是否显示"控制台"按钮 */
const isAdmin = computed(() => permissionStore.hasPermission('job:user-job-type:list'))

// ─── 工作台状态 ───

const loading = ref(false)
const tableData = ref<UserJob[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchJobName = ref('')
const searchJobType = ref('')
const searchStatus = ref<number | undefined>(undefined)

/** 统计数据 */
const stats = ref<MyJobStats>({ totalJobs: 0, todayExecuted: 0, todayFailed: 0 })

/** 任务类型下拉 */
const enabledTypes = ref<UserJobType[]>([])

/** 创建/编辑对话框 */
const formDialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<{
  jobName: string
  jobType: string
  cronExpression: string
  jobParams: Record<string, any>
}>({
  jobName: '',
  jobType: '',
  cronExpression: '0 * * * * ?',
  jobParams: {},
})

/** 当前选中类型的 paramTemplate schema */
const currentSchema = computed<Record<string, any> | null>(() => {
  const selected = enabledTypes.value.find(tp => tp.typeCode === form.jobType)
  if (!selected?.paramTemplate) return null
  try {
    return JSON.parse(selected.paramTemplate)
  } catch {
    return null
  }
})

/** 执行日志弹窗 */
const logDialogVisible = ref(false)
const logJobName = ref('')
const logData = ref<UserJobLog[]>([])
const logTotal = ref(0)
const logPage = ref(1)
const logPageSize = ref(10)
const logLoading = ref(false)
const currentLogJobId = ref<number | null>(null)

// ─── 导航函数 ───

/** 跳转到登录页 */
function goToLogin() {
  router.push({ name: 'Login' })
}

/** 跳转到控制台 */
function goToConsole() {
  router.push({ name: 'Dashboard' })
}

/** 处理用户登出 */
function handleLogout() {
  userStore.logout()
}

/** 切换主题模式 */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/** 切换语言 */
function toggleLang() {
  const newLang = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = newLang
  storeLang(newLang)
}

// ─── 工作台函数 ───

/** 加载任务列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await listMyJobs({
      jobName: searchJobName.value || undefined,
      jobType: searchJobType.value || undefined,
      status: searchStatus.value,
      page: currentPage.value,
      size: pageSize.value,
    })
    const data = res.data.data as PageResult<UserJob>
    tableData.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 加载统计数据 */
async function loadStats() {
  try {
    const res = await getMyJobStats()
    stats.value = res.data.data
  } catch {
    /* 统计加载失败不阻塞页面 */
  }
}

/** 加载任务类型下拉 */
async function loadEnabledTypes() {
  try {
    const res = await getMyJobTypes()
    enabledTypes.value = res.data.data
  } catch {
    /* 类型加载失败不阻塞页面 */
  }
}

/** 加载权限菜单（用于判断管理员权限） */
async function loadPermissions() {
  if (!permissionStore.menusLoaded) {
    try {
      permissionStore.initFromToken()
      await permissionStore.loadMenus()
    } catch {
      /* 权限加载失败不阻塞页面 */
    }
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  searchJobName.value = ''
  searchJobType.value = ''
  searchStatus.value = undefined
  handleSearch()
}

function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, { jobName: '', jobType: '', cronExpression: '0 * * * * ?', jobParams: {} })
  formDialogVisible.value = true
}

async function openEditDialog(row: UserJob) {
  isEdit.value = true
  editingId.value = row.id
  let params: Record<string, any> = {}
  try {
    params = row.jobParams ? JSON.parse(row.jobParams) : {}
  } catch { /* ignore */ }
  Object.assign(form, {
    jobName: row.jobName,
    jobType: row.jobType,
    cronExpression: row.cronExpression,
    jobParams: params,
  })
  if (enabledTypes.value.length === 0) await loadEnabledTypes()
  formDialogVisible.value = true
}

async function saveForm() {
  const jobParamsStr = Object.keys(form.jobParams).length > 0
    ? JSON.stringify(form.jobParams) : undefined

  if (isEdit.value && editingId.value) {
    await updateMyJob(editingId.value, {
      jobName: form.jobName,
      cronExpression: form.cronExpression,
      jobParams: jobParamsStr,
    })
  } else {
    await createMyJob({
      jobName: form.jobName,
      jobType: form.jobType,
      cronExpression: form.cronExpression,
      jobParams: jobParamsStr,
    } as CreateUserJobRequest)
  }
  ElMessage.success(t('common.success'))
  formDialogVisible.value = false
  loadData()
  loadStats()
}

async function handleDelete(row: UserJob) {
  try {
    await ElMessageBox.confirm(t('workspace.confirmDelete'), { type: 'warning' })
    await deleteMyJob(row.id)
    ElMessage.success(t('common.success'))
    loadData()
    loadStats()
  } catch { /* cancelled */ }
}

async function handleToggleStatus(row: UserJob) {
  const newStatus = row.status === 1 ? 0 : 1
  await toggleMyJobStatus(row.id, newStatus)
  loadData()
}

async function handleTrigger(row: UserJob) {
  try {
    await ElMessageBox.confirm(t('workspace.confirmTrigger'), { type: 'warning' })

    // 记录触发前的最新日志 ID
    let lastKnownId = 0
    try {
      const logRes = await getMyJobLogs(row.id, { page: 1, size: 1 })
      const logData = logRes.data.data as PageResult<UserJobLog>
      if (logData.records.length > 0) {
        lastKnownId = logData.records[0].id
      }
    } catch { /* 首次触发无日志 */ }

    await triggerMyJob(row.id)
    ElMessage.success(t('workspace.triggerSuccess'))

    // 启动轮询等待执行结果
    pollForNewLog(row.id, lastKnownId)
  } catch { /* cancelled */ }
}

/** 打开执行日志弹窗 */
async function openLogDialog(row: UserJob) {
  currentLogJobId.value = row.id
  logJobName.value = row.jobName
  logPage.value = 1
  logDialogVisible.value = true
  await loadLogs()
}

/** 加载执行日志 */
async function loadLogs() {
  if (!currentLogJobId.value) return
  logLoading.value = true
  try {
    const res = await getMyJobLogs(currentLogJobId.value, {
      page: logPage.value,
      size: logPageSize.value,
    })
    const data = res.data.data as PageResult<UserJobLog>
    logData.value = data.records
    logTotal.value = data.total
  } finally {
    logLoading.value = false
  }
}

function handleLogPageChange(page: number) {
  logPage.value = page
  loadLogs()
}

/** 切换任务类型时重置参数 */
function onJobTypeChange() {
  form.jobParams = {}
}

onMounted(async () => {
  if (userStore.isLoggedIn) {
    await loadPermissions()
    await Promise.all([loadEnabledTypes(), loadStats(), loadData()])
    // 加载工作流统计和待办
    loadWfStats()
    loadTodoList()
    // 检查最近 10 秒内的执行结果，弹出未读通知
    checkRecentLogs()
    // 启动周期性轮询检测 cron 自动触发的执行结果
    startGlobalPolling()
  }
})

onUnmounted(() => {
  for (const timer of pollTimers) {
    clearInterval(timer)
  }
  pollTimers.clear()
  if (globalPollTimer) {
    clearInterval(globalPollTimer)
    globalPollTimer = null
  }
  // 清除日志 ID 基线，确保下次进入时重新初始化
  lastLogIdMap.clear()
})

// ===== 执行结果通知 =====

/** 各任务的轮询定时器 */
const pollTimers = new Set<ReturnType<typeof setInterval>>()

/** 周期性轮询定时器：检测所有活跃任务的 cron 自动触发日志 */
let globalPollTimer: ReturnType<typeof setInterval> | null = null
/** 各任务已知的最新日志 ID（用于检测新日志） */
const lastLogIdMap = new Map<number, number>()

/**
 * 轮询检测新执行日志，发现后弹出通知。
 */
function pollForNewLog(jobId: number, lastKnownId: number) {
  let attempts = 0
  const maxAttempts = 15
  const timer = setInterval(async () => {
    attempts++
    try {
      const res = await getMyJobLogs(jobId, { page: 1, size: 1 })
      const data = res.data.data as PageResult<UserJobLog>
      if (data.records.length > 0 && data.records[0].id > lastKnownId) {
        clearInterval(timer)
        pollTimers.delete(timer)
        showLogNotification(data.records[0])
        loadData() // 刷新列表
        loadStats() // 刷新统计
        return
      }
    } catch { /* 网络异常，继续轮询 */ }
    if (attempts >= maxAttempts) {
      clearInterval(timer)
      pollTimers.delete(timer)
    }
  }, 2000)
  pollTimers.add(timer)
}

/**
 * 页面加载时检查最近 10 秒内的执行日志。
 */
async function checkRecentLogs() {
  try {
    // 并行查询所有任务的最近日志
    const results = await Promise.all(
      tableData.value.map(job =>
        getMyJobLogs(job.id, { page: 1, size: 1 }).catch(() => null),
      ),
    )
    for (const res of results) {
      if (!res) continue
      const data = res.data.data as PageResult<UserJobLog>
      if (data.records.length > 0) {
        const log = data.records[0]
        // 检查是否在最近 2 分钟内执行（覆盖页面切换耗时）
        if (log.fireTime && log.resultMessage) {
          const fireTime = new Date(log.fireTime).getTime()
          if (Date.now() - fireTime < 120000) {
            showLogNotification(log)
          }
        }
      }
    }
  } catch { /* 静默失败 */ }
}

/**
 * 根据执行日志弹出右上角通知。
 */
function showLogNotification(log: UserJobLog) {
  if (log.status === 1) {
    ElNotification({
      title: log.jobName || t('userJob.execSuccess'),
      message: log.resultMessage || t('userJob.execSuccess'),
      type: 'success',
      duration: 3000,
    })
  } else {
    ElNotification({
      title: log.jobName || t('userJob.execFail'),
      message: log.errorMessage || t('userJob.execFail'),
      type: 'error',
      duration: 3000,
    })
  }
}

/**
 * 启动周期性轮询（每 10 秒），检测所有活跃任务的新执行日志。
 * 用于捕获 cron 自动触发产生的执行结果并弹出通知。
 */
function startGlobalPolling() {
  if (globalPollTimer) return
  globalPollTimer = setInterval(async () => {
    const activeJobs = tableData.value.filter(j => j.status === 1)
    // 并行查询所有活跃任务的最新日志
    const results = await Promise.all(
      activeJobs.map(job =>
        getMyJobLogs(job.id, { page: 1, size: 1 })
          .then(res => ({ job, data: res.data.data as PageResult<UserJobLog> }))
          .catch(() => null),
      ),
    )
    let hasNewLog = false
    for (const result of results) {
      if (!result) continue
      const { job, data } = result
      if (data.records.length > 0) {
        const latestLog = data.records[0]
        const prevId = lastLogIdMap.get(job.id) ?? 0
        if (latestLog.id > prevId) {
          // 仅在已初始化后才弹通知（避免页面加载时对旧日志弹通知）
          if (lastLogIdMap.has(job.id)) {
            showLogNotification(latestLog)
            hasNewLog = true
          }
          lastLogIdMap.set(job.id, latestLog.id)
        }
      }
    }
    // 仅在检测到新日志时刷新列表和统计，避免每 10 秒无谓刷新
    if (hasNewLog) {
      loadData()
      loadStats()
    }
  }, 10000)
}

/**
 * 根据 cron 表达式计算下次执行时间。
 */
function getNextFireTime(cronExpression: string): string {
  try {
    const interval = CronExpressionParser.parse(cronExpression)
    const next = interval.next()
    return next.toDate().toLocaleString(locale.value === 'zh-CN' ? 'zh-CN' : 'en-US', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
  } catch {
    return '-'
  }
}

// ─── 工作流标签页状态 ───

const activeTab = ref('todo')
const wfStats = ref<WorkspaceStats>({ todoCount: 0, myInitiatedRunning: 0, myInitiatedTotal: 0 })

// 待我审批
const todoList = ref<TodoTask[]>([])
const todoTotal = ref(0)
const todoPage = ref(1)
const todoLoading = ref(false)

// 我发起的
const initiatedList = ref<ProcessInstanceExt[]>([])
const initiatedTotal = ref(0)
const initiatedPage = ref(1)
const initiatedLoading = ref(false)
const initiatedStatus = ref<number | undefined>(undefined)

// 我已办的
const completedList = ref<ProcessInstanceExt[]>([])
const completedTotal = ref(0)
const completedPage = ref(1)
const completedLoading = ref(false)

// 审批对话框
const approvalVisible = ref(false)
const approvalTask = ref<TodoTask | null>(null)
const approvalForm = reactive({ approved: true, comment: '' })

async function loadWfStats() {
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

function openApproval(task: TodoTask) {
  approvalTask.value = task
  approvalForm.approved = true
  approvalForm.comment = ''
  approvalVisible.value = true
}

async function submitApproval() {
  if (!approvalTask.value) return
  await completeApproval(approvalTask.value.taskId, {
    approved: approvalForm.approved,
    comment: approvalForm.comment,
  })
  ElMessage.success(t('common.success'))
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
</script>

<template>
  <div class="home-page">
    <!-- 顶部导航栏：Logo + 功能按钮 + 用户菜单 -->
    <header class="home-header">
      <div class="home-header-left">
        <span class="home-logo gradient-text">{{ t('common.appName') }}</span>
      </div>
      <div class="home-header-right">
        <!-- 语言切换 -->
        <el-button text :title="t('lang.switch')" @click="toggleLang">
          <el-icon><Globe /></el-icon>
          {{ locale === 'zh-CN' ? 'EN' : '中' }}
        </el-button>
        <!-- 主题切换 -->
        <el-button text :title="t('theme.toggle')" @click="toggleTheme">
          <el-icon>
            <Moon v-if="appStore.theme === 'dark'" />
            <Sunny v-else />
          </el-icon>
        </el-button>
        <!-- 未登录状态：显示登录按钮 -->
        <template v-if="!userStore.isLoggedIn">
          <el-button type="primary" @click="goToLogin">
            <el-icon><User /></el-icon>
            {{ t('common.login') }}
          </el-button>
        </template>
        <!-- 已登录状态：显示用户信息和功能入口 -->
        <template v-else>
          <el-dropdown>
            <span class="home-user-info">
              <el-avatar :size="32" class="home-avatar">
                <el-icon :size="18"><User /></el-icon>
              </el-avatar>
              <span class="home-username">{{ userStore.username || 'Admin' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">{{ t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <!-- 管理员才显示控制台入口 -->
          <el-button v-if="isAdmin" type="primary" @click="goToConsole">
            <el-icon><Monitor /></el-icon>
            {{ t('common.console') }}
          </el-button>
        </template>
      </div>
    </header>

    <!-- ═══ 未登录：Hero 落地页 ═══ -->
    <main v-if="!userStore.isLoggedIn" class="home-hero">
      <div class="hero-orb"></div>
      <div class="hero-content">
        <h1 class="home-hero-title">
          <span class="gradient-text">{{ t('home.welcome') }}</span>
        </h1>
        <p class="home-hero-subtitle">{{ t('common.subtitle') }}</p>
        <p class="home-hero-desc">{{ t('home.desc') }}</p>
        <div class="home-hero-actions">
          <el-button type="primary" size="large" @click="goToLogin">
            {{ t('home.getStarted') }}
          </el-button>
        </div>
      </div>
    </main>

    <!-- ═══ 已登录：用户工作台 ═══ -->
    <main v-else class="workspace">
      <!-- 工作台标签页 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- Tab 1: 待我审批 -->
        <el-tab-pane :label="`${t('workflow.todo')} (${wfStats.todoCount})`" name="todo">
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
          <el-empty v-if="!todoLoading && todoList.length === 0" :description="t('workspace.noJobs')" />
          <el-pagination v-model:current-page="todoPage" class="pagination"
                         :page-size="10" :total="todoTotal"
                         layout="total, prev, pager, next"
                         @current-change="loadTodoList" />
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
          <el-empty v-if="!initiatedLoading && initiatedList.length === 0" :description="t('workspace.noJobs')" />
          <el-pagination v-model:current-page="initiatedPage" class="pagination"
                         :page-size="10" :total="initiatedTotal"
                         layout="total, prev, pager, next"
                         @current-change="loadInitiatedList" />
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
          <el-empty v-if="!completedLoading && completedList.length === 0" :description="t('workspace.noJobs')" />
          <el-pagination v-model:current-page="completedPage" class="pagination"
                         :page-size="10" :total="completedTotal"
                         layout="total, prev, pager, next"
                         @current-change="loadCompletedList" />
        </el-tab-pane>

        <!-- Tab 4: 我的定时任务 -->
        <el-tab-pane :label="t('workflow.myJobs')" name="jobs">
          <!-- 统计卡片 -->
          <div class="ws-stats">
            <el-card shadow="hover" class="ws-stat-card">
              <div class="ws-stat-value">{{ stats.totalJobs }}</div>
              <div class="ws-stat-label">{{ t('workspace.stats.totalJobs') }}</div>
            </el-card>
            <el-card shadow="hover" class="ws-stat-card">
              <div class="ws-stat-value ws-stat-info">{{ stats.todayExecuted }}</div>
              <div class="ws-stat-label">{{ t('workspace.stats.todayExecuted') }}</div>
            </el-card>
            <el-card shadow="hover" class="ws-stat-card">
              <div class="ws-stat-value ws-stat-danger">{{ stats.todayFailed }}</div>
              <div class="ws-stat-label">{{ t('workspace.stats.todayFailed') }}</div>
            </el-card>
          </div>

          <!-- 任务列表 -->
          <el-card class="ws-table-card">
            <template #header>
              <div class="ws-card-header">
                <span class="ws-card-title">{{ t('workspace.myJobs') }}</span>
                <el-button type="primary" @click="openCreateDialog">
                  <el-icon><Plus /></el-icon>
                  {{ t('workspace.createJob') }}
                </el-button>
              </div>
            </template>

        <!-- 搜索栏 -->
        <el-form inline style="margin-bottom: 16px">
          <el-form-item :label="t('userJob.jobName')">
            <el-input v-model="searchJobName" clearable />
          </el-form-item>
          <el-form-item :label="t('userJob.jobType')">
            <el-input v-model="searchJobType" clearable />
          </el-form-item>
          <el-form-item :label="t('common.status')">
            <el-select v-model="searchStatus" clearable style="width: 120px">
              <el-option :label="t('common.enabled')" :value="1" />
              <el-option :label="t('common.disabled')" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
            <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="tableData" stripe border>
          <el-table-column prop="jobName" :label="t('userJob.jobName')" min-width="150" />
          <el-table-column prop="jobType" :label="t('userJob.jobType')" width="130" />
          <el-table-column prop="cronExpression" :label="t('userJob.cronExpression')" width="150" />
          <el-table-column :label="t('common.status')" width="90" align="center">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" @change="handleToggleStatus(row)" />
            </template>
          </el-table-column>
          <el-table-column prop="lastFireTime" :label="t('userJob.lastFireTime')" width="170" />
          <el-table-column :label="t('userJob.nextFireTime')" width="170">
            <template #default="{ row }">
              {{ row.status === 1 ? getNextFireTime(row.cronExpression) : '-' }}
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="300" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openEditDialog(row)">{{ t('common.edit') }}</el-button>
              <el-button size="small" type="primary" @click="handleTrigger(row)">
                {{ t('userJob.triggerNow') }}
              </el-button>
              <el-button size="small" @click="openLogDialog(row)">{{ t('workspace.viewLogs') }}</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 空状态提示 -->
        <el-empty v-if="!loading && tableData.length === 0" :description="t('workspace.noJobs')" />

        <el-pagination v-model:current-page="currentPage" class="pagination"
                       :page-size="pageSize" :total="total"
                       layout="total, prev, pager, next"
                       @current-change="handlePageChange" />
          </el-card>
        </el-tab-pane>
      </el-tabs>

      <!-- 审批对话框 -->
      <el-dialog v-model="approvalVisible" :title="approvalTask?.taskName || t('workflow.approve')" width="500">
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
          <el-button type="primary" @click="submitApproval">{{ t('common.confirm') }}</el-button>
        </template>
      </el-dialog>
    </main>

    <!-- 页脚 -->
    <footer class="home-footer">
      <span>&copy; 2026 {{ t('common.appName') }}</span>
    </footer>

    <!-- ═══ 创建/编辑任务对话框 ═══ -->
    <el-dialog v-model="formDialogVisible"
               :title="isEdit ? t('workspace.editJob') : t('workspace.createJob')"
               width="700px">
      <el-form :model="form" label-width="120px">
        <el-form-item :label="t('userJob.jobName')">
          <el-input v-model="form.jobName" />
        </el-form-item>
        <el-form-item :label="t('userJob.jobType')">
          <el-select v-model="form.jobType" :disabled="isEdit"
                     style="width: 100%" @change="onJobTypeChange">
            <el-option v-for="jt in enabledTypes" :key="jt.typeCode"
                       :label="jt.typeName" :value="jt.typeCode" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('userJob.cronExpression')">
          <CronGenerator v-model="form.cronExpression" />
        </el-form-item>
        <!-- 动态参数表单 -->
        <el-form-item v-if="currentSchema" :label="t('userJob.jobParams')">
          <DynamicFormRenderer :schema="currentSchema" v-model="form.jobParams" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- ═══ 执行日志弹窗 ═══ -->
    <el-dialog v-model="logDialogVisible"
               :title="`${t('workspace.executionLogs')} — ${logJobName}`"
               width="800px">
      <el-table v-loading="logLoading" :data="logData" stripe border size="small">
        <el-table-column prop="fireTime" :label="t('userJobLog.fireTime')" width="170" />
        <el-table-column prop="executeTimeMs" :label="t('userJobLog.executeTimeMs')" width="130" align="right" />
        <el-table-column :label="t('userJobLog.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? t('userJobLog.statusSuccess') : t('userJobLog.statusFail') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resultMessage" :label="t('userJobLog.resultMessage')" show-overflow-tooltip min-width="180" />
        <el-table-column prop="errorMessage" :label="t('userJobLog.errorMessage')" show-overflow-tooltip />
      </el-table>
      <el-pagination v-model:current-page="logPage" class="pagination"
                     :page-size="logPageSize" :total="logTotal"
                     layout="total, prev, pager, next"
                     @current-change="handleLogPageChange" />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--omni-bg-base);
  overflow-x: hidden;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 var(--omni-space-xl);
  height: 72px;
  background: var(--omni-bg-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--omni-border-color);
  position: sticky;
  top: 0;
  z-index: 100;
}

.home-header-left {
  display: flex;
  align-items: center;
}

.home-logo {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.home-header-right {
  display: flex;
  align-items: center;
  gap: var(--omni-space-sm);
}

.home-user-info {
  display: flex;
  align-items: center;
  gap: var(--omni-space-sm);
  cursor: pointer;
  color: var(--omni-text-primary);
  transition: color var(--omni-duration-fast);

  &:hover {
    color: var(--omni-text-accent);
  }
}

.home-avatar {
  background: var(--omni-gradient-primary);
}

.home-username {
  font-size: 14px;
  font-weight: 500;
  color: var(--omni-text-primary);
}

/* ─── Hero（未登录） ─── */

.home-hero {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  text-align: center;
  padding: var(--omni-space-3xl) var(--omni-space-lg);
  position: relative;
}

.hero-orb {
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(0, 153, 204, 0.08) 0%,
    rgba(108, 71, 214, 0.05) 50%,
    transparent 70%
  );
  filter: blur(60px);
  pointer-events: none;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

html.dark .hero-orb {
  background: radial-gradient(
    circle,
    rgba(0, 212, 255, 0.10) 0%,
    rgba(123, 97, 255, 0.06) 50%,
    transparent 70%
  );
}

.hero-content {
  position: relative;
  z-index: 1;
  animation: omni-fade-in-up 0.8s var(--omni-ease-smooth) both;
}

.home-hero-title {
  font-size: 56px;
  font-weight: 700;
  margin: 0 0 var(--omni-space-md);
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.home-hero-subtitle {
  font-size: 22px;
  margin: 0 0 var(--omni-space-md);
  color: var(--omni-text-secondary);
  font-weight: 500;
}

.home-hero-desc {
  font-size: 16px;
  margin: 0 0 var(--omni-space-xl);
  color: var(--omni-text-tertiary);
  max-width: 560px;
  line-height: 1.6;
  margin-left: auto;
  margin-right: auto;
}

.home-hero-actions {
  display: flex;
  gap: var(--omni-space-md);
  justify-content: center;
}

/* ─── 工作台（已登录） ─── */

.workspace {
  flex: 1;
  padding: var(--omni-space-xl);
  max-width: 1440px;
  margin: 0 auto;
  width: 100%;
}

.ws-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--omni-space-lg);
  margin-bottom: var(--omni-space-xl);
}

.ws-stat-card {
  text-align: center;
}

.ws-stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--omni-text-primary);
  line-height: 1.2;
}

.ws-stat-info {
  color: var(--el-color-primary);
}

.ws-stat-danger {
  color: var(--el-color-danger);
}

.ws-stat-label {
  font-size: 14px;
  color: var(--omni-text-secondary);
  margin-top: 4px;
}

.ws-table-card {
  :deep(.el-card__header) {
    padding: 16px 20px;
  }
}

.ws-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ws-card-title {
  font-weight: 600;
  font-size: 16px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* ─── 页脚 ─── */

.home-footer {
  text-align: center;
  padding: var(--omni-space-lg);
  color: var(--omni-text-tertiary);
  font-size: 14px;
  border-top: 1px solid var(--omni-border-color);
}
</style>
