/**
 * @module composables/workflow/bpmnContextPadProvider
 * 自定义 bpmn-js Context Pad Provider。
 *
 * 完全替换 bpmn-js 默认 provider，仅展示项目支持的 BPMN 元素类型。
 * - Append 组：创建项目支持的形状（UserTask 代替通用 Task）
 * - Replace 组：在项目支持的类型间切换（排除自身）
 * - Delete / Connect 等基础操作
 *
 * 通过 DI key `contextPadProvider` 注入，作为 additionalModules 使用时
 * 自动覆盖默认 provider。
 */
import type {
  BpmnAutoPlace,
  BpmnConnect,
  BpmnContextPad,
  BpmnContextPadEntries,
  BpmnContextPadEntry,
  BpmnContextPadProvider,
  BpmnCreate,
  BpmnElement,
  BpmnElementFactory,
  BpmnModeling,
} from '@/types/bpmn'

/** 项目支持的 BPMN 元素类型定义 */
interface SupportedType {
  /** bpmn-js 类型标识 */
  type: string
  /** action ID（用于 context pad entry key 和 i18n key 映射） */
  id: string
  /** CSS 图标类名 */
  icon: string
  /** 分组标识（model / edit / connect） */
  group: string
}

/** 可追加的元素类型（排除连线和流程根元素） */
const APPENDABLE_TYPES: SupportedType[] = [
  { type: 'bpmn:UserTask', id: 'user-task', icon: 'bpmn-icon-user-task', group: 'model' },
  { type: 'bpmn:ServiceTask', id: 'service-task', icon: 'bpmn-icon-service-task', group: 'model' },
  { type: 'bpmn:StartEvent', id: 'start-event', icon: 'bpmn-icon-start-event-none', group: 'model' },
  { type: 'bpmn:EndEvent', id: 'end-event', icon: 'bpmn-icon-end-event-none', group: 'model' },
  { type: 'bpmn:IntermediateThrowEvent', id: 'intermediate-event', icon: 'bpmn-icon-intermediate-event-none', group: 'model' },
  { type: 'bpmn:ExclusiveGateway', id: 'exclusive-gateway', icon: 'bpmn-icon-gateway-xor', group: 'model' },
  { type: 'bpmn:ParallelGateway', id: 'parallel-gateway', icon: 'bpmn-icon-gateway-parallel', group: 'model' },
  { type: 'bpmn:InclusiveGateway', id: 'inclusive-gateway', icon: 'bpmn-icon-gateway-or', group: 'model' },
  { type: 'bpmn:TextAnnotation', id: 'text-annotation', icon: 'bpmn-icon-text-annotation', group: 'model' },
]

/** 可替换的目标类型（与可追加相同） */
const REPLACEABLE_TYPES = APPENDABLE_TYPES

/** 不需要 append/connect 的元素类型 */
const NO_APPEND_TYPES = new Set([
  'bpmn:Process',
  'bpmn:SequenceFlow',
  'bpmn:TextAnnotation',
  'label',
])

const NO_CONNECT_TYPES = new Set([
  'bpmn:TextAnnotation',
  'label',
])

/** 自定义 Provider 运行时实例。 */
interface CustomContextPadProviderInstance extends BpmnContextPadProvider {
  _contextPad: BpmnContextPad
  _modeling: BpmnModeling
  _elementFactory: BpmnElementFactory
  _create: BpmnCreate
  _connect: BpmnConnect
  _autoPlace?: BpmnAutoPlace
  _translate: (text: string) => string
}

/**
 * 自定义 Context Pad Provider 构造函数。
 * 通过 bpmn-js DI 系统注入所需服务。
 */
function CustomContextPadProvider(
  this: CustomContextPadProviderInstance,
  contextPad: BpmnContextPad,
  modeling: BpmnModeling,
  elementFactory: BpmnElementFactory,
  create: BpmnCreate,
  connect: BpmnConnect,
  autoPlace: BpmnAutoPlace | undefined,
  translate: (text: string) => string,
): void {
  // 注册为 context pad provider
  contextPad.registerProvider(this)

  this._contextPad = contextPad
  this._modeling = modeling
  this._elementFactory = elementFactory
  this._create = create
  this._connect = connect
  this._autoPlace = autoPlace
  this._translate = translate
}

CustomContextPadProvider.$inject = [
  'contextPad',
  'modeling',
  'elementFactory',
  'create',
  'connect',
  'autoPlace',
  'translate',
]

/**
 * 多元素选中时的 context pad 条目（仅 delete）。
 */
