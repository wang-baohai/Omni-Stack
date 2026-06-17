package com.omni.common.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.omni.common.redis.util.RedisUtils;

/**
 * Redis 自动配置类。
 * <p>提供已配置 Jackson 序列化的 {@link RedisTemplate} 和 {@link RedisUtils} 工具类。
 * Key 和 HashKey 使用 {@link StringRedisSerializer}，Value 和 HashValue 使用
 * {@link GenericJacksonJsonRedisSerializer}（自动嵌入类型信息以支持多态反序列化）。</p>
 *
 * @author Omni-Stack
 */
@AutoConfiguration
@ConditionalOnClass(RedisTemplate.class)
public class RedisAutoConfiguration {

    /**
     * RedisTemplate Bean，使用 Jackson 3 序列化 Value。
     *
     * @param connectionFactory Redis 连接工厂
     * @return 配置完成的 RedisTemplate 实例
     */
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

    /**
     * RedisUtils 工具类 Bean。
     *
     * @param redisTemplate RedisTemplate 实例
     * @return RedisUtils 工具类
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisUtils redisUtils(RedisTemplate<String, Object> redisTemplate) {
        return new RedisUtils(redisTemplate);
    }
}
