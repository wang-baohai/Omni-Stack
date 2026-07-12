<script setup lang="ts">
/**
 * 系统任务管理页面。
 * 展示所有 @SystemJobMeta 注册的 Handler，支持注册/启动/停止/触发/注销等全生命周期操作。
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listSystemJobs, registerSystemJob, startSystemJob,
  stopSystemJob, triggerSystemJob, unregisterSystemJob,
  type SystemJob,
} from '@/api/systemJob'
import CronGenerator from '@/components/CronGenerator.vue'
import DynamicFormRenderer from '@/components/DynamicFormRenderer.vue'

const { t } = useI18n()

const tableData = ref<SystemJob[]>([])
const loading = ref(false)

/** 注册弹窗 */
const registerDialogVisible = ref(false)
const registeringJob = ref<SystemJob | null>(null)
const registerCron = ref('')
const registerParams = ref<Record<string, any>>({})
const registering = ref(false)

/** 将 paramDefs 转换为 DynamicFormRenderer schema 格式（缓存避免重复创建） */
const registerSchema = computed(() => {
  const job = registeringJob.value
  if (!job) return null
  const schema: Record<string, any> = {}
  if (job.paramDefs) {
    for (const p of job.paramDefs) {
      schema[p.name] = {
        type: p.type,
        label: p.label,
        default: p.type === 'number' ? Number(p.defaultValue) : p.defaultValue,
        required: p.required,
      }
    }
  }
  return Object.keys(schema).length > 0 ? schema : null
})

/** 获取状态标签类型 */
function statusTagType(status: string) {
  switch (status) {
  case 'RUNNING': return 'success'
  case 'STOPPED': return 'info'
  default: return 'warning'
  }
}

/** 获取状态标签文本 */
function statusLabel(status: string) {
  switch (status) {
  case 'RUNNING': return t('systemJob.statusRunning')
  case 'STOPPED': return t('systemJob.statusStopped')
  default: return t('systemJob.statusUnregistered')
  }
}

