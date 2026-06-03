import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 应用设置类型定义。
 */
export interface AppSettings {
  /** 侧边栏是否折叠 */
  sidebarCollapsed: boolean
  /** 当前主题模式 */
  theme: 'light' | 'dark'
}

/** localStorage 中存储主题偏好的键名 */
const THEME_KEY = 'omni-theme'

/**
 * 应用主题 CSS 类到 HTML 根元素。
 * 暗色模式添加 'dark' 类，浅色模式移除。
 *
 * @param theme 主题模式
 */
function applyThemeClass(theme: 'light' | 'dark') {
  if (theme === 'dark') {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

/**
 * 应用状态管理 Store。
 * 管理侧边栏折叠状态和主题模式。
 */
export const useAppStore = defineStore('app', () => {
  /** 侧边栏折叠状态 */
  const sidebarCollapsed = ref(false)
  /** 当前主题，从 localStorage 读取或使用默认暗色模式 */
  const theme = ref<'light' | 'dark'>(
    (localStorage.getItem(THEME_KEY) as 'light' | 'dark') || 'dark',
  )

  /** 切换侧边栏折叠状态 */
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /**
   * 设置主题模式。
   * 同时更新状态、应用 CSS 类和持久化到 localStorage。
   *
   * @param newTheme 新的主题模式
   */
  function setTheme(newTheme: 'light' | 'dark') {
    theme.value = newTheme
    applyThemeClass(newTheme)
    localStorage.setItem(THEME_KEY, newTheme)
  }

  return { sidebarCollapsed, theme, toggleSidebar, setTheme }
})

/**
 * 初始化主题（在应用挂载后调用）。
 * 从 localStorage 读取存储的主题偏好并应用到 HTML 根元素。
 */
export function initTheme() {
  const stored = (localStorage.getItem(THEME_KEY) as 'light' | 'dark') || 'dark'
  applyThemeClass(stored)
}
