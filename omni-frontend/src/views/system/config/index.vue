<script setup lang="ts">
/**
 * 系统配置页面（D01）。
 * 本期配置分组为“登录保护”：读取/校验/保存本租户登录失败保护策略，展示乐观锁版本与生效时点说明。
 * 权限：读取 system:config:list，保存 system:config:update。后端为唯一权威（失败关闭，不隐藏即安全）。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePermissionStore } from '@/stores/permission'
import {
  getLoginPolicy,
  updateLoginPolicy,
  type AuthLoginPolicy,
} from '@/api/system-config'

const { t } = useI18n()
const permissionStore = usePermissionStore()

/** 内置默认（与后端一致，用于“恢复默认值”） */
const DEFAULT_THRESHOLD = 10
const DEFAULT_WINDOW = 3600

const loading = ref(false)
const saving = ref(false)
/** 首次加载成功前禁提交；后端为唯一权威 */
const loadedOk = ref(false)
/** 读取本租户权威策略视图 */
const policy = ref<AuthLoginPolicy | null>(null)
/** 冲突状态：保留草稿并要求用户显式处理 */
const conflict = ref(false)

/** 是否可更新：来自真实权限（只读用户不可编辑/提交），不得写死 */
const canUpdate = computed(() => permissionStore.hasPermission('system:config:update'))
/** 可提交：具备更新权限且首次加载成功且无未处理冲突 */
const canSubmit = computed(() => canUpdate.value && loadedOk.value && !conflict.value)

/** 草稿模型（用户编辑，与权威版本分离） */
const draft = reactive({
  failureThreshold: DEFAULT_THRESHOLD,
  failureWindowSeconds: DEFAULT_WINDOW,
})

/** 加载策略；失败时保持草稿/已载状态不丢失，仅标记未就绪 */
async function load() {
  loading.value = true
  try {
    const res = await getLoginPolicy()
    const data = res.data.data
    policy.value = data
    // 仅在无未提交草稿时回填权威值；刷新不覆盖用户已修改内容
    if (!conflict.value) {
      draft.failureThreshold = data.failureThreshold
      draft.failureWindowSeconds = data.failureWindowSeconds
    }
    loadedOk.value = true
  } catch {
    loadedOk.value = false
  } finally {
    loading.value = false
  }
}

/** 提交保存（乐观并发：携带 expectedVersion） */
async function submit(threshold: number, windowSeconds: number) {
  if (threshold < 3 || threshold > 10 || !Number.isInteger(threshold)) {
    ElMessage.error(t('systemConfig.thresholdInvalid'))
    return
  }
  if (windowSeconds < 3600 || windowSeconds > 86400 || !Number.isInteger(windowSeconds)) {
    ElMessage.error(t('systemConfig.windowInvalid'))
    return
  }
  saving.value = true
  try {
    const res = await updateLoginPolicy({
      failureThreshold: threshold,
      failureWindowSeconds: windowSeconds,
      expectedVersion: policy.value?.version ?? 0,
    })
    policy.value = res.data.data
    draft.failureThreshold = policy.value.failureThreshold
    draft.failureWindowSeconds = policy.value.failureWindowSeconds
    conflict.value = false
    ElMessage.success(t('systemConfig.saveSuccess'))
  } catch (err) {
    handleError(err)
  } finally {
    saving.value = false
  }
}

/** 错误分类处理：冲突保留草稿、403只读、400校验、网络/服务错误分别提示 */
async function handleError(err: unknown) {
  const e = err as { businessCode?: number; httpStatus?: number; response?: { status?: number } }
  const bizCode = e?.businessCode
  const httpStatus = e?.httpStatus ?? e?.response?.status
  if (bizCode === 409 || httpStatus === 409) {
    // 版本冲突：保留用户输入，仅刷新权威版本供对比，要求显式确认后再提交
    conflict.value = true
    await refreshAuthoritative()
    ElMessage.warning(t('systemConfig.conflictMsg'))
  } else if (bizCode === 403 || httpStatus === 403) {
    ElMessage.error(t('systemConfig.forbiddenMsg'))
  } else if (bizCode === 400 || httpStatus === 400) {
    ElMessage.error(t('systemConfig.validationMsg'))
  } else if (!e?.response && httpStatus === undefined && bizCode === undefined) {
    ElMessage.error(t('systemConfig.networkMsg'))
  } else {
    ElMessage.error(t('systemConfig.serviceErrorMsg'))
  }
}

/** 只刷新权威视图（不动草稿），供冲突对比 */
async function refreshAuthoritative() {
  try {
    const res = await getLoginPolicy()
    policy.value = res.data.data
  } catch {
    // 刷新权威失败不阻断，保留冲突提示
  }
}

/** 用户显式“采用权威值”：丢弃草稿、回填最新权威、解除冲突 */
async function adoptAuthoritative() {
  if (policy.value) {
    draft.failureThreshold = policy.value.failureThreshold
    draft.failureWindowSeconds = policy.value.failureWindowSeconds
  }
  conflict.value = false
  await load()
}

/** 冲突时刷新权威并重试提交 */
async function retryAfterConflict() {
  await submit(draft.failureThreshold, draft.failureWindowSeconds)
}

function handleSave() {
  if (!canSubmit.value) return
  submit(draft.failureThreshold, draft.failureWindowSeconds)
}

