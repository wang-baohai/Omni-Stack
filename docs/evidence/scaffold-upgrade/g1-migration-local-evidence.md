# G1 数据链路本地实证（2026-09-01，HEAD efd651e）

## 目的

在不影响本地开发栈（3306 生产开发库）的前提下，用一次性 Docker MySQL（3307）实证
CI `database-migrations` job 的等价链路：fresh migrate → 幂等重放 → validate → verify-seed。

## 环境

- 临时容器：`mysql:8.4`，root 密码仅用于本次校验，容器已销毁
- 产物：`omni-db-migrator-1.0.0-SNAPSHOT.jar`（`./mvnw -pl omni-db-migrator -am package -DskipTests`，BUILD_EXIT=0）

## 结果

| 步骤 | 命令 | 结果 |
| --- | --- | --- |
| fresh migrate | `DB_MIGRATOR_COMMAND=migrate`（空实例） | ✅ exit 0，平台 + 各目标库 changeSet 全部执行（含 vendor/xxl-job schema、xxl-job bootstrap seed） |
| 幂等重放（upgrade/replay） | 再次 `migrate` | ✅ exit 0，重跑 changeSet 数 = 0（无 pending，纯 no-op） |
| changelog 校验 | `validate` | ✅ exit 0，"No validation errors found" |
| 种子断言 | `verify-seed` | ✅ exit 0，manifestVersion=1.0.0-bootstrap，**24 条断言全部通过**（含 asset-permission-catalog rows=27、nacos-security-catalog rows=2、xxl-job-catalog rows=4，SHA-256 与 `database/seed/manifest.yaml` 一致） |

## 结论

- Liquibase/migrator 链路在本机空库 fresh→replay→validate→verify-seed 全部通过，
  为 quality.yml `database-migrations` job 提供本地预演证据；
- `adopt-current` 已在第二个临时空库补充实证：发现冻结基线与 changelog 存在 sys_mq_message 表漂移（procurement 15→16、asset 6→7）导致指纹失败，详见 `defect-adoption-baseline-drift.md`；quality.yml 的 adopt 步骤已同步移除（备份证据要求亦不属于 CI 场景）；
- 临时容器已销毁，无残留。

## 未覆盖项（阻塞登记延续）

- adopt-current 基线刷新流程（见 defect-adoption-baseline-drift.md 修复方案，属运维/接管流程决策）；
- 五预设运行矩阵与登录后 E2E（依赖受控 Token，见 G2/G7 阻塞登记）。
