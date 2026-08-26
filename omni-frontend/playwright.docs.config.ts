/**
 * @module playwright.docs.config
 * 正式文档截图配置。固定浏览器、时区、颜色模式和单 worker，降低截图随机差异。
 */
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e-docs/flows',
  outputDir: './.artifacts/docs-playwright',
  timeout: 60_000,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
    viewport: { width: 1440, height: 900 },
    timezoneId: 'Asia/Shanghai',
    locale: 'zh-CN',
    colorScheme: 'light',
    reducedMotion: 'reduce',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'off',
  },
})
