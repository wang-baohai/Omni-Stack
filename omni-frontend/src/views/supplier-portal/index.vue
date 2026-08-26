<script setup lang="ts">
/** 供应商门户工作台，覆盖邀请入驻、入驻进度、企业资料和绩效评估。 */
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { usePermissionStore } from '@/stores/permission'
import { getRolesFromToken, getTenantIdFromToken } from '@/utils/jwt'
import LanguageSelector from '@/components/LanguageSelector.vue'
import { clearAuthenticatedSession } from '@/router'
import {
  enrollSupplier,
  getPortalEnrollment,
  getPortalProfile,
  getQuotationInvitation,
  listPortalEvaluations,
  listQuotationInvitations,
  retryPortalEnrollment,
  submitQuotation,
  submitPortalProfile,
  updatePortalProfile,
  type EnrollRequest,
  type EnrollmentState,
  type PortalEvaluation,
  type PortalProfile,
  type QuotationInvitationDetail,
  type QuotationInvitationSummary,
  type SubmitQuotationRequest,
  type UpdatePortalProfileRequest,
} from '@/api/srm-portal'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const permissionStore = usePermissionStore()

const canEnroll = computed(() => permissionStore.hasPermission('srm:portal:enroll'))
const canViewProfile = computed(() => permissionStore.hasPermission('srm:portal:profile'))
const canViewEvaluation = computed(() => permissionStore.hasPermission('srm:portal:evaluation'))
const hasSupplierRole = computed(() => {
  const roles = getRolesFromToken(userStore.token)
  return roles.includes('SUPPLIER') || roles.includes('ROLE_SUPPLIER')
})
const canViewQuotation = computed(() => hasSupplierRole.value
  && permissionStore.hasPermission('srm:portal:quotation'))
const accessDenied = computed(() => !canEnroll.value && !canViewProfile.value
  && !canViewEvaluation.value && !canViewQuotation.value)

const portalLoading = ref(false)
const enrollment = ref<EnrollmentState | null>(null)
let enrollmentPollTimer: ReturnType<typeof setInterval> | undefined
const enrollFormRef = ref<FormInstance>()
const enrollForm = reactive<EnrollRequest>({
  requestId: '',
  inviteToken: '',
  name: '',
  creditCode: '',
  supplierType: 'OTHER',
  industryCode: '',
  website: '',
  phone: '',
  email: '',
  region: '',
  address: '',
})
const enrollRules: FormRules<EnrollRequest> = {
  inviteToken: [
    { required: true, message: '请输入租户管理员提供的邀请令牌', trigger: 'blur' },
    { max: 256, message: '邀请令牌不能超过 256 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入企业名称', trigger: 'blur' },
    { max: 200, message: '企业名称不能超过 200 个字符', trigger: 'blur' },
  ],
  creditCode: [
    { required: true, message: '请输入统一社会信用代码', trigger: 'blur' },
    { max: 50, message: '统一社会信用代码不能超过 50 个字符', trigger: 'blur' },
  ],
  supplierType: [{ required: true, message: '请选择供应商类型', trigger: 'change' }],
  industryCode: [{ max: 50, message: '行业代码不能超过 50 个字符', trigger: 'blur' }],
  website: [{ max: 300, message: '网站地址不能超过 300 个字符', trigger: 'blur' }],
  phone: [{ max: 32, message: '联系电话不能超过 32 个字符', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 200, message: '邮箱不能超过 200 个字符', trigger: 'blur' },
  ],
  region: [{ max: 100, message: '地区不能超过 100 个字符', trigger: 'blur' }],
  address: [{ max: 500, message: '地址不能超过 500 个字符', trigger: 'blur' }],
}

const enrollmentStatusMeta: Record<string, { title: string; description: string; icon: 'success' | 'warning' | 'error' | 'info' }> = {
  PENDING_ROLE_ASSIGN: {
    title: '入驻申请已提交',
    description: '系统正在分配供应商门户角色，请稍后刷新进度。',
    icon: 'warning',
  },
  ROLE_ASSIGN_FAILED: {
    title: '门户角色分配失败',
    description: '企业资料已保留，可以直接重试，无需重复提交入驻申请。',
    icon: 'error',
  },
  COMPLETED: {
    title: '门户授权已完成',
    description: '请重新登录以刷新访问令牌，之后即可维护企业资料。',
    icon: 'success',
  },
  CANCELLED: {
    title: '入驻申请已取消',
    description: '如需重新入驻，请联系租户管理员。',
    icon: 'info',
  },
}

