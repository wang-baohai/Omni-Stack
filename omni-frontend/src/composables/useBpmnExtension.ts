/**
 * @module composables/useBpmnExtension
 * BPMN extensionElements 读写 composable。
 * 处理 omni:assignment、omni:cc、网关条件等自定义扩展元素的读写操作。
 */

import { toRaw } from 'vue'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import type {
  BpmnElement,
  BpmnModeling,
  BpmnModdle,
  BpmnModdleElement,
} from '@/types/bpmn'

const OMNI_NS = 'http://omni.com/workflow'
const FLOWABLE_NS = 'http://flowable.org/bpmn'

/**
 * 确保 Omni / Flowable 自定义类型已注册到 moddle。
 * moddle.create 依赖类型注册表，若 Omni:assignment 等类型未注册会抛出 unknown type 错误。
 * 此函数作为防御性兜底，在 moddle.create 之前调用。
 */
function ensureTypesRegistered(moddle: BpmnModdle) {
  if (!moddle?.registry) return

  if (!moddle.registry.packageMap?.['omni']) {
    try {
      moddle.registry.registerPackage({
        name: 'Omni',
        prefix: 'omni',
        uri: OMNI_NS,
        types: [
          { name: 'assignment', superClass: ['Element'], properties: [{ name: 'body', isBody: true, type: 'String' }] },
          { name: 'cc', superClass: ['Element'], properties: [{ name: 'body', isBody: true, type: 'String' }] },
        ],
      })
    } catch { /* 忽略重复注册 */ }
  }

  if (!moddle.registry.packageMap?.['flowable']) {
    try {
      moddle.registry.registerPackage({
        name: 'Flowable',
        prefix: 'flowable',
        uri: FLOWABLE_NS,
        types: [
          {
            name: 'TaskListener',
            properties: [
              { name: 'event', isAttr: true, type: 'String' },
              { name: 'class', isAttr: true, type: 'String' },
              { name: 'expression', isAttr: true, type: 'String' },
              { name: 'delegateExpression', isAttr: true, type: 'String' },
            ],
          },
          {
            name: 'ExecutionListener',
            properties: [
              { name: 'event', isAttr: true, type: 'String' },
              { name: 'class', isAttr: true, type: 'String' },
              { name: 'expression', isAttr: true, type: 'String' },
              { name: 'delegateExpression', isAttr: true, type: 'String' },
            ],
          },
        ],
        enumerations: [],
      })
    } catch { /* 忽略重复注册 */ }
  }
}

/** 审批节点 assignment 配置 */
export interface AssignmentConfig {
  assignmentType: string
  roleCode: string
  anchorType: string
  anchorParams: Record<string, unknown>
  scopeMode?: string
  fallbackStrategy: string
  approvalMode?: string
}

/** 抄送节点 cc 配置 */
export interface CcConfig {
  serviceType: 'CC'
  recipientType: 'USER_IDS' | 'ROLE_ORG'
  userIds?: number[]
  roleCode?: string
  anchorType?: string
  anchorParams?: Record<string, unknown>
  unitId?: number
  scopeMode?: string
  channels?: string[]
}

/**
 * 从 BPMN 元素读取 omni:assignment 扩展配置。
 *
 * @param element bpmn-js 元素
 * @returns assignment 配置 JSON，未配置时返回 null
 */
export function readAssignment(element: BpmnElement): AssignmentConfig | null {
  const bo = element?.businessObject
  if (!bo) return null

  const values = bo.extensionElements?.values || []
  for (const child of values) {
    if (child.$type?.includes('assignment') || child.$type === 'omni:assignment') {
      try {
        // body 存储位置优先级：$body（moddle-xml GenericElementHandler 导入时设置）、body（moddle.create isBody:true 写入时）、$attrs.body（旧 createAny 格式）
        const text = child.$body || child.body || child.$attrs?.body || ''
        if (!text) return null
        return JSON.parse(text) as AssignmentConfig
      } catch {
        return null
      }
    }
  }

  return null
}

/**
 * 向 BPMN UserTask 元素写入 omni:assignment 扩展配置。
 *
 * @param modeler bpmn-js Modeler 实例
 * @param element BPMN 元素
 * @param config assignment 配置
 */
