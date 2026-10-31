# common-redis: serialization conventions and helpers for blocking Redis

`omni-common-redis` targets **Servlet** services: it provides a `RedisTemplate` with configured serializers and an optional `RedisUtils` helper. It is not a reactive solution — WebFlux services must use [common-redis-reactive](common-redis-reactive.en.md); the two must not be mixed.

> Evidence boundary: the module has 2 Java files. `RedisUtils` currently has **no business callers** (a repository-wide search only finds its definition and the auto-configuration); this article states that honestly and does not invent users.

## Problem

In one Redis instance, if services use different serializers, a key written by one service may be unreadable by another; JSON-serialized keys also make `keys` patterns and operations troubleshooting hard to read. Connection and timeout settings scattered per service are another source of drift.

## 1. RedisTemplate serialization conventions

### Usage

The JSON convention below belongs to `RedisTemplate<String, Object>`. `StringRedisTemplate` is a separate string client whose values are strings too; it does not inherit this JSON serializer. Use the former for objects:

```java
redisTemplate.opsForValue().set("demo:key", someObject, Duration.ofMinutes(10));
```

### Source

```java
    @Bean(name = "redisTemplate")
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder().build();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key 序列化
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // Value 序列化
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
```

The convention:

| Position | Serializer | Result |
| --- | --- | --- |
| Key / HashKey | `StringRedisSerializer` | readable strings in Redis, convenient for `SCAN`/operations |
| Value / HashValue | `GenericJacksonJsonRedisSerializer` | JSON with **embedded type information** for polymorphic deserialization |

### Principle and boundaries

- Embedded type information means services sharing a key must have compatible classpaths; renaming or moving a class breaks deserialization.
- The bean is named `redisTemplate` and guarded by `@ConditionalOnMissingBean(name = "redisTemplate")`; a service-defined bean disables this configuration entirely.
- Blocking only; calling it on a WebFlux event-loop thread blocks Netty.

### Recovery

- Connection failures propagate from Spring Data Redis; this module neither swallows nor degrades.
- Deserialization failures usually mean the writer and reader disagree on class versions — check that before blaming Redis.
- If the key itself contains JSON quotes or type fields, check whether a different key serializer was used. Do not mistake a value's JSON content for a key-serialization error.

### Extension

Changing the serializer affects every service using the bean and existing data; new types should keep the JSON + type-information convention rather than introducing a second serializer.

## 2. RedisUtils

### Usage

```java
redisUtils.setIfAbsent("lock:order:1001", "1", 30, TimeUnit.SECONDS);
```

### Source

```java
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }
```

Method families: `set/get`, `delete` (single/collection), `hasKey`, `expire/getExpire`, `increment/decrement`, `setIfAbsent`, `keys`, `opsForHash`.

### Current adoption

**No business service references `RedisUtils`**: the search only hits `RedisUtils.java` and `RedisAutoConfiguration`. Real cache components (such as the XSS configuration cache) use `StringRedisTemplate` directly.

Therefore:

- it is an available-but-unused helper, not a de facto project standard;
- articles must not claim "all services access Redis through RedisUtils";
- making it the standard requires migrating callers, not just writing docs.

### Boundaries and recovery

- `keys(pattern)` blocks Redis on large datasets; production should use `SCAN`, which this module does not wrap.
- `setIfAbsent` is SETNX + TTL; it works for simple mutual exclusion but is **not reentrant** and has no watchdog renewal.
- Helper methods do not handle exceptions; callers decide retry or degradation.

### Extension

New methods should keep `RedisTemplate` semantics. Reentrancy, renewal or fairness require a dedicated lock implementation, not more helper methods.

## Verification

- Bean name and conditions are source facts (`RedisAutoConfiguration.java:28-50`).
- Serialization behavior comes from Spring Data Redis; the project adds no extra test. The missing callers of `RedisUtils` are recorded in WP-21.S01.

## Boundary with the reactive module

| | This module | redis-reactive |
| --- | --- | --- |
| Thread model | blocking | reactive |
| Applies to | Servlet services | WebFlux (Gateway) |
| Java wrappers | RedisTemplate + RedisUtils | none (dependency + default timeout) |
| Mixing consequence | blocking the event loop, throughput collapse | — |

Next: [common-redis-reactive](common-redis-reactive.en.md).