function requestStorageKey() {
  const tenantId = getTenantIdFromToken(userStore.token) || 'unknown'
  return `srm_enroll_request_id:${tenantId}:${userStore.username}`
}

function newRequestId() {
  if (typeof crypto !== 'undefined') {
    if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
    const bytes = new Uint32Array(4)
    crypto.getRandomValues(bytes)
    return Array.from(bytes, (value) => value.toString(16).padStart(8, '0')).join('-')
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function getOrCreateRequestId() {
  const key = requestStorageKey()
  const existing = localStorage.getItem(key)
  if (existing) return existing
  const requestId = newRequestId()
  localStorage.setItem(key, requestId)
  return requestId
}

async function loadEnrollment() {
  const response = await getPortalEnrollment()
  enrollment.value = response.data.data
}

async function submitEnrollment() {
  const valid = await enrollFormRef.value?.validate().catch(() => false)
  if (!valid) return
  enrollForm.requestId = getOrCreateRequestId()
  portalLoading.value = true
  try {
    const response = await enrollSupplier({ ...enrollForm })
    enrollment.value = response.data.data
    ElMessage.success('入驻申请已提交')
  } finally {
    portalLoading.value = false
  }
}

async function refreshEnrollment() {
  portalLoading.value = true
  try {
    await loadEnrollment()
  } finally {
    portalLoading.value = false
  }
}

async function retryEnrollment() {
  portalLoading.value = true
  try {
    const response = await retryPortalEnrollment()
    enrollment.value = response.data.data
    ElMessage.success('重试请求已提交')
  } finally {
    portalLoading.value = false
  }
}

watch(() => enrollment.value?.status, (status) => {
  if (enrollmentPollTimer) clearInterval(enrollmentPollTimer)
  enrollmentPollTimer = undefined
  if (status === 'PENDING_ROLE_ASSIGN') {
    enrollmentPollTimer = setInterval(async () => {
      try {
        await loadEnrollment()
      } catch { /* 请求错误由统一拦截器提示，下一轮继续尝试。 */ }
    }, 5000)
  }
})

const profileLoading = ref(false)
const profile = ref<PortalProfile | null>(null)
const profileFormRef = ref<FormInstance>()
const profileForm = reactive<UpdatePortalProfileRequest>({ version: 0 })
const profileSubmitting = ref(false)
const profileRules: FormRules<UpdatePortalProfileRequest> = {
  name: [
    { required: true, message: '请输入企业名称', trigger: 'blur' },
    { max: 200, message: '企业名称不能超过 200 个字符', trigger: 'blur' },
  ],
  website: [{ max: 300, message: '网站地址不能超过 300 个字符', trigger: 'blur' }],
  phone: [{ max: 32, message: '联系电话不能超过 32 个字符', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 200, message: '邮箱不能超过 200 个字符', trigger: 'blur' },
  ],
  region: [{ max: 100, message: '地区不能超过 100 个字符', trigger: 'blur' }],
  address: [{ max: 500, message: '地址不能超过 500 个字符', trigger: 'blur' }],
}

const supplierTypeLabel: Record<string, string> = {
  MANUFACTURER: '制造商', DISTRIBUTOR: '分销商', SERVICE: '服务商', OTHER: '其他',
}
const supplierStatusLabel: Record<string, string> = {
  REGISTERING: '登记中',
  REGISTERING_FAILED: '入驻失败',
  PENDING_REVIEW: '待审核',
  APPROVING: '审批中',
  REJECTED: '已驳回',
  APPROVED: '已通过',
  SUSPENDED: '已冻结',
  BLACKLISTED: '黑名单',
  ELIMINATED: '已淘汰',
}

async function loadProfile() {
  profileLoading.value = true
  try {
    const response = await getPortalProfile()
    profile.value = response.data.data
    Object.assign(profileForm, {
      name: profile.value.name,
      website: profile.value.website || '',
      phone: profile.value.phone || '',
      email: profile.value.email || '',
      region: profile.value.region || '',
      address: profile.value.address || '',
      version: profile.value.version,
    })
  } finally {
    profileLoading.value = false
  }
}

async function saveProfile() {
  if (!profile.value) return
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  profileSubmitting.value = true
  try {
    await updatePortalProfile({ ...profileForm })
    ElMessage.success(t('common.success'))
    await loadProfile()
  } finally {
    profileSubmitting.value = false
  }
}

/** 保存驳回后的修订资料，并以最新版本重新提交审核。 */
async function saveAndResubmitProfile() {
  if (!profile.value || profile.value.status !== 'REJECTED') return
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  profileSubmitting.value = true
  let profileUpdated = false
  try {
    const updated = await updatePortalProfile({ ...profileForm })
    profileUpdated = true
    await submitPortalProfile(updated.data.data.version)
    ElMessage.success('企业资料已重新提交审核')
  } finally {
    if (profileUpdated) {
      try {
        await loadProfile()
      } catch { /* 资料已写入，刷新失败由统一拦截器提示。 */ }
    }
    profileSubmitting.value = false
  }
}

const evalLoading = ref(false)
const evalList = ref<PortalEvaluation[]>([])
const evalTotal = ref(0)
const evalPage = ref(1)
const evalPageSize = ref(10)
const evalStatusLabel: Record<string, string> = {
  COMPLETED: '已完成', PENDING: '待评估', IN_PROGRESS: '评估中',
}

function scoreType(score: number): 'success' | 'warning' | 'danger' {
  if (score >= 90) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

async function loadEvaluations() {
  if (!canViewEvaluation.value) return
  evalLoading.value = true
  try {
    const response = await listPortalEvaluations({ page: evalPage.value, size: Math.min(evalPageSize.value, 100) })
    evalList.value = response.data.data.records
    evalTotal.value = response.data.data.total
  } finally {
    evalLoading.value = false
  }
}

function handleEvalPageChange(page: number) {
  evalPage.value = page
  loadEvaluations()
}

const quotationLoading = ref(false)
const quotationSubmitting = ref(false)
const quotationRequestLocked = ref(false)
const quotationDialogVisible = ref(false)
const quotationInvitations = ref<QuotationInvitationSummary[]>([])
const quotationLoaded = ref(false)
const quotationError = ref('')
const activeTab = ref('')
const quotationDetail = ref<QuotationInvitationDetail | null>(null)
const quotationForm = reactive<SubmitQuotationRequest>({
  requestId: '',
  rfqId: 0,
  version: 0,
  validUntil: '',
  lines: [],
})

const POSITIVE_PRICE_PATTERN = /^(?:0|[1-9]\d{0,12})(?:\.\d{1,6})?$/

function parseDateTime(value: string): number {
  return Date.parse(value.replace(' ', 'T'))
}

function isQuotationOpen(status: string, invitationStatus: string, deadline: string): boolean {
  return status === 'SENT'
    && ['INVITED', 'QUOTED'].includes(invitationStatus)
    && parseDateTime(deadline) > Date.now()
}

function canEditInvitation(invitation: QuotationInvitationSummary): boolean {
  return isQuotationOpen(invitation.status, invitation.invitationStatus, invitation.quotationDeadline)
}

const quotationEditable = computed(() => {
  const detail = quotationDetail.value
  return detail !== null
    && isQuotationOpen(detail.status, detail.invitationStatus, detail.quotationDeadline)
})

function formatDecimal(value: string | null | undefined): string {
  if (!value) return '0'
  const [integer, fraction] = String(value).split('.')
  if (!fraction) return integer
  const trimmedFraction = fraction.replace(/0+$/, '')
  return trimmedFraction ? `${integer}.${trimmedFraction}` : integer
}

async function loadQuotationInvitations() {
  if (!canViewQuotation.value) return
  quotationLoading.value = true
  quotationError.value = ''
  try {
    const response = await listQuotationInvitations()
    quotationInvitations.value = response.data.data
    quotationLoaded.value = true
  } catch (error: unknown) {
    const response = (error as { response?: { headers?: Record<string, string> } }).response
    const traceId = response?.headers?.['x-trace-id']
    quotationError.value = traceId
      ? `${t('portal.quotation.refreshFailed')}（${traceId}）`
      : t('portal.quotation.refreshFailed')
  } finally {
    quotationLoading.value = false
  }
}

async function openQuotation(rfqId: number) {
  quotationLoading.value = true
  try {
    const response = await getQuotationInvitation(rfqId)
    const detail = response.data.data
    quotationDetail.value = detail
    quotationRequestLocked.value = false
    const quotedLines = new Map(
      (detail.currentQuotation?.lines || []).map((line) => [line.rfqLineId, line]),
    )
    Object.assign(quotationForm, {
      requestId: newRequestId(),
      rfqId: detail.rfqId,
      version: detail.currentQuotation?.version || 0,
      validUntil: detail.currentQuotation?.validUntil || detail.quotationDeadline,
      lines: detail.lines.map((line) => ({
        rfqLineId: line.rfqLineId,
        unitPrice: String(quotedLines.get(line.rfqLineId)?.unitPrice || ''),
        deliveryDays: quotedLines.get(line.rfqLineId)?.deliveryDays || 0,
        remark: quotedLines.get(line.rfqLineId)?.remark || '',
      })),
    })
    quotationDialogVisible.value = true
  } finally {
    quotationLoading.value = false
  }
}

async function resolveQuotationRequest() {
  if (!quotationForm.rfqId) return
  await openQuotation(quotationForm.rfqId)
  ElMessage.info(t('portal.quotation.requestResolved'))
}

async function saveQuotation() {
  if (!quotationDetail.value || !quotationForm.validUntil) {
    ElMessage.warning(t('portal.quotation.validUntilRequired'))
    return
  }
  if (!quotationEditable.value) {
    ElMessage.warning(t('portal.quotation.closedWarning'))
    return
  }
  if (quotationForm.lines.some((line) => {
    const price = String(line.unitPrice).trim()
    return !POSITIVE_PRICE_PATTERN.test(price) || /^0(?:\.0+)?$/.test(price)
  })) {
    ElMessage.warning(t('portal.quotation.priceInvalid'))
    return
  }
  if (quotationForm.lines.some((line) => !Number.isInteger(line.deliveryDays)
    || line.deliveryDays < 0 || line.deliveryDays > 3650)) {
    ElMessage.warning(t('portal.quotation.deliveryDaysInvalid'))
    return
  }
  const validUntil = parseDateTime(quotationForm.validUntil)
  const deadline = parseDateTime(quotationDetail.value.quotationDeadline)
  if (!Number.isFinite(validUntil) || validUntil <= Date.now() || validUntil < deadline) {
    ElMessage.warning(t('portal.quotation.validUntilInvalid'))
    return
  }
  quotationRequestLocked.value = true
  quotationSubmitting.value = true
  try {
    await submitQuotation({
      ...quotationForm,
      lines: quotationForm.lines.map((line) => ({ ...line })),
    })
  } catch {
    ElMessage.warning(t('portal.quotation.uncertainResult'))
    return
  } finally {
    quotationSubmitting.value = false
  }
  quotationRequestLocked.value = false
  ElMessage.success(t('portal.quotation.submitSuccess'))
  quotationDialogVisible.value = false
  try {
    await loadQuotationInvitations()
  } catch {
    ElMessage.warning(t('portal.quotation.refreshFailed'))
  }
}

async function initializePortal() {
  permissionStore.initFromToken()
  if (canViewProfile.value) {
    activeTab.value = 'profile'
    await loadProfile().catch(() => undefined)
  } else if (canViewQuotation.value) {
    activeTab.value = 'quotation'
    await loadQuotationInvitations()
  } else if (canViewEvaluation.value) {
    activeTab.value = 'evaluation'
    await loadEvaluations().catch(() => undefined)
  } else if (canEnroll.value) {
    await refreshEnrollment()
  }
}

/** 首次进入 Tab 时按需加载，避免无关模块故障干扰当前页面。 */
async function handleTabChange(name: string | number) {
  if (name === 'quotation' && !quotationLoaded.value) await loadQuotationInvitations()
  if (name === 'evaluation' && evalList.value.length === 0) await loadEvaluations().catch(() => undefined)
  if (name === 'profile' && !profile.value) await loadProfile().catch(() => undefined)
}

function handleLogout() {
  clearAuthenticatedSession()
  router.push('/portal-login')
}

function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}


onMounted(initializePortal)
onUnmounted(() => {
  if (enrollmentPollTimer) clearInterval(enrollmentPollTimer)
})
</script>

<template>
  <div class="portal-page">
    <header class="portal-header">
      <div class="portal-header-left">
        <span class="portal-logo gradient-text">{{ t('common.appName') }}</span>
        <span class="portal-badge">{{ t('common.supplierPortal') }}</span>
      </div>
      <div class="portal-header-right">
        <LanguageSelector />
        <el-button text :title="t('theme.toggle')" @click="toggleTheme">
          <el-icon><Moon v-if="appStore.theme === 'dark'" /><Sunny v-else /></el-icon>
        </el-button>
        <el-dropdown>
          <span class="portal-user-info">
            <el-avatar :size="32" class="portal-avatar"><el-icon :size="18"><User /></el-icon></el-avatar>
            <span class="portal-username">{{ userStore.username }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/')">{{ t('common.home') }}</el-dropdown-item>
              <el-dropdown-item @click="handleLogout">{{ t('common.logout') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="portal-main">
      <el-result
        v-if="accessDenied"
        icon="error"
        title="无供应商门户访问权限"
        sub-title="当前账号既没有入驻权限，也没有供应商门户权限，请联系租户管理员。"
      >
        <template #extra><el-button @click="router.push('/')">返回首页</el-button></template>
      </el-result>

      <el-card
        v-else-if="canEnroll && !canViewProfile && !canViewEvaluation && !canViewQuotation"
        v-loading="portalLoading"
        shadow="never"
        class="enrollment-card"
      >
        <template v-if="enrollment">
          <el-result
            :icon="enrollmentStatusMeta[enrollment.status]?.icon || 'info'"
            :title="enrollmentStatusMeta[enrollment.status]?.title || enrollment.status"
            :sub-title="enrollmentStatusMeta[enrollment.status]?.description"
          >
            <template #extra>
              <el-space wrap>
                <el-button v-if="enrollment.status === 'ROLE_ASSIGN_FAILED'" v-permission="'srm:portal:enroll'" type="primary" @click="retryEnrollment">重试角色分配</el-button>
                <el-button v-if="enrollment.status === 'COMPLETED'" type="primary" @click="handleLogout">重新登录</el-button>
                <el-button @click="refreshEnrollment">刷新进度</el-button>
              </el-space>
            </template>
          </el-result>
          <el-descriptions :column="2" border class="enrollment-detail">
            <el-descriptions-item label="申请编号">{{ enrollment.requestId }}</el-descriptions-item>
            <el-descriptions-item label="供应商 ID">{{ enrollment.supplierId }}</el-descriptions-item>
            <el-descriptions-item label="申请状态">{{ enrollment.status }}</el-descriptions-item>
            <el-descriptions-item v-if="enrollment.lastErrorCode" label="失败代码" :span="2">{{ enrollment.lastErrorCode }}</el-descriptions-item>
            <el-descriptions-item v-if="enrollment.nextRetryTime" label="下次重试时间">{{ enrollment.nextRetryTime }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <template v-else>
          <div class="enrollment-heading">
            <h2>供应商邀请入驻</h2>
            <p>账号注册仅创建登录身份。请填写企业资料并提交管理员提供的邀请令牌，完成供应商入驻。</p>
          </div>
          <el-alert title="租户和当前用户信息取自登录令牌，无需也不能手工填写。" type="info" :closable="false" show-icon />
          <el-form ref="enrollFormRef" :model="enrollForm" :rules="enrollRules" label-width="120px" class="enrollment-form">
            <el-row :gutter="20">
              <el-col :span="24"><el-form-item label="邀请令牌" prop="inviteToken"><el-input v-model="enrollForm.inviteToken" type="password" maxlength="256" show-password placeholder="请输入邀请令牌" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="企业名称" prop="name"><el-input v-model="enrollForm.name" maxlength="200" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="统一信用代码" prop="creditCode"><el-input v-model="enrollForm.creditCode" maxlength="50" /></el-form-item></el-col>
              <el-col :span="12">
                <el-form-item label="供应商类型" prop="supplierType">
                  <el-select v-model="enrollForm.supplierType" style="width: 100%">
                    <el-option v-for="(label, value) in supplierTypeLabel" :key="value" :label="label" :value="value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12"><el-form-item label="行业代码" prop="industryCode"><el-input v-model="enrollForm.industryCode" maxlength="50" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="联系电话" prop="phone"><el-input v-model="enrollForm.phone" maxlength="32" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="联系邮箱" prop="email"><el-input v-model="enrollForm.email" maxlength="200" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="网站" prop="website"><el-input v-model="enrollForm.website" maxlength="300" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="地区" prop="region"><el-input v-model="enrollForm.region" maxlength="100" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="地址" prop="address"><el-input v-model="enrollForm.address" maxlength="500" show-word-limit /></el-form-item></el-col>
            </el-row>
            <el-form-item>
              <el-button v-permission="'srm:portal:enroll'" type="primary" :loading="portalLoading" @click="submitEnrollment">提交入驻申请</el-button>
            </el-form-item>
          </el-form>
        </template>
      </el-card>

      <el-tabs v-else v-model="activeTab" type="border-card" @tab-change="handleTabChange">
        <el-tab-pane v-if="canViewProfile" name="profile" :label="t('common.srmPortalProfile')">
          <div v-loading="profileLoading" class="tab-content">
            <template v-if="profile">
              <el-descriptions :column="2" border>
                <el-descriptions-item :label="t('portal.supplierNo')">{{ profile.supplierNo }}</el-descriptions-item>
                <el-descriptions-item :label="t('portal.supplierType')">{{ supplierTypeLabel[profile.supplierType || ''] || profile.supplierType }}</el-descriptions-item>
                <el-descriptions-item :label="t('portal.creditCode')">{{ profile.creditCode }}</el-descriptions-item>
                <el-descriptions-item :label="t('portal.companyStatus')">
                  <el-tag :type="profile.status === 'APPROVED' ? 'success' : profile.status === 'REJECTED' ? 'danger' : 'warning'">
                    {{ supplierStatusLabel[profile.status] || profile.status }}
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>
              <el-alert
                v-if="profile.status === 'REJECTED'"
                title="企业资料已被驳回"
                description="请修订下方企业信息，然后保存并重新提交审核。"
                type="error"
                :closable="false"
                show-icon
                class="profile-status-alert"
              />
              <el-divider content-position="left">{{ t('portal.editSection') }}</el-divider>
              <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="100px">
                <el-row :gutter="16">
                  <el-col :span="12"><el-form-item :label="t('portalRegister.companyName')" prop="name"><el-input v-model="profileForm.name" maxlength="200" show-word-limit /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="网站" prop="website"><el-input v-model="profileForm.website" maxlength="300" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item :label="t('portalRegister.phone')" prop="phone"><el-input v-model="profileForm.phone" maxlength="32" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item :label="t('portalRegister.email')" prop="email"><el-input v-model="profileForm.email" maxlength="200" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="地区" prop="region"><el-input v-model="profileForm.region" maxlength="100" /></el-form-item></el-col>
                  <el-col :span="24"><el-form-item label="地址" prop="address"><el-input v-model="profileForm.address" maxlength="500" show-word-limit /></el-form-item></el-col>
                </el-row>
                <el-form-item>
                  <el-space wrap>
                    <el-button v-permission="'srm:portal:profile'" type="primary" :loading="profileSubmitting" @click="saveProfile">{{ t('common.save') }}</el-button>
                    <el-button
                      v-if="profile.status === 'REJECTED'"
                      v-permission="'srm:portal:profile'"
                      type="warning"
                      :loading="profileSubmitting"
                      @click="saveAndResubmitProfile"
                    >
                      保存并重新提交审核
                    </el-button>
                  </el-space>
                </el-form-item>
              </el-form>
            </template>
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canViewQuotation" name="quotation" :label="t('common.srmPortalQuotation')">
          <div class="tab-content">
            <div class="quotation-toolbar">
              <el-button :loading="quotationLoading" @click="loadQuotationInvitations">
                {{ t('portal.quotation.refresh') }}
              </el-button>
            </div>
            <el-alert
              v-if="quotationError"
              :title="quotationError"
              type="error"
              :closable="false"
              show-icon
              class="quotation-error"
            >
              <template #default>
                <el-button type="danger" plain size="small" @click="loadQuotationInvitations">
                  {{ t('common.retry') }}
                </el-button>
              </template>
            </el-alert>
            <el-table v-if="!quotationError" v-loading="quotationLoading" :data="quotationInvitations" stripe border>
              <el-table-column prop="rfqNo" :label="t('portal.quotation.rfqNo')" min-width="160" />
              <el-table-column prop="title" :label="t('portal.quotation.title')" min-width="220" show-overflow-tooltip />
              <el-table-column prop="quotationDeadline" :label="t('portal.quotation.deadline')" width="170" />
              <el-table-column prop="currencyCode" :label="t('portal.quotation.currency')" width="90" align="center" />
              <el-table-column prop="invitationStatus" :label="t('portal.quotation.invitationStatus')" width="110" align="center">
                <template #default="{ row }"><el-tag size="small">{{ row.invitationStatus }}</el-tag></template>
              </el-table-column>
              <el-table-column :label="t('portal.quotation.currentQuotation')" min-width="150" align="right">
                <template #default="{ row }">
                  <span v-if="row.quotationId">{{ row.currencyCode }} {{ formatDecimal(row.totalAmount) }}</span>
                  <el-tag v-else type="warning" size="small">{{ t('portal.quotation.notQuoted') }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('common.actions')" width="110" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button
                    v-permission="'srm:portal:quotation'"
                    type="primary"
                    link
                    @click="openQuotation(row.rfqId)"
                  >
                    {{ canEditInvitation(row)
                      ? (row.quotationId ? t('portal.quotation.edit') : t('portal.quotation.submit'))
                      : t('common.view') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="!quotationError && !quotationLoading && quotationLoaded && quotationInvitations.length === 0"
              :description="t('portal.quotation.empty')"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canViewEvaluation" name="evaluation" :label="t('common.srmPortalEvaluation')">
          <div class="tab-content">
            <el-table v-loading="evalLoading" :data="evalList" stripe border>
              <el-table-column prop="evaluationPeriod" :label="t('portal.evaluationPeriod')" min-width="150" />
              <el-table-column prop="totalScore" :label="t('portal.totalScore')" width="120" align="center">
                <template #default="{ row }"><el-tag :type="scoreType(row.totalScore)" size="large">{{ row.totalScore }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="evaluationTime" :label="t('portal.evaluationTime')" width="170" />
              <el-table-column prop="status" :label="t('common.status')" width="100" align="center">
                <template #default="{ row }"><el-tag size="small">{{ evalStatusLabel[row.status] || row.status }}</el-tag></template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!evalLoading && evalList.length === 0" :description="t('portal.noEvaluation')" />
            <el-pagination v-model:current-page="evalPage" class="pagination" :page-size="evalPageSize" :total="evalTotal" layout="total, prev, pager, next" @current-change="handleEvalPageChange" />
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-dialog
        v-model="quotationDialogVisible"
        :title="quotationDetail
          ? `${t('portal.quotation.dialogTitle')} - ${quotationDetail.rfqNo}`
          : t('portal.quotation.dialogTitle')"
        width="980px"
        append-to-body
        destroy-on-close
        :show-close="!quotationRequestLocked"
        :close-on-click-modal="!quotationRequestLocked"
        :close-on-press-escape="!quotationRequestLocked"
      >
        <template v-if="quotationDetail">
          <el-alert
            v-if="!quotationEditable"
            :title="t('portal.quotation.readOnly')"
            type="warning"
            :closable="false"
            show-icon
            class="quotation-summary"
          />
          <el-alert
            v-else-if="quotationRequestLocked"
            :title="t('portal.quotation.requestLocked')"
            type="warning"
            :closable="false"
            show-icon
            class="quotation-summary"
          />
          <el-descriptions :column="3" border class="quotation-summary">
            <el-descriptions-item :label="t('portal.quotation.title')" :span="2">{{ quotationDetail.title }}</el-descriptions-item>
            <el-descriptions-item :label="t('portal.quotation.currency')">{{ quotationDetail.currencyCode }}</el-descriptions-item>
            <el-descriptions-item :label="t('portal.quotation.deadline')">{{ quotationDetail.quotationDeadline }}</el-descriptions-item>
            <el-descriptions-item :label="t('portal.quotation.rfqStatus')">{{ quotationDetail.status }}</el-descriptions-item>
            <el-descriptions-item :label="t('portal.quotation.version')">{{ quotationForm.version }}</el-descriptions-item>
          </el-descriptions>
          <el-form label-width="100px" class="quotation-form">
            <el-form-item :label="t('portal.quotation.validUntil')" required>
              <el-date-picker
                v-model="quotationForm.validUntil"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                :placeholder="t('portal.quotation.validUntilPlaceholder')"
                :disabled="!quotationEditable || quotationSubmitting || quotationRequestLocked"
              />
            </el-form-item>
          </el-form>
          <el-table :data="quotationDetail.lines" border>
            <el-table-column prop="materialCode" :label="t('portal.quotation.materialCode')" width="130" />
            <el-table-column prop="materialName" :label="t('portal.quotation.materialName')" min-width="170" />
            <el-table-column :label="t('portal.quotation.quantity')" width="100" align="right">
              <template #default="{ row }">{{ formatDecimal(row.quantity) }}</template>
            </el-table-column>
            <el-table-column prop="unit" :label="t('portal.quotation.unit')" width="80" align="center" />
            <el-table-column :label="t('portal.quotation.unitPrice')" width="180">
              <template #default="{ $index }">
                <el-input
                  v-model="quotationForm.lines[$index].unitPrice"
                  inputmode="decimal"
                  maxlength="20"
                  :disabled="!quotationEditable || quotationSubmitting || quotationRequestLocked"
                  style="width: 150px"
                />
              </template>
            </el-table-column>
            <el-table-column :label="t('portal.quotation.deliveryDays')" width="150">
              <template #default="{ $index }">
                <el-input-number
                  v-model="quotationForm.lines[$index].deliveryDays"
                  :min="0"
                  :max="3650"
                  :precision="0"
                  :disabled="!quotationEditable || quotationSubmitting || quotationRequestLocked"
                  controls-position="right"
                  style="width: 120px"
                />
              </template>
            </el-table-column>
            <el-table-column :label="t('portal.quotation.remark')" min-width="180">
              <template #default="{ $index }">
                <el-input
                  v-model="quotationForm.lines[$index].remark"
                  maxlength="500"
                  :disabled="!quotationEditable || quotationSubmitting || quotationRequestLocked"
                />
              </template>
            </el-table-column>
          </el-table>
        </template>
        <template #footer>
          <el-button
            v-if="quotationRequestLocked && !quotationSubmitting"
            :loading="quotationLoading"
            :disabled="quotationSubmitting"
            @click="resolveQuotationRequest"
          >
            {{ t('portal.quotation.refreshResult') }}
          </el-button>
          <el-button
            :disabled="quotationSubmitting || quotationRequestLocked"
            @click="quotationDialogVisible = false"
          >
            {{ t('common.cancel') }}
          </el-button>
          <el-button
            v-if="quotationEditable"
            v-permission="'srm:portal:quotation'"
            type="primary"
            :loading="quotationSubmitting"
            @click="saveQuotation"
          >
            {{ quotationRequestLocked ? t('portal.quotation.retrySameRequest') : t('portal.quotation.submit') }}
          </el-button>
        </template>
      </el-dialog>
    </main>

    <footer class="portal-footer"><span>&copy; 2026 {{ t('common.appName') }}</span></footer>
  </div>
</template>

<style scoped lang="scss">
.portal-page { display: flex; flex-direction: column; min-height: 100vh; background-color: var(--omni-bg-base); }
.portal-header { display: flex; justify-content: space-between; align-items: center; padding: 0 var(--omni-space-xl); height: 72px; background: var(--omni-bg-glass); backdrop-filter: blur(20px) saturate(180%); border-bottom: 1px solid var(--omni-border-color); position: sticky; top: 0; z-index: 100; }
.portal-header-left, .portal-header-right, .portal-user-info { display: flex; align-items: center; gap: var(--omni-space-sm); }
.portal-logo { font-size: 22px; font-weight: 700; letter-spacing: 0.02em; }
.portal-badge { font-size: 12px; padding: 2px 8px; border-radius: 10px; background: var(--omni-gradient-primary); color: #fff; font-weight: 500; }
.portal-user-info { cursor: pointer; color: var(--omni-text-primary); }
.portal-avatar { background: var(--omni-gradient-primary); }
.portal-username { font-size: 14px; font-weight: 500; }
.portal-main { flex: 1; padding: var(--omni-space-xl); max-width: 1200px; margin: 0 auto; width: 100%; }
.enrollment-card { max-width: 900px; margin: 0 auto; }
.enrollment-heading { text-align: center; margin-bottom: 20px; }
.enrollment-heading h2 { margin-bottom: 8px; }
.enrollment-heading p { color: var(--omni-text-secondary); }
.enrollment-form { margin-top: 24px; }
.enrollment-detail { max-width: 720px; margin: 0 auto 24px; }
.profile-status-alert { margin-top: 16px; }
.tab-content { padding: var(--omni-space-md) 0; }
.quotation-toolbar { display: flex; justify-content: flex-end; margin-bottom: var(--omni-space-md); }
.quotation-summary { margin-bottom: var(--omni-space-md); }
.quotation-form { margin-top: var(--omni-space-md); }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
.portal-footer { text-align: center; padding: var(--omni-space-lg); color: var(--omni-text-tertiary); font-size: 14px; border-top: 1px solid var(--omni-border-color); }
@media (max-width: 768px) {
  .portal-header { height: auto; min-height: 64px; padding: 8px var(--omni-space-md); gap: 8px; }
  .portal-header-left, .portal-header-right { min-width: 0; }
  .portal-logo { font-size: 18px; white-space: nowrap; }
  .portal-badge { display: none; }
  .portal-username { display: none; }
  .portal-main { padding: var(--omni-space-sm); overflow-x: hidden; }
  .portal-main :deep(.el-col-12) { flex: 0 0 100%; max-width: 100%; }
  .portal-main :deep(.el-form-item) { display: block; }
  .portal-main :deep(.el-form-item__label) { width: auto !important; justify-content: flex-start; line-height: 28px; }
  .portal-main :deep(.el-form-item__content) { margin-left: 0 !important; }
  .portal-main :deep(.el-descriptions__label.el-descriptions__cell) { min-width: 104px; white-space: nowrap; }
  .portal-main :deep(.el-tabs__nav-wrap) { overflow-x: auto; }
  .portal-main :deep(.el-tabs__nav-scroll) { overflow: visible; }
  .portal-main :deep(.el-tabs__nav) { white-space: nowrap; }
  .quotation-toolbar { justify-content: stretch; }
  .quotation-toolbar .el-button { width: 100%; }
  .portal-footer { padding: var(--omni-space-md); }
}
</style>
