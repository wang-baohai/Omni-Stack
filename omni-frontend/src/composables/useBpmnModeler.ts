/**
 * @module composables/useBpmnModeler
 * bpmn-js Modeler 生命周期管理 composable。
 * 从 BpmnDesigner.vue 提取：Modeler 实例创建/销毁、XML 导入导出、事件监听。
 */
import { ref, watch, onMounted, onBeforeUnmount, toRaw, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { contextPadI18nModule } from '@/composables/workflow/bpmnContextPadI18n'
import { contextPadProviderModule } from '@/composables/workflow/bpmnContextPadProvider'
import { MODDLE_EXTENSIONS } from '@/composables/useBpmnExtension'
import {
  type BpmnCanvas,
  type BpmnElement,
  type BpmnModeling,
  type BpmnModdle,
  type BpmnModdleElement,
  type BpmnSelection,
  type BpmnSelectionChangedEvent,
} from '@/types/bpmn'
import { getErrorMessage } from '@/utils/errors'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'

interface UseBpmnModelerOptions {
  /** 容器 DOM 元素 ref */
  container: Ref<HTMLDivElement | undefined>
  /** 是否只读模式 */
  readonly?: boolean
  /** 初始 XML */
  initialXml?: string
  /** XML 内容变化回调 */
  onChanged?: (xml: string) => void
  /** 选中元素变化回调 */
  onSelectionChanged?: (element: BpmnElement | null) => void
  /** Ctrl+S 保存回调（优先级高于 onChanged） */
  onSave?: () => void
  /** 每次导入 XML 前的回调（用于注册自定义命名空间到 moddle） */
  onBeforeImport?: () => void
}

/** 默认空白 BPMN 模板 */
const DEFAULT_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  id="Definitions_1"
  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

/** bpmn-js 期望的标准命名空间 URI（仅 BPMN 和 BPMNDI 需要归一化，DC/DI 原始 URI 已正确） */
const NS_MAP: Record<string, string> = {
  'http://www.omg.org/spec/BPMN20': 'http://www.omg.org/spec/BPMN/20100524/MODEL',
  'http://www.omg.org/spec/BPMNDI': 'http://www.omg.org/spec/BPMN/20100524/DI',
}

/**
 * 归一化 BPMN XML 中的命名空间 URI。
 * 将旧版 OMG 命名空间 URI 替换为 bpmn-js 期望的标准 URI，
 * 覆盖 BPMN20、BPMNDI、DC、DI 四个命名空间。
 */
function normalizeBpmnXml(xml: string): string {
  let result = xml
  for (const [legacy, standard] of Object.entries(NS_MAP)) {
    result = result.split(legacy).join(standard)
  }
  return result
}

export function useBpmnModeler(options: UseBpmnModelerOptions) {
  const modeler = ref<BpmnModeler | null>(null) as Ref<BpmnModeler | null>
  const loading = ref(false)
  const currentXml = ref('')

  /** 导入 BPMN XML 到编辑器 */
  async function importXml(xml: string) {
    if (!modeler.value) return
    loading.value = true
    try {
      // 导入前注册自定义命名空间，确保 omni:/flowable: 扩展元素可被解析
      options.onBeforeImport?.()
      // 归一化 BPMN 命名空间 URI，避免旧版 URI 导致解析失败
      const normalizedXml = normalizeBpmnXml(xml)
      await modeler.value.importXML(normalizedXml)
      const canvas = modeler.value.get<BpmnCanvas>('canvas')
      canvas.zoom('fit-viewport')
      // 暗色模式下修正形状和文字颜色
      fixDarkModeColors()
      currentXml.value = xml
    } catch (error: unknown) {
      ElMessage.error('导入 BPMN XML 失败：' + getErrorMessage(error, '未知错误'))
    } finally {
      loading.value = false
    }
  }

  /** 导出当前编辑器的 BPMN XML */
  async function exportXml(): Promise<string> {
    if (!modeler.value) return ''
    const result = await modeler.value.saveXML({ format: true })
    currentXml.value = result.xml || ''
    return result.xml || ''
  }

  /** 获取当前选中的 BPMN 元素 */
  function getSelectedElement(): BpmnElement | null {
    if (!modeler.value) return null
    const selection = modeler.value.get<BpmnSelection>('selection')
    const selected = selection.get()
    return selected.length > 0 ? selected[0] : null
  }

  /** 更新选中元素的属性 */
  function updateElementProperties(element: BpmnElement, properties: Record<string, unknown>) {
    if (!modeler.value || !element) return
    const modeling = modeler.value.get<BpmnModeling>('modeling')
    modeling.updateProperties(toRaw(element), properties)
  }

  /** 获取元素的 moddle 扩展元素 */
  function getExtensionElements(element: BpmnElement): BpmnModdleElement[] {
    if (!element?.businessObject?.extensionElements) return []
    return element.businessObject.extensionElements.values || []
  }

  /** 获取 moddle（BPMN 模型工厂） */
  function getModdle(): BpmnModdle | null {
    if (!modeler.value) return null
    return modeler.value.get<BpmnModdle>('moddle')
  }

  /** 判断当前是否暗色模式 */
  function isDarkMode(): boolean {
    return document.documentElement.classList.contains('dark')
  }

  /** 暗色模式下强制修正 SVG 元素颜色（覆盖 bpmn-js 内联属性） */
  function fixDarkModeColors() {
    if (!isDarkMode() || !options.container.value) return
    const svgEl = options.container.value.querySelector('.djs-container svg')
    if (!svgEl) return

    const shapeBg = 'rgba(30, 30, 40, 0.85)'
    const shapeStroke = 'rgba(160, 165, 180, 0.55)'
    const textColor = '#dce1eb'
    const lineColor = 'rgba(140, 145, 160, 0.6)'

    // 1. 修正形状填充（矩形、圆形、菱形、路径）
    svgEl.querySelectorAll('.djs-visual > rect, .djs-visual > circle, .djs-visual > ellipse, .djs-visual > polygon').forEach((el) => {
      const s = (el as HTMLElement).style
      s.setProperty('fill', shapeBg)
      s.setProperty('stroke', shapeStroke)
    })

    // 2. 修正顺序流/连接线
    svgEl.querySelectorAll('.djs-connection .djs-visual > path').forEach((el) => {
      (el as HTMLElement).style.setProperty('stroke', lineColor)
    })

    // 3. 修正所有文字标签
    svgEl.querySelectorAll('.djs-label, .djs-label text, .djs-label tspan').forEach((el) => {
      (el as HTMLElement).style.setProperty('fill', textColor)
    })
  }

  /** 初始化 Modeler */
  function initModeler() {
    if (!options.container.value) return

    const additionalModules = [contextPadI18nModule, contextPadProviderModule]

    // 暗色模式下设置浅色标签，确保文字可见
    const dark = isDarkMode()
    modeler.value = new BpmnModeler({
      container: options.container.value,
      additionalModules,
      moddleExtensions: MODDLE_EXTENSIONS,
      ...(dark ? { bpmnRenderer: { defaultLabelColor: '#dce1eb' } } : {}),
    })

    // 元素变更后修正文字颜色（暗色模式）
    if (dark) {
      modeler.value.on('element.changed', () => {
        requestAnimationFrame(fixDarkModeColors)
      })
    }

    // 监听内容变化
    modeler.value.on('commandStack.changed', async () => {
      const xml = await exportXml()
      options.onChanged?.(xml)
    })

    // 监听选中变化
    if (options.onSelectionChanged) {
      modeler.value.on('selection.changed', (event: BpmnSelectionChangedEvent) => {
        const selected = event.newSelection
        options.onSelectionChanged?.(selected.length > 0 ? selected[0] : null)
      })
    }
  }

  /** 保存快捷键处理 */
  function handleKeydown(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault()
      if (options.onSave) {
        options.onSave()
      } else {
        exportXml().then(xml => options.onChanged?.(xml))
      }
    }
  }

  // 延迟容器初始化：el-dialog 首次打开才渲染 body，canvasRef 可能在 onMounted 时尚未就绪
  watch(options.container, async (el) => {
    if (el && !modeler.value) {
      initModeler()
      await importXml(options.initialXml || DEFAULT_BPMN)
    }
  })

  onMounted(async () => {
    initModeler()
    if (modeler.value) {
      await importXml(options.initialXml || DEFAULT_BPMN)
    }
    document.addEventListener('keydown', handleKeydown)
  })

  /** 销毁 Modeler 实例，释放 DOM 绑定（用于对话框关闭时清理） */
  function destroy() {
    if (modeler.value) {
      modeler.value.destroy()
      modeler.value = null
    }
  }

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', handleKeydown)
    destroy()
  })

  return {
    modeler,
    loading,
    currentXml,
    initModeler,
    importXml,
    exportXml,
    destroy,
    getSelectedElement,
    updateElementProperties,
    getExtensionElements,
    getModdle,
  }
}
