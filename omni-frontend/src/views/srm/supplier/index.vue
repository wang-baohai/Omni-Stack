<script setup lang="ts">
/** SRM 供应商管理页面，包含供应商 CRUD、状态机操作和 360 视图。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  blacklistSupplier,
  cancelSupplier,
  createSupplier,
  deleteSupplier,
  eliminateSupplier,
  getSupplier,
  listOwnerOptions,
  listSuppliers,
  restoreSupplier,
  resumeSupplier,
  submitSupplier,
  suspendSupplier,
  transferSupplierOwner,
  updateSupplier,
  withdrawSupplier,
  type SaveSupplierRequest,
  type OwnerOption,
  type SrmSupplier,
  type SupplierStatus,
} from '@/api/srm-supplier'
import SupplierOverview from '@/components/srm/SupplierOverview.vue'
import SupplierResourcesDrawer from '@/components/srm/SupplierResourcesDrawer.vue'
import ProcessProgressDialog from '@/components/workflow/ProcessProgressDialog.vue'
import { useDictOptions } from '@/composables/useDictOptions'
import { usePermissionStore } from '@/stores/permission'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const { options: categoryOptions, loading: categoryLoading } = useDictOptions('srm_supplier_category')
const loading = ref(false)
const tableData = ref<SrmSupplier[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  name: string
  status?: SupplierStatus
  categoryCode: string
  levelCode: string
  ownerUserId?: number
}>({ name: '', categoryCode: '', levelCode: '' })

const statuses: Array<{ label: string; value: SupplierStatus; type: string }> = [
  { label: '登记中', value: 'REGISTERING', type: 'info' },
  { label: '入驻失败', value: 'REGISTERING_FAILED', type: 'danger' },
  { label: '待审核', value: 'PENDING_REVIEW', type: 'warning' },
  { label: '审批中', value: 'APPROVING', type: 'warning' },
  { label: '已驳回', value: 'REJECTED', type: 'danger' },
  { label: '已通过', value: 'APPROVED', type: 'success' },
  { label: '已冻结', value: 'SUSPENDED', type: '' },
  { label: '黑名单', value: 'BLACKLISTED', type: 'danger' },
  { label: '已淘汰', value: 'ELIMINATED', type: 'danger' },
]
const statusLabel = Object.fromEntries(statuses.map((s) => [s.value, s.label])) as Record<SupplierStatus, string>
const statusType = Object.fromEntries(statuses.map((s) => [s.value, s.type])) as Record<SupplierStatus, string>

const formDialogVisible = ref(false)
const editingSupplier = ref<SrmSupplier | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SaveSupplierRequest>({
  name: '', supplierType: '', industryCode: '', creditCode: '', website: '',
  phone: '', email: '', region: '', address: '', categoryCode: '',
})
const ownerOptions = ref<OwnerOption[]>([])
const ownerLoading = ref(false)
const overviewDrawerVisible = ref(false)
const resourcesDrawerVisible = ref(false)
const selectedSupplier = ref<SrmSupplier | null>(null)
const maskedSupplierFields = ref(new Set<'phone' | 'email'>())
const rules: FormRules<SaveSupplierRequest> = {
  name: [
    { required: true, message: '请输入供应商名称', trigger: 'blur' },
    { max: 200, message: '供应商名称不能超过 200 个字符', trigger: 'blur' },
  ],
  supplierType: [
    { required: true, message: '请选择供应商类型', trigger: 'change' },
    { max: 50, message: '供应商类型不能超过 50 个字符', trigger: 'change' },
  ],
  industryCode: [{ max: 50, message: '行业代码不能超过 50 个字符', trigger: 'blur' }],
  creditCode: [{ max: 50, message: '统一信用代码不能超过 50 个字符', trigger: 'blur' }],
  website: [{ max: 300, message: '网站地址不能超过 300 个字符', trigger: 'blur' }],
  phone: [{ max: 32, message: '电话不能超过 32 个字符', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 200, message: '邮箱不能超过 200 个字符', trigger: 'blur' },
  ],
  region: [{ max: 100, message: '地区不能超过 100 个字符', trigger: 'blur' }],
  address: [{ max: 500, message: '地址不能超过 500 个字符', trigger: 'blur' }],
  categoryCode: [{ max: 50, message: '品类代码不能超过 50 个字符', trigger: 'change' }],
}
const supplierTypes = ['MANUFACTURER', 'DISTRIBUTOR', 'SERVICE', 'OTHER']
const supplierTypeLabel: Record<string, string> = {
  MANUFACTURER: '制造商', DISTRIBUTOR: '分销商', SERVICE: '服务商', OTHER: '其他',
}
const levelLabel: Record<string, string> = {
  STRATEGIC: '战略级', PREFERRED: '优选级', QUALIFIED: '合格级', ELIMINATED: '淘汰级',
}
const categoryLabel = computed(() =>
  Object.fromEntries(categoryOptions.value.map((o) => [o.value, o.label])),
)

async function loadData() {
  loading.value = true
  try {
    const response = await listSuppliers({
      name: query.name || undefined, status: query.status,
      categoryCode: query.categoryCode || undefined,
      levelCode: query.levelCode || undefined,
      ownerUserId: query.ownerUserId,
      page: currentPage.value, size: pageSize.value,
    })
    tableData.value = response.data.data.records
    total.value = response.data.data.total
  } finally { loading.value = false }
}

function search() { currentPage.value = 1; loadData() }
function resetQuery() { Object.assign(query, { name: '', status: undefined, categoryCode: '', levelCode: '', ownerUserId: undefined }); search() }
function resetForm() {
  maskedSupplierFields.value = new Set()
  Object.assign(form, { name: '', supplierType: '', industryCode: '', creditCode: '', website: '', phone: '', email: '', region: '', address: '', categoryCode: '', ownerUserId: undefined, version: undefined })
}
async function loadOwners(keyword?: string) {
  if (!permissionStore.hasPermission('srm:owner:list')) return
  ownerLoading.value = true
  try {
    const response = await listOwnerOptions({ keyword: keyword || undefined, limit: 50 })
    ownerOptions.value = response.data.data
  } finally {
    ownerLoading.value = false
  }
}
function openCreate() { editingSupplier.value = null; resetForm(); loadOwners(); formDialogVisible.value = true }
async function openEdit(row: SrmSupplier) {
  const response = await getSupplier(row.id)
  const detail = response.data.data
  maskedSupplierFields.value = new Set()
  if (detail.phone?.includes('*')) maskedSupplierFields.value.add('phone')
  if (detail.email?.includes('*')) maskedSupplierFields.value.add('email')
  editingSupplier.value = detail
  Object.assign(form, {
    name: detail.name, supplierType: detail.supplierType || '', industryCode: detail.industryCode || '',
    creditCode: detail.creditCode || '', website: detail.website || '',
    phone: maskedSupplierFields.value.has('phone') ? '' : detail.phone || '',
    email: maskedSupplierFields.value.has('email') ? '' : detail.email || '',
    region: detail.region || '',
    address: detail.address || '', categoryCode: detail.categoryCode || '', ownerUserId: detail.ownerUserId,
    version: detail.version,
  })
  ownerOptions.value = detail.ownerUserId ? [{
    userId: detail.ownerUserId,
    username: detail.ownerName || `用户 #${detail.ownerUserId}`,
    nickname: detail.ownerName,
    unitName: detail.ownerUnitName,
  }] : []
  loadOwners()
  formDialogVisible.value = true
}

function openOverview(row: SrmSupplier) {
  selectedSupplier.value = row
  overviewDrawerVisible.value = true
}

function openResources(row: SrmSupplier) {
  selectedSupplier.value = row
  resourcesDrawerVisible.value = true
}

async function saveForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const request = { ...form }
  if (maskedSupplierFields.value.has('phone') && !request.phone) request.phone = undefined
  if (maskedSupplierFields.value.has('email') && !request.email) request.email = undefined
  if (editingSupplier.value) {
    const supplierId = editingSupplier.value.id
    const originalOwnerUserId = editingSupplier.value.ownerUserId
    const { ownerUserId: targetOwnerUserId, ...supplierUpdateRequest } = request

    const profileFields = [
      'name', 'supplierType', 'industryCode', 'creditCode', 'website',
      'phone', 'email', 'region', 'address', 'categoryCode',
    ] as const
    const profileChanged = profileFields.some((field) => {
      const nextValue = supplierUpdateRequest[field]
      if (nextValue === undefined && maskedSupplierFields.value.has(field as 'phone' | 'email')) return false
      return (nextValue ?? '') !== (editingSupplier.value?.[field] ?? '')
    })
    const ownerChanged = targetOwnerUserId !== originalOwnerUserId
    let latestVersion = editingSupplier.value.version

    if (profileChanged) {
      const response = await updateSupplier(supplierId, { ...supplierUpdateRequest, version: latestVersion })
      latestVersion = response.data.data.version
      editingSupplier.value = response.data.data
      form.version = latestVersion
    }

    if (ownerChanged) {
      if (!targetOwnerUserId) {
        ElMessage.warning('请选择新的供应商负责人')
        return
      }
      const response = await transferSupplierOwner(supplierId, {
        ownerUserId: targetOwnerUserId,
        version: latestVersion,
      })
      editingSupplier.value = response.data.data
      form.version = response.data.data.version
    }

    if (!profileChanged && !ownerChanged) {
      ElMessage.info('未检测到需要保存的变更')
      return
    }
  } else {
    await createSupplier(request)
  }
  ElMessage.success(t('common.success')); formDialogVisible.value = false; loadData()
}

async function handleDelete(row: SrmSupplier) {
  try {
    await ElMessageBox.confirm(`确认删除供应商"${row.name}"？`, '删除确认', { type: 'warning' })
    await deleteSupplier(row.id, row.version); ElMessage.success(t('common.success')); loadData()
  } catch { /* 用户取消 */ }
}

