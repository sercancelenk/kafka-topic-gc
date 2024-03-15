package com.example.kafka.gc.service;

import com.example.kafka.gc.extension.RedisExtension;
import com.example.kafka.gc.messaging.kafka.model.TopicMeasurement;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Log4j2
public class TopicMeasurementRedisService implements RedisExtension {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<TopicMeasurement> getTopicMeasurement(String clusterId, String topic){
        return get(topic, clusterId, TopicMeasurement.class);
    }

    public List<String> findTopic(String clusterId, String topicSearchTerm){
        return entries(clusterId, TopicMeasurement.class)
                .stream()
                .filter(a -> a.getTopicMetadata().getName().toLowerCase().contains(topicSearchTerm.toLowerCase()))
                .map(a -> a.getTopicMetadata().getName())
                .toList();
    }


    @Override
    public RedisTemplate<String, Object> redisTemplate() {
        return redisTemplate;
    }

    @Override
    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
