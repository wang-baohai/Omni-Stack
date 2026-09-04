/**
 * @module detail-overlays.flows.spec
 * 工作流与 MQ 消息的只读详情弹层四语言正式截图：
 * 流程实例「流转进度」「审批记录」、流程模型「版本历史」、MQ 消息「查看详情」。
 *
 * 全部为只读采集：仅点击行内查看类动作打开弹层，不提交任何表单、不触发发布/删除/终止/重发/跳过，
 * 因此不要求 `E2E_MUTATIONS`，也无数据收尾。
 *
 * 按钮文案、弹层标题与字段标签全部取自对运行中应用的实测探测（四语言逐个打开确认），不从语言包推测。
 * 已实测的译文完整度现象（如实断言，不美化）：
 * - MQ 详情弹层在 en-US/ja-JP/ko-KR 下标题与全部字段标签均为英文（ja/ko 未翻译）；
 * - 审批记录弹层的「审批意见」列在 ja-JP/ko-KR 下渲染为英文 `Comment`。
 * `ui:i18n:parity`（四语言各 2319 键、0 缺失）与 `ui:i18n:check`（0/0 项）均通过，属语言包取值问题而非硬编码缺陷。
 */
import { expect, test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN

/** 四语言文案，顺序与 docsLocales 一致。 */
type Quad = [string, string, string, string]

interface OverlayScene {
  /** 稳定截图 ID，同时作为 docs/images/<locale>/<id>.png 的文件名。 */
  id: string
  /** 承载该弹层的列表页路由。 */
  route: string
  /** 行内触发按钮文案。 */
  trigger: Quad
  /** 弹层标题（实测值；未翻译语言如实保留英文）。 */
  expectTitle: Quad
  /** 弹层内一个实测的字段/列标签；null 表示该弹层无此类标签，仅断言标题。 */
  expectLabel: Quad | null
  /**
   * 异步渲染内容的就绪选择器。
   *
   * 「流转进度」用 bpmn-js NavigatedViewer 异步 importXML 后才渲染图形，
   * 仅等弹层可见会截到 loading 转圈态（已实际发生并被图片质检拦下），
   * 因此必须等到 `.djs-element` 真实出现才能截图。
   */
  readySelector?: string
}

const scenes: OverlayScene[] = [
  {
    id: 'workflow-instance-progress',
    route: '/admin/workflow/instance',
    trigger: ['流转进度', 'Process Progress', 'プロセス進捗', '프로세스 진행 상태'],
    expectTitle: ['流转进度', 'Process Progress', 'プロセス進捗', '프로세스 진행 상태'],
    // 实测该弹层为 BPMN 图形结构，无 .el-form-item__label / th / descriptions 标签，
    // 改以 diagram-js 渲染出的图形元素作为内容就绪依据。
    expectLabel: null,
    readySelector: '.bpmn-viewer-wrap .djs-element',
  },
  {
    id: 'workflow-instance-approval-records',
    route: '/admin/workflow/instance',
    trigger: ['审批记录', 'Approval Records', '承認履歴', '승인 기록'],
    expectTitle: ['审批记录', 'Approval Records', '承認履歴', '승인 기록'],
    expectLabel: ['节点名称', 'Node Name', 'ノード名', '노드 이름'],
  },
  {
    id: 'workflow-model-versions',
    route: '/admin/workflow/model',
    trigger: ['版本', 'Version', 'バージョン', '버전'],
    expectTitle: ['版本历史', 'Version History', 'バージョン履歴', '버전 기록'],
    expectLabel: ['业务版本', 'Business Version', 'ビジネスバージョン', '비즈니스 버전'],
  },
  {
    id: 'monitor-mq-message-detail',
    route: '/admin/base/mqmessage',
    // ja-JP/ko-KR 的行按钮实测渲染为英文 Detail（语言包取值如此），此处按实际值定位。
    trigger: ['查看详情', 'Detail', 'Detail', 'Detail'],
    expectTitle: ['查看详情', 'Detail', 'Detail', 'Detail'],
    expectLabel: ['消息ID', 'Message ID', 'Message ID', 'Message ID'],
  },
]

test.describe('只读详情弹层文档截图', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  for (const locale of docsLocales) {
    for (const scene of scenes) {
      test(`${scene.id} / ${locale}`, async ({ page }) => {
        const index = docsLocales.indexOf(locale)
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
        await page.goto(scene.route)
        await waitForDocsPage(page, '.el-main, .app-main')

        // 首行必须存在真实数据，否则弹层无内容可截（不用空表冒充详情态）。
        const firstRow = page.locator('.el-table__body .el-table__row').first()
        await expect(firstRow).toBeVisible()

        // 只点击行内查看类动作；本用例不涉及任何写操作按钮。
        await firstRow.locator('button, a').filter({ hasText: scene.trigger[index] }).first().click()

        const overlay = page.locator('.el-dialog, .el-drawer').first()
        await expect(overlay).toBeVisible()
        // 标题断言证明弹层按当前语言正确渲染（未翻译语言断言其实际英文取值）。
        await expect(overlay.locator('.el-dialog__title, .el-drawer__title').first())
          .toContainText(scene.expectTitle[index])
        // 字段标签断言证明弹层内容真实加载，而不是只有空壳标题。
        if (scene.expectLabel) {
          await expect(overlay.locator('.el-form-item__label, .el-descriptions__label, th')
            .filter({ hasText: scene.expectLabel[index] }).first()).toBeVisible()
        }
        // 异步图形弹层：必须等到真实图形元素渲染完成，否则会把加载态当作正式图。
        if (scene.readySelector) {
          await expect(overlay.locator(scene.readySelector).first()).toBeVisible({ timeout: 30_000 })
        }
        // 统一兜底：加载遮罩必须已消失（不存在也算通过），杜绝任何场景截到 loading 转圈。
        await expect(overlay.locator('.el-loading-mask').first()).toBeHidden({ timeout: 30_000 })
        await captureDocsImage(page, locale, scene.id)
      })
    }
  }
})
