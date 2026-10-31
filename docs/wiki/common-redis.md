# common-redis：阻塞式 Redis 的序列化约定与工具

`omni-common-redis` 面向 **Servlet** 服务：它提供一个已配置好序列化的 `RedisTemplate`，以及一个可选的 `RedisUtils` 工具类。它不是响应式方案——WebFlux 服务必须使用 [common-redis-reactive](common-redis-reactive.md)，两者不可混用。

> 事实边界：模块 2 个 Java 文件；`RedisUtils` 当前**没有业务调用方**（全仓检索只命中定义与自动配置），本文如实标注，不编造使用者。

## 问题

同一套 Redis 里，如果各服务用不同的序列化器，一个服务写入的 key 另一个服务可能读不出来；Key 用 JSON 序列化还会让 `keys` 模式与运维排查变得难读。另一个问题是连接与超时配置分散，容易各配一套。

## 一、RedisTemplate 序列化约定

### 怎么用

下面的 JSON 序列化约定属于 `RedisTemplate<String, Object>`。`StringRedisTemplate` 是另一种字符串客户端，Value 也按字符串处理，不继承这里的 JSON 序列化器。对象读写使用前者：

```java
redisTemplate.opsForValue().set("demo:key", someObject, Duration.ofMinutes(10));
```

### 实现原文

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

约定是：

| 位置 | 序列化器 | 结果 |
| --- | --- | --- |
| Key / HashKey | `StringRedisSerializer` | Redis 里是可读字符串，便于 `SCAN`/运维 |
| Value / HashValue | `GenericJacksonJsonRedisSerializer` | JSON，并**嵌入类型信息**以支持多态反序列化 |

### 原理与边界

- 类型信息内嵌意味着跨服务共享同一个 key 时，双方的类路径必须兼容；类改名或包移动会导致反序列化失败。
- Bean 名固定为 `redisTemplate`，且 `@ConditionalOnMissingBean(name = "redisTemplate")`：服务自定义同名 Bean 时整份配置不生效。
- 只做阻塞式连接；在 WebFlux 事件循环线程上调用会阻塞 Netty。

### 失败恢复

- 连接失败由 Spring Data Redis 抛出；本模块不吞异常、不降级。
- 反序列化失败通常先查写入方与读取方的类版本是否一致，而不是先怀疑 Redis。
- 若 Key 本身带有 JSON 引号或类型字段，先检查是否用了另一套 Key 序列化器；不要把 Value 的 JSON 内容误当成 Key 序列化异常。

### 扩展

统一改序列化器会影响所有使用该 Bean 的服务与已有数据；新增类型应优先保持 JSON 与类型信息约定，不要单独引入第二套序列化器。

## 二、RedisUtils

### 怎么用

```java
redisUtils.setIfAbsent("lock:order:1001", "1", 30, TimeUnit.SECONDS);
```

### 实现原文

```java
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }
```

方法族包括：`set/get`、`delete`（单个/集合）、`hasKey`、`expire/getExpire`、`increment/decrement`、`setIfAbsent`、`keys`、`opsForHash`。

### 当前接入状态

**全仓没有业务服务引用 `RedisUtils`**：检索只命中 `RedisUtils.java` 自身与 `RedisAutoConfiguration`。实际缓存组件（例如 XSS 配置缓存）直接使用 `StringRedisTemplate`。

因此：

- 它是“可用但未使用”的工具，不是事实上的项目标准；
- 文章不能写“各服务统一通过 RedisUtils 访问 Redis”；
- 若要把它作为标准，需要先迁移调用方，而不是只写文档。

### 边界与失败恢复

- `keys(pattern)` 在数据量大时会阻塞 Redis，生产应使用 `SCAN`；本模块没有封装 `SCAN`。
- `setIfAbsent` 是 SETNX + TTL，可用于简单互斥，但**不是可重入锁**，也没有看门狗续期。
- 工具方法不处理异常，调用方需自行决定重试或降级。

### 扩展

新增方法优先保持与 `RedisTemplate` 语义一致；若需要分布式锁的续期、可重入或公平性，应引入专门的锁实现，而不是继续堆工具方法。

## 验证入口

- `RedisAutoConfiguration` 的 Bean 名与条件为源码事实（`RedisAutoConfiguration.java:28-50`）。
- 序列化行为由 Spring Data Redis 的 `GenericJacksonJsonRedisSerializer` 决定，项目未额外封装测试；`RedisUtils` 的调用方缺口已在 WP-21.S01 记录。

## 与响应式的边界

| | 本模块 | redis-reactive |
| --- | --- | --- |
| 线程模型 | 阻塞 | 响应式 |
| 适用 | Servlet 服务 | WebFlux（Gateway） |
| Java 封装 | RedisTemplate + RedisUtils | 无（仅依赖 + 默认超时） |
| 混用后果 | 在事件循环上阻塞，吞吐骤降 | — |

下一章：[common-redis-reactive](common-redis-reactive.md)。
