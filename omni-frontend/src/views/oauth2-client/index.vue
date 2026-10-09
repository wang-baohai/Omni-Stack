<script setup lang="ts">
/**
 * OAuth2 客户端管理列表页。
 * 展示已注册的 OAuth2 客户端，支持创建、编辑和删除操作。
 */
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listOAuth2Clients,
  deleteOAuth2Client,
} from '@/api/oauth2-client'
import type { OAuth2ClientVO } from '@/api/oauth2-client'
import ClientForm from './form.vue'

const { t } = useI18n()

/** 客户端列表数据 */
const clients = ref<OAuth2ClientVO[]>([])
/** 分页参数 */
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})
/** 加载状态 */
const loading = ref(false)
/** 表单弹窗可见性 */
const formVisible = ref(false)
/** 当前编辑的客户端（null 表示新建） */
const editingClient = ref<OAuth2ClientVO | null>(null)

/**
 * 加载客户端列表。
 */
async function loadClients() {
  loading.value = true
  try {
    const { data: res } = await listOAuth2Clients(pagination.page, pagination.size)
    clients.value = res.data.records
    pagination.total = res.data.total
  } catch {
    ElMessage.error(t('oauth2Client.loadFailed'))
  } finally {
    loading.value = false
  }
}

/**
 * 打开创建弹窗。
 */
function handleCreate() {
  editingClient.value = null
  formVisible.value = true
}

/**
 * 打开编辑弹窗。
 */
function handleEdit(client: OAuth2ClientVO) {
  editingClient.value = client
  formVisible.value = true
}

/**
 * 删除客户端。
 */
async function handleDelete(client: OAuth2ClientVO) {
  try {
    await ElMessageBox.confirm(
      t('oauth2Client.deleteConfirm', { name: client.clientName, id: client.clientId }),
      t('oauth2Client.deleteConfirmTitle'),
      { type: 'warning' },
    )
    await deleteOAuth2Client(client.id)
    ElMessage.success(t('oauth2Client.deleteSuccess'))
    loadClients()
  } catch {
    // 用户取消或删除失败
  }
}

/**
 * 表单保存成功回调。
 */
function handleFormSuccess() {
  formVisible.value = false
  loadClients()
}

/**
 * 分页变更处理。
 */
function handlePageChange(page: number) {
  pagination.page = page
  loadClients()
}

onMounted(() => {
  loadClients()
})
</script>

<template>
  <div class="oauth2-client-page">
    <div class="page-header">
      <h2>{{ t('common.oauth2Clients') }}</h2>
      <el-button v-permission="'system:oauth2:create'" type="primary" @click="handleCreate">
        {{ t('common.create') }}
      </el-button>
    </div>

    <el-table v-loading="loading" :data="clients" stripe style="width: 100%">
      <el-table-column prop="clientName" :label="t('oauth2Client.clientName')" min-width="150" />
      <el-table-column prop="clientId" :label="t('oauth2Client.clientId')" min-width="200" show-overflow-tooltip />
      <el-table-column :label="t('oauth2Client.grantTypes')" min-width="200">
        <template #default="{ row }">
          <el-tag
            v-for="grant in row.grantTypes"
            :key="grant"
            size="small"
            class="grant-tag"
          >
            {{ grant }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('oauth2Client.redirectUris')" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.redirectUris?.join(', ') || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('oauth2Client.pkce')" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.requireProofKey ? 'success' : 'info'" size="small">
            {{ row.requireProofKey ? t('common.yes') : t('common.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:oauth2:update'" type="primary" link size="small" @click="handleEdit(row)">
            {{ t('common.edit') }}
          </el-button>
          <el-button v-permission="'system:oauth2:delete'" type="danger" link size="small" @click="handleDelete(row)">
            {{ t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="pagination.total > pagination.size"
      class="pagination"
      :current-page="pagination.page"
      :page-size="pagination.size"
      :total="pagination.total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />

    <ClientForm
      v-model:visible="formVisible"
      :client="editingClient"
      @success="handleFormSuccess"
    />
  </div>
</template>

<style scoped>
.oauth2-client-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
}

.grant-tag {
  margin-right: 4px;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
