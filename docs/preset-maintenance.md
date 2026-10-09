# 项目预设维护手册

本文面向脚手架维护者。`scaffold/catalog/modules.yaml` 是模块组合的唯一事实来源，`scaffold/presets/*.yaml` 只声明入口模块。依赖矩阵和 README 预设表由 `npm run docs:preset` 生成。

## 受管理文件

- `scaffold/catalog/modules.yaml`：模块、依赖、资源、权限、数据库、MQ、XXL-JOB、文档和兼容性。
- `scaffold/schemas/module.schema.json`：模块清单 Schema。
- `scaffold/schemas/preset.schema.json`：预设 Schema。
- `scaffold/presets/*.yaml`：五种正式预设。
- `tools/omni-cli/src/preset-generator.ts`：原子复制与结构化裁剪。
- `tools/omni-cli/scripts/preset-golden.mjs`：生成物矩阵和残留门禁。
- `scaffold.lock`：生成工程的版本与模块快照。

## 新增模块

1. 先完成模块代码、迁移、幂等种子、权限、Compose、Gateway 和文档。
2. 在 catalog 末尾按实际依赖顺序登记模块；`dependencies` 只能指向已声明模块。
3. 填全后端模块、前端 view/component/API/i18n、Gateway route、Compose service、changelog、seed、provisioning、Nacos、端口、MQ、XXL-JOB、文档和资源估算。
4. `optionalModules` 只表示可选集成，不能代替运行时关闭开关；`conflicts` 必须双向或由校验规则明确处理。
5. 运行 `omni catalog validate`，修复不存在的路径、孤立 Compose 依赖、重复端口或未受管理的正式模块。
6. 将模块加入合适的预设入口；不要复制传递依赖。
7. 增加裁剪单元测试、生成工程构建和运行时冒烟证据。

领域状态机、DataScope 表映射、子资源继承、Saga 和幂等规则属于业务模块，不能下沉到通用预设生成器。

## 修改正式预设

只编辑 `scaffold/presets/<id>.yaml` 的入口模块和说明。随后执行：

~~~powershell
Set-Location tools/omni-cli
npm test
npm run test:preset-structure
npm run docs:preset
npm run docs:preset:check
~~~

模块集合、默认配置或生成结果发生兼容性变化时递增 `version`：修复用 patch，向后兼容能力增加用 minor，破坏性边界变化用 major。catalog 模块版本与预设版本分别维护。

## 黄金样例和发布门禁

- PR：`npm run test:preset-smoke`，验证 `core` 与一个业务预设，后端使用 `clean verify` 避免共享 `.m2` 写冲突。
- 夜间/发布：`npm run test:preset-golden`，五预设执行 `clean install`、前端 ci/lint/build、Compose 配置和残留扫描。
- 运行时：每个预设执行 db-migrator fresh、真实启动、登录、菜单、health 和核心流程；`full` 运行全量 E2E。

残留扫描至少覆盖 Maven、Dockerfile、Compose、Gateway、页面、组件、API、i18n、权限、数据库、MQ 和模块独占文档。

## 失败、回滚和定位

生成使用同级 staging 目录，失败会删除 staging，目标目录不会出现半成品。已有非空目标会在复制前拒绝。

定位顺序：Schema 错误 → 依赖闭包/冲突 → catalog 路径 → 结构化裁剪 → Maven/npm → Compose → fresh 数据库 → 运行时/E2E。生成后失败时保留日志和 `scaffold.lock`，不要在受管理文件中直接修补；先修源 catalog、模板或裁剪器，再重新生成到新目录。

已执行 Liquibase changeSet 永不改写。回滚应用版本时保留兼容数据库结构，必要时追加前向修复 changeSet；不要用随意的 down SQL。
