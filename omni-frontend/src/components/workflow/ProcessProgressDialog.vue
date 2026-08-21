<script setup lang="ts">
/**
 * 流程流转进度弹窗组件。
 * 使用 bpmn-js NavigatedViewer 渲染 BPMN 图，通过三色高亮标识节点状态：
 * 绿色=已完成、蓝色=进行中、灰色=未到达。点击节点显示活动详情。
 */
import { ref, shallowRef, nextTick, computed } from 'vue'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import {
  getProcessDefinitionBpmn,
  getProcessProgress,
  type ActivityInfo,
} from '@/api/workflow'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'

const visible = ref(false)
const loading = ref(false)
const viewerContainer = ref<HTMLDivElement>()
const viewer = shallowRef<NavigatedViewer | null>(null)

const activityMap = ref<Map<string, ActivityInfo>>(new Map())
const selectedActivity = ref<ActivityInfo | null>(null)

/** 判断当前是否暗色模式 */
function isDarkMode(): boolean {
  return document.documentElement.classList.contains('dark')
}

/** 暗色模式下修正 SVG 基础颜色（普通节点、连接线、文字），跳过已高亮节点 */
function fixDarkModeColors() {
  if (!viewerContainer.value) return
  const svgEl = viewerContainer.value.querySelector('.djs-container svg')
  if (!svgEl) return

  const shapeBg = 'rgba(30, 30, 40, 0.85)'
  const shapeStroke = 'rgba(160, 165, 180, 0.55)'
  const textColor = '#dce1eb'
  const lineColor = 'rgba(140, 145, 160, 0.6)'

  // 1. 修正形状填充（排除已高亮节点）
  svgEl.querySelectorAll('.djs-visual > rect, .djs-visual > circle, .djs-visual > ellipse, .djs-visual > polygon').forEach((el) => {
    const parent = (el as HTMLElement).closest('.djs-element')
    if (parent && (parent.classList.contains('completed-node') || parent.classList.contains('active-node'))) return
    const s = (el as HTMLElement).style
    s.setProperty('fill', shapeBg)
    s.setProperty('stroke', shapeStroke)
  })

  // 2. 修正连接线
  svgEl.querySelectorAll('.djs-connection .djs-visual > path').forEach((el) => {
    const parent = (el as HTMLElement).closest('.djs-element')
    if (parent && (parent.classList.contains('completed-node') || parent.classList.contains('active-node'))) return
    (el as HTMLElement).style.setProperty('stroke', lineColor)
  })

  // 3. 修正所有文字标签
  svgEl.querySelectorAll('.djs-label, .djs-label text, .djs-label tspan').forEach((el) => {
    (el as HTMLElement).style.setProperty('fill', textColor)
  })
}

/** 暗色模式下为已高亮节点覆盖暗色适配版高亮色 */
function applyDarkHighlightColors() {
  if (!viewerContainer.value) return
  const svgEl = viewerContainer.value.querySelector('.djs-container svg')
  if (!svgEl) return

  const completedFill = 'rgba(22, 68, 30, 0.7)'
  const completedStroke = '#52c41a'
  const activeFill = 'rgba(16, 42, 86, 0.7)'
  const activeStroke = '#409eff'

  svgEl.querySelectorAll('.completed-node:not(.djs-connection) .djs-visual > rect, .completed-node:not(.djs-connection) .djs-visual > circle, .completed-node:not(.djs-connection) .djs-visual > polygon, .completed-node:not(.djs-connection) .djs-visual > path').forEach((el) => {
    const s = (el as HTMLElement).style
    s.setProperty('fill', completedFill)
    s.setProperty('stroke', completedStroke)
  })

  svgEl.querySelectorAll('.active-node:not(.djs-connection) .djs-visual > rect, .active-node:not(.djs-connection) .djs-visual > circle, .active-node:not(.djs-connection) .djs-visual > polygon, .active-node:not(.djs-connection) .djs-visual > path').forEach((el) => {
    const s = (el as HTMLElement).style
    s.setProperty('fill', activeFill)
    s.setProperty('stroke', activeStroke)
  })

  // 高亮节点内的文字修正为浅色
  svgEl.querySelectorAll('.completed-node .djs-label text, .completed-node .djs-label tspan, .active-node .djs-label text, .active-node .djs-label tspan').forEach((el) => {
    (el as HTMLElement).style.setProperty('fill', '#dce1eb')
  })
}

