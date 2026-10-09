<script setup lang="ts">
/**
 * 用户管理页面。
 * 提供用户的增删改查、角色分配和状态切换功能。
 */
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, deleteUser, toggleUserStatus, assignUserRoles, getUserRoleIds, type SysUser } from '@/api/user'
import { listAllRoles, type SysRole } from '@/api/role'
import { listTenants, type TenantOption } from '@/api/auth'
import type { PageResult } from '@/types/api'

const { t } = useI18n()

/** 用户列表数据 */
const tableData = ref<SysUser[]>([])
/** 分页总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页大小 */
const pageSize = ref(10)
/** 加载状态 */
const loading = ref(false)

/** 租户列表 */
const tenants = ref<TenantOption[]>([])

/**
 * 加载租户列表。
 */
async function loadTenants() {
  try {
    const { data: res } = await listTenants()
    tenants.value = res.data
  } catch {
    ElMessage.error(t('user.tenantLoadFailed'))
    tenants.value = []
  }
}

/** 新增用户对话框 */
const createDialogVisible = ref(false)
const createForm = ref({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  gender: 0,
  tenantId: undefined as number | undefined,
})
const createFormRef = ref()
const createRules = computed(() => ({
  username: [{ required: true, message: t('user.usernameRequired'), trigger: 'blur' }],
  password: [
    { required: true, message: t('user.passwordRequired'), trigger: 'blur' },
    { min: 6, message: t('user.passwordMinLength'), trigger: 'blur' },
  ],
  tenantId: [{ required: true, message: t('user.tenantRequired'), trigger: 'change' }],
}))

/** 角色分配对话框 */
const roleDialogVisible = ref(false)
const currentUser = ref<SysUser | null>(null)
const availableRoles = ref<SysRole[]>([])
const selectedRoleIds = ref<number[]>([])

/**
 * 加载用户列表。
 */
