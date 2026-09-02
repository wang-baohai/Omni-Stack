# 缺陷记录：adoption 冻结基线与 changelog 结构漂移（2026-09-01，第二次修订）

## 现象（本地实证复现）

对空库执行 `migrate` 成功后，`adopt-current` 指纹校验失败（首轮日志被截断误判为仅两库，
二次全量复测确认**七个业务库全部漂移**）：

| 目标库 | 冻结基线表数 | migrate 实测 | 差异构成 |
| --- | --- | --- | --- |
| omni_auth | 20 | 22 | + `sys_mq_message`（多服务迁移）+ `sys_tenant_module_provision`（S0-07 租户开通） |
| omni_base | 8 | 9 | + `sys_mq_message` |
| omni_workflow | 54 | 55 | + `sys_mq_message` |
| omni_crm | 12 | 13 | + `sys_mq_message` |
| omni_srm | 21 | 22 | + `sys_mq_message` |
| omni_procurement | 15 | 16 | + `sys_mq_message` |
| omni_asset | 6 | 7 | + `sys_mq_message` |
| nacos_config | 10 | 10 | ✅ 一致 |
| xxl_job | 8 | 8 | ✅ 一致 |

## 算法可信性证明

按 `SchemaFingerprintService`（mysql-information-schema-v1）逐条复现算法（同 SQL、同列序、
同 normalize、同排序），nacos/xxl-job 两库指纹与冻结基线**逐字节一致**——复现实现无偏差，
上表实测 sha256 全部可信。九库实测指纹已生成候选基线（见下）。

## 根因

`baseline-09a29fe.yaml` 冻结于 commit 09a29fe（2026-08-20 快照），早于多服务迁移
（WP-03 为全部业务库加入共享 `sys_mq_message`）与 S0-07（auth 租户模块开通表）。
changelog 是新的正确事实源，**冻结基线整体过时**；不是 migrate 缺陷。

## 影响

- `adopt-current` 对新初始化库必然失败（七个业务库全部指纹不匹配）；
- CI 已修正：`quality.yml` 的 database-migrations job 不含 adopt 步骤
  （其还要求与 baselineCommit 绑定的真实外部备份证据，本就不属于 CI 场景）。

## 候选基线（已生成，未启用）

- 文件：本次实测九库 `{id, database, expectedTables, expectedSha256}` 已输出
  （见 `scripts/.work/baseline-candidate.yaml`，临时目录，随本批次清理；数值抄录如下）；
- `baselineCommit: efd651e04816cdf790db8cfa926c081f230d6f2c-CANDIDATE-UNREVIEWED`，
  明确标注**未经运维确认、未绑定备份证据、禁止直接用于 adopt-current**；
- 启用流程（同复核队列模式，由责任人确认后生效）：
  1. 运维复核候选数值并重命名（如 `baseline-efd651e.yaml`）放入 `database/adoption/`；
  2. 补齐与新 baselineCommit 绑定的外部备份证据；
  3. 将 `DbMigratorProperties.adoptionBaseline` 指向新基线；
  4. 临时空库回归 migrate → adopt-current 全链路；
  5. 同步更新 `schema-snapshot.md` 与 `migration-inventory.md` 引用。

### 九库实测指纹（候选数值）

| id | database | expectedTables | expectedSha256 |
| --- | --- | --- | --- |
| auth | omni_auth | 22 | 5ee886ef540cd7f75ba8ac9564bde2789cfb0984489bcdcafbc4705314d85fe8 |
| base | omni_base | 9 | 3e23ce5f8049770cc6992bffaba1af60f3629b4a56084185be68f00a8423c280 |
| workflow | omni_workflow | 55 | 231ba38b4affde0dd59794b931c7a02ef233968197fa5f87220f01bb0a324e99 |
| crm | omni_crm | 13 | e2bee5a203c7bbf89c71d36dc3f8b3873e4fcb32769cf11159d9c96d745a162f |
| srm | omni_srm | 22 | ed4dae917d213053931b2b8d73d947ea86d2674639c1573560121930a0d2a206 |
| procurement | omni_procurement | 16 | af383d1acd6a644242a9e07d1d57a39cb1b10c1e8edd0099ea04a12502551f6b |
| asset | omni_asset | 7 | e5fab94c5c8f7f44437873e24908e8944605596c11d731c4d546e1f0c32e6a1a |
| nacos | nacos_config | 10 | 47372dd8400ee1d01e4bb8339af82b153582119937bda89139d8cafd780d88c2 |
| xxl-job | xxl_job | 8 | 9dc5f8a30ff67dd5556b4b191e2b11fd56e56d9233dad15c981909dcf7cde981 |

注：expectedRoutines 沿用冻结基线（auth=1 其余=0）；expectedViews/Triggers 均为 0。

## 复现与验证环境

- 一次性 `mysql:8.4` 容器（3307）+ `migrate` 后直接指纹采集；容器与临时脚本均已清理；
- 不影响 migrate/replay/validate/verify-seed 链路（已独立通过）。
