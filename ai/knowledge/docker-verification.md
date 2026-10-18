# 本机 Docker 验证经验

- 来源：`fc165e3` 的仓库治理规则与 2026-10-09 本机 Docker 身份核验。
- 适用：本仓库与遵守相同运行规则的生成项目。
- 失效条件：Docker endpoint、Compose 命名、固定端口、fixture 或运行政策变化；使用前重读当前 AGENTS、Compose 和夹具说明。

Docker Desktop 的本机 context 可指向 `npipe`；沙箱拒绝访问 daemon 不等于 Docker 不可用。先读取 context/endpoint，再通过平台正式权限流程获取访问，不能改用远端 Docker 或宿主应用服务器。

标准 `omni-stack` 与教学隔离环境要区分。已有容器健康只证明当前容器状态，不证明新代码已构建，也不证明空库教程、生成物或四语截图通过。

文档截图配置的 external 模式连接 Docker 前端，缺少执行身份、租户或 registry 时应失败。真实验证码不能由测试 token fixture 冒充。临时 token 和原始浏览器材料留在受限仓库外目录。

以上是排错经验。正式规则以根 AGENTS 和 `docs/repository-governance.md` 为准，不建立第二份契约。
