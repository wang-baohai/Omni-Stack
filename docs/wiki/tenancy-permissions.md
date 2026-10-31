# 5. 数据范围：同一家企业里，谁能看哪些记录

租户解决公司之间的边界，但公司内部也有分工。小李维护自己的客户，王经理负责本部门；有的岗位还需要查看下级部门。

这就是数据范围。先有操作资格，再决定可见记录，两者都要满足。一个人有多个角色，也不等于可以随意把最宽角色用于每一个操作。

## 先看六种范围的意思

| 范围值 | 可以先怎样理解 |
|---|---|
| SELF | 本人负责的记录 |
| DEPT | 当前主组织负责的记录 |
| DEPT_AND_BELOW | 当前主组织及符合规则的下级组织 |
| CUSTOM | 角色专门配置的组织范围 |
| TENANT | 本租户内不再按本人或组织缩小 |
| ALL | 范围层不追加行条件；是否跨租户仍取决于具体接口与租户约束 |

SELF 到底对应哪一列，要看业务。有的表按负责人，有的按申请人，个人资产又有固定当前使用人，不能统一理解成“数据库里任意 user_id”。

ALL 与 TENANT 在一些行处理器里都不再缩小范围，但这不表示两者在全部接口都完全相同。

## 操作资格与范围怎样配合

common-service 的 @ServiceDataScope 方法会按当前用户、租户和具体 permissionCode 向 Auth 解析范围，检查返回身份是否一致，随后在方法执行期间绑定结果。

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java" -->
```java
        ServiceRequestIdentity identity = ServiceIdentityContext.require();
        try {
            InternalDataScopeDTO scope = dataScopeResolver.resolve(identity, annotation.permissionCode());
            validate(scope, identity, annotation.permissionCode());
            ServiceDataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            ServiceDataScopeContext.clear();
        }
```

先取得可信身份，再解析并验证范围，之后才执行原业务方法。最后清理本次范围。这里只展示连续的核心部分，没有把所有方法、所有服务都描述成使用同一个注解。

带 permissionCode 的解析只考虑真正授予该完整权限的启用角色。Auth 的普通请求解析则会合并相应角色范围，两种入口不能混为一谈。

例如，某个角色允许很广的用户管理，不代表它可以给另一项客户操作额外扩大范围；要先确认这个角色是否授予了那项操作。

<!-- omni:modules:crm -->
## 用客户记录读懂一个具体分支

CRM 的处理器先限定自己登记的业务表，再读取当前操作的数据范围。选择条件的连续片段如下：

<!-- omni:source path="omni-backend/omni-crm/src/main/java/com/omni/crm/security/CrmDataPermissionHandler.java" -->
```java
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        ServiceDataScopeContext.ScopeInfo info = ServiceDataScopeContext.get();
        if (info == null || info.effectiveScope() == null) {
            return deny(alias);
        }
        return switch (info.effectiveScope()) {
            case "ALL", "TENANT" -> null;
            case "SELF" -> equals(alias, "owner_user_id", info.userId());
            case "DEPT" -> equals(alias, "owner_unit_id", info.primaryUnitId());
            case "DEPT_AND_BELOW", "CUSTOM" -> in(alias, "owner_unit_id", info.accessibleUnitIds());
            default -> deny(alias);
        };
```

没有有效范围就返回不匹配条件。SELF 对应 owner_user_id，DEPT 对应 owner_unit_id，下级组织或自定义范围使用可见组织集合。ALL/TENANT 在这里不增加 owner 条件，但独立的租户插件仍保留企业限制。

因此，甲公司销售员看“自己负责且属于甲公司”的客户；甲公司经理看“负责部门范围内且属于甲公司”的客户。不能因为 owner 条件放宽，就把租户条件一起删掉。

这段说明是当前源码如何形成边界，不冒称每一种组合都已在当前环境重新实测。
<!-- omni:modules:end -->

## 为什么子表不能随便加负责人条件

明细行不一定保存 owner_user_id。如果供应商联系人、评估明细或采购行通过所属主记录继承可见范围，就要沿着这个关系检查。

可以把主记录看成一个文件夹，明细是里面的页。判断你能否读某页，要先看你能否读这个文件夹；不能要求每页都有一个实际上不存在的负责人栏目。

完整工程里，各领域有不同的对应关系：

| 对象 | 范围依据 |
|---|---|
| CRM 已登记的客户等业务表 | 各自的负责人和组织字段 |
| SRM 供应商及评估相关子资源 | 对应供应商/评估关系继承，不能给没有 owner 的表追加 owner 列 |
| 采购请购单 | 申请人、申请组织 |
| 询价、采购单、收货单及明细 | 负责人/组织，明细通过所属主记录继承 |
| 资产管理、我的资产与接受/归还 | 管理视图使用 owner；本人动作固定 current_user_id；子记录通过资产继承 |

这张表解释完整仓库的领域分工。生成项目只包含所选预设的业务能力，不应在缺少对应模块时期待同样的菜单或数据。

## 组织树为什么也要带租户

王经理可以看下级组织，不是把所有编号更大的部门都算进来。工程根据当前租户中的组织关系和路径展开范围。

DEPT 使用当前租户的启用主组织；DEPT_AND_BELOW 再展开符合条件的后代；CUSTOM 按参与角色配置的根组织展开。Auth 当前对 CUSTOM 根与后代的启用状态检查并不完全相同，不能只凭后代查询带 status=1 就推断全部根也做了同样检查。

角色分配还有 SAME_UNIT/UNIT_AND_BELOW，说明一份角色分配在同一组织还是连同下级组织生效，寻找审批处理人时会用到。它们与“本人、本部门、整租户”等查询范围分开配置。

## 明确读出的特例和失败边界

在线用户管理从 Redis 读在线记录，还要通过数据库核对用户所属企业与组织。当前 ALL 范围允许跨租户，其他范围先限制租户；它不能证明领域业务列表也有同样的跨租户能力。

另一个差异在“没有范围时”：CRM 等已映射领域表会拒绝匹配；Auth 自己的数据权限处理器在无上下文、未知范围或未登记表时不追加条件，需要依赖对应接口和查询的其他校验。不能把整个工程概括成“装了插件就处处自动失败关闭”。

查询范围解析失败、无操作权限、业务状态不允许，是不同问题。排障应先定位条件，不能通过改身份头、共用管理员账号或移除查询条件来取得结果。

下一篇看[缓存与后台任务](tenancy-background.md)，把 HTTP 请求之外的部分补完整。