/** 打开弹窗，加载 BPMN 图和进度数据 */
async function open(processInstanceId: string, processDefinitionId: string) {
  visible.value = true
  loading.value = true
  selectedActivity.value = null
  activityMap.value = new Map()

  await nextTick()

  if (!viewerContainer.value) {
    loading.value = false
    return
  }

  // 创建 NavigatedViewer（只读 + 平移缩放）
  const dark = isDarkMode()
  viewer.value = new NavigatedViewer({
    container: viewerContainer.value,
    ...(dark ? { bpmnRenderer: { defaultLabelColor: '#dce1eb' } } : {}),
  })

  try {
    const [bpmnRes, progressRes] = await Promise.all([
      getProcessDefinitionBpmn(processDefinitionId),
      getProcessProgress(processInstanceId),
    ])

    const bpmnXml = bpmnRes.data.data
    const progress = progressRes.data.data

    // 构建 activityId → ActivityInfo 映射
    const map = new Map<string, ActivityInfo>()
    for (const act of progress.allActivities) {
      map.set(act.activityId, act)
    }

    // 导入 BPMN XML
    await viewer.value.importXML(bpmnXml)
    const canvas = viewer.value.get('canvas') as any
    const elementRegistry = viewer.value.get('elementRegistry') as any

    // 补全所有 BPMN 节点（未到达的节点不在后端响应中，需要创建占位数据）
    const allElements = elementRegistry.getAll()
    for (const element of allElements) {
      if (element.type && !element.type.includes('SequenceFlow') && element.type !== 'Process') {
        if (!map.has(element.id)) {
          map.set(element.id, {
            activityId: element.id,
            activityName: element.businessObject?.name || element.id,
            activityType: element.type,
            assignee: null,
            assigneeName: null,
            startTime: null,
            endTime: null,
            status: 'pending',
            assigneeStatuses: null,
            completedCount: null,
            totalCount: null,
          })
        }
      }
    }
    activityMap.value = map

    // 添加颜色标记
    for (const act of progress.allActivities) {
      try {
        const element = elementRegistry.get(act.activityId)
        if (!element) continue

        if (act.status === 'completed') {
          canvas.addMarker(act.activityId, 'completed-node')
        } else if (act.status === 'active') {
          canvas.addMarker(act.activityId, 'active-node')
        }
        // pending 不加 marker，保持默认灰色
      } catch {
        // 元素不存在，跳过
      }
    }

    // 为活跃的会签节点添加进度徽章
    const overlays = viewer.value.get('overlays') as any
    for (const act of progress.allActivities) {
      if (act.status === 'active' && act.completedCount != null && act.totalCount != null) {
        try {
          const el = elementRegistry.get(act.activityId)
          if (!el) continue
          const badge = document.createElement('div')
          badge.className = 'mi-progress-badge'
          badge.textContent = `(${act.completedCount}/${act.totalCount})`
          overlays.add(act.activityId, {
            position: { top: -8, right: -8 },
            html: badge,
          })
        } catch {
          // 元素不存在，跳过
        }
      }
    }

    // 暗色模式：先修正基础颜色，再覆盖高亮色
    if (dark) {
      fixDarkModeColors()
      applyDarkHighlightColors()
    }

    canvas.zoom('fit-viewport')

    // 节点点击事件
    viewer.value.on('element.click', (event: any) => {
      const element = event.element
      if (element && element.type && !element.type.includes('SequenceFlow')) {
        const act = activityMap.value.get(element.id)
        if (act) {
          selectedActivity.value = act
        }
      }
    })
  } catch (err) {
    console.error('[ProcessProgressDialog] 加载失败:', err)
    loading.value = false
  } finally {
    loading.value = false
  }
}

/** 关闭弹窗，销毁 Viewer */
function close() {
  if (viewer.value) {
    viewer.value.destroy()
    viewer.value = null
  }
  visible.value = false
  selectedActivity.value = null
  activityMap.value = new Map()
}

/** 格式化日期时间显示 */
function formatDt(val: string | null) {
  if (!val) return '-'
  return val.length > 19 ? val.substring(0, 19) : val
}

/** 获取状态中文标签 */
function statusLabel(status: string) {
  if (status === 'completed') return '已完成'
  if (status === 'active') return '进行中'
  return '未到达'
}

/** 获取状态 Tag 类型 */
function statusType(status: string) {
  if (status === 'completed') return 'success'
  if (status === 'active') return ''
  return 'info'
}

/** 已通过的人名（会签节点，实际审批） */
const completedNames = computed(() => {
  if (!selectedActivity.value?.assigneeStatuses) return ''
  return selectedActivity.value.assigneeStatuses
    .filter(a => a.status === 'completed')
    .map(a => a.userName)
    .join('、')
})

/** 自动通过的人名（会签节点，ANY 模式下被 completionCondition 跳过） */
const autoCompletedNames = computed(() => {
  if (!selectedActivity.value?.assigneeStatuses) return ''
  return selectedActivity.value.assigneeStatuses
    .filter(a => a.status === 'auto-completed')
    .map(a => a.userName)
    .join('、')
})

