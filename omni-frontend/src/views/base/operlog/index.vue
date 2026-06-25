<script setup lang="ts">
/**
 * 操作日志管理页面。
 * 提供操作日志的只读分页查询功能，支持按模块、操作类型、操作人、时间范围筛选。
 * 点击行可查看变更快照详情。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listOperLogs, type OperLog, type OperLogQuery } from '@/api/oper-log'
import type { PageResult } from '@/types/api'

const { t } = useI18n()

/** 操作类型枚举值 */
const operTypes = ['CREATE', 'UPDATE', 'DELETE', 'QUERY', 'EXPORT', 'IMPORT']

/** 操作类型 -> i18n key 映射 */
const operTypeI18nMap: Record<string, string> = {
  CREATE: 'operLog.typeCreate',
  UPDATE: 'operLog.typeUpdate',
  DELETE: 'operLog.typeDelete',
  QUERY: 'operLog.typeQuery',
  EXPORT: 'operLog.typeExport',
  IMPORT: 'operLog.typeImport',
}

/** 操作类型 -> 标签颜色映射 */
const operTypeTagMap: Record<string, string> = {
  CREATE: 'success',
  UPDATE: 'warning',
  DELETE: 'danger',
  QUERY: 'info',
  EXPORT: 'primary',
  IMPORT: 'primary',
}

/** 获取操作类型的翻译文本 */
function getOperTypeLabel(operType: string): string {
  const key = operTypeI18nMap[operType]
  return key ? t(key) : operType
}

/** 获取状态标签样式 */
function getStatusTagType(status: number): string {
  return status === 200 ? 'success' : 'danger'
}

/** 获取状态翻译文本 */
function getStatusLabel(status: number): string {
  return status === 200 ? t('operLog.statusSuccess') : t('operLog.statusFail')
}

/** 格式化 JSON 字符串为可读格式 */
function formatJson(jsonStr: string | null): string {
  if (!jsonStr) return '-'
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    return jsonStr
  }
}

/** 查询表单 */
const queryForm = reactive({
  module: '',
  operType: '',
  operUsername: '',
  dateRange: null as [string, string] | null,
})

/** 操作日志列表 */
const tableData = ref<OperLog[]>([])
/** 分页总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页大小 */
const pageSize = ref(10)
/** 加载状态 */
const loading = ref(false)

/** 详情弹窗 */
const dialogVisible = ref(false)
const selectedLog = ref<OperLog | null>(null)

/**
 * 加载操作日志列表。
 */
