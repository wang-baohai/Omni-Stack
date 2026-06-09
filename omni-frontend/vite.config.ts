/**
 * @module vite.config
 * @description Vite 构建配置。
 * 配置 Vue 3 插件、路径别名、开发服务器代理和生产构建参数。
 * 开发服务器通过代理将 /api、/oauth2、/.well-known 请求转发至 Gateway（8102），
 * 避免跨域问题。
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      /** @ 指向 src 目录，简化模块导入路径 */
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    /** 开发服务器端口 */
    port: 3000,
    /** 监听所有网络接口（0.0.0.0），方便局域网内其他设备访问 */
    host: true,
    proxy: {
      /** 代理 REST API 请求到 Gateway，Gateway 根据路由规则分发到各微服务 */
      '/api': {
        target: 'http://localhost:8102',
        changeOrigin: true,
      },
      /** 代理 OAuth2 授权服务器端点（authorize、token、device_authorization 等） */
      '/oauth2': {
        target: 'http://localhost:8102',
        changeOrigin: true,
      },
      /** 代理 OIDC Discovery 端点（/.well-known/openid-configuration） */
      '/.well-known': {
        target: 'http://localhost:8102',
        changeOrigin: true,
      },
    },
  },
  build: {
    /** 构建目标：ES2020，支持可选链、空值合并等现代语法 */
    target: 'es2020',
    /** 输出目录 */
    outDir: 'dist',
    /** chunk 体积警告阈值（KB），Element Plus 体积较大需适当调高 */
    chunkSizeWarningLimit: 2000,
  },
})
