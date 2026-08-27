<script setup lang="ts">
/** SRM 邀请管理页面。 */
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createInvite,
  listInvites,
  revokeInvite,
  type InviteItem,
} from '@/api/srm-portal'

const { t } = useI18n()
const loading = ref(false)
const tableData = ref<InviteItem[]>([])
const createDialogVisible = ref(false)
const createForm = reactive({ maxUses: 10, expiresHours: 72 })
const createdToken = ref<string | null>(null)

const inviteStatusKeys: Record<string, string> = { ACTIVE: 'active', REVOKED: 'revoked', EXPIRED: 'expired', USED: 'used' }
function inviteStatusLabel(status: string) {
  const key = inviteStatusKeys[status]
  return key ? t(`srmInvitePage.status.${key}`) : status
}

async function loadData() {
  loading.value = true
  try {
    const response = await listInvites()
    tableData.value = response.data.data
  } finally { loading.value = false }
}

async function handleCreate() {
  const response = await createInvite(createForm)
  createdToken.value = response.data.data.inviteToken || null
  createDialogVisible.value = false
  ElMessage.success(t('srmInvitePage.created'))
  await loadData()
}

async function handleRevoke(row: InviteItem) {
  try {
    await ElMessageBox.confirm(t('srmInvitePage.revokeConfirm'), t('srmInvitePage.revokeTitle'), { type: 'warning' })
    await revokeInvite(row.id)
    ElMessage.success(t('srmInvitePage.revoked'))
    await loadData()
  } catch { /* 用户取消 */ }
}

function copyToken(token: string) {
  navigator.clipboard.writeText(token).then(() => ElMessage.success(t('srmInvitePage.copied')))
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('common.srmInvite') }}</span>
          <el-button v-permission="'srm:portal:invite'" type="primary" @click="createDialogVisible = true">{{ t('srmInvitePage.create') }}</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'REVOKED' ? 'danger' : 'info'">{{ inviteStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maxUses" :label="t('srmInvitePage.maxUses')" width="100" />
        <el-table-column prop="usedCount" :label="t('srmInvitePage.usedCount')" width="80" />
        <el-table-column prop="expiresTime" :label="t('srmInvitePage.expiresTime')" width="170" />
        <el-table-column prop="createTime" :label="t('srmInvitePage.createTime')" width="170" />
        <el-table-column :label="t('common.actions')" width="150">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'srm:portal:invite'" size="small" type="danger" @click="handleRevoke(row)">{{ t('srmInvitePage.revoke') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" :title="t('srmInvitePage.create')" width="480px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="t('srmInvitePage.maxUseCount')"><el-input-number v-model="createForm.maxUses" :min="1" :max="100" /></el-form-item>
        <el-form-item :label="t('srmInvitePage.validHours')"><el-input-number v-model="createForm.expiresHours" :min="1" :max="720" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="'srm:portal:invite'" type="primary" @click="handleCreate">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createdToken" :title="t('srmInvitePage.token')" width="560px">
      <el-alert type="warning" :closable="false" :title="t('srmInvitePage.tokenWarning')" />
      <el-input :model-value="createdToken" readonly>
        <template #append>
          <el-button @click="createdToken && copyToken(createdToken)">{{ t('srmInvitePage.copy') }}</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="createdToken = null">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
</style>
