<script setup lang="ts">
/**
 * 用户管理页面。
 * 提供用户的增删改查、角色分配和状态切换功能。
 */
import { ref, onMounted } from 'vue'
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
    ElMessage.error('租户列表加载失败')
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
const createRules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }, { min: 6, message: '密码至少 6 个字符', trigger: 'blur' }],
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
}

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
    ElMessage.success('创建成功')
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
            新增用户
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" :label="t('user.username')" width="120" />
        <el-table-column prop="nickname" :label="t('user.nickname')" width="120" />
        <el-table-column prop="email" :label="t('user.email')" min-width="180" />
        <el-table-column prop="phone" :label="t('user.phone')" width="140" />
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
        class="pagination"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
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
    <el-dialog v-model="createDialogVisible" title="新增用户" width="500px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" placeholder="请输入密码（至少6位）" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="createForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="createForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="租户" prop="tenantId">
          <el-select v-model="createForm.tenantId" placeholder="请选择租户" style="width: 100%">
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
</style>
