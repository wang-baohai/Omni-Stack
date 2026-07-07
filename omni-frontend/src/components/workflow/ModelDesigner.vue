<script setup lang="ts">
/**
 * 模型设计工作台 — 全屏对话框三栏布局。
 * 左侧：受限 Palette 面板；中央：bpmn-js Canvas；右侧：属性面板 PropertyPanel。
 * 工具栏：保存草稿 / 校验 / 发布。
 */
import { ref, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getModel,
  saveDraft,
  validateModel,
  publishModel,
  getVersion,
  type ProcessModel,
  type ProcessModelVersion,
  type ValidateResult,
} from '@/api/workflow-model'
import { useBpmnModeler } from '@/composables/useBpmnModeler'
import { registerOmniModdle } from '@/composables/useBpmnExtension'
import PropertyPanel from './panels/PropertyPanel.vue'

const props = defineProps<{
  visible: boolean
  modelId: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}>()

// ===== 模型信息 =====
const modelInfo = ref<ProcessModel | null>(null)
const currentVersion = ref<ProcessModelVersion | null>(null)
const loadingModel = ref(false)

// ===== bpmn-js Canvas =====
const canvasRef = ref<HTMLDivElement>()
const selectedElement = ref<any>(null)
const dirty = ref(false)

interface PaletteItem {
  key: string
  label: string
  icon: string
  tool: 'shape' | 'connect'
  bpmnType?: string
}

const paletteGroups: Array<{ label: string; items: PaletteItem[] }> = [
  {
    label: '事件',
    items: [
      { key: 'start-event', label: '开始事件', icon: 'bpmn-icon-start-event-none', tool: 'shape', bpmnType: 'bpmn:StartEvent' },
      { key: 'end-event', label: '结束事件', icon: 'bpmn-icon-end-event-none', tool: 'shape', bpmnType: 'bpmn:EndEvent' },
      { key: 'intermediate-throw-event', label: '中间事件', icon: 'bpmn-icon-intermediate-event-none', tool: 'shape', bpmnType: 'bpmn:IntermediateThrowEvent' },
    ],
  },
  {
    label: '活动',
    items: [
      { key: 'user-task', label: '审批节点', icon: 'bpmn-icon-user-task', tool: 'shape', bpmnType: 'bpmn:UserTask' },
      { key: 'service-task', label: '抄送节点', icon: 'bpmn-icon-service-task', tool: 'shape', bpmnType: 'bpmn:ServiceTask' },
    ],
  },
  {
    label: '网关',
    items: [
      { key: 'exclusive-gateway', label: '排他网关', icon: 'bpmn-icon-gateway-xor', tool: 'shape', bpmnType: 'bpmn:ExclusiveGateway' },
      { key: 'parallel-gateway', label: '并行网关', icon: 'bpmn-icon-gateway-parallel', tool: 'shape', bpmnType: 'bpmn:ParallelGateway' },
      { key: 'inclusive-gateway', label: '包含网关', icon: 'bpmn-icon-gateway-or', tool: 'shape', bpmnType: 'bpmn:InclusiveGateway' },
    ],
  },
  {
    label: '连线',
    items: [
      { key: 'sequence-flow', label: '顺序流', icon: 'bpmn-icon-connection', tool: 'connect' },
    ],
  },
  {
    label: '注释',
    items: [
      { key: 'text-annotation', label: '文本注释', icon: 'bpmn-icon-text-annotation', tool: 'shape', bpmnType: 'bpmn:TextAnnotation' },
    ],
  },
]

const {
  modeler,
  loading: modelerLoading,
  initModeler,
  importXml,
  exportXml,
  destroy,
} = useBpmnModeler({
  container: canvasRef,
  onSelectionChanged: (el: any) => {
    selectedElement.value = el
  },
  onChanged: () => {
    dirty.value = true
  },
  onSave: handleSave,
  onBeforeImport: () => {
    const moddle = modeler.value?.get('moddle')
    if (moddle) registerOmniModdle(moddle)
  },
})

// ===== 加载模型数据 =====
async function loadModel() {
  if (!props.modelId) return
  loadingModel.value = true
  try {
    const res = await getModel(props.modelId)
    modelInfo.value = res.data.data

    // 加载当前草稿版本
    const draftVersionId = modelInfo.value.currentDraftVersionId
    if (draftVersionId) {
      const vRes = await getVersion(draftVersionId)
      currentVersion.value = vRes.data.data
      const xml = currentVersion.value.bpmnXml
      if (xml) {
        await importXml(xml)
      }
    }
  } catch {
    ElMessage.error('加载模型数据失败')
  } finally {
    loadingModel.value = false
  }
}