/** 待审批的人名（会签节点） */
const pendingNames = computed(() => {
  if (!selectedActivity.value?.assigneeStatuses) return ''
  return selectedActivity.value.assigneeStatuses
    .filter(a => a.status === 'active')
    .map(a => a.userName)
    .join('、')
})

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="流转进度"
    width="85%"
    top="5vh"
    destroy-on-close
    @close="close"
  >
    <div class="progress-body">
      <!-- BPMN 图区域 -->
      <div v-loading="loading" class="bpmn-viewer-wrap">
        <div ref="viewerContainer" class="viewer-container" />
      </div>
      <!-- 活动详情面板 -->
      <div class="detail-panel">
        <div class="detail-header">节点详情</div>
        <div v-if="selectedActivity" class="detail-content">
          <div class="detail-row">
            <span class="label">状态</span>
            <el-tag :type="statusType(selectedActivity.status)" size="small">
              {{ statusLabel(selectedActivity.status) }}
            </el-tag>
          </div>
          <div class="detail-row">
            <span class="label">名称</span>
            <span class="value">{{ selectedActivity.activityName || selectedActivity.activityId }}</span>
          </div>
          <!-- 会签节点：分组展示已通过/待审批 -->
          <template v-if="selectedActivity.assigneeStatuses && selectedActivity.assigneeStatuses.length > 1">
            <div v-if="completedNames" class="detail-row">
              <span class="label label-passed">已通过</span>
              <span class="value">{{ completedNames }}</span>
            </div>
            <div v-if="autoCompletedNames" class="detail-row">
              <span class="label label-auto">自动通过</span>
              <span class="value">{{ autoCompletedNames }}</span>
            </div>
            <div v-if="pendingNames" class="detail-row">
              <span class="label label-pending">待审批</span>
              <span class="value">{{ pendingNames }}</span>
            </div>
          </template>
          <!-- 非会签节点：原有单行展示 -->
          <div v-else-if="selectedActivity.assigneeName" class="detail-row">
            <span class="label">
              {{ selectedActivity.activityType === 'startEvent'
                ? '申请人'
                : selectedActivity.status === 'pending' ? '预计处理人' : '处理人' }}
            </span>
            <span class="value">{{ selectedActivity.assigneeName }}</span>
          </div>
          <div class="detail-row">
            <span class="label">开始时间</span>
            <span class="value">{{ formatDt(selectedActivity.startTime) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">结束时间</span>
            <span class="value">{{ formatDt(selectedActivity.endTime) }}</span>
          </div>
        </div>
        <div v-else class="detail-empty">
          点击流程图中的节点查看详情
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.progress-body {
  display: flex;
  gap: 16px;
  height: 65vh;
}

.bpmn-viewer-wrap {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 4px;
  overflow: hidden;
  background: var(--el-bg-color, #fff);
}

.viewer-container {
  width: 100%;
  height: 100%;
}

.detail-panel {
  flex: 0 0 260px;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 4px;
  padding: 16px;
  overflow-y: auto;
}

.detail-header {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-row .label {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.detail-row .value {
  font-size: 13px;
  color: var(--el-text-color-primary, #303133);
  word-break: break-all;
}

.detail-empty {
  color: var(--el-text-color-placeholder, #a8abb2);
  font-size: 13px;
  text-align: center;
  padding-top: 40px;
}

.label-passed {
  color: #52c41a !important;
}

.label-auto {
  color: #909399 !important;
}

.label-pending {
  color: #e6a23c !important;
}
</style>

<!-- bpmn-js 三色高亮样式（不受 scoped 影响） -->
<style>
.completed-node:not(.djs-connection) .djs-visual > rect,
.completed-node:not(.djs-connection) .djs-visual > circle,
.completed-node:not(.djs-connection) .djs-visual > polygon,
.completed-node:not(.djs-connection) .djs-visual > path {
  fill: #e6f7e6 !important;
  stroke: #52c41a !important;
}

.active-node:not(.djs-connection) .djs-visual > rect,
.active-node:not(.djs-connection) .djs-visual > circle,
.active-node:not(.djs-connection) .djs-visual > polygon,
.active-node:not(.djs-connection) .djs-visual > path {
  fill: #e6f0ff !important;
  stroke: #1890ff !important;
}

/* 暗色模式：viewer 容器背景 + 高亮色适配 */
html.dark .bpmn-viewer-wrap {
  background: #1a1a2e;
  border-color: rgba(160, 165, 180, 0.2);
}

html.dark .completed-node:not(.djs-connection) .djs-visual > rect,
html.dark .completed-node:not(.djs-connection) .djs-visual > circle,
html.dark .completed-node:not(.djs-connection) .djs-visual > polygon,
html.dark .completed-node:not(.djs-connection) .djs-visual > path {
  fill: rgba(22, 68, 30, 0.7) !important;
  stroke: #52c41a !important;
}

html.dark .active-node:not(.djs-connection) .djs-visual > rect,
html.dark .active-node:not(.djs-connection) .djs-visual > circle,
html.dark .active-node:not(.djs-connection) .djs-visual > polygon,
html.dark .active-node:not(.djs-connection) .djs-visual > path {
  fill: rgba(16, 42, 86, 0.7) !important;
  stroke: #409eff !important;
}

.mi-progress-badge {
  background: #409eff;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 10px;
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  line-height: 18px;
}
</style>