export function writeAssignment(modeler: BpmnModeler, element: BpmnElement, config: AssignmentConfig) {
  if (!modeler || !element) return

  // 解包 Vue 响应式 Proxy，避免与 bpmn-js 内部 Proxy 冲突
  const rawElement = toRaw(element)
  const moddle = modeler.get<BpmnModdle>('moddle')
  const modeling = modeler.get<BpmnModeling>('modeling')
  const bo = rawElement.businessObject

  // 保留已有的非 assignment/taskListener 扩展元素
  const existingExt = bo.extensionElements
  const keptValues: BpmnModdleElement[] = []
  if (existingExt?.values) {
    for (const v of existingExt.values) {
      if (!v.$type?.includes('assignment')
        && !v.$type?.includes('taskListener')
        && !v.$type?.includes('TaskListener')) {
        keptValues.push(v)
      }
    }
  }

  // 确保自定义类型已注册，防止 moddle.create 抛出 unknown type 错误
  ensureTypesRegistered(moddle)

  // 创建 omni:assignment 元素（使用 moddle.create 确保 body 序列化为文本内容而非 XML 属性）
  const assignmentElement = moddle.create('omni:assignment', {
    body: JSON.stringify(config),
  })
  keptValues.push(assignmentElement)

  // 创建 flowable:taskListener
  const listenerElement = moddle.create('flowable:TaskListener', {
    event: 'create',
    delegateExpression: '${scopedRoleAssignmentListener}',
  })
  keptValues.push(listenerElement)

  // 每次创建全新的 extensionElements，确保 saveXML 能感知变更
  const extensionElements = moddle.create('bpmn:ExtensionElements')
  extensionElements.values = keptValues
  bo.extensionElements = extensionElements

  modeling.updateProperties(rawElement, { extensionElements })
}

/**
 * 从 BPMN ServiceTask 元素读取 omni:cc 扩展配置。
 *
 * @param element bpmn-js 元素
 * @returns cc 配置 JSON，未配置时返回 null
 */
export function readCcConfig(element: BpmnElement): CcConfig | null {
  const bo = element?.businessObject
  if (!bo) return null

  const values = bo.extensionElements?.values || []
  for (const child of values) {
    if (child.$type?.includes('cc') || child.$type === 'omni:cc') {
      try {
        // body 存储位置优先级：$body（导入时）、body（写入时）、$attrs.body（旧格式）
        const text = child.$body || child.body || child.$attrs?.body || ''
        if (!text) return null
        return JSON.parse(text) as CcConfig
      } catch {
        return null
      }
    }
  }

  return null
}

/**
 * 向 BPMN ServiceTask 元素写入 omni:cc 扩展配置。
 *
 * @param modeler bpmn-js Modeler 实例
 * @param element BPMN 元素
 * @param config cc 配置
 */
export function writeCcConfig(modeler: BpmnModeler, element: BpmnElement, config: CcConfig) {
  if (!modeler || !element) return

  // 解包 Vue 响应式 Proxy，避免与 bpmn-js 内部 Proxy 冲突
  const rawElement = toRaw(element)
  const moddle = modeler.get<BpmnModdle>('moddle')
  const modeling = modeler.get<BpmnModeling>('modeling')
  const bo = rawElement.businessObject

  // 保留已有的非 cc 扩展元素
  const existingExt = bo.extensionElements
  const keptValues: BpmnModdleElement[] = []
  if (existingExt?.values) {
    for (const v of existingExt.values) {
      if (!v.$type?.includes('cc')) {
        keptValues.push(v)
      }
    }
  }

  // 确保自定义类型已注册
  ensureTypesRegistered(moddle)

  const ccElement = moddle.create('omni:cc', {
    body: JSON.stringify(config),
  })
  keptValues.push(ccElement)

  // 每次创建全新的 extensionElements，确保 saveXML 能感知变更
  const extensionElements = moddle.create('bpmn:ExtensionElements')
  extensionElements.values = keptValues
  bo.extensionElements = extensionElements

  modeling.updateProperties(rawElement, {
    extensionElements,
    'flowable:delegateExpression': '${ccNotifyDelegate}',
  })
}

/**
 * 读取排他网关的条件配置。
 *
 * @param element bpmn-js Gateway 元素
 * @returns 默认分支 ID 和分支条件映射
 */
export function readGatewayConditions(element: BpmnElement): {
  defaultFlow: string | null
  conditions: Record<string, string>
} {
  const bo = element?.businessObject
  if (!bo) return { defaultFlow: null, conditions: {} }

  const defaultFlow = bo.default?.id || null
  const conditions: Record<string, string> = {}

  const outgoing = bo.outgoing || []
  for (const flow of outgoing) {
    const condExpr = flow.conditionExpression
    if (condExpr) {
      if (flow.id) conditions[flow.id] = typeof condExpr.body === 'string' ? condExpr.body : ''
    }
  }

  return { defaultFlow, conditions }
}

/**
 * 写入排他网关的默认分支。
 *
 * @param modeler bpmn-js Modeler 实例
 * @param element Gateway 元素
 * @param defaultFlowId 默认分支 SequenceFlow ID
 */
export function writeGatewayDefault(modeler: BpmnModeler, element: BpmnElement, defaultFlowId: string) {
  if (!modeler || !element) return

  const rawElement = toRaw(element)
  const modeling = modeler.get<BpmnModeling>('modeling')
  const bo = rawElement.businessObject
  const outgoing = bo.outgoing || []
  const targetFlow = outgoing.find(flow => flow.id === defaultFlowId)

  if (targetFlow) {
    modeling.updateProperties(rawElement, { default: targetFlow })
  }
}

