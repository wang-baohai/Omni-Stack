<script setup lang="ts">
/**
 * MQ 消息记录管理页面。
 * 提供消息记录的分页查询、详情查看、重发和忽略操作，面向运维人员。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  listMqMessages,
  getMqRelayRuntimeStatus,
  resendMessage,
  skipMessage,
  MqMessageStatus,
  statusMap,
  type MqMessage,
  type MqMessageQuery,
  type MqRelayRuntimeStatus,
} from '@/api/mqMessage'
import type { PageResult } from '@/types/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()

/** 状态下拉选项 */
const statusOptions = [
  { value: MqMessageStatus.PENDING, label: 'PENDING' },
  { value: MqMessageStatus.SENT, label: 'SENT' },
  { value: MqMessageStatus.FAILED, label: 'FAILED' },
  { value: MqMessageStatus.DEAD_LETTER, label: 'DEAD_LETTER' },
  { value: MqMessageStatus.SKIPPED, label: 'SKIPPED' },
]

/** 查询表单 */
const queryForm = reactive({
  status: undefined as number | undefined,
  topic: '',
  msgKey: '',
  serviceName: '',
  dateRange: null as [string, string] | null,
})

/** 消息列表 */
const tableData = ref<MqMessage[]>([])
/** 分页总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页大小 */
const pageSize = ref(10)
/** 加载状态 */
const loading = ref(false)
/** 持久加载错误；与“查询成功但无数据”明确区分。 */
const loadError = ref('')
/** 轻量模式下异步投递关闭，但事务 Outbox 仍可写入。 */
const runtimeStatus = ref<MqRelayRuntimeStatus | null>(null)

/** 详情弹窗 */
const dialogVisible = ref(false)
const selectedMessage = ref<MqMessage | null>(null)

/**
 * 加载消息列表。
 */
async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const runtimeResponse = await getMqRelayRuntimeStatus()
    runtimeStatus.value = runtimeResponse.data.data
    const params: MqMessageQuery = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (queryForm.status !== undefined) {
      params.status = queryForm.status
    }
    if (queryForm.topic) {
      params.topic = queryForm.topic
    }
    if (queryForm.msgKey) {
      params.msgKey = queryForm.msgKey
    }
    if (queryForm.serviceName) {
      params.serviceName = queryForm.serviceName
    }
    if (queryForm.dateRange && queryForm.dateRange.length === 2) {
      params.beginTime = queryForm.dateRange[0]
      params.endTime = queryForm.dateRange[1]
    }
    const res = await listMqMessages(params)
    const data = res.data.data as PageResult<MqMessage>
    tableData.value = data.records
    total.value = data.total
  } catch (error: unknown) {
    const response = (error as { response?: { headers?: Record<string, string> } }).response
    const traceId = response?.headers?.['x-trace-id']
    loadError.value = traceId
      ? t('mqMessage.loadFailedWithTrace', { traceId })
      : t('mqMessage.loadFailed')
  } finally {
    loading.value = false
  }
}

/** 处理搜索 */
function handleSearch() {
  currentPage.value = 1
  loadData()
}

/** 处理重置筛选 */
function handleReset() {
  queryForm.status = undefined
  queryForm.topic = ''
  queryForm.msgKey = ''
  queryForm.serviceName = ''
  queryForm.dateRange = null
  handleSearch()
}

/** 处理分页变化 */
function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/** 打开详情弹窗 */
function handleViewDetail(row: MqMessage) {
  selectedMessage.value = row
  dialogVisible.value = true
}

/** 关闭详情弹窗 */
function closeDialog() {
  dialogVisible.value = false
  selectedMessage.value = null
}

/** 格式化 JSON 字符串 */
function formatJson(jsonStr: string | null): string {
  if (!jsonStr) return '-'
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    return jsonStr
  }
}

/** 判断是否可重发 */
function canResend(status: number): boolean {
  return runtimeStatus.value?.deliveryEnabled === true
    && [MqMessageStatus.PENDING, MqMessageStatus.FAILED, MqMessageStatus.DEAD_LETTER].includes(status)
}

/** 判断是否可忽略 */
function canSkip(status: number): boolean {
  return status === MqMessageStatus.DEAD_LETTER
}

