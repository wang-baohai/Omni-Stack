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

/** 邀请状态中文映射 */
const inviteStatusLabel: Record<string, string> = { ACTIVE: '有效', REVOKED: '已撤销', EXPIRED: '已过期', USED: '已用完' }

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
  ElMessage.success('邀请已创建')
  await loadData()
}

async function handleRevoke(row: InviteItem) {
  try {
    await ElMessageBox.confirm('确认撤销此邀请？', '撤销确认', { type: 'warning' })
    await revokeInvite(row.id)
    ElMessage.success('已撤销')
    await loadData()
  } catch { /* 用户取消 */ }
}

function copyToken(token: string) {
  navigator.clipboard.writeText(token).then(() => ElMessage.success('邀请链接已复制'))
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('common.srmInvite') }}</span>
          <el-button v-permission="'srm:portal:invite'" type="primary" @click="createDialogVisible = true">创建邀请</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'REVOKED' ? 'danger' : 'info'">{{ inviteStatusLabel[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="maxUses" label="最大次数" width="100" />
        <el-table-column prop="usedCount" label="已使用" width="80" />
        <el-table-column prop="expiresTime" label="过期时间" width="170" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'srm:portal:invite'" size="small" type="danger" @click="handleRevoke(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="创建邀请" width="480px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="最大使用次数"><el-input-number v-model="createForm.maxUses" :min="1" :max="100" /></el-form-item>
        <el-form-item label="有效期（小时）"><el-input-number v-model="createForm.expiresHours" :min="1" :max="720" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button v-permission="'srm:portal:invite'" type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createdToken" title="邀请令牌" width="560px">
      <el-alert type="warning" :closable="false" title="请保存此邀请令牌，关闭后将无法再次查看" />
      <el-input :model-value="createdToken" readonly>
        <template #append>
          <el-button @click="createdToken && copyToken(createdToken)">复制</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="createdToken = null">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
</style>