const progressDialogRef = ref<InstanceType<typeof ProcessProgressDialog>>()

async function handleSubmit(row: SrmSupplier) {
  await ElMessageBox.confirm(`确认提交供应商“${row.name}”重新审批？将启动新一轮审批流程。`, '重新提交')
  await submitSupplier(row.id, row.version); ElMessage.success('已提交审批'); loadData()
}
async function handleWithdraw(row: SrmSupplier) {
  await ElMessageBox.confirm(`确认撤回供应商“${row.name}”的审批流程？`, '撤回审批')
  await withdrawSupplier(row.id, row.version); ElMessage.success('已撤回'); loadData()
}
async function handleCancel(row: SrmSupplier) {
  await ElMessageBox.confirm(`确认取消供应商“${row.name}”的审批流程？`, '取消审批', { type: 'warning' })
  await cancelSupplier(row.id, row.version); ElMessage.success('已取消'); loadData()
}
function openProcessProgress(row: SrmSupplier) {
  if (row.processInstanceId) {
    progressDialogRef.value?.open(row.processInstanceId, '')
  }
}
async function handleSuspend(row: SrmSupplier) {
  await ElMessageBox.confirm(`确认冻结供应商"${row.name}"？`, '冻结确认', { type: 'warning' })
  await suspendSupplier(row.id, row.version); ElMessage.success('已冻结'); loadData()
}
async function handleResume(row: SrmSupplier) {
  await ElMessageBox.confirm(`确认恢复供应商"${row.name}"？`, '恢复确认')
  await resumeSupplier(row.id, row.version); ElMessage.success('已恢复'); loadData()
}
async function handleRestore(row: SrmSupplier) {
  await ElMessageBox.confirm(`确认将供应商"${row.name}"移出黑名单？`, '移出黑名单')
  await restoreSupplier(row.id, row.version); ElMessage.success('已移出黑名单'); loadData()
}
async function handleBlacklist(row: SrmSupplier) {
  await ElMessageBox.confirm(`确认将供应商"${row.name}"加入黑名单？`, '加入黑名单', { type: 'warning' })
  await blacklistSupplier(row.id, row.version); ElMessage.success('已加入黑名单'); loadData()
}
async function handleEliminate(row: SrmSupplier) {
  await ElMessageBox.confirm(`确认淘汰供应商"${row.name}"？此操作不可撤销。`, '淘汰确认', { type: 'warning' })
  await eliminateSupplier(row.id, row.version); ElMessage.success('已淘汰'); loadData()
}

