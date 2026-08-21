<script setup lang="ts">
/**
 * 审批节点（UserTask）属性面板。
 * 配置：角色 + 锚点组织 + 目标组织 + 审批模式 + 兜底策略。
 * 使用 useBpmnExtension 的 readAssignment / writeAssignment 读写 omni:assignment。
 */
import { ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import type { BpmnElement } from '@/types/bpmn'
import { isRecord } from '@/types/schema'
import {
  readAssignment,
  writeAssignment,
  type AssignmentConfig,
} from '@/composables/useBpmnExtension'
import {
  listRoles,
  getUnitOptions,
  resolvePreview,
  listUsers,
  type IdentityRoleVO,
  type OrgTreeNodeVO,
  type CandidateUser,
  type IdentityUserVO,
} from '@/api/workflow-model'
import { useUserStore } from '@/stores/user'
import { getUserIdFromToken } from '@/utils/jwt'

const props = defineProps<{
  element: BpmnElement
  modeler: BpmnModeler | null
}>()

// ===== 选项数据 =====
const roles = ref<IdentityRoleVO[]>([])
const unitOptions = ref<OrgTreeNodeVO[]>([])

async function loadOptions() {
  try {
    const [rolesRes, unitsRes] = await Promise.all([
      listRoles(),
      getUnitOptions(),
    ])
    roles.value = rolesRes.data.data || []
    unitOptions.value = unitsRes.data.data || []
  } catch {
    // 静默失败
  }
}

onMounted(() => {
  loadOptions()
  loadUsers()
})

// ===== 表单 =====
const form = reactive<{
  roleCode: string
  anchorType: string
  selectedUnitId: number | null
  selectedUnitIds: number[]
  fallbackStrategy: string
  approvalMode: string
}>({
  roleCode: '',
  anchorType: 'START_USER_PRIMARY_UNIT',
  selectedUnitId: null,
  selectedUnitIds: [],
  fallbackStrategy: 'ERROR',
  approvalMode: 'ANY',
})

const anchorTypes = [
  { value: 'START_USER_PRIMARY_UNIT', label: '发起人所在组织' },
  { value: 'PARENT', label: '发起人上级组织' },
  { value: 'CHILD_UNIT', label: '发起人下级组织' },
  { value: 'ABSOLUTE_UNIT', label: '指定组织' },
]

const fallbackStrategies = [
  { value: 'ERROR', label: '报错（仅日志）' },
  { value: 'ASSIGN_ADMIN', label: '分配给管理员' },
  { value: 'ESCALATE_PARENT', label: '上报上级' },
]

const approvalModes = [
  { value: 'ANY', label: '任一人审批即可' },
  { value: 'ALL', label: '全员审批（会签）' },
]

// ===== 读取已有配置 =====
/** 加载期间禁止 syncToElement，避免将未完成的表单值写回元素 */
let isLoading = false

/** 需要用户在树形下拉中重新选择组织的锚点类型（单选） */
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

function loadFromElement() {
  isLoading = true
  const config = readAssignment(props.element)
  if (config) {
    form.roleCode = config.roleCode || ''
    form.fallbackStrategy = config.fallbackStrategy || 'ERROR'
    form.approvalMode = config.approvalMode || 'ANY'

    // 旧锚点类型兼容映射
    const rawType = config.anchorType || 'START_USER_PRIMARY_UNIT'
    form.anchorType = LEGACY_ANCHOR_MAP[rawType] ?? rawType

    // 还原 selectedUnitId：仅 CHILD_UNIT 使用单选
    const params = config.anchorParams || {}
    if (NEEDS_UNIT_SELECTION_TYPES.has(form.anchorType)) {
      form.selectedUnitId = (params.unitId as number) ?? (params.absoluteUnitId as number) ?? null
    } else {
      form.selectedUnitId = null
    }

    // 还原 ABSOLUTE_UNIT 多选配置
    if (form.anchorType === 'ABSOLUTE_UNIT') {
      const unitIds = params.unitIds as number[] | null
      const unitId = params.unitId as number | null
      if (unitIds && unitIds.length > 0) {
        form.selectedUnitIds = unitIds
      } else if (unitId != null) {
        // 旧格式兼容：unitId 单值包装为数组
        form.selectedUnitIds = [unitId]
      } else {
        form.selectedUnitIds = []
      }
    } else {
      form.selectedUnitIds = []
    }
  } else {
    // 重置为默认值
    form.roleCode = ''
    form.anchorType = 'START_USER_PRIMARY_UNIT'
    form.selectedUnitId = null
    form.selectedUnitIds = []
    form.fallbackStrategy = 'ERROR'
    form.approvalMode = 'ANY'

    // 回退：从 multiInstanceLoopCharacteristics 的 completionCondition 推断审批模式
    const bo = props.element?.businessObject
    const loopChar = bo?.loopCharacteristics
    const completionCondition = isRecord(loopChar) && isRecord(loopChar.completionCondition)
      ? loopChar.completionCondition
      : null
    if (completionCondition) {
      const condBody = typeof completionCondition.body === 'string' ? completionCondition.body : ''
      // approvedCount >= nrOfInstances 表示全员审批（会签）
      if (condBody.includes('approvedCount') && condBody.includes('nrOfInstances')) {
        form.approvalMode = 'ALL'
      }
    }
  }
  isLoading = false
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
/** 将当前表单值静默写入 BPMN 元素的 omni:assignment 扩展（保存草稿时自动生效） */
function syncToElement() {
  if (isLoading || !props.modeler || !props.element) return

  const config: AssignmentConfig = {
    assignmentType: 'SCOPED_ROLE',
    roleCode: form.roleCode,
    anchorType: form.anchorType,
    anchorParams: buildAnchorParams(),
    scopeMode: 'SAME_UNIT',
    fallbackStrategy: form.fallbackStrategy,
    approvalMode: form.approvalMode,
  }

  writeAssignment(props.modeler, props.element, config)
}

/**
 * 防抖自动同步：表单变化后延迟 300ms 写入 BPMN 元素。
 * 避免 syncToElement → updateProperties → element watch → loadFromElement → form watch 的循环。
 */
let syncTimer: ReturnType<typeof setTimeout> | null = null
watch(form, () => {
  if (isLoading) return
  if (syncTimer) clearTimeout(syncTimer)
  syncTimer = setTimeout(() => syncToElement(), 300)
}, { deep: true })

/** 保存草稿前强制 flush 未执行的同步定时器 */
function flushSync() {
  if (syncTimer) {
    clearTimeout(syncTimer)
    syncTimer = null
  }
  syncToElement()
}
window.addEventListener('bpmn:flush-sync', flushSync)
onBeforeUnmount(() => {
  window.removeEventListener('bpmn:flush-sync', flushSync)
})

// ===== 解析预览 =====
const previewLoading = ref(false)
const previewCandidates = ref<CandidateUser[]>([])
const previewVisible = ref(false)
const allUsers = ref<IdentityUserVO[]>([])
const simulateUserId = ref<number | null>(null)

async function loadUsers() {
  try {
    const res = await listUsers()
    allUsers.value = res.data.data || []
    // 默认选中当前登录用户
    const userStore = useUserStore()
    const currentUserId = getUserIdFromToken(userStore.token)
    if (currentUserId) {
      simulateUserId.value = currentUserId
    } else if (allUsers.value.length > 0) {
      simulateUserId.value = allUsers.value[0].userId
    }
  } catch {
    // 静默失败
  }
}

async function handlePreview() {
  if (!form.roleCode) {
    ElMessage.warning('请先选择审批角色')
    return
  }
  if (!simulateUserId.value) {
    ElMessage.warning('请先选择模拟发起人')
    return
  }
  previewLoading.value = true
  try {
    const res = await resolvePreview({
      assignmentType: 'SCOPED_ROLE',
      roleCode: form.roleCode,
      anchorType: form.anchorType,
      anchorParams: buildAnchorParams(),
      scopeMode: 'SAME_UNIT',
      simulateUserId: simulateUserId.value,
    })
    previewCandidates.value = res.data.data.candidates || []
    previewVisible.value = true
  } catch {
    ElMessage.error('预览解析失败')
  } finally {
    previewLoading.value = false
  }
}
</script>

<template>
  <div class="user-task-panel">
    <div class="section-title">审批人配置</div>

    <el-form label-width="90px" size="small" @submit.prevent>
      <!-- 审批角色 -->
      <el-form-item required>
        <template #label>
          <span>审批角色</span>
          <el-tooltip placement="top" :show-after="300">
            <template #content>
              <div class="help-content">
                选择审批人在组织中担任的角色。系统会在目标组织范围内查找拥有该角色的用户作为审批候选人。<br />
                <br />
                例如：选择「工作组组长」，则会在目标组织内查找所有 TEAM_LEADER 角色的用户。
              </div>
            </template>
            <el-icon class="help-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-select v-model="form.roleCode" placeholder="选择角色" filterable style="width: 100%">
          <el-option
            v-for="role in roles"
            :key="role.roleCode"
            :label="role.roleName"
            :value="role.roleCode"
          />
        </el-select>
      </el-form-item>

      <!-- 锚点组织 -->
      <el-form-item>
        <template #label>
          <span>审批组织</span>
          <el-tooltip placement="top" :show-after="300">
            <template #content>
              <div class="help-content">
                决定去哪个组织里找审批人。系统先定位到目标组织，再在该组织内查找拥有指定角色的人。<br />
                <br />
                <b>发起人所在组织</b>：发起人属于哪个组，就在那个组里找（最常用，适合“找直属领导”）<br />
                <b>发起人上级组织</b>：从发起人所在组往上找一级父组织（适合“找部门领导审批”）<br />
                <b>发起人下级组织</b>：从下拉选择一个子组织（适合“指派下属小组负责人”）<br />
                <b>指定组织</b>：直接从组织树中选择一个或多个固定组织（适合“人事部/财务部固定审批”）
              </div>
            </template>
            <el-icon class="help-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-select v-model="form.anchorType" style="width: 100%">
          <el-option
            v-for="at in anchorTypes"
            :key="at.value"
            :label="at.label"
            :value="at.value"
          />
        </el-select>
      </el-form-item>
      
      <!-- 组织树选择（CHILD_UNIT 单选） -->
      <el-form-item v-if="form.anchorType === 'CHILD_UNIT'" label="选择下级组织">
        <el-tree-select
          v-model="form.selectedUnitId"
          :data="unitOptions"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="选择下级组织"
          check-strictly
          filterable
          style="width: 100%"
        />
      </el-form-item>

      <!-- ABSOLUTE_UNIT：多选组织树 -->
      <el-form-item v-if="form.anchorType === 'ABSOLUTE_UNIT'" label="选择组织">
        <el-tree-select
          v-model="form.selectedUnitIds"
          :data="unitOptions"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="选择组织（可多选）"
          multiple
          check-strictly
          filterable
          style="width: 100%"
        />
      </el-form-item>
      
      <!-- 审批模式 -->
      <el-form-item>
        <template #label>
          <span>审批模式</span>
          <el-tooltip placement="top" :show-after="300">
            <template #content>
              <div class="help-content">
                决定审批节点的通过规则。<br />
                <br />
                <b>任一人审批即可</b>：只要有一位审批人操作（通过或驳回），该节点即完成。适用于"一个领导签字就够"的场景。<br />
                <br />
                <b>全员审批（会签）</b>：所有审批人都必须逐一操作。任一人驳回则整条流程立即终止（一票否决）。适用于"多个领导都要签字"的场景。
              </div>
            </template>
            <el-icon class="help-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-radio-group v-model="form.approvalMode">
          <el-radio
            v-for="am in approvalModes"
            :key="am.value"
            :value="am.value"
          >
            {{ am.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 兜底策略 -->
      <el-form-item>
        <template #label>
          <span>兜底策略</span>
          <el-tooltip placement="top" :show-after="300">
            <template #content>
              <div class="help-content">
                当系统找不到符合条件的审批候选人时，如何处理。<br />
                <br />
                <b>报错（仅日志）</b>：仅记录错误日志，任务可能无人处理。适合开发测试阶段排查问题。<br />
                <br />
                <b>分配给管理员</b>：自动将任务转给拥有 SUPER_ADMIN 角色的用户，确保流程不会卡住。推荐生产环境使用。<br />
                <br />
                <b>上报上级</b>：记录日志并尝试在上级组织中查找候选人。适用于有多级组织结构的场景。
              </div>
            </template>
            <el-icon class="help-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-select v-model="form.fallbackStrategy" style="width: 100%">
          <el-option
            v-for="fs in fallbackStrategies"
            :key="fs.value"
            :label="fs.label"
            :value="fs.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button size="small" :loading="previewLoading" @click="handlePreview">
          预览候选人
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 预览结果 -->
    <el-dialog v-model="previewVisible" title="候选人预览" width="480" append-to-body>
      <el-form label-width="100px" size="small" style="margin-bottom: 12px" @submit.prevent>
        <el-form-item label="模拟发起人">
          <el-select v-model="simulateUserId" filterable placeholder="选择模拟用户" style="width: 100%">
            <el-option
              v-for="u in allUsers"
              :key="u.userId"
              :label="`${u.nickname || u.username}（${u.unitName || '未分配组织'}）`"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="previewCandidates.length === 0">
        <el-empty description="未找到匹配的候选人" :image-size="60" />
      </div>
      <el-table v-else :data="previewCandidates" stripe size="small">
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="unitName" label="所属组织" />
      </el-table>
      <div class="preview-count">
        共 {{ previewCandidates.length }} 位候选人
      </div>
      <template #footer>
        <el-button size="small" type="primary" :loading="previewLoading" @click="handlePreview">
          重新解析
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-task-panel {
  margin-top: 4px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}
.help-icon {
  margin-left: 4px;
  color: var(--el-color-info);
  cursor: help;
  font-size: 14px;
  vertical-align: middle;
}
.help-content {
  max-width: 300px;
  line-height: 1.7;
  font-size: 13px;
}
.preview-count {
  margin-top: 8px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
