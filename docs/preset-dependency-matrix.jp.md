<!-- generated-by: @omni-stack/cli preset-docs; catalog-version: 1.0.0 -->
# プリセット依存関係マトリクス

> `scaffold/catalog/modules.yaml` から自動生成されます。事実表を手動で編集しないでください。

## モジュール

| モジュール | 種別 | 必須依存 | 任意モジュール | 競合 | バックエンド | Compose サービス | 権限ルート |
|---|---|---|---|---|---|---|---|
| platform | foundation | なし | なし | なし | omni-common-core, omni-common, omni-common-mybatis, omni-common-redis, omni-common-redis-reactive, omni-common-operlog, omni-common-job, omni-common-mqlog, omni-common-service, omni-db-migrator | omni-db-migrator, omni-frontend | なし |
| auth | foundation | platform | rocketmq, xxl-job | なし | omni-auth | omni-auth | system |
| base | foundation | auth | rocketmq, xxl-job | なし | omni-base | omni-base | base, dict, job, monitor |
| workflow | capability | base | rocketmq, xxl-job | なし | omni-common-workflow, omni-workflow | omni-workflow | workflow |
| crm | business | base | rocketmq, xxl-job | なし | omni-crm | omni-crm | crm |
| srm | business | workflow | rocketmq, xxl-job | なし | omni-srm | omni-srm | srm |
| procurement | business | srm | rocketmq, xxl-job | なし | omni-procurement | omni-procurement | procurement |
| asset | business | procurement, srm | rocketmq, xxl-job | なし | omni-asset | omni-asset | asset |
| nacos | infrastructure | なし | mysql | なし | なし | nacos | なし |
| xxl-job | infrastructure | nacos | mysql | なし | なし | xxl-job-admin | なし |
| gateway | foundation | base | なし | なし | omni-gateway | omni-gateway | なし |
| mysql | infrastructure | なし | なし | なし | なし | mysql | なし |
| redis | infrastructure | なし | なし | なし | なし | redis | なし |
| rocketmq | infrastructure | なし | なし | なし | なし | rocketmq-namesrv, rocketmq-broker | なし |

## プリセット

| プリセット | 明示モジュール | 依存クロージャ | メモリ（最小/推奨） |
|---|---|---|---|
| core | base, gateway, mysql, redis, nacos | platform, auth, base, nacos, gateway, mysql, redis | 3328 MB / 5632 MB |
| crm | crm, gateway, mysql, redis, nacos | platform, auth, base, crm, nacos, gateway, mysql, redis | 3840 MB / 6400 MB |
| full | crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq | 7296 MB / 11776 MB |
| supply-chain | asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq | 6784 MB / 11008 MB |
| workflow | workflow, gateway, mysql, redis, nacos | platform, auth, base, workflow, nacos, gateway, mysql, redis | 4096 MB / 6656 MB |