async function loadData() {
  loading.value = true
  try {
    const params: OperLogQuery = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (queryForm.module) {
      params.module = queryForm.module
    }
    if (queryForm.operType) {
      params.operType = queryForm.operType
    }
    if (queryForm.operUsername) {
      params.operUsername = queryForm.operUsername
    }
    if (queryForm.dateRange && queryForm.dateRange.length === 2) {
      params.startTime = queryForm.dateRange[0]
      params.endTime = queryForm.dateRange[1]
    }
    const res = await listOperLogs(params)
    const data = res.data.data as PageResult<OperLog>
    tableData.value = data.records
    total.value = data.total
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
  queryForm.module = ''
  queryForm.operType = ''
  queryForm.operUsername = ''
  queryForm.dateRange = null
  handleSearch()
}

/** 处理分页变化 */
function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/** 处理行点击，打开详情弹窗 */
function handleRowClick(row: OperLog) {
  selectedLog.value = row
  dialogVisible.value = true
}

/** 关闭详情弹窗 */
function closeDialog() {
  dialogVisible.value = false
  selectedLog.value = null
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('common.operLogs') }}</span>
        </div>
      </template>

      <!-- 筛选栏 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item :label="t('operLog.module')">
          <el-input
            v-model="queryForm.module"
            :placeholder="t('operLog.modulePlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('operLog.operType')">
          <el-select
            v-model="queryForm.operType"
            :placeholder="t('operLog.operTypePlaceholder')"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="ot in operTypes"
              :key="ot"
              :label="getOperTypeLabel(ot)"
              :value="ot"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('operLog.operator')">
          <el-input
            v-model="queryForm.operUsername"
            :placeholder="t('operLog.operatorPlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('operLog.timeRange')">
          <el-date-picker
            v-model="queryForm.dateRange"
            type="datetimerange"
            :range-separator="t('operLog.to')"
            :start-placeholder="t('operLog.startTime')"
            :end-placeholder="t('operLog.endTime')"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table
        v-permission="'base:operlog:list'"
        v-loading="loading"
        :data="tableData"
        stripe
        border
        row-key="id"
        style="width: 100%; cursor: pointer"
        @row-click="handleRowClick"
      >
        <el-table-column prop="operTime" :label="t('operLog.operTime')" width="180" />
        <el-table-column prop="operUsername" :label="t('operLog.operator')" width="120" />
        <el-table-column prop="module" :label="t('operLog.module')" width="140" show-overflow-tooltip />
        <el-table-column prop="operType" :label="t('operLog.operType')" width="120">
          <template #default="{ row }">
            <el-tag :type="operTypeTagMap[row.operType]" size="small">
              {{ getOperTypeLabel(row.operType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('operLog.request')" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.requestMethod }} {{ row.requestUrl }}
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" :label="t('operLog.ipAddress')" width="140" />
        <el-table-column prop="executionTime" :label="t('operLog.execTime')" width="110">
          <template #default="{ row }">
            {{ row.executionTime != null ? row.executionTime + ' ' + t('operLog.execTimeUnit') : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="responseStatus" :label="t('operLog.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.responseStatus)" size="small">
              {{ getStatusLabel(row.responseStatus) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="currentPage"
        class="pagination"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="t('operLog.detail')"
      width="720px"
      destroy-on-close
    >
      <template v-if="selectedLog">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('operLog.operator')">{{ selectedLog.operUsername }}</el-descriptions-item>
          <el-descriptions-item :label="t('operLog.module')">{{ selectedLog.module }}</el-descriptions-item>
          <el-descriptions-item :label="t('operLog.operType')">
            <el-tag :type="operTypeTagMap[selectedLog.operType]" size="small">
              {{ getOperTypeLabel(selectedLog.operType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('operLog.status')">
            <el-tag :type="getStatusTagType(selectedLog.responseStatus)" size="small">
              {{ getStatusLabel(selectedLog.responseStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('operLog.request')">{{ selectedLog.requestMethod }} {{ selectedLog.requestUrl }}</el-descriptions-item>
          <el-descriptions-item :label="t('operLog.ipAddress')">{{ selectedLog.ipAddress }}</el-descriptions-item>
          <el-descriptions-item :label="t('operLog.execTime')">{{ selectedLog.executionTime }} {{ t('operLog.execTimeUnit') }}</el-descriptions-item>
          <el-descriptions-item :label="t('operLog.operTime')">{{ selectedLog.operTime }}</el-descriptions-item>
          <el-descriptions-item :label="'User-Agent'" :span="2">{{ selectedLog.userAgent || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 请求参数 -->
        <div class="section-title">{{ t('operLog.requestParams') }}</div>
        <pre class="json-block">{{ formatJson(selectedLog.requestParams) }}</pre>

        <!-- 变更快照 -->
        <template v-if="selectedLog.oldValue || selectedLog.newValue">
          <div class="section-title">{{ t('operLog.changeSnapshot') }}</div>
          <div class="diff-container">
            <div class="diff-panel">
              <h5>{{ t('operLog.oldValue') }}</h5>
              <pre class="json-block">{{ formatJson(selectedLog.oldValue) }}</pre>
            </div>
            <div class="diff-panel">
              <h5>{{ t('operLog.newValue') }}</h5>
              <pre class="json-block">{{ formatJson(selectedLog.newValue) }}</pre>
            </div>
          </div>
        </template>

        <!-- 错误信息 -->
        <template v-if="selectedLog.errorMsg">
          <div class="section-title">{{ t('operLog.errorMsg') }}</div>
          <div class="error-block">{{ selectedLog.errorMsg }}</div>
        </template>
      </template>

      <template #footer>
        <el-button @click="closeDialog">{{ t('operLog.close') }}</el-button>
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
.diff-container {
  display: flex;
  gap: 16px;
}
.diff-panel {
  flex: 1;
  min-width: 0;
}
.diff-panel h5 {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.error-block {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  padding: 10px 14px;
  border-radius: 4px;
  font-size: 13px;
}
</style>
