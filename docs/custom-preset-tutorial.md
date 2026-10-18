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

将上述 YAML 保存为源仓库的 scaffold/presets/supplier-workspace.yaml。以下命令从源仓库根目录执行，在本机 Docker 工具容器中构建和运行 CLI。

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate scaffold/presets/supplier-workspace.yaml
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset explain scaffold/presets/supplier-workspace.yaml
~~~

未知模块、Schema 错误或冲突会在写入任何文件前失败。

## 3. 预览并生成

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create scaffold/presets/supplier-workspace.yaml --output generated/supplier-app --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create scaffold/presets/supplier-workspace.yaml --output generated/supplier-app
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate scaffold/presets/supplier-workspace.yaml --output generated/supplier-app
~~~

生成工程会保存规范化的 `scaffold/presets/supplier-workspace.yaml` 和 `scaffold.lock`。共享 Workflow 服务中的采购、资产默认流程会按组合裁掉，只保留供应商准入流程。

## 4. 验证生成工程

`docker compose config`/启动会读取工程根目录的 `.env`；缺它会以 "required variable is missing" 失败（exit 1）。先从生成工程自带的 `.env.example` 复制并填入**自造的随机秘密**（≥ 32 字节，`JWK_ENCRYPT_KEY` 必须 32 字符；禁止复用仓库占位默认值或真实共享密钥），并行隔离时另设独立 project/端口/卷/镜像命名空间：

NACOS_AUTH_TOKEN 必须是至少 32 字节随机值的 Base64 编码；NACOS_USERNAME 使用 nacos，NACOS_PASSWORD 需与服务端和所有客户端保持一致。

~~~powershell
Set-Location generated/supplier-app
Copy-Item .env.example .env   # 填必填: OMNI_INTERNAL_API_TOKEN / GATEWAY_ASSERTION_SECRET / MYSQL_ROOT_PASSWORD / OMNI_DB_PASSWORD / REDIS_PASSWORD / XXL_JOB_ACCESS_TOKEN / XXL_JOB_ADMIN_USERNAME / XXL_JOB_ADMIN_PASSWORD / NACOS_PASSWORD / NACOS_AUTH_TOKEN / NACOS_AUTH_IDENTITY_KEY / NACOS_AUTH_IDENTITY_VALUE / OAUTH2_STATE_SECRET / JWK_ENCRYPT_KEY; 隔离设 COMPOSE_PROJECT_NAME(或 -p)、OMNI_MYSQL_VOLUME_NAME、OMNI_*_HOST_PORT、OMNI_IMAGE_NAMESPACE
~~~

随后构建后端、前端并校验 Compose 配置：

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose config --quiet
~~~

随后使用隔离 Compose project/volume 执行 fresh 数据库、启动和浏览器冒烟。不要与现有开发栈共用数据库卷。

## 常见错误

- 把 `omni-*` artifact 名写进 `modules`：这里必须使用 catalog 的模块 ID。
- 显式复制全部传递依赖：不需要，且会增加维护噪声。
- 输出覆盖源仓库根目录本身或非空目录：生成器会拒绝。
- 手工删除生成后的模块：会使 `scaffold.lock`、种子摘要和路由失真，应修改 YAML 后重新生成。
- 将可选基础设施理解为自动关闭：生成后仍需检查对应运行时开关。
- 含 RocketMQ 却未含 `srm`：`auth` 的门户角色消费者订阅 `srm-domain-event`，生成器会因悬空 consumer 拦截写入；启用 MQ 的组合必须一并纳入 `srm`。不含 RocketMQ 时该约束不适用（如只含 `auth`、`workflow`、`crm` 的裁剪组合可正常生成）。
