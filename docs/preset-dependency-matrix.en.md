<!-- generated-by: @omni-stack/cli preset-docs; catalog-version: 1.0.0 -->
# Preset Dependency Matrix

> Generated from `scaffold/catalog/modules.yaml`; do not maintain the fact tables manually.

## Module

| Module | Kind | Required dependencies | Optional modules | Conflicts | Backend modules | Compose services | Permission roots |
|---|---|---|---|---|---|---|---|
| platform | foundation | None | None | None | omni-common-core, omni-common, omni-common-mybatis, omni-common-redis, omni-common-redis-reactive, omni-common-operlog, omni-common-job, omni-common-mqlog, omni-common-service, omni-db-migrator | omni-db-migrator, omni-frontend | None |
| auth | foundation | platform | rocketmq, xxl-job | None | omni-auth | omni-auth | system |
| base | foundation | auth | rocketmq, xxl-job | None | omni-base | omni-base | base, dict, job, monitor |
| workflow | capability | base | rocketmq, xxl-job | None | omni-common-workflow, omni-workflow | omni-workflow | workflow |
| crm | business | base | rocketmq, xxl-job | None | omni-crm | omni-crm | crm |
| srm | business | workflow | rocketmq, xxl-job | None | omni-srm | omni-srm | srm |
| procurement | business | srm | rocketmq, xxl-job | None | omni-procurement | omni-procurement | procurement |
| asset | business | procurement, srm | rocketmq, xxl-job | None | omni-asset | omni-asset | asset |
| nacos | infrastructure | None | mysql | None | None | nacos | None |
| xxl-job | infrastructure | nacos | mysql | None | None | xxl-job-admin | None |
| gateway | foundation | base | None | None | omni-gateway | omni-gateway | None |
| mysql | infrastructure | None | None | None | None | mysql | None |
| redis | infrastructure | None | None | None | None | redis | None |
| rocketmq | infrastructure | None | None | None | None | rocketmq-namesrv, rocketmq-broker | None |

## Preset

| Preset | Explicit modules | Dependency closure | Memory (min/recommended) |
|---|---|---|---|
| core | base, gateway, mysql, redis, nacos | platform, auth, base, nacos, gateway, mysql, redis | 3328 MB / 5632 MB |
| crm | crm, gateway, mysql, redis, nacos | platform, auth, base, crm, nacos, gateway, mysql, redis | 3840 MB / 6400 MB |
| full | crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq | 7296 MB / 11776 MB |
| supply-chain | asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq | 6784 MB / 11008 MB |
| workflow | workflow, gateway, mysql, redis, nacos | platform, auth, base, workflow, nacos, gateway, mysql, redis | 4096 MB / 6656 MB |
