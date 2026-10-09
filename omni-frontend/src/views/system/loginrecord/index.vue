<script setup lang="ts">
/**
 * 登录记录页面（D02）。
 * 安全审计的独立只读投影：仅展示 LOGIN_SUCCESS/LOGIN_FAILED 事件，后端已固定租户与事件集合、脱敏与白名单映射。
 * 权限：system:loginrecord:list。账号锁定/角色/密码/配置更新属“审计日志”，OAuth 授权/撤销属“授权记录”。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listLoginRecords, type LoginRecord, type LoginRecordQuery } from '@/api/login-record'
import type { PageResult } from '@/types/api'

const { t } = useI18n()

const resultOptions = ['SUCCESS', 'FAILURE']
const methodOptions = ['PASSWORD', 'SESSION', 'SOCIAL', 'UNKNOWN']

const queryForm = reactive({
  username: '',
  result: '',
  method: '',
  dateRange: null as [string, string] | null,
})

const tableData = ref<LoginRecord[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)

function resultLabel(v: string): string {
  return v === 'SUCCESS' ? t('loginRecord.resultSuccess') : t('loginRecord.resultFailure')
}
function methodLabel(v: string): string {
  switch (v) {
  case 'PASSWORD': return t('loginRecord.methodPassword')
  case 'SESSION': return t('loginRecord.methodSession')
  case 'SOCIAL': return t('loginRecord.methodSocial')
  default: return t('loginRecord.methodUnknown')
  }
}
function reasonLabel(v: string | null): string {
  if (!v) return '-'
  const key = `loginRecord.reason_${v}`
  const text = t(key)
  return text === key ? v : text
}
function resultTagType(v: string): string {
  return v === 'SUCCESS' ? 'success' : 'danger'
}

async function loadData() {
  loading.value = true
  try {
    const params: LoginRecordQuery = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (queryForm.username) params.username = queryForm.username
    if (queryForm.result) params.result = queryForm.result
    if (queryForm.method) params.method = queryForm.method
    if (queryForm.dateRange && queryForm.dateRange.length === 2) {
      params.startTime = queryForm.dateRange[0]
      params.endTime = queryForm.dateRange[1]
    }
    const res = await listLoginRecords(params)
    const data = res.data.data as PageResult<LoginRecord>
    tableData.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  queryForm.username = ''
  queryForm.result = ''
  queryForm.method = ''
  queryForm.dateRange = null
  handleSearch()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('loginRecord.title') }}</span>
        </div>
      </template>

      <el-alert type="info" :closable="false" class="filter-note">
        {{ t('loginRecord.filterNote') }}
      </el-alert>

      <el-form :inline="true" class="filter-form">
        <el-form-item :label="t('loginRecord.username')">
          <el-input
            v-model="queryForm.username"
            :placeholder="t('loginRecord.usernamePlaceholder')"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item :label="t('loginRecord.result')">
          <el-select v-model="queryForm.result" clearable style="width: 140px">
            <el-option
              v-for="r in resultOptions"
              :key="r"
              :label="resultLabel(r)"
              :value="r"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('loginRecord.method')">
          <el-select v-model="queryForm.method" clearable style="width: 140px">
            <el-option
              v-for="m in methodOptions"
              :key="m"
              :label="methodLabel(m)"
              :value="m"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('loginRecord.timeRange')">
          <el-date-picker
            v-model="queryForm.dateRange"
            type="datetimerange"
            :range-separator="t('loginRecord.to')"
            :start-placeholder="t('loginRecord.startTime')"
            :end-placeholder="t('loginRecord.endTime')"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: min(380px, 100%)"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" stripe border row-key="id" style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="createTime" :label="t('loginRecord.time')" width="180" />
        <el-table-column prop="username" :label="t('loginRecord.username')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="t('loginRecord.result')" width="110">
          <template #default="{ row }">
            <el-tag :type="resultTagType(row.result)" size="small">{{ resultLabel(row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('loginRecord.method')" width="110">
          <template #default="{ row }">{{ methodLabel(row.method) }}</template>
        </el-table-column>
        <el-table-column :label="t('loginRecord.provider')" width="110">
          <template #default="{ row }">{{ row.provider || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('loginRecord.reason')" width="140">
          <template #default="{ row }">{{ reasonLabel(row.reasonCode) }}</template>
        </el-table-column>
        <el-table-column prop="ipMasked" :label="t('loginRecord.ip')" width="150" />
        <el-table-column prop="clientSummary" :label="t('loginRecord.client')" min-width="160" show-overflow-tooltip />
        <template #empty>
          <span>{{ t('loginRecord.empty') }}</span>
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="currentPage = 1; handlePageChange(1)"
      />
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}
.card-header {
  font-weight: 600;
}
.filter-note {
  margin-bottom: 16px;
}
.filter-form {
  margin-bottom: 8px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 600px) {
  .page-container { padding: 4px; }
  /* 用更高权重压过全局 html.dark .el-card .el-card__body 的 20px 内边距，为窄屏控件争取可读宽度 */
  .page-container :deep(.el-card .el-card__body) { padding: 12px; }
  .filter-form :deep(.el-form-item) {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    margin-right: 0;
  }
  .filter-form :deep(.el-form-item__content) { width: 100%; min-width: 0; }
  .pagination { justify-content: flex-start; overflow-x: auto; }

  /* D3 修复：datetimerange 范围型控件是单一 inline-flex 容器，内部两个输入各占 39%，
     窄屏下整块被压到不足 100px 时两个输入各仅约 12px，占位文字几乎不可辨识。
     改为纵向堆叠：起始/结束时间各占一行（宽度 100%），“至”分隔符单独占一行，
     前缀图标隐藏、清除图标改为右上角绝对定位，不再参与横向宽度分配。 */
  .filter-form :deep(.el-date-editor.el-range-editor) {
    display: block;
    height: auto;
    padding: 4px 10px;
  }
  .filter-form :deep(.el-date-editor .el-range__icon) { display: none; }
  .filter-form :deep(.el-date-editor .el-range-input) {
    display: block;
    width: 100%;
    height: 26px;
    line-height: 26px;
  }
  .filter-form :deep(.el-date-editor .el-range-separator) {
    display: block;
    width: auto;
    height: auto;
    padding: 0;
    line-height: 16px;
  }
  .filter-form :deep(.el-date-editor .el-range__close-icon) {
    position: absolute;
    top: 4px;
    right: 10px;
  }
}
</style>
