/**
 * @module directives/permission
 * v-permission 自定义指令。
 * 根据用户权限编码控制 DOM 元素的显隐。
 *
 * 使用方式：
 * ```vue
 * <el-button v-permission="'system:user:create'">创建用户</el-button>
 * ```
 */
import type { App, Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/stores/permission'

/**
 * 检查元素是否应该显示。
 * 使用 display:none 隐藏而非 removeChild，以兼容 Vue 响应式更新。
 */
function checkPermission(el: HTMLElement, binding: DirectiveBinding) {
  const permissionStore = usePermissionStore()
  const requiredPermission = binding.value

  if (requiredPermission && !permissionStore.hasPermission(requiredPermission)) {
    el.style.display = 'none'
  } else {
    el.style.display = ''
  }
}

const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    checkPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    checkPermission(el, binding)
  },
}

/**
 * 注册 v-permission 指令到 Vue 应用。
 */
export function setupPermissionDirective(app: App) {
  app.directive('permission', permissionDirective)
}