async function handleRestoreDefault() {
  if (!canSubmit.value) return
  await ElMessageBox.confirm(t('systemConfig.restoreConfirm'), t('systemConfig.title'), { type: 'warning' })
  submit(DEFAULT_THRESHOLD, DEFAULT_WINDOW)
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>{{ t('systemConfig.title') }}</span>
        </div>
      </template>

      <el-alert type="info" :closable="false" class="group-desc">
        {{ t('systemConfig.groupLoginProtectionDesc') }}
      </el-alert>

      <el-alert
        v-if="conflict"
        type="error"
        :closable="false"
        class="conflict-note"
      >
        {{ t('systemConfig.conflictKeepDraft') }}
        <div class="conflict-actions">
          <el-button size="small" @click="refreshAuthoritative">{{ t('systemConfig.reloadAuthoritative') }}</el-button>
          <el-button size="small" @click="adoptAuthoritative">{{ t('systemConfig.adoptAuthoritative') }}</el-button>
          <el-button
            v-if="canUpdate"
            size="small"
            type="primary"
            :loading="saving"
            @click="retryAfterConflict"
          >
            {{ t('systemConfig.retrySubmit') }}
          </el-button>
        </div>
      </el-alert>

      <el-form label-width="180px" class="policy-form">
        <el-form-item :label="t('systemConfig.failureThreshold')">
          <el-input-number
            v-model="draft.failureThreshold"
            :min="3"
            :max="10"
            :step="1"
            :disabled="!canUpdate"
          />
          <div class="hint">{{ t('systemConfig.failureThresholdHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('systemConfig.failureWindowSeconds')">
          <el-input-number
            v-model="draft.failureWindowSeconds"
            :min="3600"
            :max="86400"
            :step="60"
            :disabled="!canUpdate"
          />
          <div class="hint">{{ t('systemConfig.failureWindowSecondsHint') }}</div>
        </el-form-item>

        <el-form-item :label="t('systemConfig.source')">
          <el-tag :type="policy?.source === 'TENANT_OVERRIDE' ? 'success' : 'info'" size="small">
            {{ policy?.source === 'TENANT_OVERRIDE' ? t('systemConfig.sourceTenantOverride') : t('systemConfig.sourceDefault') }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="t('systemConfig.version')">
          <span>{{ policy?.version ?? 0 }}</span>
        </el-form-item>
        <el-form-item :label="t('systemConfig.updatedBy')">
          <span>{{ policy?.updatedBy || '-' }}</span>
        </el-form-item>
        <el-form-item :label="t('systemConfig.updateTime')">
          <span>{{ policy?.updateTime || '-' }}</span>
        </el-form-item>
      </el-form>

      <el-alert type="warning" :closable="false" class="effective-note">
        {{ t('systemConfig.effectiveNote') }}
      </el-alert>

      <div class="actions">
        <el-button
          v-permission="'system:config:update'"
          type="primary"
          :loading="saving"
          :disabled="!canSubmit"
          @click="handleSave"
        >
          {{ t('systemConfig.save') }}
        </el-button>
        <el-button
          v-permission="'system:config:update'"
          :loading="saving"
          :disabled="!canSubmit"
          @click="handleRestoreDefault"
        >
          {{ t('systemConfig.restoreDefault') }}
        </el-button>
        <el-button @click="load">{{ t('systemConfig.reload') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}
.card-header {
  font-weight: 600;
}
.group-desc {
  margin-bottom: 16px;
}
.policy-form {
  max-width: 640px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
.effective-note {
  margin-top: 8px;
  max-width: 640px;
}
.conflict-note {
  margin-bottom: 16px;
  max-width: 640px;
}
.conflict-actions {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.actions {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

@media (max-width: 600px) {
  .page-container { padding: 4px; }
  /* 用更高权重压过全局 html.dark .el-card .el-card__body 的 20px 内边距，
     使本行原本声明的 12px 真正生效，为窄屏控件多争取可读宽度 */
  .page-container :deep(.el-card .el-card__body) { padding: 12px; }
  .policy-form :deep(.el-form-item) {
    flex-direction: column;
    align-items: flex-start;
  }
  .policy-form :deep(.el-form-item__label) {
    justify-content: flex-start;
    height: auto;
    line-height: 1.5;
    margin-bottom: 6px;
  }
  .policy-form :deep(.el-form-item__content) { width: 100%; }
  .actions :deep(.el-button + .el-button) { margin-left: 0; }

  /* D2 修复：el-input-number 默认 width:150px 且左右各预留 42px 步进按钮留白，
     在窄屏被 flex 压缩后数值可视区仅剩约 7px（10 显示成 1C、3600 显示成 3E）。
     这里让输入框占满可用宽度（min-width:0 解除 flex 最小内容限制、flex-shrink:0 禁止再被压缩），
     并把步进按钮收窄到 24px、对应留白同步收窄，保证数值完整可读且仍可点选。 */
  .policy-form :deep(.el-input-number) {
    width: 100%;
    min-width: 0;
    flex-shrink: 0;
  }
  .policy-form :deep(.el-input-number .el-input-number__decrease),
  .policy-form :deep(.el-input-number .el-input-number__increase) {
    width: 24px;
  }
  .policy-form :deep(.el-input-number .el-input__wrapper) {
    padding-left: 28px;
    padding-right: 28px;
  }
}
</style>
