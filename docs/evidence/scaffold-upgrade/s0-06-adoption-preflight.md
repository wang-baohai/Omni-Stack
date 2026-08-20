# S0-06 既有数据库接管预检证据

> 记录日期：2026-08-20
> 状态：Preflight complete；接管写入仍关闭

## 已完成

- 在 `omni-db-migrator` 内实现 `mysql-show-create-v1` 强结构指纹，不再依赖临时脚本。
- 指纹覆盖基础表完整 `SHOW CREATE TABLE`、视图、触发器、存储过程/函数及其关键会话属性。
- 排除实例运行态 `AUTO_INCREMENT=n`，统一换行和 MySQL/YAML 不可打印控制字符。
- Workflow 指纹额外包含 `ACT_GE_PROPERTY` 中的 `schema.version` 与 `schema.history`。
- 九个目标数据库均冻结表/视图/触发器/例程数量和 SHA-256，参考提交为 `09a29fe10af9c7ddffe5001238d048947868dc98`。
- `adopt-current` 已串联九库结构指纹和 24 项种子自然键断言；当前参考数据卷全部通过。
- 即使全部预检通过，命令仍在写入前强制失败，不会提前创建或写入 `DATABASECHANGELOG`。

## 参考指纹

| 目标 | 表/视图/触发器/例程 | SHA-256 |
|---|---|---|
| auth | 20/0/0/1 | `8424eff26b8b2973569af7361c861090672f2ed6bcd1ff19f53cbe35e83e0c99` |
| base | 8/0/0/0 | `f1c0ff6f491e0f5eeed5da53145b0374387c986b46885df0d5f11a13e697f67c` |
| workflow | 54/0/0/0 | `ac88fb4afc935a6a3ddf3fa351dabb5e24d336883b54e6338b15da428a1674cf` |
| crm | 12/0/0/0 | `8c548ad2b46ec79c12d80c86c71e16407daa6f17f68f0ed18845a159be71bee5` |
| srm | 21/0/0/0 | `6110eee2894cdef7f1593a3d62cc308ae85fefb8d9762032fb266adc8c8987e6` |
| procurement | 15/0/0/0 | `9e952029e6ce90f1861c3b78853a2c30299d6850b046391f8d30269703fcb1d0` |
| asset | 6/0/0/0 | `4e032fbdf8179d686ec4bd4e82531a5229cf0bd6fa65539e14107e2509c2d29b` |
| nacos | 10/0/0/0 | `052066e374ba5986c0a3e61357da8fc193cf9c768b54bd97b972ed28ceb34281` |
| xxl-job | 8/0/0/0 | `1331d03b9618206abbe95c38f28aa33fd8749a8914fd5ec2fbe9eae41f50e3e4` |

## 尚未开放的写入门

1. 定义并校验备份证据文件：备份摘要、来源实例、完成时间和隔离恢复结果。
2. 生成包含全部结构与种子摘要的接管报告及一次性确认摘要。
3. 用户使用完全匹配的确认摘要二次执行。
4. 只同步 baseline changeSet；公共 MQ `0002` 必须保持未执行，随后由正常 `migrate` 真实升级。
5. 在参考数据卷副本验证 upgrade、重复执行和失败恢复，不在当前原始卷直接接管。
