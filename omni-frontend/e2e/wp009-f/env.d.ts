/// <reference types="vite/client" />

/** 端到端测试仅使用 API 类型；声明 SFC 类型以解析其类型依赖链。 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent
  export default component
}