/**
 * 写入 SequenceFlow 的条件表达式。
 *
 * @param modeler bpmn-js Modeler 实例
 * @param flowElement SequenceFlow 元素
 * @param condition 条件表达式
 */
export function writeGatewayCondition(modeler: BpmnModeler, flowElement: BpmnElement, condition: string) {
  if (!modeler || !flowElement) return

  const rawElement = toRaw(flowElement)
  const moddle = modeler.get<BpmnModdle>('moddle')
  const modeling = modeler.get<BpmnModeling>('modeling')

  if (condition && condition.trim()) {
    const conditionExpr = moddle.create('bpmn:FormalExpression', {
      body: condition,
    })
    modeling.updateProperties(rawElement, {
      conditionExpression: conditionExpr,
    })
  } else {
    modeling.updateProperties(rawElement, {
      conditionExpression: undefined,
    })
  }
}

/** omni 命名空间的 moddle 描述符，传给 Modeler 构造函数的 moddleExtensions */
export const OMNI_MODDLE_DESCRIPTOR = {
  name: 'Omni',
  prefix: 'omni',
  uri: OMNI_NS,
  types: [
    { name: 'assignment', superClass: ['Element'], properties: [{ name: 'body', isBody: true, type: 'String' }] },
    { name: 'cc', superClass: ['Element'], properties: [{ name: 'body', isBody: true, type: 'String' }] },
  ],
}

/** flowable 命名空间的 moddle 描述符 */
export const FLOWABLE_MODDLE_DESCRIPTOR = {
  name: 'Flowable',
  prefix: 'flowable',
  uri: FLOWABLE_NS,
  types: [
    {
      name: 'TaskListener',
      properties: [
        { name: 'event', isAttr: true, type: 'String' },
        { name: 'class', isAttr: true, type: 'String' },
        { name: 'expression', isAttr: true, type: 'String' },
        { name: 'delegateExpression', isAttr: true, type: 'String' },
      ],
    },
    {
      name: 'ExecutionListener',
      properties: [
        { name: 'event', isAttr: true, type: 'String' },
        { name: 'class', isAttr: true, type: 'String' },
        { name: 'expression', isAttr: true, type: 'String' },
        { name: 'delegateExpression', isAttr: true, type: 'String' },
      ],
    },
  ],
  enumerations: [],
}

/** bpmn-js Modeler moddleExtensions 配置（构造时传入，确保自定义命名空间可被正确解析和序列化） */
export const MODDLE_EXTENSIONS = {
  Omni: OMNI_MODDLE_DESCRIPTOR,
  Flowable: FLOWABLE_MODDLE_DESCRIPTOR,
}

/**
 * 注册 omni 和 flowable 命名空间到 moddle（确保自定义扩展元素可被正确解析和序列化）。
 * 必须在 importXML 之前调用，否则包含 omni:/flowable: 扩展的 XML 会导入失败。
 *
 * @param moddle bpmn-js moddle 实例
 */
export function registerOmniModdle(moddle: BpmnModdle) {
  if (!moddle) return

  // 注册 omni 命名空间
  try {
    if (!moddle.registry?.packageMap?.['omni']) {
      moddle.registry?.registerPackage({
        name: 'Omni',
        prefix: 'omni',
        uri: OMNI_NS,
        types: [
          { name: 'assignment', superClass: ['Element'], properties: [{ name: 'body', isBody: true, type: 'String' }] },
          { name: 'cc', superClass: ['Element'], properties: [{ name: 'body', isBody: true, type: 'String' }] },
        ],
      })
    }
  } catch {
    // 命名空间注册失败不影响基本功能
  }

  // 注册 flowable 命名空间（taskListener / executionListener / collection 等扩展属性）
  try {
    if (!moddle.registry?.packageMap?.['flowable']) {
      moddle.registry?.registerPackage({
        name: 'Flowable',
        prefix: 'flowable',
        uri: FLOWABLE_NS,
        types: [
          {
            name: 'TaskListener',
            properties: [
              { name: 'event', isAttr: true, type: 'String' },
              { name: 'class', isAttr: true, type: 'String' },
              { name: 'expression', isAttr: true, type: 'String' },
              { name: 'delegateExpression', isAttr: true, type: 'String' },
            ],
          },
          {
            name: 'ExecutionListener',
            properties: [
              { name: 'event', isAttr: true, type: 'String' },
              { name: 'class', isAttr: true, type: 'String' },
              { name: 'expression', isAttr: true, type: 'String' },
              { name: 'delegateExpression', isAttr: true, type: 'String' },
            ],
          },
        ],
        enumerations: [],
      })
    }
  } catch {
    // 命名空间注册失败不影响基本功能
  }
}