watch(() => props.visible, (val) => {
  if (val && props.modelId) {
    dirty.value = false
    // 销毁旧实例，等 @opened 事件后在 onDialogOpened 中重建
    destroy()
  } else if (!val) {
    // 对话框关闭时销毁 Modeler
    destroy()
  }
})

/** el-dialog 过渡动画完成后触发，此时 DOM 容器已完全就绪 */
async function onDialogOpened() {
  if (!props.modelId) return
  destroy()
  await nextTick()
  initModeler()
  await loadModel()
}

// ===== 保存草稿 =====
const saving = ref(false)

async function handleSave() {
  if (!modelInfo.value || saving.value) return
  saving.value = true
  try {
    // 保存前强制 flush 各属性面板的防抖同步，确保最新表单值写入 BPMN 元素
    window.dispatchEvent(new Event('bpmn:flush-sync'))
    // flush 是同步的，但给 Vue 一个微任务周期完成响应式更新
    await new Promise<void>(r => setTimeout(r, 0))
    const xml = await exportXml()
    await saveDraft(modelInfo.value.id, {
      designerJson: JSON.stringify({ version: 1 }),
      bpmnXml: xml,
    })
    dirty.value = false
    ElMessage.success('草稿已保存')
    emit('saved')
  } catch {
    ElMessage.error('保存草稿失败')
  } finally {
    saving.value = false
  }
}

// ===== 校验 =====
const validating = ref(false)
const validateResult = ref<ValidateResult | null>(null)
const validateDialogVisible = ref(false)

async function handleValidate() {
  if (!modelInfo.value || validating.value) return
  // 先保存再校验
  if (dirty.value) {
    await handleSave()
  }
  validating.value = true
  try {
    const res = await validateModel(modelInfo.value.id)
    validateResult.value = res.data.data
    validateDialogVisible.value = true
  } catch {
    ElMessage.error('校验请求失败')
  } finally {
    validating.value = false
  }
}

// ===== 发布 =====
const publishing = ref(false)

async function handlePublish() {
  if (!modelInfo.value || publishing.value) return
  try {
    await ElMessageBox.confirm(
      '确认发布当前模型？发布后将生成新版本并部署到流程引擎。',
      '发布确认',
      { type: 'warning' },
    )
    // 先保存再发布
    if (dirty.value) {
      await handleSave()
    }
    publishing.value = true
    const res = await publishModel(modelInfo.value.id)
    ElMessage.success(`发布成功！业务版本: v${res.data.data.businessVersion}`)
    dirty.value = false
    emit('saved')
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '发布失败')
    }
  } finally {
    publishing.value = false
  }
}

// ===== 关闭 =====
async function handleClose() {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm('有未保存的修改，是否保存后关闭？', '提示', {
        confirmButtonText: '保存',
        cancelButtonText: '不保存',
        distinguishCancelAndClose: true,
        type: 'warning',
      })
      await handleSave()
    } catch (action: any) {
      if (action === 'close') return // 点击 X 取消关闭
    }
  }
  emit('update:visible', false)
}

/** 从左侧元素面板拖拽创建 BPMN 节点或连线 */
function handlePaletteMouseDown(event: MouseEvent, item: PaletteItem) {
  if (!modeler.value || event.button !== 0) return
  event.preventDefault()

  if (item.tool === 'connect') {
    if (!selectedElement.value) {
      ElMessage.info('请先选中一个起点节点')
      return
    }
    const connect = modeler.value.get('connect') as any
    connect.start(event, selectedElement.value)
    return
  }

  if (!item.bpmnType) return
  const elementFactory = modeler.value.get('elementFactory') as any
  const create = modeler.value.get('create') as any
  const shape = elementFactory.createShape({ type: item.bpmnType })
  create.start(event, shape)
}

