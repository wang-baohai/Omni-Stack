# 独立文档站与四语言维护

适用范围：仓库 Markdown 和文档站，不需要启动任何业务服务。规范正文由 `docs/docs-manifest.yaml` 登记；`docs/.vitepress/source` 是生成投影，不能手改或提交。

## 1. 构建、启动和停止

在仓库根目录执行，前提是本机 Docker 可用、4173 端口空闲：

```bash
docker compose -p omni-stack-docs -f compose.docs.yaml up -d --build
docker compose -p omni-stack-docs -f compose.docs.yaml ps
docker compose -p omni-stack-docs -f compose.docs.yaml logs wiki
```

浏览器打开 `http://localhost:4173`，选一种语言进入学习路径，再搜索一个项目术语。四种语言分别有正文和导航；缺少语言文件时构建失败，不使用中文回退。停止：

```bash
docker compose -p omni-stack-docs -f compose.docs.yaml down
```

文档 project 为 `omni-stack-docs`，只运行一个 Nginx 容器，固定绑定 `127.0.0.1:4173`，没有业务数据卷。若端口冲突，检查并解除冲突，不静默换端口。镜像构建中使用 Node，运行时只提供静态文件。

## 2. 一份正文、两种阅读入口

GitHub/Gitee 直接阅读 `docs/wiki/index.md` 及相对链接。构建器从同一份正文生成 `/zh-CN/`、`/en-US/`、`/ja-JP/`、`/ko-KR/` 页面；网站生成目录和缓存被忽略。

源码和规则链接在仓库中使用相对路径，在网站中转成托管平台源码链接。默认平台为 GitHub；构建时可设置 `DOCS_REPOSITORY_URL=https://gitee.com/wang-baohai/Omni-Stack`。裁剪项目应设置自己的仓库地址。旧验收记录保留适用提交，不被当作当前源码行为。

图片来自 `docs/images`，由构建器复制到站点。Markdown 中的说明和代码仍可直接阅读。站点使用随构建打包的 Mermaid，在浏览器中将图表源码渲染为 SVG，并在切换页面或明暗主题后更新；渲染失败时保留源码。大图可在图表区域内滚动，不依赖外部图服务。

## 3. 修改与质量门

1. 修改中文源，四语言同步正文、导航和图注；包含界面文字的截图必须切换对应语言。
2. 在清单登记新增文件，中文和译文更新后刷新摘要。源或译文变化自动使旧复核记录失效。
3. 运行内容完整性、链接、截图、敏感信息、预设派生文档和站点构建检查。内容检查通过不等于复核通过。
4. 按当前清单策略完成实际复核，记录 `reviewer`、`reviewed_at`、`review_type`、`reviewed_source_sha256` 和 `translation_sha256`。真人使用 `HUMAN_REVIEW`；所有者已按 [ADR-0003](../adr/0003-authorized-ai-translation-review.md) 授权 Codex 使用 `AI_REVIEW`，另记录逐项审校证据路径与摘要，才可以标 `synchronized`。
5. 用清单重新生成复核队列；严格 `docs:i18n:check` 是发布门，不使用草稿参数取得发布通过。

AI 审校必须逐篇核对完整语义、技术字面量、安全与失败条件、目标语言、图片和链接；记录不冒称真人。自动翻译、平台审批和内容检查本身不生成签核。待复核页面在站点显示提示。适用版本、验证日期和证据必须反映真实运行，不能填推测值。

业务界面取证使用本机 Docker 中的 Playwright，选择 `E2E_RUNTIME_MODE=external`，连接已确认的 Docker 前端，并显式设置 `E2E_BASE_URL`、`E2E_TENANT_ID`、唯一的 `E2E_EXECUTION`、`E2E_REGISTRY_PATH` 和仓库外的 `E2E_ARTIFACT_DIR`。用 `E2E_DOCS_IMAGE_DIR` 指定仓库外的待审图片目录；完成视觉检查、遮罩和来源登记后，只将必要图片纳入正式资产。真实登录的验证码由人填写，测试身份通过测试 classpath 的受控 fixture 获取短期令牌。

`E2E_RUNTIME_MODE=display-fixtures` 仅用于界面展示：它替换验证码、租户和设备码响应，并固定时钟。这种结果须标明模拟展示，不能登记为真实认证或业务链路通过。正式 `external` 模式使用真实接口与时钟，保留真实倒计时；必要的故障注入也须单独标注测试范围。`npm run docs:screenshots:test` 校验两种模式的边界，命令本身也在 Docker 前端工具容器中执行。

## 4. 静态交付

`docker/docs/Dockerfile` 的 build 阶段输出 `/workspace/docs/.vitepress/dist`，最终镜像只携带该目录。可以从构建后的容器导出静态产物，交给独立 Web 服务器。对子路径部署，通过 `DOCS_BASE` 在构建时设置站点基路径，并验证导航、搜索和图片。

本仓库的普通业务镜像仍使用根 `.dockerignore`，不会带入 docs 或 ai。文档镜像使用自己的 Dockerfile 忽略文件，只打包文档输入。发布前必须完成严格语言和截图门；本地预览不会自动发布到 GitHub Pages、Gitee 或生产服务器。

## 5. 常见问题

| 现象 | 检查 |
|---|---|
| 页面或语言缺失 | 清单中的源与三语路径是否存在；是否登记了新页 |
| 站点链接错误 | 仓库相对链接是否正确，生成投影是否重新构建 |
| 搜索无结果 | 内容是否属于当前语言；页面是否已纳入清单和索引 |
| 图片缺失 | 原始图片路径、构建上下文、截图清单与对应语言 |
| 翻译门失败 | 复核者、日期、正文与译文摘要；AI 复核还需授权和证据摘要，变化后需真实重新复核 |
| 构建使用旧内容 | 重建镜像，核对源摘要和镜像身份 |
