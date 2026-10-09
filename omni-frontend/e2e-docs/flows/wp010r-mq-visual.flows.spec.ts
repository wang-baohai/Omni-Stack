/**
 * @module wp010r-mq-visual.flows.spec
 * WP-010 R03 修复后的 MQ 消息页四语正式截图 spec（VIS-01 / AC14 P1 重拍准备）。
 *
 * 背景（缺陷登记）：
 * - 旧证据 wp010f-mq-detail-ko.png 与列表图 SHA 相同（详情图实际只有列表），旧清单
 *   「详情和操作权限已像素验证」的说法已撤回。本 spec 每图截图前做确定性内容断言，
 *   并在写入后做字节级 SHA-256 互斥校验：任意两张本批图相同即抛错，杜绝同类缺陷复发。
 * - R03 前状态标签硬编码英文（PENDING/FAILED/…）；本轮已改为 i18n key
 *   （mqMessage.statusPending/Sent/Failed/DeadLetter/Skipped/Processing），
 *   断言按修复后的四语真实取值编写。
 *
 * 数据前置（如实声明，不伪造）：
 * - FAILED(2)/DEAD_LETTER(3)/SKIPPED(4) 无法经 API 只读构造（列表接口无写入口，
 *   注入必然失败消息属"制造故障"且污染共享 outbox，见 screenshot-coverage.yaml
 *   messaging-monitoring 注释）。相关图依赖 R10 运行环境预置真实死信；
 *   环境无该状态数据时对应步骤如实 annotate 并跳过，不出空图假图。
 * - 基线实测（2026-08）：5 个库 sys_mq_message 共 809 行 status 全为 1（SENT）。
 *
 * 认证（不硬编码任何凭证）：
 * - 首选：E2E_ADMIN_TOKEN（受信任环境预取，与既有 flows 相同）。
 * - 或真实 API 登录：E2E_ADMIN_USER + E2E_ADMIN_PASSWORD +
 *   E2E_CAPTCHA_KEY + E2E_CAPTCHA_CODE（验证码对由受信任运行器预置 Redis，
 *   与 e2e/wp009-f controlledCaptcha 同一机制）+ E2E_TENANT_ID（默认 1）。
 *
 * 写操作门控：
 * - 重发确认框为纯只读（点击后取消）；真实提交产生 toast 反馈的
 *   wp010r-mq-action-feedback 仅在 E2E_MQ_WRITE_ACTIONS=allow 时执行（会改消息状态）。
 *
 * 运行方法（F 前端，127.0.0.1:53000 或 override 端口）：
 *   cd omni-frontend
 *   $env:E2E_RUNTIME_MODE='external'; $env:E2E_BASE_URL='http://127.0.0.1:53000'
 *   $env:E2E_TENANT_ID='1'; $env:E2E_EXECUTION='wp010r10'; $env:E2E_REGISTRY_PATH='.qoder/wp010-evidence/registry.json'
 *   $env:E2E_ADMIN_USER='admin'; $env:E2E_ADMIN_PASSWORD='<受信任注入>'; $env:E2E_CAPTCHA_KEY='<预置>'; $env:E2E_CAPTCHA_CODE='<预置>'
 *   npx playwright test --config=playwright.docs.config.ts flows/wp010r-mq-visual.flows.spec.ts
 *   （预置验证码对参考 scripts/fixtures/captcha/CaptchaFixture.java 机制，或由主控提供 E2E_ADMIN_TOKEN。）
 *
 * 产物命名：docs/images/<locale>/wp010r-<page>-<locale>-<viewport>.png
 */
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import {
  captureDocsImage,
  docsLocales,
  prepareDocsPage,
  waitForDocsPage,
  type DocsLocale,
  type DocsViewport,
} from '../fixtures/docs-page'

const injectedToken = process.env.E2E_ADMIN_TOKEN || ''
const adminUser = process.env.E2E_ADMIN_USER || ''
const adminPassword = process.env.E2E_ADMIN_PASSWORD || ''
const captchaKey = process.env.E2E_CAPTCHA_KEY || ''
const captchaCode = process.env.E2E_CAPTCHA_CODE || ''
const tenantId = Number(process.env.E2E_TENANT_ID || '1')
const writeActionsAllowed = process.env.E2E_MQ_WRITE_ACTIONS === 'allow'

/** 运行期取得的 admin 令牌（injected 优先，否则 beforeAll 真实 API 登录换取）。 */
let adminToken = injectedToken

