/**
 * @module playwright.docs.config
 * 正式文档截图配置。固定浏览器、时区、颜色模式和单 worker，降低截图随机差异。
 *
 * 运行模式：
 * - 默认（文档截图）：启动本地 Vite dev server，连接 localhost:3000。
 * - E2E_RUNTIME_MODE=external：连接已部署的 Docker 前端，不启动 webServer。
 *   必须显式提供 E2E_BASE_URL、E2E_TENANT_ID、E2E_ARTIFACT_DIR、E2E_REGISTRY_PATH。
 */
import { defineConfig, devices } from '@playwright/test'

const external = process.env.E2E_RUNTIME_MODE === 'external'
const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:3000'
const outputDir = process.env.E2E_ARTIFACT_DIR || './.artifacts/docs-playwright'

if (external) {
  if (!process.env.E2E_BASE_URL) throw new Error('external 模式必须显式指定 E2E_BASE_URL')
  if (!process.env.E2E_TENANT_ID) throw new Error('external 模式必须显式指定 E2E_TENANT_ID')
  if (!process.env.E2E_EXECUTION || process.env.E2E_EXECUTION === 'unknown') {
    throw new Error('external 模式必须显式指定 E2E_EXECUTION，拒绝 unknown')
  }
  if (!process.env.E2E_REGISTRY_PATH) {
    throw new Error('external 模式必须显式指定 E2E_REGISTRY_PATH')
  }
}

export default defineConfig({
  testDir: './e2e-docs/flows',
  outputDir,
  timeout: external ? 100_000 : 60_000,
  workers: 1,
  retries: external ? 0 : (process.env.CI ? 1 : 0),
  reporter: external
    ? [['list'], ['json', { outputFile: outputDir + '/test-results.json' }]]
    : 'list',
  webServer: external ? undefined : {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:3000/login',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  use: {
    ...devices['Desktop Chrome'],
    baseURL,
    viewport: { width: 1440, height: 900 },
    timezoneId: 'Asia/Shanghai',
    locale: 'zh-CN',
    colorScheme: 'light',
    screenshot: 'only-on-failure',
    trace: external ? 'off' : 'retain-on-failure',
    video: 'off',
  } as const,
})
