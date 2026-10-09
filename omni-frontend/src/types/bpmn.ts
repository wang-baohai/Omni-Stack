/** BPMN moddle 元素的项目最小视图。 */
export interface BpmnModdleElement {
  $type?: string
  $body?: string
  body?: string
  $attrs?: Record<string, string>
  id?: string
  name?: string
  extensionElements?: BpmnExtensionElements
  outgoing?: BpmnModdleElement[]
  default?: BpmnModdleElement
  conditionExpression?: BpmnModdleElement
  values?: BpmnModdleElement[]
  [key: string]: unknown
}

/** BPMN 扩展元素容器。 */
export interface BpmnExtensionElements extends BpmnModdleElement {
  values?: BpmnModdleElement[]
}

/** bpmn-js 画布元素的项目最小视图。 */
export interface BpmnElement {
  id: string
  type?: string
  businessObject: BpmnModdleElement
  x?: number
  y?: number
  width?: number
  height?: number
}

/** bpmn-js Canvas 服务。 */
export interface BpmnCanvas {
  zoom(value: string | number): void
  addMarker(elementId: string, marker: string): void
  removeMarker?(elementId: string, marker: string): void
}

/** bpmn-js Selection 服务。 */
export interface BpmnSelection {
  get(): BpmnElement[]
}

/** bpmn-js Modeling 服务。 */
export interface BpmnModeling {
  updateProperties(element: BpmnElement, properties: Record<string, unknown>): void
  removeElements(elements: BpmnElement[]): void
}

/** bpmn-js Moddle 类型注册表。 */
export interface BpmnModdleRegistry {
  packageMap?: Record<string, unknown>
  registerPackage(descriptor: Record<string, unknown>): void
}

/** bpmn-js Moddle 服务。 */
export interface BpmnModdle {
  registry?: BpmnModdleRegistry
  create(type: string, properties?: Record<string, unknown>): BpmnModdleElement
}

/** bpmn-js ElementRegistry 服务。 */
export interface BpmnElementRegistry {
  get(id: string): BpmnElement | undefined
  getAll(): BpmnElement[]
}

/** bpmn-js Overlays 服务。 */
export interface BpmnOverlays {
  add(elementId: string, options: {
    position: { top?: number; right?: number; bottom?: number; left?: number }
    html: HTMLElement
  }): string
}

/** bpmn-js ElementFactory 服务。 */
export interface BpmnElementFactory {
  createShape(options: { type: string }): BpmnElement
}

/** bpmn-js Create 服务。 */
export interface BpmnCreate {
  start(event: Event, shape: BpmnElement, context?: { source?: BpmnElement }): void
}

/** bpmn-js Connect 服务。 */
export interface BpmnConnect {
  start(event: Event, element: BpmnElement): void
}

/** bpmn-js AutoPlace 服务。 */
export interface BpmnAutoPlace {
  append(source: BpmnElement, shape: BpmnElement): void
}

/** bpmn-js 弹出菜单服务。 */
export interface BpmnPopupMenu {
  open(element: BpmnElement, providerId: string, position: { x: number; y: number }): void
}

/** bpmn-js ContextPad 服务。 */
export interface BpmnContextPad {
  registerProvider(provider: BpmnContextPadProvider): void
  getPad(element: BpmnElement): { html: HTMLElement }
  _popupMenu?: BpmnPopupMenu
  _injector?: { get?(name: string): BpmnPopupMenu | undefined }
}

/** ContextPad 点击或拖拽动作。 */
export interface BpmnContextPadAction {
  click?: ((event: Event, element: BpmnElement) => void)
    | ((event: Event, elements: BpmnElement[]) => void)
  dragstart?: (event: Event, element: BpmnElement) => void
}

/** ContextPad 单个条目。 */
export interface BpmnContextPadEntry {
  group: string
  className: string
  title: string
  action: BpmnContextPadAction
}

/** ContextPad 条目映射。 */
export type BpmnContextPadEntries = Record<string, BpmnContextPadEntry>

/** ContextPad Provider 实例。 */
export interface BpmnContextPadProvider {
  getContextPadEntries?(element: BpmnElement): BpmnContextPadEntries
  getMultiElementContextPadEntries?(elements: BpmnElement[]): BpmnContextPadEntries
}

/** bpmn-js 依赖注入模块。 */
export type BpmnModule = Record<string, unknown>

/** 选中元素变化事件。 */
export interface BpmnSelectionChangedEvent {
  newSelection: BpmnElement[]
}

/** 元素点击事件。 */
export interface BpmnElementClickEvent {
  element: BpmnElement
}
