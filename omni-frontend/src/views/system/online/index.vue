<script setup lang="ts">
/**
 * 在线用户管理页面。
 * 提供在线用户列表查询和强制踢出功能。
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOnlineUsers, kickOnlineUser, type OnlineUser } from '@/api/online'

const { t } = useI18n()

/** 在线用户列表 */
const tableData = ref<OnlineUser[]>([])
/** 加载状态 */
const loading = ref(false)

/**
 * 加载在线用户列表。
 */
async function loadData() {
  loading.value = true
  try {
    const res = await listOnlineUsers()
    tableData.value = res.data.data ?? []
  } finally {
    loading.value = false
  }
}

/**
 * 处理强制踢出用户。
 */
async function handleKick(row: OnlineUser) {
  try {
    await ElMessageBox.confirm(t('online.confirmKick'), { type: 'warning' })
    await kickOnlineUser(row.userId)
    ElMessage.success(t('common.success'))
    loadData()
  } catch {
    // 取消操作
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('common.onlineUsers') }}</span>
          <el-button v-permission="'system:online:list'" @click="loadData">
            {{ t('common.search') }}
          </el-button>
        </div>
      </template>

      <el-empty v-if="!loading && tableData.length === 0" :description="t('online.noOnlineUsers')" />

      <el-table v-else v-loading="loading" :data="tableData" stripe border>
        <el-table-column prop="userId" :label="t('online.userId')" width="120" />
        <el-table-column prop="username" :label="t('online.username')" width="200" />
        <el-table-column prop="jti" :label="t('online.jti')" min-width="300" />
        <el-table-column :label="t('common.actions')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:online:kick'" size="small" type="danger" @click="handleKick(row)">
              {{ t('online.kick') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
</style>