const canRun = Boolean(injectedToken)
  || Boolean(adminUser && adminPassword && captchaKey && captchaCode)

/** 四语文案，顺序与 docsLocales（zh-CN/en-US/ja-JP/ko-KR）严格一致，取自本轮修复后的 locale 源文件。 */
type Quad = readonly [string, string, string, string]
const pick = (quad: Quad, locale: DocsLocale): string => quad[docsLocales.indexOf(locale)]

const text = {
  title: ['MQ 消息记录', 'MQ Messages', 'MQ メッセージ記録', 'MQ 메시지 기록'],
  status: ['状态', 'Status', '状態', '상태'],
  search: ['搜索', 'Search', '検索', '검색'],
  detail: ['查看详情', 'Detail', '詳細表示', '상세 보기'],
  msgId: ['消息ID', 'Message ID', 'メッセージ ID', '메시지 ID'],
  serviceName: ['来源服务', 'Service', 'サービス名', '서비스'],
  createTime: ['创建时间', 'Created At', '作成日時', '생성 시간'],
  nextRetryTime: ['下次重试时间', 'Next Retry', '次回再試行時刻', '다음 재시도 시각'],
  errorMsg: ['错误信息', 'Error Message', 'エラーメッセージ', '오류 메시지'],
  close: ['关闭', 'Close', '閉じる', '닫기'],
  confirm: ['确认', 'Confirm', '確認', '확인'],
  cancel: ['取消', 'Cancel', 'キャンセル', '취소'],
  resend: ['重发', 'Resend', '再送信', '재전송'],
  skip: ['忽略', 'Skip', 'スキップ', '건너뛰기'],
  confirmResend: ['确认重发该消息？', 'Are you sure to resend this message?', 'このメッセージを再送信しますか？', '이 메시지를 재전송하시겠습니까?'],
  resendSuccess: ['重发指令已提交', 'Resend request submitted', '再送信リクエストを受け付けました', '재전송 요청이 접수되었습니다'],
  statusPending: ['待发送', 'Pending', '保留中', '대기'],
  statusSent: ['已发送', 'Sent', '送信済み', '전송됨'],
  statusFailed: ['失败', 'Failed', '失敗', '실패'],
  statusDeadLetter: ['死信', 'Dead Letter', 'デッドレター', '데드레터'],
  statusSkipped: ['已忽略', 'Skipped', 'スキップ済み', '건너뜀'],
  statusProcessing: ['投递中', 'Processing', '投信中', '전송 중'],
  /** 加载失败告警的本地化前缀（loadFailedWithTrace 的固定片段）。 */
  loadFailed: ['消息聚合服务加载失败', 'The message aggregation service could not be loaded', 'メッセージ集約サービスを読み込めません', '메시지 집계 서비스를 불러오지 못했습니다'],
  tracePrefix: ['追踪号', 'Trace ID', 'トレース ID', '추적 ID'],
  outboxTitle: ['当前为仅写入模式，异步投递未启用', 'Outbox-only mode: asynchronous delivery is disabled', '現在は書き込み専用モードで、非同期配信は無効です', '현재 쓰기 전용 모드로 비동기 전달이 비활성화되었습니다'],
} as const satisfies Record<string, Quad>

const desktop: DocsViewport = { width: 1440, height: 900 }
const tablet: DocsViewport = { width: 768, height: 1024 }
const mobile: DocsViewport = { width: 390, height: 844 }

