<script setup lang="ts">
/**
 * 审计日志管理页面。
 * 提供审计日志的只读分页查询功能，支持按事件类型、用户名、时间范围筛选。
 * 行展开可查看 extra JSON 扩展信息。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listAuditLogs, type AuditLog, type AuditLogQuery } from '@/api/audit-log'
import type { PageResult } from '@/types/api'

const { t } = useI18n()

/** 事件类型枚举值 */
const eventTypes = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILED',
  'LOGOUT',
  'ACCOUNT_LOCKED',
  'ACCOUNT_UNLOCKED',
  'PASSWORD_CHANGED',
  'USER_CREATED',
  'USER_DELETED',
  'USER_STATUS_CHANGED',
  'ROLE_ASSIGNED',
  'ROLE_REVOKED',
]

/** 事件类型枚举值 -> i18n key 映射 */
const eventTypeI18nMap: Record<string, string> = {
  LOGIN_SUCCESS: 'auditLog.eventLoginSuccess',
  LOGIN_FAILED: 'auditLog.eventLoginFailed',
  LOGOUT: 'auditLog.eventLogout',
  ACCOUNT_LOCKED: 'auditLog.eventAccountLocked',
  ACCOUNT_UNLOCKED: 'auditLog.eventAccountUnlocked',
  PASSWORD_CHANGED: 'auditLog.eventPasswordChanged',
  USER_CREATED: 'auditLog.eventUserCreated',
  USER_DELETED: 'auditLog.eventUserDeleted',
  USER_STATUS_CHANGED: 'auditLog.eventUserStatusChanged',
  ROLE_ASSIGNED: 'auditLog.eventRoleAssigned',
  ROLE_REVOKED: 'auditLog.eventRoleRevoked',
}

/** 获取事件类型的翻译文本 */
function getEventLabel(eventType: string): string {
  const key = eventTypeI18nMap[eventType]
  return key ? t(key) : eventType
}

/** 查询表单 */
const queryForm = reactive({
  eventType: '',
  username: '',
  dateRange: null as [string, string] | null,
})

/** 审计日志列表 */
const tableData = ref<AuditLog[]>([])
/** 分页总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页大小 */
const pageSize = ref(10)
/** 加载状态 */
const loading = ref(false)

/**
 * 加载审计日志列表。
 */
async function loadData() {
  loading.value = true
  try {
    const params: AuditLogQuery = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (queryForm.eventType) {
      params.eventType = queryForm.eventType
    }
    if (queryForm.username) {
      params.username = queryForm.username
    }
    if (queryForm.dateRange && queryForm.dateRange.length === 2) {
      params.startTime = queryForm.dateRange[0]
      params.endTime = queryForm.dateRange[1]
    }
    const res = await listAuditLogs(params)
    const data = res.data.data as PageResult<AuditLog>
    tableData.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/**
 * 处理搜索。
 */
function handleSearch() {
  currentPage.value = 1
  loadData()
}

/**
 * 处理重置筛选。
 */
function handleReset() {
  queryForm.eventType = ''
  queryForm.username = ''
  queryForm.dateRange = null
  handleSearch()
}

/**
 * 处理分页变化。
 */
function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/**
 * 格式化 extra JSON 为可读字符串。
 */
function formatExtra(extra: Record<string, unknown> | null): string {
  if (!extra || Object.keys(extra).length === 0) return '-'
  return JSON.stringify(extra, null, 2)
}

/**
 * 获取事件类型的标签样式。
 */
function getEventTagType(eventType: string): string {
  switch (eventType) {
  case 'LOGIN_SUCCESS':
  case 'USER_CREATED':
  case 'ROLE_ASSIGNED':
  case 'ACCOUNT_UNLOCKED':
    return 'success'
  case 'LOGIN_FAILED':
  case 'ACCOUNT_LOCKED':
  case 'USER_DELETED':
  case 'ROLE_REVOKED':
    return 'danger'
  case 'LOGOUT':
  case 'PASSWORD_CHANGED':
  case 'USER_STATUS_CHANGED':
    return 'warning'
  default:
    return 'info'
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('common.auditLogs') }}</span>
        </div>
      </template>

      <!-- 筛选栏 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item :label="t('auditLog.eventType')">
          <el-select
            v-model="queryForm.eventType"
            :placeholder="t('auditLog.eventTypePlaceholder')"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="et in eventTypes"
              :key="et"
              :label="getEventLabel(et)"
              :value="et"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('auditLog.username')">
          <el-input
            v-model="queryForm.username"
            :placeholder="t('auditLog.usernamePlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('auditLog.timeRange')">
          <el-date-picker
            v-model="queryForm.dateRange"
            type="datetimerange"
            :range-separator="t('auditLog.to')"
            :start-placeholder="t('auditLog.startTime')"
            :end-placeholder="t('auditLog.endTime')"
            value-format="YYYY-MM-DDTHH:mm:ss"
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
        v-loading="loading"
        :data="tableData"
        stripe
        border
        row-key="id"
        style="width: 100%"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <h4>{{ t('auditLog.extraInfo') }}</h4>
              <pre class="extra-json">{{ formatExtra(row.extra) }}</pre>
              <p v-if="row.userAgent" class="ua-info">
                <strong>User-Agent:</strong> {{ row.userAgent }}
              </p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="eventType" :label="t('auditLog.eventType')" width="180">
          <template #default="{ row }">
            <el-tag :type="getEventTagType(row.eventType)" size="small">
              {{ getEventLabel(row.eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="t('auditLog.username')" width="140" />
        <el-table-column prop="ipAddress" :label="t('auditLog.ipAddress')" width="140" />
        <el-table-column prop="description" :label="t('auditLog.description')" min-width="240" show-overflow-tooltip />
        <el-table-column prop="createBy" :label="t('auditLog.operator')" width="120" />
        <el-table-column prop="createTime" :label="t('auditLog.createTime')" width="180" />
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
.expand-content {
  padding: 12px 20px;
}
.expand-content h4 {
  margin: 0 0 8px;
  font-size: 13px;
}
.extra-json {
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
.ua-info {
  margin-top: 8px;
  font-size: 12px;
  word-break: break-all;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
