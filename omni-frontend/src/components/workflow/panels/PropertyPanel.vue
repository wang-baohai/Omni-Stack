<script setup lang="ts">
/**
 * 属性面板容器 — 根据当前选中 BPMN 元素类型分发到具体面板。
 * StartEvent / EndEvent / UserTask / ServiceTask / ExclusiveGateway / SequenceFlow。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import type { BpmnElement } from '@/types/bpmn'
import UserTaskPanel from './UserTaskPanel.vue'
import ServiceTaskPanel from './ServiceTaskPanel.vue'
import GatewayPanel from './GatewayPanel.vue'
import StartEventPanel from './StartEventPanel.vue'
import EndEventPanel from './EndEventPanel.vue'
import ParallelGatewayPanel from './ParallelGatewayPanel.vue'
import IntermediateThrowEventPanel from './IntermediateThrowEventPanel.vue'

const { t } = useI18n()

const props = defineProps<{
  element: BpmnElement | null
  modeler: BpmnModeler | null
}>()

const elementType = computed(() => {
  if (!props.element) return null
  const bo = props.element.businessObject
  if (!bo) return null
  return bo.$type
})

const elementName = computed(() => {
  if (!props.element) return ''
  return props.element.businessObject?.name || props.element.businessObject?.id || ''
})

const isUserTask = computed(() => elementType.value === 'bpmn:UserTask')
const isServiceTask = computed(() => elementType.value === 'bpmn:ServiceTask')
const isGateway = computed(() => elementType.value === 'bpmn:ExclusiveGateway')
const isStartEvent = computed(() => elementType.value === 'bpmn:StartEvent')
const isEndEvent = computed(() => elementType.value === 'bpmn:EndEvent')
const isParallelGateway = computed(() => elementType.value === 'bpmn:ParallelGateway')
const isInclusiveGateway = computed(() => elementType.value === 'bpmn:InclusiveGateway')
const isIntermediateThrowEvent = computed(() => elementType.value === 'bpmn:IntermediateThrowEvent')
const isTextAnnotation = computed(() => elementType.value === 'bpmn:TextAnnotation')

function typeLabel(type: string | null) {
  if (!type) return ''
  const map: Record<string, string> = {
    'bpmn:UserTask': t('workflow.nodeTypeUserTask'),
    'bpmn:ServiceTask': t('workflow.nodeTypeServiceTask'),
    'bpmn:ExclusiveGateway': t('workflow.nodeTypeExclusiveGateway'),
    'bpmn:ParallelGateway': t('workflow.nodeTypeParallelGateway'),
    'bpmn:InclusiveGateway': t('workflow.nodeTypeInclusiveGateway'),
    'bpmn:StartEvent': t('workflow.nodeTypeStartEvent'),
    'bpmn:EndEvent': t('workflow.nodeTypeEndEvent'),
    'bpmn:IntermediateThrowEvent': t('workflow.nodeTypeIntermediateEvent'),
    'bpmn:SequenceFlow': t('workflow.nodeTypeSequenceFlow'),
    'bpmn:Process': t('workflow.nodeTypeProcess'),
    'bpmn:TextAnnotation': t('workflow.nodeTypeTextAnnotation'),
  }
  return map[type] || type
}
</script>

<template>
  <div class="property-panel">
    <!-- 未选中 -->
    <div v-if="!element || !elementType" class="panel-empty">
      <el-empty :description="t('workflow.selectCanvasNodeHint')" :image-size="80" />
    </div>

    <!-- 已选中 -->
    <template v-else>
      <div class="panel-header">
        <div class="panel-type">{{ typeLabel(elementType) }}</div>
        <div class="panel-name">{{ elementName }}</div>
      </div>

      <el-divider />

      <!-- 通用属性 -->
      <div class="panel-section">
        <div class="section-title">{{ t('workflow.basicInfo') }}</div>
        <el-form label-width="80px" size="small">
          <el-form-item label="ID">
            <el-input :model-value="element.businessObject?.id" disabled />
          </el-form-item>
        </el-form>
      </div>

      <el-divider />

      <!-- 审批节点 -->
      <UserTaskPanel
        v-if="isUserTask"
        :element="element"
        :modeler="modeler"
      />

      <!-- 抄送节点 -->
      <ServiceTaskPanel
        v-if="isServiceTask"
        :element="element"
        :modeler="modeler"
      />

      <!-- 排他网关 -->
      <GatewayPanel
        v-if="isGateway"
        :element="element"
        :modeler="modeler"
      />

      <!-- 开始事件 -->
      <StartEventPanel
        v-if="isStartEvent"
        :element="element"
      />

      <!-- 结束事件 -->
      <EndEventPanel
        v-if="isEndEvent"
        :element="element"
      />

      <!-- 并行网关 -->
      <ParallelGatewayPanel
        v-if="isParallelGateway"
        :element="element"
      />

      <!-- 包含网关 — 复用 GatewayPanel -->
      <GatewayPanel
        v-if="isInclusiveGateway"
        :element="element"
        :modeler="modeler"
      />

      <!-- 中间事件 -->
      <IntermediateThrowEventPanel
        v-if="isIntermediateThrowEvent"
        :element="element"
      />

      <!-- 文本注释 -->
      <el-alert
        v-if="isTextAnnotation"
        type="info"
        :closable="false"
        show-icon
      >
        <template #title>
          {{ t('workflow.textAnnotationHint') }}
        </template>
      </el-alert>
    </template>
  </div>
</template>

<style scoped>
.property-panel {
  padding: 16px;
}
.panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
}
.panel-header {
  margin-bottom: 8px;
}
.panel-type {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.panel-name {
  font-size: 15px;
  font-weight: 600;
}
.panel-section {
  margin-bottom: 4px;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}
</style>
