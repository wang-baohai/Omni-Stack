<script setup lang="ts">
/** 将安全审批图转换为业务人员可理解的步骤或分支节点。 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ApprovalGraph, ApprovalNode } from '@/api/procurement-approval-route'

const props = defineProps<{
  graph?: ApprovalGraph
}>()

const { t } = useI18n()
const businessNodes = computed(() => props.graph?.nodes.filter(
  (node) => node.type === 'APPROVAL' || node.type === 'GATEWAY',
) ?? [])

function nodeDescription(node: ApprovalNode) {
  if (node.type === 'GATEWAY') return t('procurementApprovalRules.flowBranchHint')
  if (node.description) return node.description
  if (node.roleCode) return `${t('procurementApprovalRules.role')}：${node.roleCode}`
  return node.name
}
</script>

<template>
  <div v-if="graph" class="flow-preview">
    <el-alert
      v-if="graph.hasBranches"
      type="warning"
      :closable="false"
      show-icon
      :title="t('procurementApprovalRules.flowBranchHint')"
    />
    <template v-if="graph.linearSummary?.length && !graph.hasBranches">
      <div class="preview-title">{{ t('procurementApprovalRules.linearFlow') }}</div>
      <el-steps direction="vertical" :active="graph.linearSummary.length" finish-status="success">
        <el-step
          v-for="(step, index) in graph.linearSummary"
          :key="`${index}-${step}`"
          :title="step"
        />
      </el-steps>
    </template>
    <template v-else>
      <div class="preview-title">{{ t('procurementApprovalRules.flowNodes') }}</div>
      <div class="node-list">
        <div v-for="node in businessNodes" :key="node.id" class="flow-node">
          <el-tag :type="node.type === 'GATEWAY' ? 'warning' : 'primary'">
            {{ node.type === 'GATEWAY'
              ? t('procurementApprovalRules.nodeBranch')
              : t('procurementApprovalRules.nodeApproval') }}
          </el-tag>
          <div>
            <strong>{{ node.name }}</strong>
            <div class="node-description">{{ nodeDescription(node) }}</div>
            <div v-if="node.approvalMode" class="node-description">
              {{ node.approvalMode === 'ALL'
                ? t('procurementApprovalRules.modeAll')
                : t('procurementApprovalRules.modeAny') }}
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.flow-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.node-list {
  display: grid;
  gap: 10px;
}

.flow-node {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.node-description {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
