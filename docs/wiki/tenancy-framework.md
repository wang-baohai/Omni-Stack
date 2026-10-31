# 3. 框架原理：查询执行之前，怎样加上企业条件

你已经知道共享表需要记录所属企业。现在看第二个问题：每次查数据时，怎样把这个归属用起来？

可以把一次数据库查询看成一张取档案的申请。原申请写了“找出客户”，租户插件补上“属于当前企业”，数据范围再补上“由自己或本部门负责”。这些条件要一起成立。

下面使用当前工程的 MyBatis-Plus 3.5.16，来源是实际使用的 JSQLParser-4.9 模块源码包。片段是连续原文，技术变量不用先背下来。

## 先看整体位置

```mermaid
flowchart LR
    A["业务请求数据库记录"] --> B["MyBatis准备执行查询"]
    B --> C["启用的租户插件处理企业条件"]
    C --> D["启用的数据范围插件处理可见记录"]
    D --> E["后续并发和分页等插件"]
    E --> F["数据库执行最终语句"]
```

这是 common-service 启用相应能力后的责任图，不是声称 Auth、Workflow 和原始 JDBC 都经过同一条链。下一篇会分开解释。

## 第一段：在查询前改写语句

TenantLineInnerInterceptor 的 beforeQuery 中有下面一段原文，位于实际版本第 63–70 行。

<!-- omni:source path="docs/reference/upstream/mybatis-plus-3.5.16/query-rewrite.java.txt" -->
```java
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (InterceptorIgnoreHelper.willIgnoreTenantLine(ms.getId())) {
            return;
        }
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        mpBs.sql(parserSingle(mpBs.sql(), null));
    }
```

第一项检查允许某些被明确标记的查询跳过租户插件。没有跳过时，框架取出待执行语句，交给 parserSingle 解析，再把处理后的语句放回去。

所以，插件是在应用准备执行数据库访问时工作，数据库并不会自动从浏览器认识当前用户。某条访问绕过插件，仍需要自己的归属条件；也不能随意给业务方法添加忽略标记。

## 第二段：企业条件由什么组成

继续看同一类第 264–270 行。这个方法负责为一张表构造租户条件。

<!-- omni:source path="docs/reference/upstream/mybatis-plus-3.5.16/tenant-condition.java.txt" -->
```java
    @Override
    public Expression buildTableExpression(final Table table, final Expression where, final String whereSegment) {
        if (tenantLineHandler.ignoreTable(table.getName())) {
            return null;
        }
        return new EqualsTo(getAliasColumn(table), tenantLineHandler.getTenantId());
    }
```

如果项目策略要求忽略这张表，就返回 null，意思是这一步不额外添加企业条件。否则把“租户列”和“当前租户值”组成相等条件。

一张表在查询中可以有一个短称，叫别名。getAliasColumn 会把它带进列名，避免多表查询时指向不清。getTenantId 则由工程提供；框架本身不知道当前来访者属于甲公司还是乙公司。

默认列名在 TenantLineHandler 第 46–48 行里很直白：

<!-- omni:source path="docs/reference/upstream/mybatis-plus-3.5.16/tenant-column.java.txt" -->
```java
    default String getTenantIdColumn() {
        return "tenant_id";
    }
```

这只能说明默认叫 tenant_id，不能证明每一张表都有这个字段。基础设施表、引擎表和专门设计的全局目录，要按各自规则处理。

## 第三段：数据范围不是租户插件替你决定的

DataPermissionInterceptor 是另一项职责。它在当前使用的多表处理分支中，把表、原条件和映射语句标识交给工程的数据范围处理器。原文件第 166–174 行如下。

<!-- omni:source path="docs/reference/upstream/mybatis-plus-3.5.16/permission-delegation.java.txt" -->
```java
    @Override
    public Expression buildTableExpression(final Table table, final Expression where, final String whereSegment) {
        if (dataPermissionHandler == null) {
            return null;
        }
        // 只有新版数据权限处理器才会执行到这里
        final MultiDataPermissionHandler handler = (MultiDataPermissionHandler) dataPermissionHandler;
        return handler.getSqlSegment(table, where, whereSegment);
    }
```

这段源码没有写“小李属于华东销售部”。它只负责调用处理器；人员、角色、组织和字段对应关系，要由项目提供。

这里的 null 也只是“不增加本插件的额外条件”，不是拒绝访问，也不是放开其他插件。租户条件、业务本来就写的条件和接口授权仍需要各自成立。

## 框架与工程各做什么

| 框架负责 | 工程必须提供 |
|---|---|
| 在相应数据库访问前接入插件 | 哪些服务启用插件，注册顺序是什么 |
| 解析支持的语句，构造条件表达式 | 当前可信租户来自哪里，哪些表适用 |
| 把数据范围处理交给回调 | 当前操作的权限、本人/组织范围、表字段与父子关系 |
| 把结果继续交给后续执行流程 | 写操作的归属、状态、版本和事务约束 |

common-service 当前的顺序是 TenantLine、DataPermission、OptimisticLock、Pagination。先处理租户和行范围，再分页，才能让返回记录和总数使用相应限制。不要把这项顺序直接套到有独立配置的其他服务。

后两项可以先理解为：结合记录版本防止旧更新覆盖别人刚做的修改，以及分批返回列表。是否实际生效，还需要实体、版本字段和查询按对应机制接入。

## 读完这三段之后，应该留下什么判断

看到“项目用了租户插件”，还不能立即得出“所有数据都已经隔离”。继续问：这张表是否适用，这次查询是否经过插件，当前身份从哪里来，业务授权与异步路径有没有自己的规则？

下一篇把这些问题接到[工程中的一次请求](tenancy-request.md)。

## 原文与许可

源码来自 [MyBatis-Plus 3.5.16 官方 Maven 源码包](https://repo.maven.apache.org/maven2/com/baomidou/mybatis-plus-jsqlparser-4.9/3.5.16/mybatis-plus-jsqlparser-4.9-3.5.16-sources.jar)，其官方 SHA-256 与取得的源码包一致。引用保留原文，原文件版权为 Copyright (c) 2011-2025, baomidou (jobob@qq.com)，采用 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)。

来源记录保留包、原文件、行范围、片段和完整许可证摘要。片段用于讲解，不是额外的运行依赖，也不替代真实业务隔离测试。

完整[许可证](../reference/upstream/mybatis-plus-3.5.16/LICENSE.txt)和[来源登记](../reference/upstream/mybatis-plus-3.5.16/provenance.json)随本站提供，无需访问外部仓库。
