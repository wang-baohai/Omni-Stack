# S1-06 前端类型治理与功能闭环验收证据

验收日期：2026-08-26
工作分支：`codex/scaffold-upgrade`

## 1. 结论

WP-08 已完成。前端 ESLint 从计划基线的 197 条 warning 清零，生产构建通过；Workflow 模型属性编辑、保存、校验、发布，以及动态任务表单创建、编辑、立即执行和执行日志均在独立全栈环境通过真实浏览器验收。

## 2. 类型与 lint 治理

- 新增 `src/types/bpmn.ts`，为 bpmn-js Modeler、Canvas、Moddle、BusinessObject、命令上下文和 Context Pad 建立项目所需的最小类型视图。
- 新增 `src/types/schema.ts`，统一 JSON Schema 字段、选项、参数值与运行时守卫。
- 对第三方未知值使用 `unknown`、窄化函数和结构化错误提取，没有以 `any` 或全局规则降级绕过。
- 清除生产代码中的 `console` 调用、显式 `any`、双重断言和大范围 ESLint 禁用。
- `npm run lint` 固化为 `eslint . --max-warnings 0`；CI 继续调用该脚本，因此 warning 会直接阻断质量门。
- ESLint 排除 Playwright 的 `test-results/**` 与 `playwright-report/**` 运行产物，避免失败 trace 中的第三方快照被误当成源码扫描。

类型治理主体提交：`80a7e27 refactor(frontend): enforce zero-warning type safety`。

## 3. 运行态发现并修复的问题

1. 标准 JSON Schema 使用 `type: "string"` 配合 `enum` 时，动态表单和 Schema 编辑器曾错误渲染为文本框。现统一识别为下拉选择，兼容标准 Schema 与既有扁平配置。
2. Playwright 初始选择器未按 Element Plus 控件的真实可访问名称区分 `OK`、`确认`，已改为与页面语义一致的定位。
3. 创建任务后的 API 断言曾与 Vue 异步保存、列表刷新竞争。现先等待对话框关闭和业务行出现，再读取 API，避免以固定延迟掩盖时序问题。

## 4. 自动化门禁

| 检查 | 结果 |
|---|---|
| `npm run lint` | 通过，0 error / 0 warning |
| `npm run build` | 通过，TypeScript 检查成功，Vite 转换 2448 个模块 |
| `npx playwright test --list` | 通过，共发现 19 条 Chromium 用例 |
| 禁止逃逸模式扫描 | 源码中无显式 `any`、`as any`、双重断言、`ts-ignore`、`ts-expect-error`、`eslint-disable` 和 `console` |

## 5. 独立全栈浏览器验收

使用 Compose project `omni-wp08-e2e` 启动独立 MySQL、Redis、Nacos、XXL-JOB、Auth、Base、Workflow、Gateway 和 Frontend。前端入口为 `http://localhost:24000`，Gateway 为 `http://localhost:29102`。登录使用一次性 CAPTCHA，凭据和访问令牌仅在进程内存中传递，未写入仓库或证据文件。

最终联合运行命令：

```text
npx playwright test --grep '流程模型属性|动态任务表单' --workers=1
```

结果：2 passed，耗时 10.2 秒。

### 5.1 Workflow 模型闭环

1. 创建唯一测试模型并保存合法 BPMN 草稿。
2. 从管理页面搜索并进入流程设计器。
3. 选中审批节点，修改审批角色属性。
4. 保存草稿并确认成功提示。
5. 执行服务端校验并确认通过。
6. 发布模型并核对已生成发布版本。

单条最终用例耗时 3.8 秒。

### 5.2 动态任务表单闭环

1. 在“我的定时任务”创建“喝水提醒”任务。
2. 标准 JSON Schema 的杯型枚举正确渲染为下拉框。
3. 编辑任务名称并将杯型改为“大杯”。
4. 立即执行任务。
5. 轮询执行日志，确认结果消息包含更新后的“大杯”参数。
6. 通过业务 API 删除测试任务。

单条最终用例耗时 5.8 秒。

## 6. 隔离与清理

- 用例名称和模型标识均使用 `wp08-e2e-*` 唯一前缀，正常路径在 `finally` 中调用业务 API 清理。
- 失败重试遗留的草稿模型和任务已通过业务 API 删除。
- 两条已发布测试模型按产品规则禁止直接删除，随专属数据库卷一并销毁。
- 最终 `omni-wp08-e2e` 容器残留 0、数据卷残留 0。
- Playwright `test-results` 已删除，`playwright-report` 未生成。

## 7. WP-08 验收映射

| 计划验收项 | 证据 | 状态 |
|---|---|---|
| lint 0 error / 0 warning | 第 4 节 | 通过 |
| production build | 第 4 节 | 通过 |
| Workflow 建模、保存、校验、发布、属性编辑 | 第 5.1 节 | 通过 |
| 动态任务表单创建、编辑、触发 | 第 5.2 节 | 通过 |
| CI 使用 `--max-warnings 0` | 第 2 节 | 通过 |