CustomContextPadProvider.prototype.getMultiElementContextPadEntries = function (
  this: CustomContextPadProviderInstance,
  _elements: BpmnElement[],
): BpmnContextPadEntries {
  const modeling = this._modeling

  return {
    delete: {
      group: 'edit',
      className: 'bpmn-icon-trash',
      title: this._translate('Delete'),
      action: {
        click(_event: Event, elements: BpmnElement[]) {
          modeling.removeElements(elements.slice())
        },
      },
    },
  }
}

/**
 * 获取单个元素的 context pad 条目。
 *
 * @param element 当前选中的 BPMN 元素
 * @returns context pad 条目映射
 */
CustomContextPadProvider.prototype.getContextPadEntries = function (
  this: CustomContextPadProviderInstance,
  element: BpmnElement,
): BpmnContextPadEntries {
  const modeling = this._modeling
  const elementFactory = this._elementFactory
  const create = this._create
  const connect = this._connect
  const autoPlace = this._autoPlace
  const translate = this._translate
  const contextPad = this._contextPad

  const actions: BpmnContextPadEntries = {}
  const type = element.type || element.businessObject?.$type || ''

  // ===== label 类型只显示 delete =====
  if (type === 'label') {
    actions.delete = createDeleteAction()
    return actions
  }

  // ===== 基础操作：Delete =====
  actions.delete = createDeleteAction()

  // ===== Process / SequenceFlow 只显示 delete =====
  if (NO_APPEND_TYPES.has(type)) {
    // SequenceFlow 额外支持 delete，不支持 append/connect
    return actions
  }

  // ===== Append 组 =====
  for (const item of APPENDABLE_TYPES) {
    actions[`append.${item.type.replace('bpmn:', '').replace(/([A-Z])/g, '-$1').toLowerCase().replace(/^-/, '')}`] = createAppendAction(item)
  }

  // ===== Connect（非 TextAnnotation 支持） =====
  if (!NO_CONNECT_TYPES.has(type)) {
    actions.connect = {
      group: 'connect',
      className: 'bpmn-icon-connection-multi',
      title: translate('Connect to other element'),
      action: {
        click(event: Event, element: BpmnElement) {
          connect.start(event, element)
        },
      },
    }
  }

  // ===== Replace（更改类型） =====
  const currentBpmnType = element.businessObject?.$type
  if (currentBpmnType && REPLACEABLE_TYPES.some(r => r.type === currentBpmnType)) {
    actions.replace = {
      group: 'edit',
      className: 'bpmn-icon-screw-wrench',
      title: translate('Change element'),
      action: {
        click(_event: Event, element: BpmnElement) {
          // 打开替换菜单（使用 popupMenu）
          const popupMenu = contextPad._popupMenu || contextPad._injector?.get?.('popupMenu')
          if (popupMenu) {
            const position = getReplaceMenuPosition(element)
            popupMenu.open(element, 'bpmn-replace', position)
          }
        },
      },
    }
  }

  return actions

  // ===== 内部辅助函数 =====

  /** 创建 delete action */
  function createDeleteAction(): BpmnContextPadEntry {
    return {
      group: 'edit',
      className: 'bpmn-icon-trash',
      title: translate('Delete'),
      action: {
        click(_event: Event, element: BpmnElement) {
          modeling.removeElements([element])
        },
      },
    }
  }

  /** 创建 append action */
  function createAppendAction(item: SupportedType): BpmnContextPadEntry {
    function appendStart(event: Event, element: BpmnElement) {
      const shape = elementFactory.createShape({ type: item.type })
      create.start(event, shape, { source: element })
    }

    const clickAction = autoPlace
      ? function (_event: Event, element: BpmnElement) {
        const shape = elementFactory.createShape({ type: item.type })
        autoPlace.append(element, shape)
      }
      : appendStart

    return {
      group: item.group,
      className: item.icon,
      title: translate(`Append ${item.type.replace('bpmn:', '')}`),
      action: {
        dragstart: appendStart,
        click: clickAction,
      },
    }
  }

  /** 计算替换菜单位置 */
  function getReplaceMenuPosition(element: BpmnElement): { x: number; y: number } {
    const pad = contextPad.getPad(element).html
    const padRect = pad.getBoundingClientRect()
    return {
      x: padRect.left,
      y: padRect.bottom + 5,
    }
  }
}

/** 导出 bpmn-js 模块定义，使用同名 DI key 替换默认 provider */
export const contextPadProviderModule = {
  __init__: ['contextPadProvider'],
  contextPadProvider: ['type', CustomContextPadProvider],
}
