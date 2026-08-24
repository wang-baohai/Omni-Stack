# 项目预设快速选择

预设用于从 Omni-Stack 源工程生成一个新的、边界清晰的工程目录。命令不会原地删除当前仓库内容。模块、依赖、端口和资源占用的事实来源是 `scaffold/catalog/modules.yaml`；完整结果见[预设依赖矩阵](preset-dependency-matrix.md)。

## 选择建议

| 需求 | 选择 | 包含的业务边界 |
|---|---|---|
| 登录、RBAC、组织、字典、日志和基础任务 | `core` | 不带工作流和业务域 |
| 需要 BPMN、审批、待办和流程实例 | `workflow` | `core` + Workflow |
| 建设销售、客户和商机系统 | `crm` | `core` + CRM，不带供应链 |
| 建设供应商、采购、资产闭环 | `supply-chain` | Workflow + SRM + Procurement + Asset |
| 需要当前仓库全部能力 | `full` | CRM、供应链、资产和完整基础设施 |

若仍不确定，从满足近期需求的最小预设开始。后续增加模块比从完整工程人工删除模块更安全。

## 使用步骤

~~~powershell
Set-Location tools/omni-cli
npm ci
npm run build
node dist/src/cli.js preset list
node dist/src/cli.js preset explain workflow
node dist/src/cli.js preset create workflow --output C:\WorkSpace\my-workflow --dry-run
node dist/src/cli.js preset create workflow --output C:\WorkSpace\my-workflow
node dist/src/cli.js preset validate workflow --output C:\WorkSpace\my-workflow
~~~

生成前会显示依赖闭包、后端模块、Compose 服务、端口、数据库、权限根和内存估算。输出目录必须不存在或为空。生成完成后，`scaffold.lock` 记录源版本、预设版本、模块版本和模板版本。

## 生成后必须验证

在生成工程中执行后端构建、`npm ci`、前端 lint/build 和 `docker compose config --quiet`。仓库维护者还应运行：

~~~powershell
npm run test:preset-structure
npm run test:preset-smoke
~~~

`test:preset-golden` 会执行全部五种预设的完整构建矩阵，适合夜间或发布前运行。数据库 fresh、真实启动和浏览器冒烟属于运行时验收，不能用结构检查替代。

## 重要边界

- `core`、`workflow` 和 `crm` 不启动 RocketMQ、XXL-JOB 管理端；相关能力必须通过配置关闭或按需加入自定义预设。
- `supply-chain` 通过依赖闭包自动包含 Workflow、SRM 和 Procurement，不能跳过中间依赖。
- 自定义组合必须引用 catalog 中已有模块，详见[自定义预设教程](custom-preset-tutorial.md)。
- 不支持对当前仓库原地裁剪，也不要手工删除生成工程中的受管理文件。
