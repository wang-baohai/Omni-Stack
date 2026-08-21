<script setup lang="ts">
/**
 * 排他网关（ExclusiveGateway）属性面板。
 * 配置：分支条件表达式 + 默认分支。
 * 使用 useBpmnExtension 的 readGatewayConditions / writeGatewayDefault / writeGatewayCondition。
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import type { BpmnElement, BpmnElementRegistry, BpmnModdleElement } from '@/types/bpmn'
import {
  readGatewayConditions,
  writeGatewayDefault,
  writeGatewayCondition,
} from '@/composables/useBpmnExtension'

const props = defineProps<{
  element: BpmnElement
  modeler: BpmnModeler | null
}>()

// ===== 分支列表 =====
interface FlowBranch {
  id: string
  name: string
  condition: string
  isDefault: boolean
}

const branches = ref<FlowBranch[]>([])

function loadFromElement() {
  if (!props.element) {
    branches.value = []
    return
  }

  const bo = props.element.businessObject
  const outgoing = bo.outgoing || []
  const { defaultFlow, conditions } = readGatewayConditions(props.element)

  branches.value = outgoing.filter(
    (flow): flow is BpmnModdleElement & { id: string } => typeof flow.id === 'string',
  ).map(flow => ({
    id: flow.id,
    name: flow.name || flow.id,
    condition: conditions[flow.id] || '',
    isDefault: flow.id === defaultFlow,
  }))
}

watch(() => props.element, () => {
  if (props.element) loadFromElement()
}, { immediate: true })

// ===== 设为默认分支 =====
function setDefault(flowId: string) {
  if (!props.modeler || !props.element) return

  // 先清除所有 isDefault
  branches.value.forEach(b => { b.isDefault = false })
  const target = branches.value.find(b => b.id === flowId)
  if (target) target.isDefault = true

  writeGatewayDefault(props.modeler, props.element, flowId)
  ElMessage.success('默认分支已更新')
}

// ===== 保存条件 =====
function saveCondition(branch: FlowBranch) {
  if (!props.modeler || !props.element) return

  const bo = props.element.businessObject
  const outgoing = bo.outgoing || []
  const flowElement = outgoing.find(flow => flow.id === branch.id)

  if (!flowElement) return

  // 需要通过 modeler 获取实际的 SequenceFlow 包装元素
  const elementRegistry = props.modeler.get<BpmnElementRegistry>('elementRegistry')
  const wrappedFlow = elementRegistry?.get(branch.id)

  if (wrappedFlow) {
    writeGatewayCondition(props.modeler, wrappedFlow, branch.condition)
    ElMessage.success(`分支「${branch.name}」条件已更新`)
  }
}
</script>

<template>
  <div class="gateway-panel">
    <div class="section-title">分支配置</div>

    <div v-if="branches.length === 0" class="no-branches">
      <el-text type="info" size="small">
        请先连接网关到下游节点
      </el-text>
    </div>

    <div v-for="branch in branches" :key="branch.id" class="branch-item">
      <div class="branch-header">
        <span class="branch-name">{{ branch.name }}</span>
        <el-tag
          v-if="branch.isDefault"
          type="success"
          size="small"
        >
          默认
        </el-tag>
        <el-button
          v-else
          link
          type="primary"
          size="small"
          @click="setDefault(branch.id)"
        >
          设为默认
        </el-button>
      </div>

      <div v-if="!branch.isDefault" class="branch-condition">
        <el-input
          v-model="branch.condition"
          placeholder="条件表达式（如: ${amount > 1000}）"
          size="small"
        >
          <template #append>
            <el-button @click="saveCondition(branch)">
              保存
            </el-button>
          </template>
        </el-input>
      </div>
    </div>

    <el-divider />

    <el-alert
      type="info"
      :closable="false"
      show-icon
    >
      <template #title>
        默认分支无需条件表达式。当所有条件分支均不满足时，流程将走默认分支。
      </template>
    </el-alert>
  </div>
</template>

<style scoped>
.gateway-panel {
  margin-top: 4px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
}
.no-branches {
  padding: 16px 0;
  text-align: center;
}
.branch-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.branch-item:last-child {
  border-bottom: none;
}
.branch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.branch-name {
  font-size: 13px;
  font-weight: 500;
}
.branch-condition {
  margin-top: 4px;
}
</style>
