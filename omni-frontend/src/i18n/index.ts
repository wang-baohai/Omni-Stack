import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN'
import enUS from '@/locales/en-US'

const LANG_KEY = 'omni-lang'

export function getStoredLang(): string {
  return localStorage.getItem(LANG_KEY) || 'zh-CN'
}

export function storeLang(lang: string) {
  localStorage.setItem(LANG_KEY, lang)
}

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
