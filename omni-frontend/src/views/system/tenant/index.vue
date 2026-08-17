<script setup lang="ts">
/**
 * 租户管理页面。
 * 提供租户的增删改查功能。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listTenantsPage,
  createTenant,
  updateTenant,
  deleteTenant,
  type SysTenant,
  type CreateTenantRequest,
} from '@/api/tenant'
import type { PageResult } from '@/types/api'

const { t } = useI18n()

/** 租户列表数据 */
const tableData = ref<SysTenant[]>([])
/** 分页总数 */
const total = ref(0)
/** 当前页码 */
const currentPage = ref(1)
/** 每页大小 */
const pageSize = ref(10)
/** 加载状态 */
const loading = ref(false)

/** 表单对话框 */
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editingId = ref<number | null>(null)
const formRef = ref()
const form = reactive<CreateTenantRequest>({
  tenantCode: '',
  tenantName: '',
  adminPassword: '',
  domain: '',
  contactName: '',
  contactPhone: '',
  status: 1,
})

/** 表单校验规则 */
const rules = {
  tenantCode: [{ required: true, message: t('tenant.tenantCode'), trigger: 'blur' }],
  tenantName: [{ required: true, message: t('tenant.tenantName'), trigger: 'blur' }],
  adminPassword: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (editingId.value !== null) return callback()
        if (!value) return callback(new Error(t('tenant.adminPasswordRequired')))
        if (value.length < 8 || value.length > 64) {
          return callback(new Error(t('tenant.adminPasswordLength')))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

/**
 * 加载租户列表。
 */
async function loadData() {
  loading.value = true
  try {
    const res = await listTenantsPage(currentPage.value, pageSize.value)
    const data = res.data.data as PageResult<SysTenant>
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
 * 打开新建对话框。
 */
function openCreateDialog() {
  editingId.value = null
  dialogTitle.value = t('common.create')
  Object.assign(form, {
    tenantCode: '',
    tenantName: '',
    adminPassword: '',
    domain: '',
    contactName: '',
    contactPhone: '',
    status: 1,
  })
  dialogVisible.value = true
}

/**
 * 打开编辑对话框。
 */
function openEditDialog(row: SysTenant) {
  editingId.value = row.id
  dialogTitle.value = t('common.edit')
  Object.assign(form, {
    tenantCode: row.tenantCode,
    tenantName: row.tenantName,
    adminPassword: '',
    domain: row.domain ?? '',
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    status: row.status,
  })
  dialogVisible.value = true
}

/**
 * 保存表单。
 */
async function handleSave() {
  await formRef.value?.validate()
  if (editingId.value !== null) {
    const { tenantCode: _tenantCode, adminPassword: _adminPassword, ...updateData } = form
    void _tenantCode
    void _adminPassword
    await updateTenant(editingId.value, updateData)
  } else {
    await createTenant(form)
  }
  form.adminPassword = ''
  ElMessage.success(t('common.success'))
  dialogVisible.value = false
  loadData()
}

/**
 * 处理删除租户。
 */
async function handleDelete(row: SysTenant) {
  try {
    await ElMessageBox.confirm(t('tenant.confirmDelete'), { type: 'warning' })
    await deleteTenant(row.id)
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
          <span>{{ t('common.tenants') }}</span>
          <el-button v-permission="'system:tenant:create'" type="primary" @click="openCreateDialog">
            {{ t('common.create') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="tenantCode" :label="t('tenant.tenantCode')" width="140" />
        <el-table-column prop="tenantName" :label="t('tenant.tenantName')" width="160" />
        <el-table-column prop="domain" :label="t('tenant.domain')" min-width="180" />
        <el-table-column prop="contactName" :label="t('tenant.contactName')" width="120" />
        <el-table-column prop="contactPhone" :label="t('tenant.contactPhone')" width="140" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:tenant:update'" size="small" @click="openEditDialog(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-button v-permission="'system:tenant:delete'" size="small" type="danger" @click="handleDelete(row)">
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

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item :label="t('tenant.tenantCode')" prop="tenantCode">
          <el-input v-model="form.tenantCode" :disabled="editingId !== null" />
        </el-form-item>
        <el-form-item :label="t('tenant.tenantName')" prop="tenantName">
          <el-input v-model="form.tenantName" />
        </el-form-item>
        <el-form-item
          v-if="editingId === null"
          :label="t('tenant.adminPassword')"
          prop="adminPassword"
        >
          <el-input
            v-model="form.adminPassword"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="t('tenant.adminPasswordPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('tenant.domain')" prop="domain">
          <el-input v-model="form.domain" />
        </el-form-item>
        <el-form-item :label="t('tenant.contactName')" prop="contactName">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item :label="t('tenant.contactPhone')" prop="contactPhone">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item :label="t('common.status')" prop="status">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            :active-text="t('common.enabled')"
            :inactive-text="t('common.disabled')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ t('common.confirm') }}</el-button>
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
