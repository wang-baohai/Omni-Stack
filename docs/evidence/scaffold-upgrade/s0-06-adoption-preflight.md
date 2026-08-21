# S0-06 既有数据库接管与克隆升级证据

> 记录日期：2026-08-20
> 状态：完成；隔离克隆接管、升级和幂等复验通过，原始数据卷未接管；G1 最终结果见 [s0-08-g1.md](s0-08-g1.md)

## 已完成

- 在 `omni-db-migrator` 内实现 `mysql-information-schema-v1` 强结构指纹，不再依赖临时脚本。
- 指纹覆盖表引擎/排序规则、列/默认值/生成式、索引、约束、外键动作、CHECK、视图、触发器、存储过程/函数及其关键会话属性。
- 排除实例运行态 `AUTO_INCREMENT` 计数和索引基数，统一换行及不可打印控制字符。
- Workflow 指纹额外包含 `ACT_GE_PROPERTY` 中的 `schema.version` 与 `schema.history`。
- 九个目标数据库均冻结表/视图/触发器/例程数量和 SHA-256，参考提交为 `09a29fe10af9c7ddffe5001238d048947868dc98`。
- `adopt-current` 已串联九库结构指纹、24 项种子自然键断言、备份 SHA-256、备份时效、源/恢复/接管实例身份和恢复基线校验。
- 首次执行只生成完整接管报告摘要并在写入前停止；只有二次执行提供完全匹配的 `ADOPT:<sha256>` 才同步 baseline 历史。
- 接管历史不仅要求 `adoption-baseline` 标签，还必须匹配冻结 changelog 中的 `ID + AUTHOR + FILENAME`，未知历史会被拒绝。
- 公共 MQ `0002` 使用 `adoption-upgrade` 标签，不会被接管伪装为已执行，必须由后续 `migrate` 真实升级。

## 参考指纹

| 目标 | 表/视图/触发器/例程 | SHA-256 |
|---|---|---|
| auth | 20/0/0/1 | `acb200c9499f643be8d9e045d94c5a520c34c953d03cc51ff2f511bfc8db1b55` |
| base | 8/0/0/0 | `c3ffaf8e835520f420bf2f8dfcce803af60bdef1c5a4504c42cca679348b648a` |
| workflow | 54/0/0/0 | `198fe5312d31abb81d865c368d0a5a530ef0cbba60f5b4cf159c51b8a17a6678` |
| crm | 12/0/0/0 | `d590a0e19b9b42212df7420e9bf59a3d3aa651ef3fa016ecc9269e02284e4c0d` |
| srm | 21/0/0/0 | `d0af9e04d58225fa6300bf07e1c3d8450b04a2c71078dfefb3f2a10713c7f9e4` |
| procurement | 15/0/0/0 | `2f4213dcbe7eeae8d5bded181c58c2f438dc83394fb737be46fd3bf8a44e9376` |
| asset | 6/0/0/0 | `b8911dc8f5657d5bf544a7dbd15672dfbecbe9ef5afff426cf197fb4d2636c8d` |
| nacos | 10/0/0/0 | `47372dd8400ee1d01e4bb8339af82b153582119937bda89139d8cafd780d88c2` |
| xxl-job | 8/0/0/0 | `9dc5f8a30ff67dd5556b4b191e2b11fd56e56d9233dad15c981909dcf7cde981` |

## 隔离克隆演练结果

- 从原始 `omni-mysql` 创建逻辑备份，文件大小 `94,762,130` 字节，SHA-256 为 `68c69bc5ef162de8b0207b7a35ca046a21468a42fa50b403efc875e36ce3be42`。
- 备份恢复到全新 MySQL 实例；源实例 UUID 与恢复实例 UUID 不同，证明校验对象不是原实例自身。
- `mysqldump` 恢复后九库 `mysql-information-schema-v1` 指纹全部与冻结基线一致。
- 第一阶段在九库结构、24 项种子和备份证据全部通过后生成确认摘要，并按设计在任何历史写入前停止。
- 第二阶段使用精确确认摘要完成 baseline 接管；Auth 历史 3 项、Workflow 3 项、其余五个业务库各 2 项、Nacos/XXL-JOB 各 1 项。
- 后续 `migrate` 在七个业务库各真实执行一次公共 MQ `0002`；升级记录为 `EXECUTED` 和 `adoption-upgrade`。
- 154 张业务表升级前后逐表行数摘要均为 `3ff8d49e353f8be54773f671c37eab49cb1b6e6653e3206857dbcd6bdc8cb037`。
- 七库共有 116 条 MQ 消息，升级前后 `tenant_id IS NULL` 均为 0；升级后 `tenant_id` 均为 `NOT NULL`，并统一为 `uk_msg_id`、`idx_relay`、`idx_tenant_time` 三个目标索引。
- 紧接着重复执行 `migrate`，九个目标均显示无待执行 changeSet；升级后再次执行 24 项种子断言全部通过。
- 原始 `omni-mysql` 仅被只读检查和备份，未创建 `DATABASECHANGELOG`，未执行接管或升级。

## 指纹算法修正说明

最初的 `mysql-show-create-v1` 会把 MySQL 在逻辑备份恢复后对等价字符集声明的不同渲染视为结构差异，例如列级显式字符集与继承表默认字符集。该差异既不是业务结构漂移，也不适合加入白名单。因此算法升级为 `mysql-information-schema-v1`，按结构语义字段排序并序列化，同时排除运行态自增计数和索引基数。新算法已在源实例与独立恢复实例之间证明九库结果一致。

## 当时 G1 前仍需完成（现均已在 S0-07～S0-08 完成）

1. 增加 changeSet 执行中断后的失败恢复演练；备份篡改、错误确认、恢复实例身份、过期证据和未知 Liquibase 历史拒绝测试已自动化。
2. 完成 S0-07：以正式 Java 编排替换 `sp_init_tenant`，补齐模块 provisioning 契约。
3. 完成 fresh 空卷结构与正式种子初始化；当前 manifest 已能校验参考种子，但还不是 fresh 种子写入事实源。
4. 本轮隔离克隆、匿名卷、备份文件和临时证据已在验证后清理；后续演练继续遵守“先归档摘要证据、再删除大文件与临时实例”。
