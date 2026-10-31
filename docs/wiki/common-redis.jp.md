# common-redis：ブロッキング Redis の直列化規約とユーティリティ

`omni-common-redis` は **Servlet** サービス向けです。直列化を設定済みの `RedisTemplate` と、任意の `RedisUtils` を提供します。リアクティブ方式ではなく、WebFlux サービスは [common-redis-reactive](common-redis-reactive.jp.md) を使う必要があり、両者は混用できません。

> 事実境界：モジュールは Java 2 ファイルです。`RedisUtils` には現在**業務からの呼び出しがありません**（全倉検索は定義と自動設定のみ）。本稿はそれを正直に記し、利用者を捏造しません。

## 問題

同じ Redis でサービスごとに直列化器が違うと、あるサービスが書いた key を別のサービスが読めません。Key を JSON 直列化すると `keys` パターンや運用調査が読みにくくなります。接続・タイムアウト設定が分散する問題もあります。

## 1. RedisTemplate の直列化規約

### 使い方

以下の JSON 規約は `RedisTemplate<String, Object>` のものです。`StringRedisTemplate` は別の文字列クライアントで、Value も文字列として扱い、この JSON 直列化器を引き継ぎません。オブジェクトには前者を使います。

```java
redisTemplate.opsForValue().set("demo:key", someObject, Duration.ofMinutes(10));
```

### 実装原文

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

| 位置 | 直列化器 | 結果 |
| --- | --- | --- |
| Key / HashKey | `StringRedisSerializer` | Redis 上で読める文字列。`SCAN`/運用に便利 |
| Value / HashValue | `GenericJacksonJsonRedisSerializer` | JSON。**型情報を埋め込み**多態逆直列化に対応 |

### 原理と境界

- 型情報の埋め込みにより、同じ key を共有するサービス間でクラスパスの互換性が必要です。クラス名変更や移動は逆直列化失敗を招きます。
- Bean 名は `redisTemplate` 固定で、`@ConditionalOnMissingBean(name = "redisTemplate")` 付きです。同名 Bean を定義するとこの設定全体が無効になります。
- ブロッキング専用です。WebFlux のイベントループで呼ぶと Netty を塞ぎます。

### 失敗復旧

- 接続失敗は Spring Data Redis から伝播し、本モジュールは握り潰しも降格もしません。
- 逆直列化失敗は、Redis を疑う前に書き込み側と読み取り側のクラス版の不一致を確認します。
- Key 自体に JSON の引用符や型フィールドがある場合、別の Key 直列化器を使っていないか確認します。Value の JSON 内容を Key の直列化異常と取り違えないでください。

### 拡張

直列化器の一括変更は全サービスと既存データに影響します。新しい型は JSON + 型情報の規約を維持し、第 2 の直列化器を持ち込まないでください。

## 2. RedisUtils

### 使い方

```java
redisUtils.setIfAbsent("lock:order:1001", "1", 30, TimeUnit.SECONDS);
```

### 実装原文

```java
    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }
```

メソッド群：`set/get`、`delete`（単一/集合）、`hasKey`、`expire/getExpire`、`increment/decrement`、`setIfAbsent`、`keys`、`opsForHash`。

### 現在の利用状況

**業務サービスは `RedisUtils` を参照していません**。検索は `RedisUtils.java` と `RedisAutoConfiguration` のみにヒットします。実際のキャッシュ（XSS 設定キャッシュなど）は `StringRedisTemplate` を直接使います。

したがって：

- 利用可能だが未使用のユーティリティであり、事実上の標準ではありません。
- 「全サービスが RedisUtils 経由で Redis にアクセスする」と書いてはいけません。
- 標準にするには呼び出し側の移行が必要で、文書だけでは足りません。

### 境界と失敗復旧

- `keys(pattern)` は大規模データで Redis をブロックします。本番は `SCAN` を使うべきで、本モジュールは `SCAN` を包んでいません。
- `setIfAbsent` は SETNX + TTL で、単純な相互排他には使えますが**再入不可**でウォッチドッグ更新もありません。
- 例外処理はせず、再試行や降格は呼び出し側が決めます。

### 拡張

新しいメソッドは `RedisTemplate` の意味を維持します。再入・更新・公平性が必要なら専用ロック実装を導入し、ユーティリティを増やし続けないでください。

## 検証入口

- Bean 名と条件はソース事実（`RedisAutoConfiguration.java:28-50`）。
- 直列化は Spring Data Redis の挙動で、追加テストはありません。`RedisUtils` の呼び出し欠落は WP-21.S01 に記録済みです。

## リアクティブとの境界

| | 本モジュール | redis-reactive |
| --- | --- | --- |
| スレッドモデル | ブロッキング | リアクティブ |
| 適用 | Servlet サービス | WebFlux（Gateway） |
| Java ラッパー | RedisTemplate + RedisUtils | なし（依存 + 既定タイムアウト） |
| 混用の結果 | イベントループを塞ぎスループットが低下 | — |

次章：[common-redis-reactive](common-redis-reactive.jp.md)。
