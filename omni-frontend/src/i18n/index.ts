/**
 * @module i18n
 * 国际化配置模块。
 * 基于 vue-i18n 创建多语言实例，支持中文、英文、日文和韩文，
 * 语言偏好持久化到 localStorage。
 */
import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN'
import enUS from '@/locales/en-US'
import jaJP from '@/locales/ja-JP'
import koKR from '@/locales/ko-KR'

/** localStorage 中存储语言偏好的键名 */
const LANG_KEY = 'omni-lang'

/** 应用支持的界面语言。 */
export const SUPPORTED_LOCALES = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR'] as const

/** 受支持的界面语言标识。 */
export type SupportedLocale = typeof SUPPORTED_LOCALES[number]

/** 判断持久化字符串是否为受支持语言。 */
export function isSupportedLocale(value: string | null): value is SupportedLocale {
  return value !== null && SUPPORTED_LOCALES.includes(value as SupportedLocale)
}

/**
 * 获取 localStorage 中存储的语言偏好。
 * 默认返回 'zh-CN'。
 *
 * @returns 语言标识字符串
 */
export function getStoredLang(): SupportedLocale {
  const stored = localStorage.getItem(LANG_KEY)
  return isSupportedLocale(stored) ? stored : 'zh-CN'
}

/**
 * 将语言偏好持久化到 localStorage。
 *
 * @param lang 语言标识
 */
export function storeLang(lang: SupportedLocale) {
  localStorage.setItem(LANG_KEY, lang)
  document.documentElement.lang = lang
}

/** 创建 Vue I18n 国际化实例，默认使用 localStorage 中存储的语言 */
const i18n = createI18n({
  legacy: false,
  locale: getStoredLang(),
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
    'ja-JP': jaJP,
    'ko-KR': koKR,
  },
})

document.documentElement.lang = getStoredLang()

export default i18n