/** 加载数据 */
async function loadData() {
  loading.value = true
  try {
    const res = await listSystemJobs()
    if (res.data.code === 200) {
      tableData.value = res.data.data
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.warning(t('systemJob.xxlJobNotAvailable'))
  } finally {
    loading.value = false
  }
}

/** 打开注册弹窗 */
function openRegister(job: SystemJob) {
  registeringJob.value = job
  registerCron.value = job.defaultCron || '0 0 2 * * ?'
  registerParams.value = {}
  registerDialogVisible.value = true
}

/** 确认注册 */
async function handleRegister() {
  if (!registeringJob.value || registering.value) return
  registering.value = true
  const paramsJson = Object.keys(registerParams.value).length > 0
    ? JSON.stringify(registerParams.value)
    : undefined
  try {
    const res = await registerSystemJob({
      handlerName: registeringJob.value.handlerName,
      cron: registerCron.value,
      params: paramsJson,
    })
    if (res.data.code === 200) {
      ElMessage.success(t('systemJob.registerSuccess'))
      registerDialogVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.error(t('common.error'))
  } finally {
    registering.value = false
  }
}

/** 启动 */
async function handleStart(job: SystemJob) {
  await ElMessageBox.confirm(t('systemJob.confirmStart'), { type: 'warning' })
  try {
    const res = await startSystemJob(job.xxlJobId!)
    if (res.data.code === 200) {
      ElMessage.success(t('systemJob.startSuccess'))
      await loadData()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.error(t('common.error'))
  }
}

/** 停止 */
async function handleStop(job: SystemJob) {
  await ElMessageBox.confirm(t('systemJob.confirmStop'), { type: 'warning' })
  try {
    const res = await stopSystemJob(job.xxlJobId!)
    if (res.data.code === 200) {
      ElMessage.success(t('systemJob.stopSuccess'))
      await loadData()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.error(t('common.error'))
  }
}

/** 立即执行 */
async function handleTrigger(job: SystemJob) {
  await ElMessageBox.confirm(t('systemJob.confirmTrigger'), { type: 'warning' })
  try {
    const res = await triggerSystemJob(job.xxlJobId!, job.actualParam ?? undefined)
    if (res.data.code === 200) {
      ElMessage.success(t('systemJob.triggerSuccess'))
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.error(t('common.error'))
  }
}

/** 注销 */
async function handleUnregister(job: SystemJob) {
  await ElMessageBox.confirm(t('systemJob.confirmUnregister'), { type: 'warning' })
  try {
    const res = await unregisterSystemJob(job.xxlJobId!)
    if (res.data.code === 200) {
      ElMessage.success(t('systemJob.unregisterSuccess'))
      await loadData()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch {
    ElMessage.error(t('common.error'))
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="system-job-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('common.systemJobs') }}</span>
          <el-button type="primary" :icon="'Refresh'" @click="loadData">
            {{ t('common.search') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="name" :label="t('systemJob.jobName')" min-width="140" />
        <el-table-column prop="handlerName" :label="t('systemJob.handlerName')" min-width="200" />
        <el-table-column prop="description" :label="t('systemJob.description')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="t('systemJob.cron')" min-width="180">
          <template #default="{ row }">
            <span v-if="row.status !== 'UNREGISTERED'" class="cron-actual">
              {{ row.actualCron }}
            </span>
            <span v-else class="cron-default">
              {{ row.defaultCron }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('systemJob.params')" min-width="160">
          <template #default="{ row }">
            <span v-if="row.actualParam" class="param-text">{{ row.actualParam }}</span>
            <span v-else class="param-empty">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('systemJob.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="280" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 'UNREGISTERED'">
              <el-button type="primary" size="small" @click="openRegister(row)">
                {{ t('systemJob.register') }}
              </el-button>
            </template>
            <template v-else-if="row.status === 'RUNNING'">
              <el-button type="warning" size="small" @click="handleStop(row)">
                {{ t('systemJob.stop') }}
              </el-button>
              <el-button type="success" size="small" @click="handleTrigger(row)">
                {{ t('systemJob.trigger') }}
              </el-button>
              <el-button type="danger" size="small" @click="handleUnregister(row)">
                {{ t('systemJob.unregister') }}
              </el-button>
            </template>
            <template v-else>
              <el-button type="success" size="small" @click="handleStart(row)">
                {{ t('systemJob.start') }}
              </el-button>
              <el-button type="primary" size="small" @click="handleTrigger(row)">
                {{ t('systemJob.trigger') }}
              </el-button>
              <el-button type="danger" size="small" @click="handleUnregister(row)">
                {{ t('systemJob.unregister') }}
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 注册弹窗 -->
    <el-dialog
      v-model="registerDialogVisible"
      :title="t('systemJob.registerTitle')"
      width="600px"
      destroy-on-close
    >
      <div v-if="registeringJob">
        <el-descriptions :column="1" border size="small" class="job-desc">
          <el-descriptions-item :label="t('systemJob.handlerName')">
            {{ registeringJob.handlerName }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('systemJob.jobName')">
            {{ registeringJob.name }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('systemJob.description')">
            {{ registeringJob.description }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">{{ t('systemJob.cronConfig') }}</el-divider>
        <CronGenerator v-model="registerCron" />

        <template v-if="registerSchema">
          <el-divider content-position="left">{{ t('systemJob.paramConfig') }}</el-divider>
          <DynamicFormRenderer
            v-model="registerParams"
            :schema="registerSchema"
          />
        </template>
      </div>

      <template #footer>
        <el-button @click="registerDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="registering" @click="handleRegister">{{ t('systemJob.register') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.system-job-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cron-actual {
  font-family: monospace;
  color: var(--el-color-success);
}

.cron-default {
  font-family: monospace;
  color: var(--el-text-color-secondary);
  font-style: italic;
}

.param-text {
  font-family: monospace;
  font-size: 12px;
}

.param-empty {
  color: var(--el-text-color-placeholder);
}

.job-desc {
  margin-bottom: 16px;
}
</style>
