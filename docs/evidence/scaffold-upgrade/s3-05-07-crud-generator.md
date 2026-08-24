# S3-05～S3-07 全栈 CRUD 生成器证据

日期：2026-08-24

分支：`codex/scaffold-upgrade`

关键提交：

- `c2ae80f feat(scaffold): validate safe crud declarations`
- `95dd0e6 feat(scaffold): generate atomic full-stack crud slices`
- `5ae25cd fix(scaffold): register generated data scope tables`
- `5fa77ef test(scaffold): verify generated crud database contracts`
- `7333970 fix(scaffold): preserve generated numeric contracts`
- `ddea08b test(scaffold): verify generated crud runtime matrix`

## 1. 验收结论

CLI 0.6.1 的固定黄金声明 `scaffold/specs/material-brand.yaml` 已完成从安全输入契约到真实运行的闭环：

- JSON Schema 和语义校验拒绝复杂状态机、Workflow、Saga、跨聚合事务、事件语义推断和未声明 PII 策略。
- 一次生成 18 个受锁文件，连同 8 个已有注册点，共形成 27 项原子变更。
- 第二次生成无差异；受锁文件发生人工漂移时停止，注入中途失败时完整回滚。
- 生成后端通过 JDK 25 Maven 构建与采购模块 174 项测试。
- 生成前端通过生产构建、ESLint 和依赖审计，锁定依赖审计为 0 vulnerabilities。
- fresh DB、权限种子幂等、租户隔离、逻辑删除后唯一值复用均通过真实 MySQL 验证。
- 真实服务完成鉴权、CRUD、重复冲突、跨租户读写隔离和逻辑删除矩阵。
- 所有临时服务、端口、数据库和系统临时目录均已清理。

## 2. S3-05 输入契约

`scaffold/schemas/crud.schema.json` 固定以下安全边界：

- Java、TypeScript 和 MySQL 类型必须使用允许列表并保持兼容映射。
- Decimal 只能声明为字符串传输；DateTime 固定为 `yyyy-MM-dd HH:mm:ss`。
- tenant 资源的唯一约束必须包含 `tenant_id`。
- DataScope 只能在 `tenant=true` 且同时声明 owner user/unit 时启用。
- PII 分类和掩码策略必须显式声明，生成器不做业务推断。
- 权限角色必须显式声明且包含 `SUPER_ADMIN`。
- 当前经过完整模块约定验证的目标是 Procurement；其他模块在建立等价约定前会被拒绝，而不是生成未经验证的代码。

固定黄金声明生成 `proc_material_brand`，资源路径为
`/api/procurement/material-brands`，权限前缀为 `procurement:material-brand`。

## 3. S3-06 生成结果与所有权

生成范围包括：

- 后端 Entity、Query、Create/Update Request、VO、Mapper、Service、ServiceImpl、Controller 和契约测试。
- 前端 TypeScript API、列表/筛选/分页/新增/编辑/删除页面和 `v-permission`。
- Procurement 与 Auth 的 forward-only Liquibase changeSet、独立权限 seed、种子 manifest 来源和断言。
- 菜单、路由、中文和英文正式语言包，以及日文、韩文待翻译契约片段。
- API、权限、表结构、使用方法和所有权说明文档。
- DataScope 仅在声明完整 owner 列时注册到 `ProcDataPermissionHandler`。

受锁生成文件保存模板版本和 SHA-256。已执行 changeSet 不会原地改写；人工修改 generated 文件会触发漂移错误，
handwritten 文件不会被生成器接管。逻辑删除资源使用活动唯一守卫列，使同租户删除后能够安全复用业务唯一值。

Spring Boot 4 使用 Jackson 3，生成 VO 对 Long 和 Decimal 同时写入 Jackson 2/3 的字符串序列化注解，
既保持现有兼容层，也保证实际运行 JSON 不丢失 64 位精度。该行为已有生成器回归断言和运行时响应验证。

## 4. S3-07 自动化质量门

```text
命令：cd tools/omni-cli && npm test
结果：23 passed，0 failed
```

```text
命令：cd tools/omni-cli && npm run test:crud-golden
结果：generated=18, changes=27
后端：omni-procurement 及依赖构建成功，采购模块 174 项测试通过
前端：npm ci 0 vulnerabilities，生产 build 和 lint --max-warnings 0 通过
再生成：unchanged
```

```text
命令：cd tools/omni-cli && npm run test:crud-db-golden
结果：fresh DDL=1, permissions=5, roleBindings=5
租户隔离：pass
逻辑删除后唯一值复用：pass
```

```text
命令：cd tools/omni-cli && npm run test:crud-runtime-golden
结果：direct=403, anonymous=401, noScope=403, crud=pass,
      duplicate=409, crossTenant=isolated, deleted=404
```

运行时黄金脚本在经过校验的系统临时目录复制干净工作区，原子应用生成结果，按统一数据库顺序加载公共
Outbox 表和生成业务表，使用随机临时数据库与独立端口启动生成后的采购 JAR，最后无条件停止服务、撤销
单库授权、删除数据库并删除临时工作区。

## 5. 运行时权限与业务矩阵

| 场景 | HTTP | 响应 code | 结果 |
|---|---:|---:|---|
| 绕过 Gateway 直连列表 | 403 | 403 | 通过 |
| Gateway 标记存在但无身份 | 401 | 401 | 通过 |
| 身份有效但缺资源权限 | 403 | 403 | 通过 |
| 管理员创建、分页、更新、删除 | 200 | 200 | 通过 |
| 另一租户分页读取 | 200 | 200 | 0 条，不可见 |
| 另一租户更新已有 ID | 200 | 404 | 不可写 |
| 同租户重复业务编码 | 200 | 409 | 冲突被拒绝 |
| 删除后按 ID 查询 | 200 | 404 | 不可见 |

401/403 由安全过滤器直接映射为 HTTP 状态；业务异常沿用项目当前统一约定，以 HTTP 200 携带 404/409
业务码。测试同时断言两层状态，不把传输状态与业务码混为一谈。

测试环境关闭 Nacos config/discovery，因此 XSS 配置无法回源 Auth 时按设计启用基线防护；这不绕过 XSS，
也不改变正式 Compose 配置。

## 6. 清理证明与能力边界

- 独立服务端口 28216/28217 最终监听数均为 0。
- `omni-g1` MySQL 中 `omni_crud_runtime_*` 数据库数量为 0。
- 系统临时目录中 `omni-crud-runtime-*` 数量为 0。
- 主工作区没有生成 MaterialBrand 业务文件、临时 SQL、Python、Node 脚本或事务备份残留。
- 运行时脚本是正式可重复测试资产，不属于一次性任务临时脚本。

WP-04 当前交付的是经过实证的“标准主数据/简单聚合”生成器。Workflow、Saga、复杂状态机、跨服务事件、
复杂金额/库存规则和其他未经建立模块约定的目标仍明确拒绝自动生成，必须由后续领域实现处理。

## 7. S3-05～S3-07 状态

S3-05、S3-06、S3-07 已达到输入契约、原子全栈生成、所有权保护、fresh DB、全栈构建、真实运行、权限与
租户隔离矩阵、再生成无差异和清理目标。WP-04 可以关闭，后续进入 WP-05 项目裁剪预设与维护说明。
