/** 文档截图共享夹具：固定语言、登录态、遮罩与稳定等待。 */
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { expect, type Page } from '@playwright/test'

export const docsLocales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR'] as const
export type DocsLocale = typeof docsLocales[number]

export interface DocsViewport {
  width: number
  height: number
}

interface PrepareOptions {
  locale: DocsLocale
  token?: string
  username?: string
  viewport?: DocsViewport
}

/** 在应用启动前注入语言和隔离环境短期令牌。 */
export async function prepareDocsPage(page: Page, options: PrepareOptions) {
  const captchaSvg = `data:image/svg+xml;base64,${Buffer.from(`
    <svg xmlns="http://www.w3.org/2000/svg" width="120" height="40" viewBox="0 0 120 40">
      <rect width="120" height="40" rx="6" fill="#eef3fb"/>
      <path d="M4 31L116 9M8 8L112 34" stroke="#91a4c4" stroke-width="2" opacity=".65"/>
      <circle cx="28" cy="20" r="8" fill="#5b7db6" opacity=".8"/>
      <rect x="52" y="12" width="16" height="16" rx="3" fill="#6f8fc4" opacity=".8"/>
      <path d="M88 29L98 11L108 29Z" fill="#4f6f9f" opacity=".8"/>
    </svg>
  `).toString('base64')}`
  await page.route('**/api/auth/captcha', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: { captchaKey: 'docs-display-only', captchaImage: captchaSvg },
      }),
    })
  })
  await page.route('**/api/auth/tenants', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: [{ id: 1, name: 'Omni-Stack Docs', code: 'docs' }],
      }),
    })
  })
  await page.route('**/oauth2/device_authorization', async (route) => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        device_code: 'docs-display-only',
        user_code: 'DOCS-CODE',
        verification_uri: 'http://localhost:3000/device/verify',
        verification_uri_complete: 'http://localhost:3000/device/verify?user_code=DOCS-CODE',
        expires_in: 600,
        interval: 5,
      }),
    })
  })
  if (options.viewport) await page.setViewportSize(options.viewport)
  await page.addInitScript(({ locale, token, username }) => {
    localStorage.setItem('omni-lang', locale)
    if (token) localStorage.setItem('token', token)
    if (username) localStorage.setItem('username', username)
  }, options)
}

/** 等待目标页面和字体稳定，不使用固定休眠。 */
export async function waitForDocsPage(page: Page, selector: string) {
  await expect(page.locator(selector).first()).toBeVisible({ timeout: 20_000 })
  await page.evaluate(async () => {
    await document.fonts.ready
    await new Promise<void>((resolveFrame) => requestAnimationFrame(() => requestAnimationFrame(() => resolveFrame())))
  })
  await page.addStyleTag({ content: `
    *, *::before, *::after {
      animation-duration: 0s !important;
      animation-delay: 0s !important;
      transition-duration: 0s !important;
      caret-color: transparent !important;
    }
    input[type="password"], [data-docs-mask="true"], .captcha-image, .captcha-img {
      filter: blur(8px) !important;
    }
  ` })
}

/** 将正式截图写入 docs/images 的语言目录。 */
export async function captureDocsImage(page: Page, locale: DocsLocale, imageName: string) {
  const output = resolve(process.cwd(), '..', 'docs', 'images', locale, `${imageName}.png`)
  mkdirSync(dirname(output), { recursive: true })
  await page.screenshot({ path: output, fullPage: false })
}
