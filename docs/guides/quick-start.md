# 五分钟快速启动

适用版本：Omni-Stack 0.6.x

本指南用于在本机启动开发环境并完成第一次登录。生产部署请改读 [Docker 部署指南](../docker-deployment.md) 和 [运维、备份、恢复与升级](operations-upgrade.md)。

## 1. 前置条件

- JDK 25。
- Node.js 22.12.0 或更高的 LTS 版本。
- Docker Desktop，且 Docker Compose 可用。
- 可用内存建议不少于 12 GB；只体验单一领域时优先使用项目预设。

## 2. 选择启动方式

完整系统：

```bash
docker compose --profile full up -d
docker compose ps
```

只体验 CRM：

```bash
npm --prefix tools/omni-cli run dev -- dev up --preset crm
```

可用预设包括 `core`、`crm`、`srm`、`procurement`、`asset` 和 `supply-chain`。选择依据见 [项目预设快速选择](../preset-quick-selection.md)。首次启动会先运行 Liquibase 迁移器，再启动服务；不要再执行历史 `init-all.sql` 或 `migrate-*.sql`。

## 3. 确认服务状态

等待 `docker compose ps` 中已选择的应用变为 `healthy`。常用入口：

| 入口 | 地址 | 用途 |
|---|---|---|
| 前端 | `http://localhost:3000` | 登录、工作台与管理端 |
| Nacos | `http://localhost:8080` | 本地服务发现与配置 |
| Gateway | `http://localhost:8102` | 所有前端 API 的统一入口 |

如果端口被占用，先用 `docker compose ps` 和 `docker compose logs <service>` 找出实际失败服务，不要反复重建数据库卷。

## 4. 第一次登录

1. 打开前端登录页。
2. 选择开发租户。
3. 使用 `scripts/sql/seed/auth.sql` 中明确标记的开发管理员账号。
4. 输入当前页面生成的一次性验证码。
5. 登录后先修改或替换开发默认凭据；不得把种子账号用于共享、测试外或生产环境。

验证码区分一次请求一次使用。刷新验证码或提交失败后，应使用新图片和新 `captchaKey`，不能重复提交旧验证码。

## 5. 第一次功能检查

管理员应能看到系统管理、基础数据、任务调度、运维监控、工作流，以及所选预设包含的业务模块。建议依次检查：

1. “系统管理 → 用户管理”能加载列表。
2. “工作流管理 → 流程模型”能看到预置模型。
3. 目标业务模块概览页能加载真实数据或明确空状态。
4. 工作台“我的定时任务”能打开创建对话框。

菜单缺失通常意味着当前角色没有对应权限，或预设没有包含该模块，不应通过手工修改前端路由解决。

## 6. 本地源码开发

后端全量验证：

```bash
cd omni-backend
./mvnw clean install
```

Windows 执行前必须把 `JAVA_HOME` 设置为 JDK 25。前端验证：

```bash
cd omni-frontend
npm install
npm run lint
npm run build
npm run dev
```

## 7. 停止环境

```bash
docker compose --profile full down
```

默认不要加 `--volumes`，这样本地业务数据会保留。只有明确要重建全新数据库，并确认目标 Compose project 和卷名后，才删除专属数据卷。

## 8. 下一步

- 登录与租户：[认证与租户选择](authentication.md)
- 权限模型：[菜单、角色与数据权限](permissions.md)
- 新服务与 CRUD：[脚手架开发教程](scaffold-development.md)
- 常见错误：[故障排查手册](troubleshooting.md)