/** 当前语言下所有合法状态标签（未知/英文残留一律视为缺陷，不接受）。 */
function statusLabelPattern(locale: DocsLocale): RegExp {
  const labels = [
    text.statusPending, text.statusSent, text.statusFailed,
    text.statusDeadLetter, text.statusSkipped, text.statusProcessing,
  ].map((quad) => escapeRegExp(pick(quad, locale)))
  return new RegExp(`^(${labels.join('|')})$`)
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 截图 + VIS-01 复发防护：写入后取本批所有 wp010r 图的 SHA-256，
 * 任意两张字节一致（旧「详情=列表」缺陷的充要特征）立即抛错。
 */
const capturedDigests = new Map<string, string>()
async function captureUnique(page: Page, locale: DocsLocale, imageName: string): Promise<void> {
  await captureDocsImage(page, locale, imageName)
  const file = resolve(process.cwd(), '..', 'docs', 'images', locale, `${imageName}.png`)
  const digest = createHash('sha256').update(readFileSync(file)).digest('hex')
  for (const [previousName, previousDigest] of capturedDigests) {
    if (previousDigest === digest) {
      throw new Error(`VIS-01 复发：${imageName} 与 ${previousName} 字节级相同（SHA-256 ${digest}），拒绝把列表冒充详情`)
    }
  }
  capturedDigests.set(imageName, digest)
}

interface OpenOptions {
  viewport?: DocsViewport
  /** true 时要求表格至少 1 行真实数据（列表/详情图），false 用于故障态图。 */
  expectRows?: boolean
}

/** 打开 MQ 消息页并做 locale 标志断言（卡片标题=当前语言文案，杜绝语言目录与内容错位）。 */
async function openMqPage(page: Page, locale: DocsLocale, options: OpenOptions = {}) {
  await prepareDocsPage(page, { locale, token: adminToken, username: adminUser || 'admin', viewport: options.viewport ?? desktop })
  await page.goto('/admin/base/mqmessage')
  await waitForDocsPage(page, '[data-testid="mq-status-filter"]')
  await expect(page.locator('.card-header')).toContainText(pick(text.title, locale))
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  if (options.expectRows !== false) {
    // 空表不允许冒充列表证据：无数据直接失败，由环境侧补真实数据。
    await expect(page.locator('.el-table__body .el-table__row').first()).toBeVisible()
  }
}

/** 用状态下拉筛选并查询（值为当前语言的状态标签文本，同时验证筛选器已本地化）。 */
async function filterByStatus(page: Page, locale: DocsLocale, label: string) {
  await page.getByTestId('mq-status-filter').click()
  const option = page.locator('.el-select-dropdown__item:visible')
    .filter({ hasText: new RegExp(`^${escapeRegExp(label)}$`) })
    .first()
  await expect(option).toBeVisible()
  await option.click()
  await page.getByRole('button', { name: pick(text.search, locale) }).click()
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
}

/** 当前筛选结果行数。 */
function rowCount(page: Page) {
  return page.locator('.el-table__body .el-table__row').count()
}

test.describe('WP-010R MQ 消息页四语截图（admin）', () => {
  test.beforeAll(async ({ request }) => {
    if (injectedToken) return
    if (!canRun) return
    const response = await request.post('/api/auth/login', {
      data: { username: adminUser, password: adminPassword, tenantId, captchaKey, captchaCode },
    })
    const body = await response.json().catch(() => ({ code: -1 })) as { code: number; data?: { accessToken?: string } }
    if (body.code !== 200 || !body.data?.accessToken) {
      throw new Error(`真实 API 登录失败（code=${body.code}）；验证码对需由受信任运行器预置，禁止硬编码兜底`)
    }
    adminToken = body.data.accessToken
  })

  for (const locale of docsLocales) {
    // ---- 列表：桌面/窄屏/移动三档 viewport，同 fixture 同状态 ----
    for (const [viewportId, viewport] of [['desktop', desktop], ['tablet', tablet], ['mobile', mobile]] as [string, DocsViewport][]) {
      test(`wp010r-mq-list-${viewportId} / ${locale}`, async ({ page }) => {
        test.skip(!canRun, '需要注入 E2E_ADMIN_TOKEN，或 E2E_ADMIN_USER/E2E_ADMIN_PASSWORD + E2E_CAPTCHA_KEY/E2E_CAPTCHA_CODE')
        await openMqPage(page, locale, { viewport })
        // 确定性内容断言：表头状态列存在 + 首行状态标签为当前语言的合法值
        //（防止英文残留回归；基线全 SENT 时断言集合同样成立）。
        await expect(page.getByRole('columnheader').filter({ hasText: pick(text.status, locale) }).first()).toBeVisible()
        await expect(page.getByTestId('mq-status-tag').first()).toHaveText(statusLabelPattern(locale))
        await captureUnique(page, locale, `wp010r-mq-list-${locale}-${viewportId}`)
      })
    }

    // ---- 详情弹层（VIS-01 直接复拍对象）----
    test(`wp010r-mq-detail / ${locale}`, async ({ page }) => {
      test.skip(!canRun, '需要注入 E2E_ADMIN_TOKEN 或真实 API 登录凭证')
      await openMqPage(page, locale)
      await page.getByTestId('mq-detail-button').first().click()
      const dialog = page.getByTestId('mq-detail-dialog')
      await expect(dialog).toBeVisible()
      await expect(dialog.locator('.el-dialog__title')).toHaveText(pick(text.detail, locale))
      // 字段标签与值断言：证明弹层真实渲染，而不是列表残留画面。
      //（多元素容器用 filter+first 避免 strict mode violation：标签集合有 11 个）
      const label = (name: string) => dialog.locator('.el-descriptions__label').filter({ hasText: name }).first()
      await expect(label(pick(text.msgId, locale))).toBeVisible()
      await expect(label(pick(text.serviceName, locale))).toBeVisible()
      await expect(label(pick(text.createTime, locale))).toBeVisible()
      await expect(label(pick(text.nextRetryTime, locale))).toBeVisible()
      await expect(dialog.getByTestId('mq-detail-status')).toHaveText(statusLabelPattern(locale))
      // 日期格式确定性断言：yyyy-MM-dd HH:mm:ss
      await expect(dialog).toContainText(/\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)
      await expect(dialog.getByRole('button', { name: pick(text.close, locale), exact: true })).toBeVisible()
      await captureUnique(page, locale, `wp010r-mq-detail-${locale}-desktop`)
    })

    // ---- FAILED(2) 状态标签 + 重发按钮（v-permission base:mqmessage:resend）----
    test(`wp010r-mq-list-failed / ${locale}`, async ({ page }) => {
      test.skip(!canRun, '需要注入 E2E_ADMIN_TOKEN 或真实 API 登录凭证')
      await openMqPage(page, locale)
      await filterByStatus(page, locale, pick(text.statusFailed, locale))
      if (await rowCount(page) === 0) {
        // 数据前置不满足：如实登记，不截空图。需要 R10 环境预置真实失败消息。
        test.info().annotations.push({ type: 'notice', description: '环境无 FAILED 消息（基线实测仅 SENT），跳过本图；需运行环境预置真实失败/死信' })
        return
      }
      await expect(page.getByTestId('mq-status-tag').first()).toHaveText(pick(text.statusFailed, locale))
      const alertVisible = await page.getByText(pick(text.outboxTitle, locale)).first().isVisible().catch(() => false)
      if (alertVisible) {
        test.info().annotations.push({ type: 'notice', description: 'OUTBOX_ONLY 模式：重发按钮按运行时状态隐藏（业务语义如实入图）' })
      } else {
        await expect(page.getByTestId('mq-resend-button').first()).toBeVisible()
      }
      await captureUnique(page, locale, `wp010r-mq-list-failed-${locale}-desktop`)
    })

    // ---- DEAD_LETTER(3) 状态标签 + 重发/忽略按钮（v-permission resend/skip）+ 详情含 errorMsg 脱敏 ----
    test(`wp010r-mq-list-dead-letter / ${locale}`, async ({ page }) => {
      test.skip(!canRun, '需要注入 E2E_ADMIN_TOKEN 或真实 API 登录凭证')
      await openMqPage(page, locale)
      await filterByStatus(page, locale, pick(text.statusDeadLetter, locale))
      if (await rowCount(page) === 0) {
        test.info().annotations.push({ type: 'notice', description: '环境无 DEAD_LETTER 消息，跳过本图；需运行环境预置真实死信' })
        return
      }
      const firstRow = page.locator('.el-table__body .el-table__row').first()
      await expect(firstRow.getByTestId('mq-status-tag')).toHaveText(pick(text.statusDeadLetter, locale))
      await expect(firstRow.getByTestId('mq-skip-button')).toBeVisible()
      // 死信详情：错误信息区块按当前语言标签渲染且凭证模式已脱敏（前端再保一层）。
      await firstRow.getByTestId('mq-detail-button').click()
      const dialog = page.getByTestId('mq-detail-dialog')
      await expect(dialog).toBeVisible()
      await expect(dialog.getByTestId('mq-detail-status')).toHaveText(pick(text.statusDeadLetter, locale))
      const errorBlock = dialog.getByTestId('mq-detail-errormsg')
      if (await errorBlock.isVisible().catch(() => false)) {
        // 错误区块小标题（section-title 有多个，按当前语言文案过滤取其一）
        await expect(dialog.locator('.section-title').filter({ hasText: pick(text.errorMsg, locale) }).first()).toBeVisible()
        // 脱敏断言：若出现凭证模式，其值必须已折叠为 ***。
        expect(await errorBlock.textContent()).not.toMatch(/(password|passwd|token|secret|api[-_]?key|authorization|cookie)\s*[=:]\s*(?!\*\*\*)\S/i)
      }
      await captureUnique(page, locale, `wp010r-mq-detail-dead-letter-${locale}-desktop`)
      await dialog.getByRole('button', { name: pick(text.close, locale), exact: true }).click()
      // 忽略按钮可见性证据（不点击，写操作另有门控用例）。
      await captureUnique(page, locale, `wp010r-mq-list-dead-letter-${locale}-desktop`)
    })

    // ---- 重发确认框（只读：打开后取消，不产生状态变化）----
    test(`wp010r-mq-resend-confirm / ${locale}`, async ({ page }) => {
      test.skip(!canRun, '需要注入 E2E_ADMIN_TOKEN 或真实 API 登录凭证')
      await openMqPage(page, locale)
      const resendButton = page.getByTestId('mq-resend-button').first()
      if (await resendButton.count() === 0) {
        test.info().annotations.push({ type: 'notice', description: '当前列表无「可重发」状态行（需 FAILED/DEAD_LETTER/PENDING 且投递已启用），跳过确认框图' })
        return
      }
      await resendButton.click()
      const box = page.locator('.el-message-box')
      await expect(box).toBeVisible()
      await expect(box).toContainText(pick(text.confirmResend, locale))
      await expect(box.getByRole('button', { name: pick(text.confirm, locale) })).toBeVisible()
      await captureUnique(page, locale, `wp010r-mq-resend-confirm-${locale}-desktop`)
      // 取消收尾：不触发任何写操作。
      await box.getByRole('button', { name: pick(text.cancel, locale) }).click()
      await expect(box).toBeHidden()
    })

    // ---- 操作反馈 toast（写门控：E2E_MQ_WRITE_ACTIONS=allow 才真实提交）----
    test(`wp010r-mq-action-feedback / ${locale}`, async ({ page }) => {
      test.skip(!canRun, '需要注入 E2E_ADMIN_TOKEN 或真实 API 登录凭证')
      test.skip(!writeActionsAllowed, '写操作图会真实重发消息，须显式 E2E_MQ_WRITE_ACTIONS=allow 授权（归 R10 决策）')
      await openMqPage(page, locale)
      const resendButton = page.getByTestId('mq-resend-button').first()
      if (await resendButton.count() === 0) {
        test.info().annotations.push({ type: 'notice', description: '无可重发行，跳过反馈 toast 图' })
        return
      }
      await resendButton.click()
      await page.locator('.el-message-box').getByRole('button', { name: pick(text.confirm, locale) }).click()
      // 成功/错误两条真实路径都如实入图（toast 3s 自动消失，断言后立即截）。
      const toast = page.locator('.el-message').first()
      await expect(toast).toBeVisible({ timeout: 15_000 })
      const toastText = ((await toast.textContent()) || '').trim()
      expect(toastText.length).toBeGreaterThan(0)
      if (!toastText.includes(pick(text.resendSuccess, locale))) {
        test.info().annotations.push({ type: 'notice', description: `服务端返回错误反馈，如实入图：${toastText}` })
      }
      await captureUnique(page, locale, `wp010r-mq-action-feedback-${locale}-desktop`)
      // 反馈后的列表真实态（状态列已反映操作结果）作为收尾图。
      await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
      await captureUnique(page, locale, `wp010r-mq-action-result-${locale}-desktop`)
    })

    // ---- trace 字段展示（确定性测试故障：仅本测试进程拦截列表接口 503 + x-trace-id）----
    test(`wp010r-mq-trace-error / ${locale}`, async ({ page }) => {
      test.skip(!canRun, '需要注入 E2E_ADMIN_TOKEN 或真实 API 登录凭证')
      const traceValue = 'wp010r-trace-0f3a9c2d8e7b4a15'
      await page.route('**/api/base/mq-message/list**', async (route) => {
        await route.fulfill({
          status: 503,
          contentType: 'application/json',
          headers: { 'x-trace-id': traceValue },
          body: JSON.stringify({ code: 503, message: 'message aggregation unavailable (docs fault injection)', data: null }),
        })
      })
      await openMqPage(page, locale, { expectRows: false })
      const alertBox = page.getByTestId('mq-load-error')
      await expect(alertBox).toBeVisible()
      // 告警标题 = 当前语言的 loadFailedWithTrace + 真实 trace 值，证明 trace 展示链路与本地化。
      await expect(alertBox).toContainText(pick(text.loadFailed, locale))
      await expect(alertBox).toContainText(pick(text.tracePrefix, locale))
      await expect(alertBox).toContainText(traceValue)
      await captureUnique(page, locale, `wp010r-mq-trace-error-${locale}-desktop`)
      await page.unroute('**/api/base/mq-message/list**')
    })
  }
})
