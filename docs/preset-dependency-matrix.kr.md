<!-- generated-by: @omni-stack/cli preset-docs; catalog-version: 1.0.0 -->
# 프리셋 의존성 매트릭스

> `scaffold/catalog/modules.yaml`에서 자동 생성됩니다. 사실 표를 수동으로 관리하지 마세요.

## 모듈

| 모듈 | 유형 | 필수 의존성 | 선택 모듈 | 충돌 | 백엔드 모듈 | Compose 서비스 | 권한 루트 |
|---|---|---|---|---|---|---|---|
| platform | foundation | 없음 | 없음 | 없음 | omni-common-core, omni-common, omni-common-mybatis, omni-common-redis, omni-common-redis-reactive, omni-common-operlog, omni-common-job, omni-common-mqlog, omni-common-service, omni-db-migrator | omni-db-migrator, omni-frontend | 없음 |
| auth | foundation | platform | rocketmq, xxl-job | 없음 | omni-auth | omni-auth | system |
| base | foundation | auth | rocketmq, xxl-job | 없음 | omni-base | omni-base | base, dict, job, monitor |
| workflow | capability | base | rocketmq, xxl-job | 없음 | omni-common-workflow, omni-workflow | omni-workflow | workflow |
| crm | business | base | rocketmq, xxl-job | 없음 | omni-crm | omni-crm | crm |
| srm | business | workflow | rocketmq, xxl-job | 없음 | omni-srm | omni-srm | srm |
| procurement | business | srm | rocketmq, xxl-job | 없음 | omni-procurement | omni-procurement | procurement |
| asset | business | procurement, srm | rocketmq, xxl-job | 없음 | omni-asset | omni-asset | asset |
| nacos | infrastructure | 없음 | mysql | 없음 | 없음 | nacos | 없음 |
| xxl-job | infrastructure | nacos | mysql | 없음 | 없음 | xxl-job-admin | 없음 |
| gateway | foundation | base | 없음 | 없음 | omni-gateway | omni-gateway | 없음 |
| mysql | infrastructure | 없음 | 없음 | 없음 | 없음 | mysql | 없음 |
| redis | infrastructure | 없음 | 없음 | 없음 | 없음 | redis | 없음 |
| rocketmq | infrastructure | 없음 | 없음 | 없음 | 없음 | rocketmq-namesrv, rocketmq-broker | 없음 |

## 프리셋

| 프리셋 | 명시 모듈 | 의존성 클로저 | 메모리(최소/권장) |
|---|---|---|---|
| core | base, gateway, mysql, redis, nacos | platform, auth, base, nacos, gateway, mysql, redis | 3328 MB / 5632 MB |
| crm | crm, gateway, mysql, redis, nacos | platform, auth, base, crm, nacos, gateway, mysql, redis | 3840 MB / 6400 MB |
| full | crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq | 7296 MB / 11776 MB |
| supply-chain | asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq | 6784 MB / 11008 MB |
| workflow | workflow, gateway, mysql, redis, nacos | platform, auth, base, workflow, nacos, gateway, mysql, redis | 4096 MB / 6656 MB |