/** 重发消息 */
async function handleResend(row: MqMessage) {
  try {
    await ElMessageBox.confirm(t('mqMessage.confirmResend'), t('common.confirm'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await resendMessage(row.msgId)
    ElMessage.success(t('common.success'))
    loadData()
  } catch (e: unknown) {
    if (e !== 'cancel') {
      ElMessage.error((e as Error)?.message || t('common.error'))
    }
  }
}

/** 忽略消息 */
async function handleSkip(row: MqMessage) {
  try {
    await ElMessageBox.confirm(t('mqMessage.confirmSkip'), t('common.confirm'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await skipMessage(row.msgId)
    ElMessage.success(t('common.success'))
    loadData()
  } catch (e: unknown) {
    if (e !== 'cancel') {
      ElMessage.error((e as Error)?.message || t('common.error'))
    }
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('mqMessage.title') }}</span>
        </div>
      </template>

      <!-- 筛选栏 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item :label="t('mqMessage.status')">
          <el-select
            v-model="queryForm.status"
            :placeholder="t('mqMessage.statusPlaceholder')"
            clearable
            style="width: 160px"
          >
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('mqMessage.topic')">
          <el-input
            v-model="queryForm.topic"
            :placeholder="t('mqMessage.topicPlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('mqMessage.msgKey')">
          <el-input
            v-model="queryForm.msgKey"
            :placeholder="t('mqMessage.msgKeyPlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('mqMessage.serviceName')">
          <el-input
            v-model="queryForm.serviceName"
            :placeholder="t('mqMessage.serviceNamePlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('mqMessage.timeRange')">
          <el-date-picker
            v-model="queryForm.dateRange"
            type="datetimerange"
            :range-separator="t('mqMessage.to')"
            :start-placeholder="t('mqMessage.startTime')"
            :end-placeholder="t('mqMessage.endTime')"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="runtimeStatus && !runtimeStatus.deliveryEnabled"
        :title="t('mqMessage.deliveryDisabledTitle')"
        :description="t('mqMessage.deliveryDisabledDescription')"
        type="warning"
        :closable="false"
        show-icon
        class="runtime-warning"
      />

      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        show-icon
        class="load-error"
      >
        <template #default>
          <el-button type="danger" plain size="small" :loading="loading" @click="loadData">
            {{ t('common.retry') }}
          </el-button>
        </template>
      </el-alert>

      <!-- 数据表格 -->
      <el-table
        v-if="!loadError"
        v-permission="'base:mqmessage:list'"
        v-loading="loading"
        :data="tableData"
        stripe
        border
        row-key="id"
        style="width: 100%"
      >
        <el-table-column prop="msgId" :label="t('mqMessage.msgId')" width="120" show-overflow-tooltip />
        <el-table-column prop="topic" :label="t('mqMessage.topic')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="msgKey" :label="t('mqMessage.msgKey')" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.msgKey || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="t('mqMessage.status')" width="130">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.tagType || 'info'" size="small">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="serviceName" :label="t('mqMessage.serviceName')" width="140" />
        <el-table-column prop="retryCount" :label="t('mqMessage.retryCount')" width="100">
          <template #default="{ row }">
            {{ row.retryCount }} / {{ row.maxRetry }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('mqMessage.createTime')" width="170" />
        <el-table-column :label="t('common.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleViewDetail(row)">
              {{ t('mqMessage.detail') }}
            </el-button>
            <el-button
              v-if="canResend(row.status)"
              v-permission="'base:mqmessage:resend'"
              size="small"
              type="warning"
              @click="handleResend(row)"
            >
              {{ t('mqMessage.resend') }}
            </el-button>
            <el-button
              v-if="canSkip(row.status)"
              v-permission="'base:mqmessage:skip'"
              size="small"
              type="info"
              @click="handleSkip(row)"
            >
              {{ t('mqMessage.skip') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-if="!loadError"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[5, 10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="currentPage = 1; handlePageChange(1)"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="t('mqMessage.detail')"
      width="720px"
      destroy-on-close
    >
      <template v-if="selectedMessage">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('mqMessage.msgId')">{{ selectedMessage.msgId }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.topic')">{{ selectedMessage.topic }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.bindingName')">{{ selectedMessage.bindingName }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.tag')">{{ selectedMessage.tag || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.msgKey')">{{ selectedMessage.msgKey || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.brokerType')">{{ selectedMessage.brokerType }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.status')">
            <el-tag :type="statusMap[selectedMessage.status]?.tagType || 'info'" size="small">
              {{ statusMap[selectedMessage.status]?.label || selectedMessage.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.retryCount')">{{ selectedMessage.retryCount }} / {{ selectedMessage.maxRetry }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.serviceName')">{{ selectedMessage.serviceName }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.createTime')">{{ selectedMessage.createTime }}</el-descriptions-item>
          <el-descriptions-item :label="t('mqMessage.nextRetryTime')" :span="2">{{ selectedMessage.nextRetryTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- Payload -->
        <div class="section-title">{{ t('mqMessage.payload') }}</div>
        <pre class="json-block">{{ formatJson(selectedMessage.payload) }}</pre>

        <!-- 错误信息 -->
        <template v-if="selectedMessage.errorMsg">
          <div class="section-title">{{ t('mqMessage.errorMsg') }}</div>
          <div class="error-block">{{ selectedMessage.errorMsg }}</div>
        </template>
      </template>

      <template #footer>
        <el-button @click="closeDialog">{{ t('mqMessage.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.filter-form {
  margin-bottom: 16px;
}
.load-error {
  margin-bottom: 16px;
}
.runtime-warning {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
}
.json-block {
  background: var(--el-fill-color-light);
  padding: 10px 14px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.error-block {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  padding: 10px 14px;
  border-radius: 4px;
  font-size: 13px;
}
</style>
