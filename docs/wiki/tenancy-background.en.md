# 6. Beyond requests: caches, messages, background jobs, and extension

Having a company identity inside a request does not finish the work. Caches, online state, messages, and jobs outside the database also need to know which company's data they handle.

## Distinguish companies in a cache

Both companies may have a “Customer level” dictionary. Caching only by that name could let the first company's value affect the other.

Dictionary options currently use dict:type:{tenantId}:{typeCode}: company and dictionary type together. The beginning of DictDataServiceImpl below uses CACHE_KEY_PREFIX = dict:type:.

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/service/impl/DictDataServiceImpl.java" -->
```java
    public List<SysDictData> listEnabledData(Long tenantId, String typeCode) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + typeCode;

        // 优先从 Redis 读取
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
```

tenantId is an explicit method parameter, not a guess from the cache. Reads, database fallback, cache writes, and invalidation after changes must follow the same ownership rules.

Frontend useDictOptions keys also include the tenant. XSS configuration uses xss:enabled:{tenantId} and xss:rules:{tenantId}; changes invalidate the corresponding keys. One Redis does not mean one shared configuration value.

## Not every key has the same shape

Online state uses online:{userId}, with username|jti as its value. The key lacks full tenant information; list and kick services consult the user table to enforce company and organization scope.

The JWT blacklist identifies a particular token by jti, rather than making an identically named access card for each company. Isolation depends on related identifiers, fallback, and authorization during operations, not just key names.

## Why background threads cannot infer the tenant

An HTTP request usually runs in a worker thread. Think of ThreadLocal as a temporary note for that thread. A thread pool, queue, or scheduled job may execute on another thread or even another service.

Background code cannot assume it receives the browser request's note. Carry company identity explicitly in messages, jobs, or method parameters.

The reliable-message contract requires tenantId:

<!-- omni:source path="omni-backend/omni-common-core/src/main/java/com/omni/common/core/mq/ReliableMessageRelay.java" -->
```java
    void send(String bindingName, Object payload, Long tenantId);
```

The caller supplies ownership; the relay does not guess from its thread. The implementation first writes a local Outbox, a pending-message list in the business database, then delivers in the background.

Personal-job messages also contain an explicit field:

<!-- omni:source path="omni-backend/omni-common-core/src/main/java/com/omni/common/core/job/UserJobMessage.java" -->
```java
    /** 租户 ID，用于多租户隔离，确保任务在正确的租户上下文中执行 */
    private Long tenantId;
```

Handlers must validate and use it. Adding a DTO property alone does not isolate all processing. Routing, parameter validation, queries, and cleanup retain their own responsibilities.

## Cross-tenant background work has a defined purpose

The MQ relay scans pending messages across tenants as an infrastructure responsibility. Consumers validate the event's company identity and use a domain Inbox, a received-event deduplication list, to avoid duplicate execution. They cannot rely on producer-thread context.

Archives, migrations, and other system work may span tenants too. Dedicated entries and responsibilities justify that scope; ordinary business interfaces cannot copy the exception. External message queries still restrict tenantId.

| Content | Current boundary |
|---|---|
| sys_tenant | Controlled tenant directory |
| sys_permission | Global permission-code catalog; role grants remain tenant-specific |
| sys_user_job_type | Shared personal-job types; instances retain tenant and creator |
| Nacos, XXL-JOB, and similar infrastructure | Management-plane rules do not grant unrestricted business access |
| MQ Relay | Intentional cross-tenant Outbox scan; ordinary queries remain restricted |
| Flowable ACT_* | Engine tenancy and dedicated query validation, not universal domain TenantLine handling |

Shared content has different ownership rules; it still needs identity and permission checks.

## Check a new feature along one complete path

1. Identify the owning tenant, required table field, and component that supplies it.
2. Determine the access path: explicit SQL, tenant plugin, raw JDBC, or engine API. Check paths outside the plugin separately.
3. Separate operation permission from scope. Does self mean requester, owner, or current user? Which column represents the organization?
4. Identify the parent through which details inherit visibility. Do not append nonexistent owner columns.
5. Check cache keys, fallback, and invalidation. Special keys such as online state need association checks.
6. Carry company identity in messages/jobs, and validate consumers, deduplication, and failure retry without relying on the original request thread.
7. Clear identity and scope in finally. Writes also check ownership, state, versions, and affected rows.

This is an integration approach, not a request to rewrite every service. One authorization annotation or tenant plugin cannot complete the whole path by itself.

## Find the layer where a problem occurs

| Symptom | Check first | Recovery direction |
|---|---|---|
| A shared username logs in somewhere unexpected | Selected company, corresponding account, current credential | Sign in through the correct company; do not only edit the browser tenant header |
| Menu exists but records are absent | Operation, current company, role scope, primary organization, owner | Check ownership/scope before removing filters |
| Scope service denies or is unavailable | Parameters, grants, user/tenant, upstream service | Follow actual branches such as 403/503; never fall back to unrestricted queries |
| Cache shows another/old setting | Ownership in keys, fallback conditions, matching invalidation | Correct the ownership/invalidation path and retrieve again |
| Background task lacks a company | Message/job parameters and consumer context lifecycle | Carry and validate it explicitly rather than guessing |
| Workflow extension lacks context | Entry, TenantInfoHolder, and current fallback | Investigate the dedicated workflow path; default 1 is not a safe general solution |

These explanations come from current source. Understanding the mechanism and executing all cross-tenant scenarios are separate; static excerpts or images do not establish complete isolation acceptance.

Future improvements can focus on consistent missing-tenant handling, explicit ownership in special queries, and broader cross-tenant negative cases. Each needs its own reproduction and verification; this tutorial does not expand into business refactoring.
