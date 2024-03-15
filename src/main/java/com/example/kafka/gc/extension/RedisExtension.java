package com.example.kafka.gc.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface RedisExtension extends JsonExtension {
    RedisTemplate<String, Object> redisTemplate();

    ObjectMapper objectMapper();

    default boolean remove(String key) {
        return Optional.ofNullable(redisTemplate().opsForValue().getOperations().delete(key)).orElse(false);
    }

    default <T> Optional<T> get(String key, Class<T> tClass) {
        Optional<Object> maybeObj = Optional.ofNullable(redisTemplate().opsForValue().get(key));
        return maybeObj
                .map(obj -> fromJson(objectMapper(), tClass, obj));
    }

    default void setWithExpire(String key, Object value, Duration expire) {
        redisTemplate().opsForValue().set(key, value, expire);
    }

    default void set(String key, Object value) {
        redisTemplate().opsForValue().set(key, value);
    }

    default void set(String hashKey, String key, Object value) {
        redisTemplate().opsForHash().put(key, hashKey, value);
    }

    default <T> Optional<T> get(String hashKey, String key, Class<T> clazz) {
        return Optional.ofNullable(redisTemplate().opsForHash().get(key, hashKey))
                .map(obj -> fromJson(objectMapper(), clazz, obj));
    }

    default <T> List<T> entries(String key, Class<T> clazz){
        return redisTemplate().opsForHash().entries(key)
                .entrySet().stream().map(a -> fromJson(objectMapper(), clazz, a.getValue())).toList();
    }
}
