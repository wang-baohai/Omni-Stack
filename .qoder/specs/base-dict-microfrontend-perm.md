# Base 服务 - 数据字典 CRUD 实现方案

## Context

项目需要一个通用基础数据服务来替代已删除的 omni-business 模块，首个功能为数据字典管理（两级结构：字典类型 + 字典项）。字典数据是高频读、低频写的典型场景，通过 Redis 缓存加速。前端保持单体 SPA 架构（不引入微前端），利用现有动态路由 + v-permission + RBAC 体系支撑新模块的权限控制。

**设计决策汇总（已与用户确认）：**

| 决策项 | 结论 |
|--------|------|
| 架构 | 单体 SPA，不引入微前端 |
| 数据模型 | 两级结构（sys_dict_type + sys_dict_data） |
| 多租户 | 两表均含 tenant_id，租户隔离 |
| 缓存 | Redis + DB，写操作主动失效，提供手动刷新接口 |
| 前端布局 | 左右分栏（master-detail） |
| 预置数据 | 提供 Seed Data |
| 服务端口 | 8101 |
| API 路径 | /api/base/dict/* |
| 权限码 | dict:type:* + dict:data:*（类型和项分开管理） |
| 字典项字段 | Value + Label 双字段、sort 排序、status 启停、tagType 标签样式 |

---

## Step 1: 数据库 Schema

**修改文件：** `scripts/sql/init-all.sql`

在文件末尾追加 `omni_base` 数据库及两张表：

```sql
-- ============================================================
-- 5. Base 服务 - 数据字典
-- ============================================================
CREATE DATABASE IF NOT EXISTS omni_base DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE omni_base;

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    type_code   VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    type_name   VARCHAR(200) NOT NULL COMMENT '字典类型名称',
    remark      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_dict_type_tenant_code (tenant_id, type_code),
    INDEX idx_dict_type_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS sys_dict_data (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    type_code   VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    dict_value  VARCHAR(200) NOT NULL COMMENT '字典值',
    dict_label  VARCHAR(200) NOT NULL COMMENT '字典标签',
    tag_type    VARCHAR(50)  DEFAULT NULL COMMENT '标签样式：success/warning/danger/info/primary',
    remark      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    sort        INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64)  DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    INDEX idx_dict_data_tenant (tenant_id),
    INDEX idx_dict_data_tenant_type (tenant_id, type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';
```

**Seed Data（追加到 init-all.sql）：**

```sql
-- 预置字典类型（默认租户 1）
INSERT INTO sys_dict_type (tenant_id, type_code, type_name, sort, status, create_by) VALUES
    (1, 'sys_user_gender',   '用户性别',   1, 1, 'system'),
    (1, 'sys_common_status', '通用状态',   2, 1, 'system'),
    (1, 'sys_notice_type',   '通知类型',   3, 1, 'system');

-- 预置字典数据
INSERT INTO sys_dict_data (tenant_id, type_code, dict_value, dict_label, tag_type, sort, status, create_by) VALUES
    (1, 'sys_user_gender', '1', '男',     'primary', 1, 1, 'system'),
    (1, 'sys_user_gender', '2', '女',     'danger',  2, 1, 'system'),
    (1, 'sys_user_gender', '0', '未知',   'info',    3, 1, 'system'),
    (1, 'sys_common_status', '1', '启用', 'success', 1, 1, 'system'),
    (1, 'sys_common_status', '0', '禁用', 'danger',  2, 1, 'system'),
    (1, 'sys_notice_type', '1', '系统通知', 'primary', 1, 1, 'system'),
    (1, 'sys_notice_type', '2', '业务通知', 'warning', 2, 1, 'system');
```

**权限 Seed Data（追加到 init-all.sql 的 omni_auth 部分，在 4.9 XSS 节点之后）：**

```sql
USE omni_auth;

-- 4.12 Base 服务权限节点（1 个目录 + 1 个菜单 + 9 个 API 权限 = 11 条）
INSERT INTO sys_permission (id, tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
VALUES
    (50, 0,  0,  'base',                 '基础数据',       'DIRECTORY', '/50/',       1, 1, 1, 'system'),
    (51, 0,  50, 'base:dict',            '字典管理',       'MENU',      '/50/51/',    2, 1, 1, 'system'),
    (52, 0,  51, 'dict:type:list',       '查看字典类型',   'API',       '/50/51/52/', 3, 1, 1, 'system'),
    (53, 0,  51, 'dict:type:create',     '创建字典类型',   'API',       '/50/51/53/', 3, 2, 1, 'system'),
    (54, 0,  51, 'dict:type:update',     '更新字典类型',   'API',       '/50/51/54/', 3, 3, 1, 'system'),
    (55, 0,  51, 'dict:type:delete',     '删除字典类型',   'API',       '/50/51/55/', 3, 4, 1, 'system'),
    (56, 0,  51, 'dict:data:list',       '查看字典数据',   'API',       '/50/51/56/', 3, 5, 1, 'system'),
    (57, 0,  51, 'dict:data:create',     '创建字典数据',   'API',       '/50/51/57/', 3, 6, 1, 'system'),
    (58, 0,  51, 'dict:data:update',     '更新字典数据',   'API',       '/50/51/58/', 3, 7, 1, 'system'),
    (59, 0,  51, 'dict:data:delete',     '删除字典数据',   'API',       '/50/51/59/', 3, 8, 1, 'system'),
    (60, 0,  51, 'dict:data:refresh',    '刷新字典缓存',   'API',       '/50/51/60/', 3, 9, 1, 'system');

-- SUPER_ADMIN 角色追加 Base 权限
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 50), (1, 51), (1, 52), (1, 53), (1, 54), (1, 55),
    (1, 56), (1, 57), (1, 58), (1, 59), (1, 60);
```

---

## Step 2: Maven 模块

**新建文件：** `omni-backend/omni-base/pom.xml`

依赖列表（参照 omni-auth/pom.xml 模式，去掉 auth 专属依赖）：
- `com.omni:omni-common-core`
- `com.omni:omni-common`（XSS 自动配置）
- `spring-boot-starter-web`
- `commons-logging`
- `spring-boot-starter-validation`
- `com.omni:omni-common-mybatis`
- `com.omni:omni-common-redis`
- `spring-cloud-starter-alibaba-nacos-discovery`
- `spring-cloud-starter-alibaba-nacos-config`
- `spring-cloud-starter-loadbalancer`
- `spring-boot-starter-actuator`
- `lombok`（scope: provided）

Build plugin: `spring-boot-maven-plugin`

**修改文件：** `omni-backend/pom.xml`

在 `<modules>` 中添加 `<module>omni-base</module>`，位于 `omni-auth` 之后、`omni-gateway` 之前。

---

## Step 3: 应用启动与配置

**新建文件：** `omni-backend/omni-base/src/main/java/com/omni/base/BaseApplication.java`

```java
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.omni.base.mapper")
public class BaseApplication { ... }
```

**新建文件：** `omni-backend/omni-base/src/main/resources/application.yml`

参照 auth 的 application.yml，关键差异：
- `server.port: 8101`
- `spring.application.name: omni-base`
- `spring.datasource.url`: 连接 `omni_base` 数据库
- 无 Authorization Server / OAuth2 / Captcha 配置

---

## Step 4: 安全配置

**新建文件：** `omni-backend/omni-base/src/main/java/com/omni/base/security/GatewayPreAuthFilter.java`

从 auth 服务复制 `GatewayPreAuthFilter`（读取 X-User-Id / X-User-Name / X-Tenant-Id / X-User-Roles / X-User-Scopes 请求头，构建 `UsernamePasswordAuthenticationToken`）。代码完全一致，仅修改包名。

**新建文件：** `omni-backend/omni-base/src/main/java/com/omni/base/config/SecurityConfig.java`

简化的 SecurityFilterChain（不需要 OAuth2 Authorization Server）：
- `@EnableWebSecurity` + `@EnableMethodSecurity`
- permitAll: `/actuator/**`, `/error`
- 其余请求 `authenticated()`
- 注册 `GatewayPreAuthFilter` 在 `AuthorizationFilter` 之前
- `sessionManagement: STATELESS`
- `csrf: disabled`
- `formLogin: disabled`

**新建文件：** `omni-backend/omni-base/src/main/java/com/omni/base/security/XssConfigProviderImpl.java`

实现 `XssConfigProvider` SPI（项目硬约束）。由于 XSS 配置数据存储在 auth 数据库，Base 服务采用 **Redis-only** 策略：
- 读取 Redis 键 `xss:enabled:{tenantId}` 和 `xss:rules:{tenantId}`（auth 服务写入）
- 缓存未命中：返回 `enabled=false, rules=[]`（安全降级），log warn
- 不直接访问 auth 数据库

---

## Step 5: Entity / Mapper

**新建文件（entity 包）：**
- `SysDictType.java` — extends `BaseEntity`, `@TableName("sys_dict_type")`, 实现 `Serializable` + `serialVersionUID`
- `SysDictData.java` — extends `BaseEntity`, `@TableName("sys_dict_data")`, 实现 `Serializable` + `serialVersionUID`

**新建文件（mapper 包）：**
- `SysDictTypeMapper extends BaseMapper<SysDictType>`
- `SysDictDataMapper extends BaseMapper<SysDictData>`

无自定义 SQL，LambdaQueryWrapper 覆盖所有查询。

---

## Step 6: DTO

**新建文件（dto 包）：**
- `CreateDictTypeRequest` — `@NotBlank typeCode`, `@NotBlank typeName`, `remark`, `sort`
- `UpdateDictTypeRequest` — `typeName`, `remark`, `sort`, `status`（全可选）
- `CreateDictDataRequest` — `@NotBlank typeCode`, `@NotBlank dictValue`, `@NotBlank dictLabel`, `tagType`, `remark`, `sort`
- `UpdateDictDataRequest` — `dictValue`, `dictLabel`, `tagType`, `remark`, `sort`, `status`（全可选）
- `DictTypeQuery` — `typeCode`（模糊）, `typeName`（模糊）, `status`

---

## Step 7: Service 层

**新建文件：**

| 文件 | 关键逻辑 |
|------|----------|
| `DictTypeService.java`（接口） | `listTypes`, `getTypeById`, `createType`, `updateType`, `deleteType`, `toggleStatus` |
| `DictTypeServiceImpl.java` | createType: 校验 `(tenantId, typeCode)` 唯一性；deleteType: `@Transactional` 级联删除关联 dict_data；所有写操作失效 Redis 缓存 |
| `DictDataService.java`（接口） | `listDataByTypeCode`, `createData`, `updateData`, `deleteData`, `refreshCache` |
| `DictDataServiceImpl.java` | listDataByTypeCode: Redis 优先（键 `dict:type:{tenantId}:{typeCode}`，TTL 30min），miss 回源 DB；createData: 校验父 typeCode 存在；所有写操作失效缓存 |

**Redis 缓存策略：**
- 键：`dict:type:{tenantId}:{typeCode}`
- 值：JSON 数组（仅 status=1 的字典项，按 sort 升序）
- TTL：30 分钟
- 写操作：主动 `delete` 缓存键（不依赖 TTL 保证一致性）
- 刷新接口：强制从 DB 重新加载并覆盖缓存
- 序列化：`StringRedisTemplate` + `ObjectMapper`（与 auth XSS 缓存模式一致）

---

## Step 8: Controller 层

**新建文件：** `DictTypeController.java` — `@RequestMapping("/api/base/dict/type")`

| 方法 | HTTP | 权限 | 描述 |
|------|------|------|------|
| GET `/list` | GET | `dict:type:list` | 分页列表（typeCode/typeName/status 可选过滤） |
| GET `/{id}` | GET | `dict:type:list` | 按 ID 查询 |
| POST `/` | POST | `dict:type:create` | 创建（@Valid） |
| PUT `/{id}` | PUT | `dict:type:update` | 更新（@Valid） |
| DELETE `/{id}` | DELETE | `dict:type:delete` | 删除（级联删除字典项） |
| PUT `/{id}/status` | PUT | `dict:type:update` | 启用/禁用 |

**新建文件：** `DictDataController.java` — `@RequestMapping("/api/base/dict/data")`

| 方法 | HTTP | 权限 | 描述 |
|------|------|------|------|
| GET `/list` | GET | `dict:data:list` | 按 typeCode 分页查询 |
| POST `/` | POST | `dict:data:create` | 创建 |
| PUT `/{id}` | PUT | `dict:data:update` | 更新 |
| DELETE `/{id}` | DELETE | `dict:data:delete` | 删除 |
| POST `/refresh-cache` | POST | `dict:data:refresh` | 手动刷新缓存 |

所有方法通过 `@RequestHeader("X-Tenant-Id")` 获取租户 ID（defaultValue="1"）。所有返回类型 `R<T>` 或 `R<PageResult<T>>`。

---

## Step 9: Gateway 路由

**修改文件：** `omni-backend/omni-gateway/src/main/resources/application.yml`

在 routes 列表末尾添加：

```yaml
- id: omni-base
  uri: lb://omni-base
  predicates:
    - Path=/api/base/**
```

不使用 StripPrefix（与 auth 路由一致，Controller 直接处理完整 `/api/base/...` 路径）。

---

## Step 10: 前端 API 模块

**新建文件：** `omni-frontend/src/api/dict.ts`

TypeScript 接口：`DictType`, `DictData`, `CreateDictTypeRequest`, `UpdateDictTypeRequest`, `CreateDictDataRequest`, `UpdateDictDataRequest`

API 函数（全部使用共享 `request` Axios 实例，注入 `X-Tenant-Id` 请求头）：
- `listDictTypes`, `getDictType`, `createDictType`, `updateDictType`, `deleteDictType`, `toggleDictTypeStatus`
- `listDictData`, `createDictData`, `updateDictData`, `deleteDictData`, `refreshDictCache`

---

## Step 11: 前端视图

**新建文件：** `omni-frontend/src/views/base/dict/index.vue`

左右分栏布局（`el-row` / `el-col`）：

**左栏（~40%）- 字典类型列表：**
- 搜索区：typeCode 输入框 + typeName 输入框 + status 下拉 + 搜索/重置按钮
- 新建按钮（`v-permission="'dict:type:create'"`）
- `el-table`：typeCode, typeName, status（el-tag）, sort, 操作列（编辑/删除）
- `el-pagination` 分页
- 行点击事件：选中后加载右侧字典项

**右栏（~60%）- 字典数据列表：**
- 标题区：当前选中类型的 typeCode + typeName
- 操作区：新建按钮（`v-permission="'dict:data:create'"`）+ 刷新缓存按钮（`v-permission="'dict:data:refresh'"`）
- `el-table`：dictValue, dictLabel, tagType（渲染为 el-tag，type 取 tag_type 值）, status, sort, 操作列
- `el-pagination` 分页
- 空状态：未选择类型时显示提示文案

**Dialog 弹窗：**
- 类型表单：typeCode（编辑时禁用）, typeName, remark, sort
- 数据表单：typeCode（自动填充、禁用）, dictValue, dictLabel, tagType（el-select: success/warning/danger/info/primary）, remark, sort

---

## Step 12: 前端路由 & 菜单集成

**修改文件：** `omni-frontend/src/router/index.ts`

`iconMap` 追加：
```typescript
'base:dict': 'Collection',
```

约定式路由自动生效：`permissionCode "base:dict"` → `modulePath "base/dict"` → `views/base/dict/index.vue`，路由路径 `/admin/dict`。

**修改文件：** `omni-frontend/src/layout/index.vue`

`menuI18nMap` 追加：
```typescript
'base': 'common.baseManagement',
'base:dict': 'common.dictManagement',
```

`iconMap` 追加（与 router 中保持一致）：
```typescript
'base:dict': 'Collection',
```

---

## Step 13: i18n

**修改文件：** `omni-frontend/src/locales/zh-CN.ts`

`common` 中追加：`baseManagement: '基础数据'`, `dictManagement: '字典管理'`

新增 `dict` 命名空间（或追加到已有 section），包含：typeCode, typeName, dictValue, dictLabel, tagType, selectTypeHint, createType, editType, createData, editData, refreshCache, confirmDeleteType, confirmDeleteData, cacheRefreshed 等。

权限名称映射追加：`perm_base`, `perm_base_dict`, `perm_dict_type_list/create/update/delete`, `perm_dict_data_list/create/update/delete/refresh`

**修改文件：** `omni-frontend/src/locales/en-US.ts`

对应英文翻译。

---

## Step 14: AGENTS.md 更新

**修改文件：** `AGENTS.md`

- Entry Points → Backend：添加 `Base service` 条目
- Entry Points → Configuration：添加 `Base config` 条目
- Service Ports 表格：添加 `Base | 8101`
- 更新 Build order 说明：`omni-base` 在 `omni-auth` 之后
- 清理已删除的 omni-business 过期引用（4 处）

---

## Step 15: Flyway 迁移脚本

**新建文件：** `omni-backend/omni-base/src/main/resources/db/migration/V1__init.sql`

与 Step 1 的 DDL 一致（CREATE TABLE IF NOT EXISTS），供 Base 服务独立数据库初始化使用。

---

## 新建文件清单

| # | 文件路径 |
|---|---------|
| 1 | `omni-backend/omni-base/pom.xml` |
| 2 | `omni-backend/omni-base/src/main/java/com/omni/base/BaseApplication.java` |
| 3 | `omni-backend/omni-base/src/main/resources/application.yml` |
| 4 | `omni-backend/omni-base/src/main/resources/db/migration/V1__init.sql` |
| 5 | `omni-backend/omni-base/src/main/java/com/omni/base/config/SecurityConfig.java` |
| 6 | `omni-backend/omni-base/src/main/java/com/omni/base/security/GatewayPreAuthFilter.java` |
| 7 | `omni-backend/omni-base/src/main/java/com/omni/base/security/XssConfigProviderImpl.java` |
| 8 | `omni-backend/omni-base/src/main/java/com/omni/base/entity/SysDictType.java` |
| 9 | `omni-backend/omni-base/src/main/java/com/omni/base/entity/SysDictData.java` |
| 10 | `omni-backend/omni-base/src/main/java/com/omni/base/mapper/SysDictTypeMapper.java` |
| 11 | `omni-backend/omni-base/src/main/java/com/omni/base/mapper/SysDictDataMapper.java` |
| 12 | `omni-backend/omni-base/src/main/java/com/omni/base/dto/CreateDictTypeRequest.java` |
| 13 | `omni-backend/omni-base/src/main/java/com/omni/base/dto/UpdateDictTypeRequest.java` |
| 14 | `omni-backend/omni-base/src/main/java/com/omni/base/dto/CreateDictDataRequest.java` |
| 15 | `omni-backend/omni-base/src/main/java/com/omni/base/dto/UpdateDictDataRequest.java` |
| 16 | `omni-backend/omni-base/src/main/java/com/omni/base/dto/DictTypeQuery.java` |
| 17 | `omni-backend/omni-base/src/main/java/com/omni/base/service/DictTypeService.java` |
| 18 | `omni-backend/omni-base/src/main/java/com/omni/base/service/DictDataService.java` |
| 19 | `omni-backend/omni-base/src/main/java/com/omni/base/service/impl/DictTypeServiceImpl.java` |
| 20 | `omni-backend/omni-base/src/main/java/com/omni/base/service/impl/DictDataServiceImpl.java` |
| 21 | `omni-backend/omni-base/src/main/java/com/omni/base/controller/DictTypeController.java` |
| 22 | `omni-backend/omni-base/src/main/java/com/omni/base/controller/DictDataController.java` |
| 23 | `omni-frontend/src/api/dict.ts` |
| 24 | `omni-frontend/src/views/base/dict/index.vue` |

## 修改文件清单

| # | 文件路径 | 变更 |
|---|---------|------|
| 1 | `omni-backend/pom.xml` | `<modules>` 添加 `omni-base` |
| 2 | `omni-backend/omni-gateway/src/main/resources/application.yml` | routes 添加 base 路由 |
| 3 | `scripts/sql/init-all.sql` | 添加 omni_base 数据库 + 表 + 种子数据 + 权限节点 |
| 4 | `omni-frontend/src/router/index.ts` | iconMap 添加 `base:dict` |
| 5 | `omni-frontend/src/layout/index.vue` | menuI18nMap + iconMap 添加 base 相关条目 |
| 6 | `omni-frontend/src/locales/zh-CN.ts` | 添加字典管理翻译 |
| 7 | `omni-frontend/src/locales/en-US.ts` | 添加字典管理英文翻译 |
| 8 | `AGENTS.md` | 添加 Base 服务条目，清理 business 过期引用 |

---

## 验证方案

1. **后端编译**：`cd omni-backend && JAVA_HOME="C:/APP/JDK25/jdk-25.0.2" ./mvnw clean install` — 无编译错误
2. **前端构建**：`cd omni-frontend && npm run build && npm run lint` — 零错误
3. **数据库初始化**：执行 `init-all.sql`，验证 `omni_base` 数据库和两张表创建成功，seed data 插入正确
4. **服务启动**：Nacos → Base(8101) → Gateway(8102) → Frontend(3000)
5. **功能验证**：
   - 登录后侧边栏出现"基础数据 > 字典管理"菜单
   - 左侧字典类型列表加载、搜索、分页、CRUD 正常
   - 选中类型后右侧字典项列表加载、CRUD 正常
   - Redis 缓存写入/失效/手动刷新正常
   - 权限控制：无权限的按钮被隐藏（v-permission），无权限的 API 返回 403
   - 多租户隔离：不同租户看到各自的字典数据
