package com.example.kafka.gc.service;

import com.example.kafka.gc.extension.JsonExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Log4j2
public class TopicMetadataService implements JsonExtension {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public boolean remove(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().getOperations().delete(key)).orElse(false);
    }

    public <T> Optional<T> get(String key, Class<T> tClass) {
        Optional<Object> maybeObj = Optional.ofNullable(redisTemplate.opsForValue().get(key));
        return maybeObj
                .map(obj -> fromJson(objectMapper, tClass, obj));
    }

    public void setWithExpire(String key, Object value, Duration expire) {
        redisTemplate.opsForValue().set(key, value, expire);
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }
}
