import { defineConfig } from '@playwright/test'
import { ARTIFACTS, ORIGIN, RUN } from './e2e/wp009-f/target'

// 独立入口：不继承文档 mock、不启动默认 3000 服务、不保存认证 trace/video/storageState。
process.env.WP009_F_RUN = RUN
process.env.PLAYWRIGHT_NO_COPY_PROMPT = '1'
export default defineConfig({
  testDir: './e2e/wp009-f',
  testMatch: '*.spec.ts',
  workers: 1,
  retries: 0,
  fullyParallel: false,
  timeout: 240_000,
  expect: { timeout: 15_000 },
  outputDir: `${ARTIFACTS}/playwright`,
  reporter: [['./e2e/wp009-f/reporter.ts']],
  use: {
    baseURL: ORIGIN,
    browserName: 'chromium',
    headless: true,
    viewport: { width: 1440, height: 900 },
    timezoneId: 'Asia/Shanghai',
    locale: 'zh-CN',
    trace: 'off',
    video: 'off',
    screenshot: 'off',
  },
})