async function loadData() {
  loading.value = true
  try {
    const res = await listUsers(currentPage.value, pageSize.value)
    const data = res.data.data as PageResult<SysUser>
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

/**
 * 处理删除用户。
 */
async function handleDelete(row: SysUser) {
  try {
    await ElMessageBox.confirm(t('user.confirmDelete'), { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success(t('common.success'))
    loadData()
  } catch {
    // 取消操作
  }
}

/**
 * 处理切换用户状态。
 */
async function handleToggleStatus(row: SysUser) {
  const newStatus = row.status === 1 ? 0 : 1
  const confirmMsg = newStatus === 0 ? t('user.confirmDisable') : t('user.confirmEnable')
  try {
    await ElMessageBox.confirm(confirmMsg, { type: 'warning' })
    await toggleUserStatus(row.id, newStatus)
    ElMessage.success(t('common.success'))
    loadData()
  } catch {
    // 取消操作
  }
}

/**
 * 打开角色分配对话框。
 */
async function openRoleDialog(row: SysUser) {
  currentUser.value = row
  // 加载所有角色和用户已分配的角色
  const [rolesRes, userRolesRes] = await Promise.all([
    listAllRoles(),
    getUserRoleIds(row.id),
  ])
  availableRoles.value = rolesRes.data.data
  selectedRoleIds.value = userRolesRes.data.data
  roleDialogVisible.value = true
}

/**
 * 保存角色分配。
 */
async function saveRoles() {
  if (!currentUser.value) return
  await assignUserRoles(currentUser.value.id, selectedRoleIds.value)
  ElMessage.success(t('common.success'))
  roleDialogVisible.value = false
}

/**
 * 打开新增用户对话框。
 */
function openCreateDialog() {
  createForm.value = {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    gender: 0,
    tenantId: undefined as number | undefined,
  }
  createDialogVisible.value = true
}

/**
 * 提交新增用户。
 */
async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await createUser({ ...createForm.value, tenantId: createForm.value.tenantId! })
    ElMessage.success(t('user.createSuccess'))
    createDialogVisible.value = false
    loadData()
  } catch {
    // 错误消息已由 Axios 响应拦截器展示
  }
}

/**
 * 获取性别显示文本。
 */
function getGenderText(gender: number): string {
  if (gender === 1) return t('user.genderMale')
  if (gender === 2) return t('user.genderFemale')
  return t('user.genderUnknown')
}

/**
 * 手机号掩码显示：保留前3后4（138****8000），短号退化为全掩码。
 * 列表页展示层脱敏，避免完整手机号进入截图/泄露面；编辑能力不受影响。
 */
function maskPhone(phone?: string): string {
  if (!phone) return '-'
  const p = String(phone)
  return p.length >= 8 ? `${p.slice(0, 3)}****${p.slice(-4)}` : '****'
}

/**
 * 邮箱掩码显示：保留首字符与@域名（w***@example.com）。
 */
function maskEmail(email?: string): string {
  if (!email) return '-'
  const e = String(email)
  const at = e.indexOf('@')
  if (at <= 0) return '****'
  return `${e.slice(0, 1)}***${e.slice(at)}`
}

onMounted(() => {
  loadData()
  loadTenants()
})
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('common.users') }}</span>
          <el-button v-permission="'system:user:create'" type="primary" @click="openCreateDialog">
            {{ t('user.createUser') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" class="user-table" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" :label="t('user.username')" width="120" />
        <el-table-column prop="nickname" :label="t('user.nickname')" width="120" />
        <el-table-column prop="email" :label="t('user.email')" min-width="180">
          <template #default="{ row }">
            {{ maskEmail(row.email) }}
          </template>
        </el-table-column>
        <el-table-column prop="phone" :label="t('user.phone')" width="140">
          <template #default="{ row }">
            {{ maskPhone(row.phone) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('user.gender')" width="80">
          <template #default="{ row }">
            {{ getGenderText(row.gender) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:user:update'" size="small" @click="openRoleDialog(row)">
              {{ t('user.assignRoles') }}
            </el-button>
            <el-button
              v-permission="'system:user:update'"
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              {{ t('user.toggleStatus') }}
            </el-button>
            <el-button v-permission="'system:user:delete'" size="small" type="danger" @click="handleDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
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

    <!-- 角色分配对话框 -->
    <el-dialog v-model="roleDialogVisible" :title="t('user.assignRoles')" width="500px">
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox
          v-for="role in availableRoles"
          :key="role.id"
          :value="role.id"
        >
          {{ role.roleName }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveRoles">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 新增用户对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('user.createUser')" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item :label="t('user.username')" prop="username">
          <el-input v-model="createForm.username" :placeholder="t('user.usernamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('user.password')" prop="password">
          <el-input v-model="createForm.password" type="password" :placeholder="t('user.passwordPlaceholder')" show-password />
        </el-form-item>
        <el-form-item :label="t('user.nickname')" prop="nickname">
          <el-input v-model="createForm.nickname" :placeholder="t('user.nicknamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('user.email')" prop="email">
          <el-input v-model="createForm.email" :placeholder="t('user.emailPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('user.phone')" prop="phone">
          <el-input v-model="createForm.phone" :placeholder="t('user.phonePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('user.tenant')" prop="tenantId">
          <el-select v-model="createForm.tenantId" :placeholder="t('user.tenantPlaceholder')" style="width: 100%">
            <el-option
              v-for="tenant in tenants"
              :key="tenant.id"
              :label="tenant.name"
              :value="tenant.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ t('common.confirm') }}</el-button>
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
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 仅窄屏生效的布局修复：桌面端（含 1440px 已有四语截图证据）不进入本断点，外观完全不变 */
@media (max-width: 600px) {
  .page-container { padding: 4px; }
  /* 用更高权重压过全局 html.dark .el-card .el-card__body 的 20px 内边距，为表格争取可视宽度 */
  .page-container :deep(.el-card .el-card__body) { padding: 12px; }

  /* D4 修复：标题与“新增用户”按钮在窄屏并排会重叠，改为允许换行并把标题占满一行，按钮落到第二行 */
  .card-header {
    flex-wrap: wrap;
    gap: 8px;
  }
  .card-header > span { flex: 1 1 100%; }

  /* D1 修复：“操作”列宽 280px 大于窄屏可视宽度，fixed="right" 的 sticky 会把它
     钉满整个视口，导致 ID/用户名等 7 列被完全遮挡。窄屏下取消吸位，
     使其回到正常文档流，配合表体原生横向滚动即可左右滑动看到全部列。 */
  .page-container .user-table { overflow-x: auto; }
  .user-table :deep(.el-table__body-wrapper) { overflow-x: auto; }
  .user-table :deep(.el-table__body-wrapper tr td.el-table-fixed-column--right),
  .user-table :deep(.el-table__header-wrapper tr th.el-table-fixed-column--right),
  .user-table :deep(.el-table__header-wrapper tr th.el-table__fixed-right-patch) {
    /* 覆盖 Element Plus 对固定列的 position: sticky !important（项目规范允许为第三方组件样式使用 !important） */
    position: static !important;
  }
  /* 去掉固定列遗留的边缘投影占位，避免取消吸位后在表格中部出现多余阴影 */
  .user-table :deep(.el-table__body-wrapper tr td.el-table-fixed-column--right:before),
  .user-table :deep(.el-table__header-wrapper tr th.el-table-fixed-column--right:before) {
    display: none;
  }

  /* 分页控件在窄屏靠左对齐并允许横向滚动，避免整块被挤出视口 */
  .pagination { justify-content: flex-start; overflow-x: auto; }
}
</style>