onMounted(() => {
  if (props.visible && props.modelId) {
    loadModel()
  }
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    fullscreen
    :close-on-click-modal="false"
    :show-close="false"
    class="model-designer-dialog"
    @opened="onDialogOpened"
  >
    <template #header>
      <div class="designer-header">
        <div class="header-left">
          <span class="header-title">
            流程设计器
            <template v-if="modelInfo">
              — {{ modelInfo.modelName }}
              <el-tag size="small" type="info" class="header-key">{{ modelInfo.modelKey }}</el-tag>
            </template>
          </span>
          <el-tag v-if="dirty" size="small" type="warning" class="header-dirty">未保存</el-tag>
        </div>
        <div class="header-actions">
          <el-button :loading="saving" type="primary" @click="handleSave">
            保存草稿
          </el-button>
          <el-button :loading="validating" @click="handleValidate">
            校验
          </el-button>
          <el-button :loading="publishing" type="success" @click="handlePublish">
            发布
          </el-button>
          <el-button @click="handleClose">关闭</el-button>
        </div>
      </div>
    </template>

    <div v-loading="loadingModel || modelerLoading" class="designer-body">
      <!-- 左侧 Palette -->
      <div class="designer-palette">
        <div class="palette-title">元素</div>
        <div
          v-for="group in paletteGroups"
          :key="group.label"
          class="palette-group"
        >
          <div class="palette-group-label">{{ group.label }}</div>
          <button
            v-for="item in group.items"
            :key="item.key"
            class="palette-item"
            type="button"
            @mousedown="handlePaletteMouseDown($event, item)"
          >
            <span class="palette-icon" :class="item.icon" />
            <span class="palette-label">{{ item.label }}</span>
          </button>
        </div>
        <div class="palette-hint">
          <el-text size="small" type="info">
            拖拽元素到画布；选择起点后可使用顺序流连线。
          </el-text>
        </div>
      </div>

      <!-- 中央 Canvas -->
      <div class="designer-canvas">
        <div ref="canvasRef" class="canvas-container" />
      </div>

      <!-- 右侧 PropertyPanel -->
      <div class="designer-property">
        <PropertyPanel
          :element="selectedElement"
          :modeler="modeler"
        />
      </div>
    </div>

    <!-- 校验结果对话框 -->
    <el-dialog v-model="validateDialogVisible" title="校验结果" width="500">
      <template v-if="validateResult">
        <el-result
          :icon="validateResult.valid ? 'success' : 'error'"
          :title="validateResult.valid ? '校验通过' : '校验失败'"
        />
        <div v-if="validateResult.errors.length" class="validate-section">
          <div class="validate-label">错误：</div>
          <ul class="validate-list">
            <li v-for="(err, idx) in validateResult.errors" :key="idx" class="validate-error">
              {{ err }}
            </li>
          </ul>
        </div>
        <div v-if="validateResult.warnings.length" class="validate-section">
          <div class="validate-label">警告：</div>
          <ul class="validate-list">
            <li v-for="(warn, idx) in validateResult.warnings" :key="idx" class="validate-warning">
              {{ warn }}
            </li>
          </ul>
        </div>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<style scoped>
:global(.model-designer-dialog .el-dialog__header) {
  padding: 0;
  margin: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
:global(.model-designer-dialog .el-dialog__body) {
  padding: 0;
  height: calc(100vh - 56px);
  overflow: hidden;
}

.designer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px;
  background: var(--el-bg-color);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
}
.header-key {
  margin-left: 8px;
}
.header-dirty {
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
.header-actions {
  display: flex;
  gap: 8px;
}

.designer-body {
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr) 380px;
  height: 100%;
  min-height: 0;
  background: var(--el-bg-color-page);
}

/* 左侧 Palette */
.designer-palette {
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  overflow-y: auto;
  padding: 12px;
}
.palette-title {
  height: 32px;
  display: flex;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.palette-group {
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.palette-group-label {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.palette-item {
  width: 100%;
  height: 36px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-primary);
  cursor: grab;
  font-size: 13px;
  text-align: left;
}
.palette-item:hover {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color);
}
.palette-item:active {
  cursor: grabbing;
}
.palette-icon {
  font-size: 20px;
  width: 22px;
  text-align: center;
  display: inline-block;
  line-height: 1;
}
.palette-label {
  font-size: 13px;
}
.palette-hint {
  margin-top: 12px;
  padding: 10px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  line-height: 1.6;
}

/* 中央 Canvas */
.designer-canvas {
  display: flex;
  min-width: 0;
  min-height: 0;
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(90deg, var(--el-border-color-lighter) 1px, transparent 1px) 0 0 / 24px 24px,
    linear-gradient(var(--el-border-color-lighter) 1px, transparent 1px) 0 0 / 24px 24px,
    var(--el-bg-color);
}
.canvas-container {
  width: 100%;
  height: 100%;
  min-height: 0;
}
.canvas-container :deep(.djs-container) {
  outline: none;
}
.canvas-container :deep(.djs-palette) {
  display: none;
}

/* 右侧 Property Panel */
.designer-property {
  min-width: 0;
  border-left: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  overflow-y: auto;
}

/* 校验结果 */
.validate-section {
  margin: 8px 0;
}
.validate-label {
  font-weight: 600;
  margin-bottom: 4px;
}
.validate-list {
  padding-left: 20px;
  margin: 0;
}
.validate-error {
  color: var(--el-color-danger);
  margin: 2px 0;
}
.validate-warning {
  color: var(--el-color-warning);
  margin: 2px 0;
}
</style>
