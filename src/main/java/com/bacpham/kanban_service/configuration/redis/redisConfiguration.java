package com.bacpham.kanban_service.configuration.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.web.client.RestTemplate;

@Configuration
public class redisConfiguration {
    @Value("${spring.data.redis.port}")
    private String redisPort;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Bean
    JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(Integer.parseInt(redisPort));
        return new JedisConnectionFactory(config);
    }

    @Bean
    public <K, V> RedisTemplate<K, V> redisTemplate() {
        RedisTemplate<K, V> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        template.setKeySerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        return template;
    }

    @Bean
    public <K, F, V> HashOperations<K, F, V> hashOperations(RedisTemplate<K, V> redisTemplate) {
        return redisTemplate.opsForHash();
    }

    @Bean(name = "stockDecrementScript")
    public RedisScript<Long> stockDecrementScript() {
        //language=Lua
        return RedisScript.of("""
        local stock = tonumber(redis.call("GET", KEYS[1]))
        if stock and stock > 0 then
            redis.call("DECR", KEYS[1])
            return 1
        else
            return 0
        end
    """, Long.class);
    }

    @Bean(name = "rollbackStockScript")
    public RedisScript<Long> rollbackStockScript() {
        //language=Lua
        return RedisScript.of("""
        local stock = tonumber(redis.call("GET", KEYS[1]))
        if stock then
            redis.call("INCR", KEYS[1])
            return 1
        else
            return 0
        end
    """, Long.class);
    }

    @Bean(name = "checkExpiredAndStockScript")
    public RedisScript<Long> checkExpiredAndStockScript() {
        //language=Lua
        return RedisScript.of("""
        local stock = tonumber(redis.call("GET", KEYS[1]))
        local expireAt = tonumber(redis.call("GET", KEYS[2]))
        local now = tonumber(ARGV[1])

        if stock and stock > 0 and expireAt and expireAt > now then
            redis.call("DECR", KEYS[1])
            return 1
        else
            return 0
        end
    """, Long.class);
    }

    @Bean(name = "applyCodeOnceScript")
    public RedisScript<Long> applyCodeOnceScript() {
        //language=Lua
        return RedisScript.of("""
            local userKey = KEYS[1]
            local code = ARGV[1]

            if redis.call("SISMEMBER", userKey, code) == 1 then
                return 0
            else
                redis.call("SADD", userKey, code)
                return 1
            end
        """, Long.class);
    }
    @Bean(name = "applyPromotionSafelyScript")
    public RedisScript<Long> applyPromotionSafelyScript() {
        //language=Lua
        return RedisScript.of("""
        -- KEYS[1] = stock key
        -- KEYS[2] = expire key
        -- KEYS[3] = applied set key (e.g., promotion:applied:code)
        -- ARGV[1] = current timestamp (in millis)
        -- ARGV[2] = userId

        local stock = tonumber(redis.call("GET", KEYS[1]))
        local expireAt = tonumber(redis.call("GET", KEYS[2]))
        local now = tonumber(ARGV[1])
        local userId = ARGV[2]

        if not stock or stock <= 0 then
            return -1 -- out of stock
        end

        if not expireAt or expireAt <= now then
            return -2 -- expired
        end

        if redis.call("SISMEMBER", KEYS[3], userId) == 1 then
            return 0 -- already used
        end

        redis.call("DECR", KEYS[1])
        redis.call("SADD", KEYS[3], userId)
        return 1 -- success
    """, Long.class);
    }

}
