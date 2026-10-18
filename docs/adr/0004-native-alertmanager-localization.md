# ADR-0004：从官方 Elm 源码构建 Alertmanager 四语界面

- 状态：已采纳
- 日期：2026-10-13
- 范围：可选本地 observability profile；不替换 Alertmanager 后端
- 关联：[Wiki 与 AI 资产治理](0002-repository-wiki-and-ai-assets.md)、[观测事实](../observability.md)

## 背景

项目所有者明确要求 Alertmanager 的中、英、日、韩真实界面与截图，不接受共用英文原图作为完成。官方 v0.30.1 中可用的完整界面是 Elm；同版本 React 目录仅有部分页面占位，不能据此替代已有功能。

## 决策

1. 后端继续使用 `prom/alertmanager:v0.30.1`，内部服务名及端口保持 `alertmanager:9093`。
2. 使用官方 `prometheus/alertmanager` 的 `v0.30.1` 源码归档，固定 SHA-256 `b3577c531d4c05e0f45f90b02224c81684be9f4083720a01cc05bfa07107fc93`。构建前验证摘要，不接受漂移源码。许可证与 NOTICE 随源记录并随产物发布。
3. `observability/alertmanager-ui/messages.tsv` 是界面文案唯一来源。构建器在已核对的 Elm 文件中增加翻译调用和原生语言选择器，分别编译四个语言包。不使用 DOM 文字替换、截图覆盖或静态页面模拟真实告警。
4. API 字段、路径、过滤键、标签值和状态枚举保持原协议。星期设置只翻译显示文字，`localStorage` 仍保存 `Monday`、`Sunday`、`Saturday`，切换语言后可继续读取。
5. 新增可选 `alertmanager-ui` 静态服务，向同一后端代理 API 和官方样式资源。宿主入口仍为 `127.0.0.1:9093`，默认中文，支持 `/zh-CN/`、`/en-US/`、`/ja-JP/`、`/ko-KR/`。语言选择器保留当前 hash 路由及筛选条件。后端不再单独映射同一宿主端口。
6. Prometheus 的告警发送、后端存储、管理 API 和指标仍使用原内部地址。界面代理不提供新的账号或授权机制；本地管理入口不得公开到互联网。

## 维护与后果

需要额外构建一个静态服务，但无须复制整个上游仓库或修改 Go 后端。Docker 构建仅需要固定版本的 Elm 编译器和上游依赖；构建镜像安装 CA 证书，保持 TLS 校验。

升级 Alertmanager 时必须同时核对归档摘要、Elm 源码变动、翻译清单、API/样式路径和四语浏览器验证。缺失文案键或结构补丁不匹配时构建失败，不能静默跳过。业务标签、告警内容、接收器名称和上游返回的诊断信息属于真实数据，保持原值，不伪造为界面翻译。

当前截图与运行证据由[运维教程](../guides/operations-upgrade.md)引用。历史英文图片保留其历史身份，不再作为当前四语界面的证明。
