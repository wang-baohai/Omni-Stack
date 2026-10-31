/**
 * @module main
 * 应用启动入口。
 * 初始化 Vue 应用实例，注册全局插件（Pinia、Vue Router、Element Plus、I18n），
 * 挂载到 DOM 后执行主题初始化。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

import App from './App.vue'
import router, { clearSessionInMemory } from './router'
import i18n from './i18n'
import './styles/index.scss'
import { initTheme } from './stores/app'
import { useUserStore } from './stores/user'
import { initSessionSync } from './utils/session-sync'
import { resolveSessionSyncRedirect } from './utils/navigation'
import { setupPermissionDirective } from './directives/permission'
import { installElementPlus } from './plugins/element-plus'

// 创建 Vue 应用实例
const app = createApp(App)

// 注册插件：状态管理、路由、UI 框架、国际化
app.use(createPinia())
app.use(router)
installElementPlus(app)
app.use(i18n)

// 注册自定义指令
setupPermissionDirective(app)

// 挂载应用到 DOM
app.mount('#app')

// 挂载后初始化主题（从 localStorage 读取主题偏好，应用暗色样式类）
initTheme()

// 跨标签会话同步：另一标签退出或换号时，本标签先复用路由层的统一清理入口回收旧会话
// （令牌与用户名、权限快照、动态路由；工作台收藏与最近访问随身份键自动作废），
// 再写入新身份并重新进入当前路由，由既有守卫按新身份重建菜单并给出合法落点。
// 监听只在应用入口安装一次，本文件不另存令牌，也不把令牌放进导航参数。
const userStore = useUserStore()
initSessionSync({
  local: () => ({ token: userStore.token, username: userStore.username }),
  apply: (next) => userStore.applyLocalSession(next.token, next.username),
  signOut: () => clearSessionInMemory(),
  rerun: () => {
    const target = resolveSessionSyncRedirect(router.currentRoute.value)
    if (!target) return undefined
    return router.replace(target).catch(() => undefined)
  },
})
