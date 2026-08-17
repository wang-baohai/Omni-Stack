<script setup lang="ts">
/**
 * 角色管理页面。
 * 提供角色的增删改查、权限分配和部门分配功能。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRoles, createRole, updateRole, deleteRole,
  assignRolePermissions, assignRoleDepts, getRoleDeptIds,
  type SysRole, type CreateRoleRequest,
} from '@/api/role'
import { fetchRolePermissionTree, type PermissionNode } from '@/api/permission'
import { fetchOrgTree, type OrgUnitTreeNode } from '@/api/org'
import type { PageResult } from '@/types/api'

const { t } = useI18n()

/** 角色列表数据 */
const tableData = ref<SysRole[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

/** 表单对话框 */
const formDialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<CreateRoleRequest>({
  roleCode: '',
  roleName: '',
  dataScope: 'ALL',
  sort: 0,
  status: 1,
})

/** 权限分配对话框 */
const permDialogVisible = ref(false)
const permTree = ref<PermissionNode[]>([])
const permCheckedKeys = ref<number[]>([])

/** 部门分配对话框 */
const deptDialogVisible = ref(false)
const orgTree = ref<OrgUnitTreeNode[]>([])
const deptCheckedKeys = ref<number[]>([])
const currentRoleId = ref<number | null>(null)

/** 数据范围选项 */
const dataScopeOptions = [
  { value: 'ALL', label: 'role.dataScopeAll' },
  { value: 'TENANT', label: 'role.dataScopeTenant' },
  { value: 'DEPT_AND_BELOW', label: 'role.dataScopeDeptAndBelow' },
  { value: 'DEPT', label: 'role.dataScopeDept' },
  { value: 'SELF', label: 'role.dataScopeSelf' },
  { value: 'CUSTOM', label: 'role.dataScopeCustom' },
]

async function loadData() {
  loading.value = true
  try {
    const res = await listRoles(currentPage.value, pageSize.value)
    const data = res.data.data as PageResult<SysRole>
    tableData.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, { roleCode: '', roleName: '', dataScope: 'ALL', sort: 0, status: 1 })
  formDialogVisible.value = true
}

function openEditDialog(row: SysRole) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    roleCode: row.roleCode,
    roleName: row.roleName,
    dataScope: row.dataScope,
    sort: row.sort,
    status: row.status,
  })
  formDialogVisible.value = true
}

async function saveForm() {
  if (isEdit.value && editingId.value) {
    await updateRole(editingId.value, {
      roleName: form.roleName,
      dataScope: form.dataScope,
      sort: form.sort,
      status: form.status,
    })
  } else {
    await createRole(form)
  }
  ElMessage.success(t('common.success'))
  formDialogVisible.value = false
  loadData()
}

