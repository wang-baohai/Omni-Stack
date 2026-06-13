<script setup lang="ts">
/**
 * 授权记录页面。
 * 提供 OAuth2 授权记录的只读分页查询功能。
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { listAuthRecords, type AuthRecord } from '@/api/auth-record'
import type { PageResult } from '@/types/api'

const { t } = useI18n()

/** 授权记录列表 */
const tableData = ref<AuthRecord[]>([])
/** 分页总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页大小 */
const pageSize = ref(10)
/** 加载状态 */
const loading = ref(false)

/**
 * 加载授权记录列表。
 */
async function loadData() {
  loading.value = true
  try {
    const res = await listAuthRecords(currentPage.value, pageSize.value)
    const data = res.data.data as PageResult<AuthRecord>
    tableData.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/**
 * 处理分页变化。
 */
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
          <span>{{ t('common.authRecords') }}</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="registeredClientId" :label="t('authRecord.clientId')" width="200" />
        <el-table-column prop="principalName" :label="t('authRecord.principal')" width="160" />
        <el-table-column prop="authorizationGrantType" :label="t('authRecord.grantType')" width="180" />
        <el-table-column prop="authorizedScopes" :label="t('authRecord.scopes')" min-width="240" />
        <el-table-column prop="createdAt" :label="t('authRecord.createdAt')" width="200" />
      </el-table>

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
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
