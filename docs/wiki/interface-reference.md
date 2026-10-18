# 界面参考与历史图片

这里陈列基线主干 fc165e3 保留的历史布局图片，共 68 张文件，在每篇语言版本中重复展示四个语言目录。它们不是本次业务复现或当前版本界面验收证明。旧登记的 last_success_at 没有绑定图片摘要与容器镜像，不能据此补写真实采集时间或运行环境。请按当前指南准备自己的记录，不要猜测历史业务主键；语言目录也不代表画面文字已全部翻译。

<!-- omni:modules:workflow -->
## workflow

| 页面 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 流程定义（publish） | ![流程定义（简体中文）](../images/zh-CN/workflow-definitions.png) | ![流程定义（英文）](../images/en-US/workflow-definitions.png) | ![流程定义（日文）](../images/ja-JP/workflow-definitions.png) | ![流程定义（韩文）](../images/ko-KR/workflow-definitions.png) |
| 流程实例跟踪（instance-tracking） | ![流程实例（简体中文）](../images/zh-CN/workflow-instances.png) | ![流程实例（英文）](../images/en-US/workflow-instances.png) | ![流程实例（日文）](../images/ja-JP/workflow-instances.png) | ![流程实例（韩文）](../images/ko-KR/workflow-instances.png) |
| 统计看板（汇总视图） | ![统计看板（简体中文）](../images/zh-CN/workflow-stats.png) | ![统计看板（英文）](../images/en-US/workflow-stats.png) | ![统计看板（日文）](../images/ja-JP/workflow-stats.png) | ![统计看板（韩文）](../images/ko-KR/workflow-stats.png) |
### 只读详情弹层（四语言）
| 弹层 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 流转进度（instance-tracking） | ![流转进度（简体中文）](../images/zh-CN/workflow-instance-progress.png) | ![流转进度（英文）](../images/en-US/workflow-instance-progress.png) | ![流转进度（日文）](../images/ja-JP/workflow-instance-progress.png) | ![流转进度（韩文）](../images/ko-KR/workflow-instance-progress.png) |
| 审批记录（approval） | ![审批记录（简体中文）](../images/zh-CN/workflow-instance-approval-records.png) | ![审批记录（英文）](../images/en-US/workflow-instance-approval-records.png) | ![审批记录（日文）](../images/ja-JP/workflow-instance-approval-records.png) | ![审批记录（韩文）](../images/ko-KR/workflow-instance-approval-records.png) |
| 版本历史（publish） | ![版本历史（简体中文）](../images/zh-CN/workflow-model-versions.png) | ![版本历史（英文）](../images/en-US/workflow-model-versions.png) | ![版本历史（日文）](../images/ja-JP/workflow-model-versions.png) | ![版本历史（韩文）](../images/ko-KR/workflow-model-versions.png) |

旧版本历史图中，中文显示 DRAFT 版本 0，英日韩显示 PUBLISHED 版本 2，不能视为同一记录的四语对照或本轮发布成功证据。当前设计、发布与审批操作见[工作流指南](../guides/workflow-approval.md)。

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## outbox

| 页面 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 消息详情弹层（message-status） | ![消息详情（简体中文）](../images/zh-CN/monitor-mq-message-detail.png) | ![消息详情（英文）](../images/en-US/monitor-mq-message-detail.png) | ![消息详情（日文）](../images/ja-JP/monitor-mq-message-detail.png) | ![消息详情（韩文）](../images/ko-KR/monitor-mq-message-detail.png) |

旧 MQ 详情图的日、韩标题与标签仍为英文；中文图为另一条 PENDING 消息，英日韩图显示 SENT，不能据此证明同一消息在四语下完成投递。图中消息体下半部被视口截断，完整字段需查自己的记录。当前租约、CAS、重试和消费幂等规则见[MQ 事实文档](../mq-reliability.md)。

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## srm