onMounted(() => {
  loadData()
  loadOwners()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('common.srmSuppliers') }}</span>
          <el-button v-permission="'srm:supplier:create'" type="primary" @click="openCreate">新建供应商</el-button>
        </div>
      </template>
      <el-form inline class="search-form">
        <el-form-item label="关键词">
          <el-input v-model="query.name" clearable placeholder="名称/编号" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 130px">
            <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="query.levelCode" clearable style="width: 130px">
            <el-option v-for="(label, value) in levelLabel" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="品类">
          <el-select v-model="query.categoryCode" clearable filterable :loading="categoryLoading.value" placeholder="全部品类" style="width: 150px">
            <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-permission="'srm:owner:list'" label="负责人">
          <el-select
            v-model="query.ownerUserId"
            filterable
            remote
            clearable
            :remote-method="loadOwners"
            :loading="ownerLoading"
            placeholder="全部负责人"
            style="width: 180px"
          >
            <el-option
              v-for="owner in ownerOptions"
              :key="owner.userId"
              :label="owner.nickname || owner.username"
              :value="owner.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="supplierNo" label="供应商编号" width="155" />
        <el-table-column prop="name" label="供应商名称" min-width="200" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ supplierTypeLabel[row.supplierType] || row.supplierType }}</template>
        </el-table-column>
        <el-table-column label="品类" width="120">
          <template #default="{ row }">{{ categoryLabel[row.categoryCode] || row.categoryCode }}</template>
        </el-table-column>
        <el-table-column label="等级" width="110">
          <template #default="{ row }">{{ levelLabel[row.levelCode] || row.levelCode }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status as SupplierStatus]">{{ statusLabel[row.status as SupplierStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="110">
          <template #default="{ row }">{{ row.ownerName || (row.ownerUserId ? `用户 #${row.ownerUserId}` : '未分配') }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" fixed="right" width="360">
          <template #default="{ row }">
            <el-button size="small" @click="openOverview(row)">360 视图</el-button>
            <el-button v-if="row.status !== 'ELIMINATED'" v-permission="'srm:supplier:update'" size="small" @click="openEdit(row)">编辑</el-button>
            <el-dropdown trigger="click">
              <el-button size="small">更多<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-if="permissionStore.hasPermission('srm:contact:list') || permissionStore.hasPermission('srm:qualification:list') || permissionStore.hasPermission('srm:bank-account:list')"
                    @click="openResources(row)"
                  >
                    维护关联资料
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'REJECTED'" v-permission="'srm:supplier:create'" @click="handleSubmit(row)">重新提交审核</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVING'" v-permission="'srm:supplier:withdraw'" @click="handleWithdraw(row)">撤回审批</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVING'" v-permission="'srm:supplier:cancel'" @click="handleCancel(row)">取消审批</el-dropdown-item>
                  <el-dropdown-item v-if="row.processInstanceId" @click="openProcessProgress(row)">审批进度</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVED'" v-permission="'srm:supplier:suspend'" @click="handleSuspend(row)">冻结</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'SUSPENDED'" v-permission="'srm:supplier:resume'" @click="handleResume(row)">恢复</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'BLACKLISTED'" v-permission="'srm:supplier:restore'" @click="handleRestore(row)">移出黑名单</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVED'" v-permission="'srm:supplier:blacklist'" @click="handleBlacklist(row)">加入黑名单</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVED' || row.status === 'SUSPENDED'" v-permission="'srm:supplier:eliminate'" @click="handleEliminate(row)">淘汰</el-dropdown-item>
                  <el-dropdown-item v-permission="'srm:supplier:delete'" divided @click="handleDelete(row)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :total="total"
        :page-sizes="[5, 10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadData"
        @size-change="currentPage = 1; loadData()"
      />
    </el-card>

    <el-dialog v-model="formDialogVisible" :title="editingSupplier ? '编辑供应商' : '新建供应商'" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="供应商名称" prop="name"><el-input v-model="form.name" maxlength="200" show-word-limit /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="供应商类型" prop="supplierType">
              <el-select v-model="form.supplierType" style="width: 100%">
                <el-option v-for="item in supplierTypes" :key="item" :label="supplierTypeLabel[item] || item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="行业" prop="industryCode"><el-input v-model="form.industryCode" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="品类" prop="categoryCode">
              <el-select v-model="form.categoryCode" clearable filterable :loading="categoryLoading.value" style="width: 100%">
                <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="信用代码" prop="creditCode"><el-input v-model="form.creditCode" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="网站" prop="website"><el-input v-model="form.website" maxlength="300" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话" prop="phone"><el-input v-model="form.phone" maxlength="32" :placeholder="maskedSupplierFields.has('phone') ? '已脱敏，留空保持不变' : ''" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" maxlength="200" :placeholder="maskedSupplierFields.has('email') ? '已脱敏，留空保持不变' : ''" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item v-permission="'srm:supplier:transfer'" label="负责人">
              <el-select
                v-model="form.ownerUserId"
                filterable
                remote
                :clearable="!editingSupplier"
                :remote-method="loadOwners"
                :loading="ownerLoading"
                placeholder="默认分配给当前用户"
                style="width: 100%"
              >
                <el-option
                  v-for="owner in ownerOptions"
                  :key="owner.userId"
                  :label="`${owner.nickname || owner.username}${owner.unitName ? ` · ${owner.unitName}` : ''}`"
                  :value="owner.userId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="地区" prop="region"><el-input v-model="form.region" maxlength="100" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="地址" prop="address"><el-input v-model="form.address" maxlength="500" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button v-permission="editingSupplier ? 'srm:supplier:update' : 'srm:supplier:create'" type="primary" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="overviewDrawerVisible" :title="`${selectedSupplier?.name || '供应商'} · 360 视图`" size="900px" destroy-on-close>
      <SupplierOverview :supplier-id="selectedSupplier?.id" />
    </el-drawer>

    <SupplierResourcesDrawer
      v-model="resourcesDrawerVisible"
      :supplier-id="selectedSupplier?.id"
      :supplier-name="selectedSupplier?.name"
    />

    <ProcessProgressDialog ref="progressDialogRef" />
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.search-form { margin-bottom: 4px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
</style>
