<script setup lang="ts">
/**
 * 工作台 · 我的定时任务页面。
 * 由首页拆分迁入（WP-03.S02），完整保全任务列表/分页搜索/CRUD/启停触发/动态表单/执行日志/结果通知能力；
 * 请求与错误语义沿用首页原实现，onUnmounted 清除局部与全局轮询。作为受保护路由页面（注册见 WP-03.S04），
 * 挂载时用户已通过路由守卫认证，无需再在组件内判断登录态。
 */
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  listMyJobs, createMyJob, updateMyJob, deleteMyJob,
  toggleMyJobStatus, triggerMyJob, getMyJobTypes,
  getMyJobStats, getMyJobLogs,
  type MyJobStats,
} from '@/api/myJob'
import type { UserJob, CreateUserJobRequest, UserJobLog } from '@/api/myJob'
import type { UserJobType } from '@/api/userJobType'
import type { PageResult } from '@/types/api'
import { isRecord, type DynamicFormValues } from '@/types/schema'
import CronGenerator from '@/components/CronGenerator.vue'
import DynamicFormRenderer from '@/components/DynamicFormRenderer.vue'
import { CronExpressionParser } from 'cron-parser'

const { t, locale } = useI18n()
const props = defineProps<{ embedded?: boolean }>()
const emit = defineEmits<{ changed: [] }>()

// ─── 列表与分页状态 ───

const loading = ref(false)
const tableData = ref<UserJob[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const searchJobName = ref('')
const searchJobType = ref('')
const searchStatus = ref<number | undefined>(undefined)

/** 统计数据 */
const stats = ref<MyJobStats | null>(null)

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
  jobParams: DynamicFormValues
}>({
  jobName: '',
  jobType: '',
  cronExpression: '0 * * * * ?',
  jobParams: {},
})

/** 当前选中类型的 paramTemplate schema */
const currentSchema = computed<Record<string, unknown> | null>(() => {
  const selected = enabledTypes.value.find(tp => tp.typeCode === form.jobType)
  if (!selected?.paramTemplate) return null
  try {
    const parsed: unknown = JSON.parse(selected.paramTemplate)
    return isRecord(parsed) ? parsed : null
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

// ─── 数据加载 ───

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
  if (props.embedded) { emit('changed'); return }
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
  let params: DynamicFormValues = {}
  try {
    const parsed: unknown = row.jobParams ? JSON.parse(row.jobParams) : {}
    params = isRecord(parsed) ? parsed as DynamicFormValues : {}
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
  await Promise.all([loadEnabledTypes(), loadStats(), loadData()])
  // 检查最近 2 分钟内的执行结果，弹出未读通知
  checkRecentLogs()
  // 启动周期性轮询检测 cron 自动触发的执行结果
  startGlobalPolling()
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
 * 页面加载时检查最近 2 分钟内的执行日志。
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
    return next.toDate().toLocaleString(locale.value, {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
  } catch {
    return '-'
  }
}
</script>

<template>
  <div class="jobs-page" :class="{ 'jobs-page--embedded': props.embedded }">
    <!-- 统计卡片 -->
    <div v-if="!props.embedded" class="ws-stats">
      <el-card shadow="hover" class="ws-stat-card">
        <div class="ws-stat-value">{{ stats?.totalJobs ?? '—' }}</div>
        <div class="ws-stat-label">{{ t('workspace.stats.totalJobs') }}</div>
      </el-card>
      <el-card shadow="hover" class="ws-stat-card">
        <div class="ws-stat-value ws-stat-info">{{ stats?.todayExecuted ?? '—' }}</div>
        <div class="ws-stat-label">{{ t('workspace.stats.todayExecuted') }}</div>
      </el-card>
      <el-card shadow="hover" class="ws-stat-card">
        <div class="ws-stat-value ws-stat-danger">{{ stats?.todayFailed ?? '—' }}</div>
        <div class="ws-stat-label">{{ t('workspace.stats.todayFailed') }}</div>
      </el-card>
    </div>

    <!-- 任务列表 -->
    <el-card class="ws-table-card">
      <template #header>
        <div class="ws-card-header">
          <h2 class="ws-card-title">{{ t('workspace.myJobs') }}</h2>
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

      <el-pagination
        v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination"
        :page-sizes="[5, 10, 20, 50, 100]" :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="currentPage = 1; handlePageChange(1)" />
    </el-card>

    <!-- ═══ 创建/编辑任务对话框 ═══ -->
    <el-dialog
      v-model="formDialogVisible"
      :title="isEdit ? t('workspace.editJob') : t('workspace.createJob')"
      width="700px">
      <el-form :model="form" label-width="120px">
        <el-form-item :label="t('userJob.jobName')">
          <el-input v-model="form.jobName" />
        </el-form-item>
        <el-form-item :label="t('userJob.jobType')">
          <el-select
            v-model="form.jobType" :disabled="isEdit"
            style="width: 100%" @change="onJobTypeChange">
            <el-option
              v-for="jt in enabledTypes" :key="jt.typeCode"
              :label="jt.typeName" :value="jt.typeCode" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('userJob.cronExpression')">
          <CronGenerator v-model="form.cronExpression" compact />
        </el-form-item>
        <!-- 动态参数表单 -->
        <el-form-item v-if="currentSchema" :label="t('userJob.jobParams')">
          <DynamicFormRenderer v-model="form.jobParams" :schema="currentSchema" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- ═══ 执行日志弹窗 ═══ -->
    <el-dialog
      v-model="logDialogVisible"
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
      <el-pagination
        v-model:current-page="logPage" v-model:page-size="logPageSize" class="pagination"
        :page-sizes="[5, 10, 20, 50, 100]" :total="logTotal"
        layout="total, sizes, prev, pager, next"
        @current-change="handleLogPageChange"
        @size-change="logPage = 1; handleLogPageChange(1)" />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.jobs-page {
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
  margin: 0;
  font-weight: 600;
  font-size: 16px;
}

.jobs-page--embedded { padding: 0; max-width: none; }

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
