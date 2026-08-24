<!-- generated-by: @omni-stack/cli preset-docs; catalog-version: 1.0.0 -->
# 预设依赖矩阵

> 本文件由 `scaffold/catalog/modules.yaml` 自动生成，请勿手工维护事实表。

## 模块

| 模块 | 类型 | 必选依赖 | 可选模块 | 冲突 | 后端模块 | Compose 服务 | 权限根 |
|---|---|---|---|---|---|---|---|
| platform | foundation | 无 | 无 | 无 | omni-common-core, omni-common, omni-common-mybatis, omni-common-redis, omni-common-redis-reactive, omni-common-operlog, omni-common-job, omni-common-mqlog, omni-common-service, omni-db-migrator | omni-db-migrator, omni-frontend | 无 |
| auth | foundation | platform | rocketmq, xxl-job | 无 | omni-auth | omni-auth | system |
| base | foundation | auth | rocketmq, xxl-job | 无 | omni-base | omni-base | base, dict, job, monitor |
| workflow | capability | base | rocketmq, xxl-job | 无 | omni-common-workflow, omni-workflow | omni-workflow | workflow |
| crm | business | base | rocketmq, xxl-job | 无 | omni-crm | omni-crm | crm |
| srm | business | workflow | rocketmq, xxl-job | 无 | omni-srm | omni-srm | srm |
| procurement | business | srm | rocketmq, xxl-job | 无 | omni-procurement | omni-procurement | procurement |
| asset | business | procurement, srm | rocketmq, xxl-job | 无 | omni-asset | omni-asset | asset |
| nacos | infrastructure | 无 | mysql | 无 | 无 | nacos | 无 |
| xxl-job | infrastructure | nacos | mysql | 无 | 无 | xxl-job-admin | 无 |
| gateway | foundation | base | 无 | 无 | omni-gateway | omni-gateway | 无 |
| mysql | infrastructure | 无 | 无 | 无 | 无 | mysql | 无 |
| redis | infrastructure | 无 | 无 | 无 | 无 | redis | 无 |
| rocketmq | infrastructure | 无 | 无 | 无 | 无 | rocketmq-namesrv, rocketmq-broker | 无 |

## 预设

| 预设 | 显式模块 | 依赖闭包 | 内存（最低/建议） |
|---|---|---|---|
| core | base, gateway, mysql, redis, nacos | platform, auth, base, nacos, gateway, mysql, redis | 3328 MB / 5632 MB |
| crm | crm, gateway, mysql, redis, nacos | platform, auth, base, crm, nacos, gateway, mysql, redis | 3840 MB / 6400 MB |
| full | crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq | 7296 MB / 11776 MB |
| supply-chain | asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq | 6784 MB / 11008 MB |
| workflow | workflow, gateway, mysql, redis, nacos | platform, auth, base, workflow, nacos, gateway, mysql, redis | 4096 MB / 6656 MB |
