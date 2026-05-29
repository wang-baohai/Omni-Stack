import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface AppSettings {
  sidebarCollapsed: boolean
  theme: 'light' | 'dark'
}

const THEME_KEY = 'omni-theme'

function applyThemeClass(theme: 'light' | 'dark') {
  if (theme === 'dark') {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const theme = ref<'light' | 'dark'>(
    (localStorage.getItem(THEME_KEY) as 'light' | 'dark') || 'dark',
  )

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setTheme(newTheme: 'light' | 'dark') {
    theme.value = newTheme
    applyThemeClass(newTheme)
    localStorage.setItem(THEME_KEY, newTheme)
  }

  return { sidebarCollapsed, theme, toggleSidebar, setTheme }
})

export function initTheme() {
  const stored = (localStorage.getItem(THEME_KEY) as 'light' | 'dark') || 'dark'
  applyThemeClass(stored)
}
