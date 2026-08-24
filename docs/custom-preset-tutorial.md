# 自定义预设教程

自定义预设适合在五种正式预设之外组合 catalog 已知模块。它使用与正式预设相同的 Schema、依赖闭包、冲突检查、原子生成和残留检查。

## 1. 编写 YAML

例如只需要供应商和工作流能力：

~~~yaml
id: supplier-workspace
version: "1.0.0"
displayName: 供应商工作台
description: 核心平台、工作流和供应商管理。
modules: [srm, gateway, mysql, redis, nacos]
~~~

`modules` 只写入口模块。`srm` 会自动引入 `workflow → base → auth → platform`；生成器不会自动加入与依赖无关的 Procurement 或 Asset。

## 2. 校验和解释

~~~powershell
Set-Location tools/omni-cli
npm run build
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset explain C:\WorkSpace\supplier-workspace.yaml
~~~

未知模块、Schema 错误或冲突会在写入任何文件前失败。

## 3. 预览并生成

~~~powershell
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app --dry-run
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
~~~

生成工程会保存规范化的 `scaffold/presets/supplier-workspace.yaml` 和 `scaffold.lock`。共享 Workflow 服务中的采购、资产默认流程会按组合裁掉，只保留供应商准入流程。

## 4. 验证生成工程

~~~powershell
Set-Location C:\WorkSpace\supplier-app\omni-backend
$env:JAVA_HOME='C:\APP\JDK25\jdk-25.0.2'
.\mvnw.cmd clean install

Set-Location ..\omni-frontend
npm ci
npm run lint
npm run build

Set-Location ..
docker compose config --quiet
~~~

随后使用隔离 Compose project/volume 执行 fresh 数据库、启动和浏览器冒烟。不要与现有开发栈共用数据库卷。

## 常见错误

- 把 `omni-*` artifact 名写进 `modules`：这里必须使用 catalog 的模块 ID。
- 显式复制全部传递依赖：不需要，且会增加维护噪声。
- 输出到当前仓库或非空目录：生成器会拒绝。
- 手工删除生成后的模块：会使 `scaffold.lock`、种子摘要和路由失真，应修改 YAML 后重新生成。
- 将可选基础设施理解为自动关闭：生成后仍需检查对应运行时开关。
