/**
 * @module management.flows.spec
 * 管理端只读页面四语言正式截图：系统管理、工作流定义/实例/看板、SRM 管理视图。
 *
 * 全部场景均为只读采集：不创建、不修改、不删除任何业务数据，因此不要求 `E2E_MUTATIONS`，
 * 也无需数据收尾。expectTitle 与 expectMain 取自对运行中应用的实测探测（document.title 与
 * 真实渲染的表格列标签），不是从语言包推测；ja-JP/ko-KR 下列标签渲染为英文属当前语言包
 * 既有取值（`npm run ui:i18n:check` 与 `ui:i18n:parity` 均通过），此处如实断言实际渲染结果。
 *
 * 有意排除：procurement 物料/订单/收货与 asset 台账/调拨/处置（活行为 0，空表不具文档价值，
 * 待种子目录 DATA_DEFECT 修复后补采）；procurement 请购/询价（当前仅存历史 E2ESQ 测试残留行，
 * 不宜作为正式文档图片）。
 */
import { expect, test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN

/** 四语言文案，顺序与 docsLocales 一致。 */
type Quad = [string, string, string, string]

interface Scene {
  /** 稳定截图 ID，同时作为 docs/images/<locale>/<id>.png 的文件名。 */
  id: string
  /** 由 permissionCode 派生的动态路由（`code.replace(/:/g,'/')` 挂在 /admin 下）。 */
  route: string
  /** 页面标题，证明语言已加载且动态路由与菜单 i18n 正确解析。 */
  expectTitle: Quad
  /** 主区内真实渲染的本地化标签；null 表示该页无稳定表格标签，仅断言标题与主区可见。 */
  expectMain: Quad | null
}

const scenes: Scene[] = [
  {
    id: 'system-tenants',
    route: '/admin/system/tenant',
    expectTitle: ['租户管理', 'Tenant Management', 'テナント管理', '테넌트 관리'],
    expectMain: ['租户编码', 'Tenant Code', 'Tenant Code', 'Tenant Code'],
  },
  {
    id: 'system-organizations',
    route: '/admin/system/org',
    expectTitle: ['组织管理', 'Organization Mgmt', '組織管理', '조직 관리'],
    expectMain: null,
  },
  {
    id: 'system-roles',
    route: '/admin/system/role',
    expectTitle: ['角色管理', 'Role Management', 'ロール管理', '역할 관리'],
    expectMain: ['角色编码', 'Role Code', 'Role Code', 'Role Code'],
  },
  {
    id: 'system-permissions',
    route: '/admin/system/permission',
    expectTitle: ['权限管理', 'Permission Mgmt', '権限管理', '권한 관리'],
    expectMain: ['权限名称', 'Permission Name', 'Permission Name', 'Permission Name'],
  },
  {
    id: 'system-dictionaries',
    route: '/admin/base/dict',
    expectTitle: ['字典管理', 'Dictionary', '辞書管理', '사전 관리'],
    expectMain: ['类型编码', 'Type Code', 'Type Code', 'Type Code'],
  },
  {
    id: 'system-online-users',
    route: '/admin/system/online',
    expectTitle: ['在线用户', 'Online Users', 'オンラインユーザー', '온라인 사용자'],
    expectMain: null,
  },
  {
    id: 'system-audit-log',
    route: '/admin/system/auditlog',
    expectTitle: ['审计日志', 'Audit Logs', '監査ログ', '감사 로그'],
    expectMain: ['事件类型', 'Event Type', 'Event Type', 'Event Type'],
  },
  {
    id: 'system-auth-records',
    route: '/admin/system/authrecord',
    expectTitle: ['授权记录', 'Auth Records', '認証記録', '인증 기록'],
    expectMain: ['客户端 ID', 'Client ID', 'Client ID', 'Client ID'],
  },
  {
    id: 'system-xss-config',
    route: '/admin/system/xssconfig',
    expectTitle: ['XSS 防护', 'XSS Protection', 'XSS 防御', 'XSS 보호'],
    expectMain: ['规则名称', 'Rule Name', 'Rule Name', 'Rule Name'],
  },
  {
    id: 'system-operation-log',
    route: '/admin/base/operlog',
    expectTitle: ['操作日志', 'Operation Logs', '操作ログ', '작업 로그'],
    expectMain: ['操作时间', 'Operation Time', '操作日時', '작업 시간'],
  },
  {
    id: 'workflow-instances',
    route: '/admin/workflow/instance',
    expectTitle: ['流程实例', 'Process Instances', 'プロセスインスタンス', '프로세스 인스턴스'],
    expectMain: ['流程标题', 'Title', 'Title', 'Title'],
  },
  {
    id: 'workflow-definitions',
    route: '/admin/workflow/definition',
    expectTitle: ['流程定义', 'Process Definitions', 'プロセス定義', '프로세스 정의'],
    expectMain: ['流程名称', 'Process Name', 'プロセス名', '프로세스 이름'],
  },
  {
    id: 'workflow-stats',
    route: '/admin/workflow/stats',
    expectTitle: ['统计看板', 'Statistics', '統計ダッシュボード', '통계 대시보드'],
    expectMain: null,
  },
  {
    id: 'srm-suppliers',
    route: '/admin/srm/supplier',
    expectTitle: ['供应商管理', 'Suppliers', 'サプライヤー管理', '공급업체 관리'],
    expectMain: ['供应商编号', 'Supplier No.', 'サプライヤー番号', '공급사 번호'],
  },
  {
    id: 'srm-evaluations',
    route: '/admin/srm/evaluation',
    expectTitle: ['绩效评估', 'Evaluations', '業績評価', '성과 평가'],
    expectMain: ['名称', 'Name', '名称', '이름'],
  },
  {
    id: 'srm-risks',
    route: '/admin/srm/risk',
    expectTitle: ['风险管理', 'Risk Management', 'リスク管理', '위험 관리'],
    expectMain: ['名称', 'Name', '名称', '이름'],
  },
  {
    id: 'srm-risk-config',
    route: '/admin/srm/risk/config',
    expectTitle: ['风险指标配置', 'Risk Indicator Config', 'リスク指標設定', '위험 지표 설정'],
    expectMain: ['名称', 'Name', '名称', '이름'],
  },
  {
    id: 'srm-invites',
    route: '/admin/srm/invite',
    expectTitle: ['邀请管理', 'Invitations', '招待管理', '초대 관리'],
    expectMain: ['状态', 'Status', '状態', '상태'],
  },
]

test.describe('管理端只读页面文档截图', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  for (const locale of docsLocales) {
    for (const scene of scenes) {
      test(`${scene.id} / ${locale}`, async ({ page }) => {
        const index = docsLocales.indexOf(locale)
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
        await page.goto(scene.route)
        await waitForDocsPage(page, '.el-main, .app-main')
        // 标题断言证明语言包已加载且动态路由/菜单 i18n 解析正确。
        await expect(page).toHaveTitle(scene.expectTitle[index])
        const main = page.locator('.el-main, .app-main').first()
        // 主区标签断言证明业务内容真实渲染，而不是只等容器出现就截图。
        if (scene.expectMain) await expect(main).toContainText(scene.expectMain[index])
        await captureDocsImage(page, locale, scene.id)
      })
    }
  }
})
