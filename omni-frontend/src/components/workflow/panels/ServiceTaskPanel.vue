<script setup lang="ts">
/**
 * 抄送节点（ServiceTask）属性面板。
 * 配置：抄送对象（直接指定用户 / 角色+组织）+ 通知渠道。
 * 使用 useBpmnExtension 的 readCcConfig / writeCcConfig。
 */
import { computed, ref, reactive, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import type { BpmnElement } from '@/types/bpmn'
import {
  readCcConfig,
  writeCcConfig,
  type CcConfig,
} from '@/composables/useBpmnExtension'
import {
  listUsers,
  listRoles,
  getUnitOptions,
  type IdentityUserVO,
  type IdentityRoleVO,
  type OrgTreeNodeVO,
} from '@/api/workflow-model'

const { t } = useI18n()

const props = defineProps<{
  element: BpmnElement
  modeler: BpmnModeler | null
}>()

// ===== 选项数据 =====
const users = ref<IdentityUserVO[]>([])
const roles = ref<IdentityRoleVO[]>([])
const unitOptions = ref<OrgTreeNodeVO[]>([])

async function loadOptions() {
  try {
    const [usersRes, rolesRes, unitsRes] = await Promise.all([
      listUsers(),
      listRoles(),
      getUnitOptions(),
    ])
    users.value = usersRes.data.data || []
    roles.value = rolesRes.data.data || []
    unitOptions.value = unitsRes.data.data || []
  } catch {
    // 静默失败
  }
}

onMounted(loadOptions)

// ===== 表单 =====
const form = reactive<{
  recipientType: 'USER_IDS' | 'ROLE_ORG'
  selectedUserIds: number[]
  roleCode: string
  anchorType: string
  selectedUnitId: number | null
  selectedUnitIds: number[]
  channels: string[]
}>({
  recipientType: 'USER_IDS',
  selectedUserIds: [],
  roleCode: '',
  anchorType: 'START_USER_PRIMARY_UNIT',
  selectedUnitId: null,
  selectedUnitIds: [],
  channels: ['SYSTEM'],
})

const anchorTypes = computed(() => [
  { value: 'START_USER_PRIMARY_UNIT', label: t('workflow.initiatorOrganization') },
  { value: 'PARENT', label: t('workflow.initiatorParentOrganization') },
  { value: 'CHILD_UNIT', label: t('workflow.initiatorChildOrganization') },
  { value: 'ABSOLUTE_UNIT', label: t('workflow.specifiedOrganization') },
])

const channelOptions = computed(() => [
  { value: 'SYSTEM', label: t('workflow.systemNotification') },
  { value: 'EMAIL', label: t('workflow.emailNotification') },
  { value: 'SMS', label: t('workflow.smsNotification') },
])

/** 需要用户在树形下拉中选择组织的锚点类型（单选） */
const NEEDS_UNIT_SELECTION_TYPES = new Set(['CHILD_UNIT'])

/** 旧锚点类型 → 新锚点类型的兼容映射 */
const LEGACY_ANCHOR_MAP: Record<string, string> = {
  PARENT_BY_TYPE: 'PARENT',
  PARENT_CHILDREN: 'PARENT',
  CHILD_BY_CODE: 'CHILD_UNIT',
  SIBLING_BY_CODE: 'ABSOLUTE_UNIT',
  SIBLING_UNIT: 'ABSOLUTE_UNIT',
  PARENT_ALL_CHILDREN: 'ABSOLUTE_UNIT',
  DEPT_BY_CODE: 'ABSOLUTE_UNIT',
}

// ===== 读取已有配置 =====
function loadFromElement() {
  const config = readCcConfig(props.element)
  if (config) {
    form.recipientType = config.recipientType || 'USER_IDS'
    form.selectedUserIds = config.userIds || []
    form.roleCode = config.roleCode || ''
    form.channels = config.channels || ['SYSTEM']

    // 锚点类型：优先读 anchorType，回退兼容旧 unitId
    const rawType = config.anchorType || (config.unitId ? 'ABSOLUTE_UNIT' : 'START_USER_PRIMARY_UNIT')
    form.anchorType = LEGACY_ANCHOR_MAP[rawType] ?? rawType

    // 还原 selectedUnitId
    if (NEEDS_UNIT_SELECTION_TYPES.has(form.anchorType)) {
      const unitId = config.anchorParams?.unitId ?? config.unitId ?? null
      form.selectedUnitId = unitId as number | null
    } else {
      form.selectedUnitId = null
    }

    // 还原 ABSOLUTE_UNIT 多选配置
    if (form.anchorType === 'ABSOLUTE_UNIT') {
      const unitIds = config.anchorParams?.unitIds as number[] | null
      const unitId = config.anchorParams?.unitId ?? config.unitId ?? null
      if (unitIds && unitIds.length > 0) {
        form.selectedUnitIds = unitIds
      } else if (unitId != null) {
        form.selectedUnitIds = [unitId as number]
      } else {
        form.selectedUnitIds = []
      }
    } else {
      form.selectedUnitIds = []
    }
  } else {
    form.recipientType = 'USER_IDS'
    form.selectedUserIds = []
    form.roleCode = ''
    form.anchorType = 'START_USER_PRIMARY_UNIT'
    form.selectedUnitId = null
    form.selectedUnitIds = []
    form.channels = ['SYSTEM']
  }
}

watch(() => props.element, () => {
  if (props.element) loadFromElement()
}, { immediate: true })

// ===== 构建锚点参数 =====
function buildAnchorParams(): Record<string, unknown> {
  if (NEEDS_UNIT_SELECTION_TYPES.has(form.anchorType)) {
    return { unitId: form.selectedUnitId }
  }
  if (form.anchorType === 'ABSOLUTE_UNIT') {
    return { unitIds: form.selectedUnitIds.length > 0 ? form.selectedUnitIds : null }
  }
  return {}
}

// ===== 写入配置 =====
function applyConfig() {
  if (!props.modeler || !props.element) return

  const config: CcConfig = {
    serviceType: 'CC',
    recipientType: form.recipientType,
    channels: form.channels,
  }

  if (form.recipientType === 'USER_IDS') {
    config.userIds = form.selectedUserIds
  } else {
    config.roleCode = form.roleCode
    config.anchorType = form.anchorType
    config.anchorParams = buildAnchorParams()
    config.scopeMode = 'SAME_UNIT'
  }

  writeCcConfig(props.modeler, props.element, config)
  ElMessage.success(t('workflow.ccConfigurationUpdated'))
}
</script>

<template>
  <div class="service-task-panel">
    <div class="section-title">{{ t('workflow.ccConfiguration') }}</div>

    <el-form label-width="90px" size="small" @submit.prevent>
      <el-form-item :label="t('workflow.ccRecipients')">
        <el-radio-group v-model="form.recipientType">
          <el-radio value="USER_IDS">{{ t('workflow.specifiedUsers') }}</el-radio>
          <el-radio value="ROLE_ORG">{{ t('workflow.roleAndOrganization') }}</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 直接指定用户 -->
      <el-form-item v-if="form.recipientType === 'USER_IDS'" :label="t('workflow.selectUsers')">
        <el-select
          v-model="form.selectedUserIds"
          multiple
          filterable
          :placeholder="t('workflow.selectUsers')"
          style="width: 100%"
        >
          <el-option
            v-for="user in users"
            :key="user.userId"
            :label="`${user.nickname}（${user.username}）`"
            :value="user.userId"
          />
        </el-select>
      </el-form-item>

      <!-- 角色+组织模式 -->
      <template v-if="form.recipientType === 'ROLE_ORG'">
        <el-form-item :label="t('workflow.role')">
          <el-select v-model="form.roleCode" :placeholder="t('workflow.selectRole')" filterable style="width: 100%">
            <el-option
              v-for="role in roles"
              :key="role.roleCode"
              :label="role.roleName"
              :value="role.roleCode"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('workflow.ccOrganization')">
          <el-select v-model="form.anchorType" style="width: 100%">
            <el-option
              v-for="at in anchorTypes"
              :key="at.value"
              :label="at.label"
              :value="at.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.anchorType === 'CHILD_UNIT'" :label="t('workflow.selectChildOrganization')">
          <el-tree-select
            v-model="form.selectedUnitId"
            :data="unitOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            :placeholder="t('workflow.selectChildOrganization')"
            check-strictly
            filterable
            style="width: 100%"
          />
        </el-form-item>

        <!-- ABSOLUTE_UNIT：多选组织树 -->
        <el-form-item v-if="form.anchorType === 'ABSOLUTE_UNIT'" :label="t('workflow.selectOrganization')">
          <el-tree-select
            v-model="form.selectedUnitIds"
            :data="unitOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            :placeholder="t('workflow.selectOrganizationsPlaceholder')"
            multiple
            check-strictly
            filterable
            style="width: 100%"
          />
        </el-form-item>
      </template>

      <el-form-item :label="t('workflow.notificationChannels')">
        <el-checkbox-group v-model="form.channels">
          <el-checkbox
            v-for="ch in channelOptions"
            :key="ch.value"
            :value="ch.value"
            :label="ch.label"
          />
        </el-checkbox-group>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" size="small" @click="applyConfig">
          {{ t('workflow.applyConfiguration') }}
        </el-button>
      </el-form-item>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="cc-hint"
    >
      <template #title>
        {{ t('workflow.ccHint') }}
      </template>
    </el-alert>
  </div>
</template>

<style scoped>
.service-task-panel {
  margin-top: 4px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}
.cc-hint {
  margin-top: 12px;
}
</style>