async function handleDelete(row: SysRole) {
  try {
    await ElMessageBox.confirm(t('role.confirmDelete'), { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success(t('common.success'))
    loadData()
  } catch { /* cancelled */ }
}

/** 打开权限分配对话框 */
async function openPermDialog(row: SysRole) {
  currentRoleId.value = row.id
  const res = await fetchRolePermissionTree(row.id)
  permTree.value = res.data.data
  // 提取已选中的权限 ID
  permCheckedKeys.value = extractCheckedIds(permTree.value)
  permDialogVisible.value = true
}

function extractCheckedIds(nodes: PermissionNode[]): number[] {
  const ids: number[] = []
  for (const node of nodes) {
    if (node.checked) ids.push(node.id)
    if (node.children) ids.push(...extractCheckedIds(node.children as PermissionNode[]))
  }
  return ids
}

async function savePermissions() {
  if (!currentRoleId.value) return
  await assignRolePermissions(currentRoleId.value, permCheckedKeys.value)
  ElMessage.success(t('common.success'))
  permDialogVisible.value = false
}

/** 打开部门分配对话框 */
async function openDeptDialog(row: SysRole) {
  currentRoleId.value = row.id
  const [orgRes, deptRes] = await Promise.all([
    fetchOrgTree(),
    getRoleDeptIds(row.id),
  ])
  orgTree.value = orgRes.data.data
  deptCheckedKeys.value = deptRes.data.data
  deptDialogVisible.value = true
}

async function saveDepts() {
  if (!currentRoleId.value) return
  await assignRoleDepts(currentRoleId.value, deptCheckedKeys.value)
  ElMessage.success(t('common.success'))
  deptDialogVisible.value = false
}

/** 权限树节点 checkbox 变化 */
function handlePermCheck(nodeId: number, checked: boolean) {
  if (checked) {
    if (!permCheckedKeys.value.includes(nodeId)) {
      permCheckedKeys.value.push(nodeId)
    }
  } else {
    permCheckedKeys.value = permCheckedKeys.value.filter((id) => id !== nodeId)
  }
}

/** 翻译权限名称（基于 permissionCode 映射 i18n 键） */
function getPermName(code: string, fallback: string): string {
  const key = `permission.perm_${code.replace(/[:.]/g, '_')}`
  const translated = t(key)
  return translated !== key ? translated : fallback
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('common.roles') }}</span>
          <el-button v-permission="'system:role:create'" type="primary" @click="openCreateDialog">{{ t('common.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleCode" :label="t('role.roleCode')" width="150" />
        <el-table-column prop="roleName" :label="t('role.roleName')" width="180" />
        <el-table-column :label="t('role.dataScope')" min-width="150">
          <template #default="{ row }">
            {{ t(`role.dataScope${row.dataScope.split('_').map((s: string) => s.charAt(0) + s.slice(1).toLowerCase()).join('')}`) }}
          </template>
        </el-table-column>
        <el-table-column prop="sort" :label="t('common.sort')" width="80" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="360" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:role:update'" size="small" @click="openEditDialog(row)">{{ t('common.edit') }}</el-button>
            <el-button v-permission="'system:role:update'" size="small" type="primary" @click="openPermDialog(row)">
              {{ t('role.assignPermissions') }}
            </el-button>
            <el-button
              v-permission="'system:role:update'"
              :disabled="row.dataScope !== 'CUSTOM'"
              size="small"
              type="warning"
              @click="openDeptDialog(row)"
            >
              {{ t('role.assignDepts') }}
            </el-button>
            <el-button v-permission="'system:role:delete'" size="small" type="danger" @click="handleDelete(row)">
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

    <!-- 角色表单对话框 -->
    <el-dialog v-model="formDialogVisible" :title="isEdit ? t('common.edit') : t('common.create')" width="500px">
      <el-form :model="form" label-width="120px">
        <el-form-item :label="t('role.roleCode')">
          <el-input v-model="form.roleCode" :disabled="isEdit" />
        </el-form-item>
        <el-form-item :label="t('role.roleName')">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item :label="t('role.dataScope')">
          <el-select v-model="form.dataScope">
            <el-option
              v-for="opt in dataScopeOptions"
              :key="opt.value"
              :value="opt.value"
              :label="t(opt.label)"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配对话框 -->
    <el-dialog v-model="permDialogVisible" :title="t('role.assignPermissions')" width="500px">
      <div class="perm-tree">
        <div v-for="dirNode in permTree" :key="dirNode.id" class="perm-group">
          <div class="perm-group-title">{{ getPermName(dirNode.permissionCode, dirNode.permissionName) }}</div>
          <el-checkbox-group v-model="permCheckedKeys">
            <div v-for="menuNode in dirNode.children" :key="menuNode.id" class="perm-menu">
              <el-checkbox
                :value="menuNode.id"
                @change="(val: boolean) => handlePermCheck(menuNode.id, val)"
              >
                {{ getPermName(menuNode.permissionCode, menuNode.permissionName) }}
              </el-checkbox>
              <div class="perm-apis">
                <el-checkbox
                  v-for="apiNode in menuNode.children"
                  :key="apiNode.id"
                  :value="apiNode.id"
                  @change="(val: boolean) => handlePermCheck(apiNode.id, val)"
                >
                  {{ getPermName(apiNode.permissionCode, apiNode.permissionName) }}
                </el-checkbox>
              </div>
            </div>
          </el-checkbox-group>
        </div>
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="savePermissions">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 部门分配对话框 -->
    <el-dialog v-model="deptDialogVisible" :title="t('role.assignDepts')" width="500px">
      <el-tree
        :data="orgTree"
        show-checkbox
        node-key="id"
        :default-checked-keys="deptCheckedKeys"
        :props="{ label: 'name', children: 'children' }"
        @check-change="(node: OrgUnitTreeNode, checked: boolean) => {
          if (checked) {
            if (!deptCheckedKeys.includes(node.id)) deptCheckedKeys.push(node.id)
          } else {
            deptCheckedKeys = deptCheckedKeys.filter(id => id !== node.id)
          }
        }"
      />
      <template #footer>
        <el-button @click="deptDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveDepts">{{ t('common.confirm') }}</el-button>
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
.perm-tree {
  max-height: 400px;
  overflow-y: auto;
}
.perm-group {
  margin-bottom: 16px;
}
.perm-group-title {
  font-weight: 600;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color);
}
.perm-menu {
  margin-left: 8px;
  margin-bottom: 8px;
}
.perm-apis {
  margin-left: 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
