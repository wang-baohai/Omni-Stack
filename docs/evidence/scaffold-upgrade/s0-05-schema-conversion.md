# S0-05 数据库结构转换证据

> 记录日期：2026-08-20
> 对应基线：`09a29fe` 参考数据卷
> 适用工作包：`S0-05 转换平台、业务、vendor 和 MQ schema`

## 1. 结论

当前 9 个目标数据库的正式 Liquibase 结构基线已经建立，并通过隔离 MySQL 8.4 实例完成全新建库验证。基线覆盖 154 张既有表：

| 范围 | 表数 | 说明 |
|---|---:|---|
| 7 个业务数据库的模块表 | 84 | 不含每库一张 `sys_mq_message` |
| 7 个业务数据库的公共 MQ 表 | 7 | 使用同一公共 changelog |
| Flowable vendor 表 | 45 | 39 张 `ACT_*` 与 6 张 `FLW_*` |
| Nacos vendor 表 | 10 | Nacos 3.1.1 当前结构 |
| XXL-JOB vendor 表 | 8 | XXL-JOB 3.3.1 当前结构 |
| 合计 | 154 | 与 S0-03 数据库台账一致 |

隔离库重建后的强元数据比较没有发现未知结构差异。唯一差异是公共 MQ 表的预期规范化：

- `tenant_id` 从历史可空状态统一为 `NOT NULL`；现有 7 张表共 93 行，空租户记录为 0。
- 历史模块前缀索引名统一为 `uk_msg_id`、`idx_relay`、`idx_tenant_time`。
- 表、列、默认值、生成表达式、索引字段与前缀、约束、外键动作、CHECK 表达式、引擎和排序规则均无其他差异。

## 2. 转换策略

曾验证 Liquibase 通用反向生成 YAML，但结果无法完整表达当前 MySQL 结构，至少会丢失或改变：

- CHECK 约束及其 enforced 状态；
- 生成列表达式；
- 列级和表级排序规则；
- vendor 表的兼容性属性与注释。

因此正式基线使用 `SHOW CREATE TABLE` 的 MySQL DDL，嵌入不可变 Liquibase YAML changeSet。每个目标变更集具备：

- 固定 `id`、`author`、目标数据库标签和说明；
- MySQL `dbms` 与目标数据库名 `sqlCheck` 前置条件；
- 建表前表不存在检查，避免误覆盖；
- `FOREIGN_KEY_CHECKS` 的受控关闭和恢复；
- `runInTransaction: false`，与 MySQL DDL 的隐式提交语义一致。

只移除了实例相关的 `AUTO_INCREMENT=n` 计数，不改写结构语义。参考 SRM 历史注释中存在 YAML 1.1 不允许的 C1 控制字节 `0x90`；生成时仅将该非法注释字符替换为 Unicode replacement character。强元数据指纹不包含注释，此项属于已解释白名单，不影响运行结构。

## 3. Vendor 版本证据

- Flowable 当前 `ACT_GE_PROPERTY.schema.version` 为 `8.0.0.0`，`schema.history` 为 `create(8.0.0.0)`。
- Nacos 结构以当前固定镜像 3.1.1 的数据库为基线。
- XXL-JOB 结构以当前固定版本 3.3.1 的数据库为基线。
- vendor 结构中的 `utf8mb3` 警告属于上游兼容结构，不在接管阶段静默升级排序规则。

## 4. 公共 MQ 规范化

公共 changelog 分为两步：

1. `0001-mq-schema.yaml`：全新环境直接创建规范化结构。
2. `0002-mq-normalize.yaml`：升级环境在确认不存在空租户值和三个语义索引后，收紧列约束并将历史索引名改为规范名称。

第二步在执行前检查表、空租户数量和索引语义。它不依赖具体历史索引名；通过 `information_schema` 识别等价索引，再执行条件重命名。接管现有库时只能同步 `0001` 基线，不能把 `0002` 标记为已执行；后续正常 `migrate` 必须真实执行规范化。

## 5. 验证结果

### 5.1 离线 changelog 校验

- 模块测试：6 个通过，0 失败，0 错误，0 跳过。
- 资源打包：24 个数据库资源进入 `omni-db-migrator`。
- 所有根 changelog 和目标 changelog 均通过 Liquibase 离线解析与校验。

### 5.2 隔离库 Fresh 重建

使用随机容器名、随机强密码和随机宿主端口启动一次性 `mysql:8.4`，运行打包后的 `omni-db-migrator migrate`。迁移完成后，基于 `information_schema` 对参考库和隔离库生成规范序列并计算 SHA-256。

| 目标 | Fresh 强元数据 SHA-256 |
|---|---|
| auth | `ff9b22a1a5569de26c38060c332c99a7d82a27457745c314cdbf66328cfbf334` |
| base | `50edc3abb1d7a9d83e22c52c4e381532bc9f78cdfcc2d69701b6983e9f94d02f` |
| workflow | `1dd81539eed90823c35c9fa676db0bd3dafb44013b789c45a87e56009da7cabd` |
| crm | `d8a70da50bac3a16f32ce5156afdad3f0be2006de7e97f20db27467d114b6ad7` |
| srm | `5269048f68639bd60d618b1e9a0056ba7b694f4a290fd3cd6cd2e845d523530c` |
| procurement | `3c159fc29420f7fbdcf6f21611a56738a8461924ba344a0b2e2e9ad677c3ce56` |
| asset | `4af56206e10f8ca18a3339a31d1587859b7d1e29490f710134d04e50c152215d` |
| nacos | `79d065e1f25ad9115f33d4fc1bc2d2c5e2f790317a27013af48cebc55e11bd8c` |
| xxl-job | `700c60ad45f4b018df4891a6e9879d6c4fc8b314cc646684a0267b117b16d62e` |

强元数据包含：表引擎与排序规则、列类型/空值/默认值/extra/生成表达式/排序规则、索引名/顺序/前缀/表达式、约束类型与 enforced 状态、外键列与引用动作、CHECK 表达式。

### 5.3 差异判定

- Nacos、XXL-JOB：0 条差异。
- Auth、Base、Workflow：仅 `sys_mq_message.tenant_id` 从可空变为非空。
- CRM、SRM、Procurement、Asset：仅公共 MQ 索引/约束名称规范化。
- 未发现缺表、未知列、缺失约束、生成列表达式变化、字符集漂移或 vendor 结构漂移。

## 6. 当时尚未关闭的门（现均已在 S0-06～S0-08 完成）

以下内容保留为阶段历史；G1 最终结论见 [s0-08-g1.md](s0-08-g1.md)：

1. 建立 `database/seed/manifest.yaml`，生成并校验正式幂等种子。
2. 实现 `verify-seed`，检查缺失、重复、受保护字段覆盖和已裁剪模块残留。
3. 实现带备份证明、强指纹和人工确认的 `adopt-current`。
4. 在参考数据卷副本上执行接管与公共 MQ 规范化，验证 93 行业务数据不变。
5. 完成租户 provisioning 替代方案和 Compose one-shot 启动门。
