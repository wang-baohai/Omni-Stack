# common-redis: 블로킹 Redis의 직렬화 규약과 도구

`omni-common-redis`는 **Servlet** 서비스용입니다. 직렬화가 설정된 `RedisTemplate`과 선택적 `RedisUtils`를 제공합니다. 반응형 방식이 아니며 WebFlux 서비스는 [common-redis-reactive](common-redis-reactive.kr.md)를 써야 하고, 둘을 혼용할 수 없습니다.

> 사실 경계: 모듈에는 Java 파일 2개가 있습니다. `RedisUtils`는 현재 **업무 호출자가 없습니다**(전체 저장소 검색은 정의와 자동 설정만 찾음). 이 글은 이를 사실대로 적고 사용자를 지어내지 않습니다.

## 문제

같은 Redis에서 서비스마다 직렬화기가 다르면 한 서비스가 쓴 key를 다른 서비스가 읽지 못할 수 있습니다. Key를 JSON으로 직렬화하면 `keys` 패턴과 운영 조사가 읽기 어려워집니다. 연결·타임아웃 설정이 흩어지는 문제도 있습니다.

## 1. RedisTemplate 직렬화 규약

### 사용법

아래 JSON 규약은 `RedisTemplate<String, Object>`에 해당합니다. `StringRedisTemplate`은 Value도 문자열로 처리하는 별도 클라이언트이며 이 JSON 직렬화기를 물려받지 않습니다. 객체에는 전자를 사용합니다.

```java
redisTemplate.opsForValue().set("demo:key", someObject, Duration.ofMinutes(10));
```

### 구현 원문

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

| 위치 | 직렬화기 | 결과 |
| --- | --- | --- |
| Key / HashKey | `StringRedisSerializer` | Redis에서 읽을 수 있는 문자열, `SCAN`/운영에 유리 |
| Value / HashValue | `GenericJacksonJsonRedisSerializer` | JSON, **타입 정보 내장**으로 다형 역직렬화 지원 |

### 원리와 경계

- 타입 정보가 내장되므로 같은 key를 공유하는 서비스 간 클래스패스 호환이 필요합니다. 클래스 이름 변경·이동은 역직렬화 실패를 부릅니다.
- Bean 이름은 `redisTemplate` 고정이며 `@ConditionalOnMissingBean(name = "redisTemplate")`입니다. 같은 이름 Bean을 정의하면 이 설정 전체가 비활성화됩니다.
- 블로킹 전용입니다. WebFlux 이벤트 루프에서 호출하면 Netty를 막습니다.

### 실패 복구

- 연결 실패는 Spring Data Redis에서 전파되며 이 모듈은 삼키거나 강등하지 않습니다.
- 역직렬화 실패는 Redis를 의심하기 전에 쓰기 측과 읽기 측의 클래스 버전 불일치를 확인합니다.
- Key 자체에 JSON 따옴표나 타입 필드가 있으면 다른 Key 직렬화기를 사용했는지 확인하세요. Value의 JSON 내용을 Key 직렬화 오류로 착각해서는 안 됩니다.

### 확장

직렬화기를 일괄 변경하면 모든 서비스와 기존 데이터에 영향을 줍니다. 새 타입은 JSON + 타입 정보 규약을 유지하고 두 번째 직렬화기를 들이지 않습니다.

## 2. RedisUtils

### 사용법

```java
redisUtils.setIfAbsent("lock:order:1001", "1", 30, TimeUnit.SECONDS);
```

### 구현 원문

```java
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }
```

메서드군: `set/get`, `delete`(단일/집합), `hasKey`, `expire/getExpire`, `increment/decrement`, `setIfAbsent`, `keys`, `opsForHash`.

### 현재 도입 상태

**업무 서비스는 `RedisUtils`를 참조하지 않습니다**. 검색은 `RedisUtils.java`와 `RedisAutoConfiguration`만 찾습니다. 실제 캐시(XSS 설정 캐시 등)는 `StringRedisTemplate`을 직접 사용합니다.

따라서:

- 사용 가능하지만 쓰이지 않는 도구이며 사실상 표준이 아닙니다.
- "모든 서비스가 RedisUtils로 Redis에 접근한다"고 쓰면 안 됩니다.
- 표준으로 만들려면 호출자 이전이 필요하고 문서만으로는 부족합니다.

### 경계와 실패 복구

- `keys(pattern)`은 대량 데이터에서 Redis를 막습니다. 운영은 `SCAN`을 써야 하며 이 모듈은 `SCAN`을 감싸지 않습니다.
- `setIfAbsent`는 SETNX + TTL로 단순 상호 배제에는 쓰이지만 **재진입 불가**이며 워치독 갱신이 없습니다.
- 예외를 처리하지 않으며 재시도·강등은 호출자가 결정합니다.

### 확장

새 메서드는 `RedisTemplate` 의미를 유지합니다. 재진입·갱신·공정성이 필요하면 전용 잠금 구현을 도입하고 도구만 늘리지 않습니다.

## 검증 진입

- Bean 이름과 조건은 소스 사실(`RedisAutoConfiguration.java:28-50`).
- 직렬화는 Spring Data Redis 동작이며 추가 테스트는 없습니다. `RedisUtils` 호출자 부재는 WP-21.S01에 기록했습니다.

## 반응형과의 경계

| | 이 모듈 | redis-reactive |
| --- | --- | --- |
| 스레드 모델 | 블로킹 | 반응형 |
| 적용 | Servlet 서비스 | WebFlux(Gateway) |
| Java 래퍼 | RedisTemplate + RedisUtils | 없음(의존 + 기본 타임아웃) |
| 혼용 결과 | 이벤트 루프 차단, 처리량 급락 | — |

다음: [common-redis-reactive](common-redis-reactive.kr.md).