### 供应商报价流程截图（四语言）
| 项 | 内容 |
|---|---|
| 环境 | 历史脚本默认前端地址为 `127.0.0.1:3000`，访问 `omni-procurement` 与 `omni-srm`；这不证明旧图的实际部署。重新取证时，应用与浏览器都在本机 Docker，以 `E2E_RUNTIME_MODE=external` 连接容器前端，参数见[截图配置](../../omni-frontend/playwright.docs.config.ts)，不得在宿主运行默认 webServer。 |
| 数据前置 | 历史准备脚本用 `admin` 经正式 API 创建品类与物料 → 创建绑定 `procurement-approval` 模型版本的请购审批规则 → 请购提交并审批 → 创建询价单并 `send`（DRAFT→SENT，邀请 `supplier1`）。每个语言场景使用自己的询价单；本文没有重跑该写入链路。 |
| 操作者 | 历史准备脚本使用 `admin`，页面账号为 `supplier1`（`SUPPLIER` 角色及有效的 `srm_supplier_portal_user` 关联）。这不是当前普通用户的最小权限清单；当前角色、数据范围和候选约束见[SRM 指南](../guides/srm-flow.md)与[采购指南](../guides/procurement-flow.md)。 |
| 令牌 | `E2eTokenFixture` 使用现有租户用户及数据库授权签发 TTL 1200 秒的测试 JWT，不能代替密码登录或真实 CAPTCHA。调用者必须删除仓库外临时文件；本文没有证明历史文件实际已清除。令牌不得进入文档、日志或版本库。 |
| 写入开关 | 整个报价套件在缺少测试令牌或未显式设置 `E2E_MUTATIONS=true` 时跳过。脚本的 expectOk 在等待网络响应后检查开关，不能把它当作请求发出前的拦截器；只可在获授权的隔离环境执行，本文不执行该套件。 |
| 视口 | 旧报价图为 1440×900；辅助器禁用截图动画。当前辅助器只在 `display-fixtures` 模式固定时钟，不能把该设置追认为全部旧图的实际采集模式。 |
#### 步骤 1：邀请列表（未报价）
| zh-CN | en-US |
|---|---|
| ![供应商门户询价报价邀请列表（简体中文）](../images/zh-CN/srm-portal-quotation-invitations.png) | ![供应商门户询价报价邀请列表（英文）](../images/en-US/srm-portal-quotation-invitations.png) |
| ja-JP | ko-KR |
|---|---|
| ![供应商门户询价报价邀请列表（日文）](../images/ja-JP/srm-portal-quotation-invitations.png) | ![供应商门户询价报价邀请列表（韩文）](../images/ko-KR/srm-portal-quotation-invitations.png) |
#### 步骤 2：报价表单（填写单价与有效期）
| zh-CN | en-US |
|---|---|
| ![供应商门户报价表单（简体中文）](../images/zh-CN/srm-portal-quotation-form.png) | ![供应商门户报价表单（英文）](../images/en-US/srm-portal-quotation-form.png) |
| ja-JP | ko-KR |
|---|---|
| ![供应商门户报价表单（日文）](../images/ja-JP/srm-portal-quotation-form.png) | ![供应商门户报价表单（韩文）](../images/ko-KR/srm-portal-quotation-form.png) |
#### 步骤 3：提交成功（QUOTED 与报价总额）
| zh-CN | en-US |
|---|---|
| ![供应商门户报价提交成功（简体中文）](../images/zh-CN/srm-portal-quotation-submitted.png) | ![供应商门户报价提交成功（英文）](../images/en-US/srm-portal-quotation-submitted.png) |
| ja-JP | ko-KR |
|---|---|
| ![供应商门户报价提交成功（日文）](../images/ja-JP/srm-portal-quotation-submitted.png) | ![供应商门户报价提交成功（韩文）](../images/ko-KR/srm-portal-quotation-submitted.png) |
### SRM 管理端页面截图（四语言）
| 页面 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 供应商管理（lifecycle） | ![供应商管理（简体中文）](../images/zh-CN/srm-suppliers.png) | ![供应商管理（英文）](../images/en-US/srm-suppliers.png) | ![供应商管理（日文）](../images/ja-JP/srm-suppliers.png) | ![供应商管理（韩文）](../images/ko-KR/srm-suppliers.png) |
| 绩效评估（evaluation） | ![绩效评估（简体中文）](../images/zh-CN/srm-evaluations.png) | ![绩效评估（英文）](../images/en-US/srm-evaluations.png) | ![绩效评估（日文）](../images/ja-JP/srm-evaluations.png) | ![绩效评估（韩文）](../images/ko-KR/srm-evaluations.png) |
| 风险管理（risk） | ![风险管理（简体中文）](../images/zh-CN/srm-risks.png) | ![风险管理（英文）](../images/en-US/srm-risks.png) | ![风险管理（日文）](../images/ja-JP/srm-risks.png) | ![风险管理（韩文）](../images/ko-KR/srm-risks.png) |
| 风险指标配置（risk） | ![风险指标配置（简体中文）](../images/zh-CN/srm-risk-config.png) | ![风险指标配置（英文）](../images/en-US/srm-risk-config.png) | ![风险指标配置（日文）](../images/ja-JP/srm-risk-config.png) | ![风险指标配置（韩文）](../images/ko-KR/srm-risk-config.png) |
| 邀请管理（invite） | ![邀请管理（简体中文）](../images/zh-CN/srm-invites.png) | ![邀请管理（英文）](../images/en-US/srm-invites.png) | ![邀请管理（日文）](../images/ja-JP/srm-invites.png) | ![邀请管理（韩文）](../images/ko-KR/srm-invites.png) |
### 供应商门户响应式稳定性截图（四语言）
| 视口 | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 390×844 移动 | ![供应商门户报价列表移动视口（简体中文）](../images/zh-CN/srm-portal-quotation-mobile.png) | ![供应商门户报价列表移动视口（英文）](../images/en-US/srm-portal-quotation-mobile.png) | ![供应商门户报价列表移动视口（日文）](../images/ja-JP/srm-portal-quotation-mobile.png) | ![供应商门户报价列表移动视口（韩文）](../images/ko-KR/srm-portal-quotation-mobile.png) |
| 1024×768 平板 | ![供应商门户报价列表平板视口（简体中文）](../images/zh-CN/srm-portal-quotation-tablet.png) | ![供应商门户报价列表平板视口（英文）](../images/en-US/srm-portal-quotation-tablet.png) | ![供应商门户报价列表平板视口（日文）](../images/ja-JP/srm-portal-quotation-tablet.png) | ![供应商门户报价列表平板视口（韩文）](../images/ko-KR/srm-portal-quotation-tablet.png) |

这两种窄屏图只作为门户列表与页签布局参考，不证明移动端填单、提交和完整报价闭环。旧报价表单保留了背景透出、日韩有效期标签换行及单位“件”等表现；不改绘图片来掩盖缺口。

<!-- omni:modules:end -->

[本次核对记录](../reference/evidence/interface-reference-wiki-review-2026-10-17.json)区分 68 张基线图片的摘要核对与 12 张原图的实际查看。本文按真实 AI_REVIEW 验收，人工文档复核不是必需条件；历史界面翻译缺口、当前业务运行与最终站点验收仍分别保留。
