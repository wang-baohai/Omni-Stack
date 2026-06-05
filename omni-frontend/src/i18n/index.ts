/**
 * @module i18n
 * 国际化配置模块。
 * 基于 vue-i18n 创建多语言实例，支持中文（zh-CN）和英文（en-US），
 * 语言偏好持久化到 localStorage。
 */
import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN'
import enUS from '@/locales/en-US'

/** localStorage 中存储语言偏好的键名 */
const LANG_KEY = 'omni-lang'

/**
 * 获取 localStorage 中存储的语言偏好。
 * 默认返回 'zh-CN'。
 *
 * @returns 语言标识字符串
 */
export function getStoredLang(): string {
  return localStorage.getItem(LANG_KEY) || 'zh-CN'
}

/**
 * 将语言偏好持久化到 localStorage。
 *
 * @param lang 语言标识
 */
export function storeLang(lang: string) {
  localStorage.setItem(LANG_KEY, lang)
}

/** 创建 Vue I18n 国际化实例，默认使用 localStorage 中存储的语言 */
const i18n = createI18n({
  legacy: false,
  locale: getStoredLang(),
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export default i18n
