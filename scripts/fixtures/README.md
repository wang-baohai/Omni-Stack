# 受控验证资产

这些文件保留可复用的测试能力，不参与正式数据库初始化。正式结构与数据分别由 `database/changelog/`、`scripts/sql/seed/` 管理。

- `captcha/CaptchaFixture.java`：受控验证码图片 fixture；真实登录仍须使用服务生成的一次性验证码。
- `integration/*.sql`：显式隔离的双租户与 CRM 测试数据。只能在已核实目标的测试数据库执行，不能导入标准环境或生产库。历史绑定环境的测试保留其 UUID、租户和资源保护条件，不能取消保护条件以冒充通过。
- `observability/alert-receiver.py`：通知送达与短时失败 fixture。只记录白名单字段；标准 Alertmanager 默认不依赖此接收器。
- `observability/perf-compare.py`：对有效业务 GET 的性能比较工具。认证头只从受限文件读取，不打印其值。
- `run-token-fixture.ps1`：测试 classpath 身份生成包装器。标准应用镜像不包含该 fixture；验证时在 Docker 中使用测试 classpath 执行，令牌输出放在仓库外。

所有验证工具、服务和应用在本机 Docker 内运行，输出写到仓库外的受限目录。使用 fixture 前先核实目标容器、数据库、租户和业务键；不得仅根据旧环境名称复用运行数据。
