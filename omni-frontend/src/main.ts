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
import router from './router'
import i18n from './i18n'
import './styles/index.scss'
import { initTheme } from './stores/app'
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
