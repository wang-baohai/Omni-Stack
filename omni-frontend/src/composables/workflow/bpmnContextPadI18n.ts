/**
 * @module composables/workflow/bpmnContextPadI18n
 * bpmn-js 上下文操作面板（Context Pad）国际化模块。
 *
 * 监听 contextPad.open 事件（entry 已全部渲染到 DOM），
 * 根据 data-action 属性查找 i18n key 替换 title。
 * 作为 additionalModules 注入 BpmnModeler，对画布行为零侵入。
 */
import i18n from '@/i18n'

const { t } = i18n.global

/**
 * 遍历 context pad DOM 中所有 .entry 元素，
 * 根据 data-action 属性值查找 i18n key 并替换 title。
 *
 * @param event contextPad.open 事件，包含 current.html（pad 根 DOM 元素）
 */
function translateContextPad(event: { current: { html: HTMLElement } }): void {
  const padEl = event.current?.html
  if (!padEl) return

  const entries = padEl.querySelectorAll<HTMLElement>('.entry[data-action]')
  entries.forEach((el) => {
    const action = el.getAttribute('data-action')
    if (!action) return

    const key = `workflow.contextPad.${action}`
    const translated = t(key)
    // i18n 未匹配时返回 key 本身，跳过
    if (translated !== key) {
      el.setAttribute('title', translated)
    }
  })
}

/**
 * bpmn-js 模块定义。
 * 监听 contextPad.open 事件，在 entry DOM 渲染后翻译条目。
 */
function I18nContextPadModule(eventBus: any): void {
  eventBus.on('contextPad.open', translateContextPad)
}

I18nContextPadModule.$inject = ['eventBus']

/** 导出可直接作为 additionalModules 元素使用 */
export const contextPadI18nModule = {
  __init__: ['i18nContextPad'],
  i18nContextPad: ['type', I18nContextPadModule],
}
