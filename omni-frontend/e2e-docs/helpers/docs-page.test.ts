import assert from 'node:assert/strict'
import { test } from 'node:test'
import type { Page } from '@playwright/test'
import { prepareDocsPage } from '../fixtures/docs-page.ts'

test('正式截图模式不能替换验证码、租户、设备码或真实时钟', async () => {
  const previous = process.env.E2E_RUNTIME_MODE
  try {
    for (const mode of [undefined, 'external', 'display-fixtures']) {
      if (mode === undefined) delete process.env.E2E_RUNTIME_MODE
      else process.env.E2E_RUNTIME_MODE = mode
      const routes: string[] = []
      let clocks = 0
      let initScripts = 0
      const page = {
        emulateMedia: async () => {},
        clock: { install: async () => { clocks += 1 } },
        route: async (pattern: string) => { routes.push(pattern) },
        addInitScript: async () => { initScripts += 1 },
      } as unknown as Page
      await prepareDocsPage(page, { locale: 'zh-CN' })
      assert.equal(initScripts, 1)
      if (mode === 'display-fixtures') {
        assert.equal(clocks, 1)
        assert.deepEqual(routes, ['**/api/auth/captcha', '**/api/auth/tenants', '**/oauth2/device_authorization'])
      } else {
        assert.equal(clocks, 0)
        assert.deepEqual(routes, [])
      }
    }
  } finally {
    if (previous === undefined) delete process.env.E2E_RUNTIME_MODE
    else process.env.E2E_RUNTIME_MODE = previous
  }
})
